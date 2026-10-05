package com.accord.discipline

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.google.gson.Gson
import androidx.datastore.preferences.core.booleanPreferencesKey
import javax.inject.Inject

data class AutoPunishment(
    val habitId: String,
    val habitTitle: String,
    val punishmentId: String,
    val punishmentTitle: String,
    val missedDates: List<String>,
    val count: Int
)

data class AppState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isOfflineMode: Boolean = false,
    val isServerUnreachable: Boolean = false,
    val isEditingServer: Boolean = false,
    val serverUrl: String = "",
    val darkModeConfig: String = "system", // "system", "light", "dark"
    val dynamicColor: Boolean = true,
    val accentColor: String = "purple", // "purple", "red", "green", "blue", "orange", "teal"
    val backgroundSyncEnabled: Boolean = false,
    val savedUsername: String = "",
    val savedPassword: String = "",
    val user: User? = null,
    val selectedPartnerId: String? = null,
    val habits: List<Habit> = emptyList(),
    val partnerHabits: List<Habit> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val punishments: List<Punishment> = emptyList(),
    val assignedPunishments: List<AssignedPunishment> = emptyList(),
    val autoPunishments: List<AutoPunishment> = emptyList(),
    val pendingSubmissions: List<HabitSubmission> = emptyList(),
    val allSubmissions: List<HabitSubmission> = emptyList(),
    val checkIns: List<MonthlyCheckIn> = emptyList(),
    val history: List<HabitCompletion> = emptyList(),
    val stats: Stats? = null,
    val adminUsers: List<User> = emptyList(),
    val error: String? = null,
    val isTailscaleError: Boolean = false,
    val notification: String? = null
) {
    val selectedPartner: Partner?
        get() = user?.partners?.find { it.id == selectedPartnerId } ?: user?.partner
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val api: ApiService,
    private val wsManager: WebSocketManager,
    private val dataStore: DataStore<Preferences>,
    private val notificationHelper: NotificationHelper,
    private val taskDueDateChecker: TaskDueDateChecker,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        // Load saved prefs and try auto-login
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val token = prefs[PrefsKeys.TOKEN]
            val serverUrl = prefs[PrefsKeys.SERVER_URL] ?: ""
            val isOffline = prefs[PrefsKeys.OFFLINE_MODE] ?: false
            val darkMode = prefs[PrefsKeys.DARK_MODE] ?: "system"
            val dynamicColor = prefs[PrefsKeys.DYNAMIC_COLOR] ?: true
            val accentColor = prefs[PrefsKeys.ACCENT_COLOR] ?: "purple"
            val backgroundSync = prefs[PrefsKeys.BACKGROUND_SYNC] ?: false
            val savedUsername = prefs[PrefsKeys.SAVED_USERNAME] ?: ""
            val savedPassword = prefs[PrefsKeys.SAVED_PASSWORD] ?: ""
            val savedUserJson = prefs[PrefsKeys.SAVED_USER_JSON]

            val savedUser = try {
                if (savedUserJson != null) gson.fromJson(savedUserJson, User::class.java) else null
            } catch (e: Exception) { null }

            val savedSelectedPartner = prefs[PrefsKeys.SELECTED_PARTNER_ID]
            val shouldTryServer = (token != null || (savedUsername.isNotBlank() && savedPassword.isNotBlank())) && serverUrl.isNotBlank()

            _state.update { it.copy(
                serverUrl = serverUrl,
                isOfflineMode = if (shouldTryServer) false else isOffline,
                isLoggedIn = (token != null && savedUser != null) || (isOffline && !shouldTryServer),
                darkModeConfig = darkMode,
                dynamicColor = dynamicColor,
                accentColor = accentColor,
                backgroundSyncEnabled = backgroundSync,
                savedUsername = savedUsername,
                savedPassword = savedPassword,
                user = savedUser,
                selectedPartnerId = savedSelectedPartner ?: savedUser?.partnerId
            ) }

            if (shouldTryServer) {
                dataStore.edit { it[PrefsKeys.OFFLINE_MODE] = false }
                if (backgroundSync) {
                    startSyncService()
                }
                if (token != null) {
                    tryAutoLogin(serverUrl, token)
                } else {
                    login(savedUsername, savedPassword)
                }
            } else if (isOffline) {
                enterOfflineMode()
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }

        // Observe WebSocket events
        viewModelScope.launch {
            wsManager.events.collect { event -> handleWsEvent(event) }
        }
    }

    // ─── Auth ────────────────────────────────────────────────────────────────

    private fun isConnectionError(e: Throwable): Boolean {
        if (e is java.io.IOException) return true
        if (e is retrofit2.HttpException) {
            return e.code() in listOf(502, 503, 504)
        }
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("connect") || 
               msg.contains("unable to resolve host") || 
               msg.contains("timeout") || 
               msg.contains("failed to connect") ||
               msg.contains("refused") ||
               msg.contains("unreachable")
    }

    private fun mapUser(user: User): User {
        return user.copy(
            avatarUrl = makeUrlAbsolute(user.avatarUrl),
            partners = user.partners.map { p -> p.copy(avatarUrl = makeUrlAbsolute(p.avatarUrl)) }
        )
    }

    private fun updatePartnerPoints(user: User, targetPartnerId: String?, newPoints: Int): User {
        val pid = targetPartnerId ?: _state.value.selectedPartnerId ?: user.partnerId ?: return user
        return user.copy(
            partners = user.partners.map { p ->
                if (p.id == pid) p.copy(points = newPoints) else p
            }
        )
    }

    private fun updatePartnerAvatar(user: User, targetPartnerId: String, newAvatarUrl: String?): User {
        return user.copy(
            partners = user.partners.map { p ->
                if (p.id == targetPartnerId) p.copy(avatarUrl = newAvatarUrl) else p
            }
        )
    }

    private suspend fun tryAutoLogin(serverUrl: String, token: String) {
        try {
            val user = api.getMe()
            _state.update { it.copy(isServerUnreachable = false) }
            onLoginSuccess(serverUrl, token, user)
        } catch (e: Exception) {
            val isAuthError = e is retrofit2.HttpException && e.code() == 401
            val isConnErr = isConnectionError(e)
            val savedUsername = _state.value.savedUsername
            val savedPassword = _state.value.savedPassword

            if (isAuthError && savedUsername.isNotBlank() && savedPassword.isNotBlank()) {
                try {
                    val response = api.login(LoginRequest(savedUsername, savedPassword))
                    dataStore.edit {
                        it[PrefsKeys.TOKEN] = response.token
                        it[PrefsKeys.SAVED_USER_JSON] = gson.toJson(response.user)
                    }
                    _state.update { it.copy(isServerUnreachable = false) }
                    onLoginSuccess(serverUrl, response.token, response.user)
                    return
                } catch (retryEx: Exception) {
                    val retryConnErr = isConnectionError(retryEx)
                    if (retryConnErr) {
                        _state.update { it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            isServerUnreachable = true,
                            isTailscaleError = true,
                            error = "Connection failed: ${retryEx.message}"
                        ) }
                    } else {
                        _state.update { it.copy(isLoading = false, isLoggedIn = false, isServerUnreachable = false) }
                    }
                }
            } else if (isConnErr) {
                _state.update { it.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    isServerUnreachable = true,
                    isTailscaleError = true,
                    error = "Could not reach server: ${e.message}"
                ) }
            } else {
                _state.update { it.copy(isLoading = false, isLoggedIn = false, isServerUnreachable = false) }
            }
        }
    }

    fun retryConnection() = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        _state.update { it.copy(isLoading = true, error = null) }
        val token = dataStore.data.first()[PrefsKeys.TOKEN]
        val serverUrl = _state.value.serverUrl

        if (token != null) {
            try {
                val user = api.getMe()
                _state.update { it.copy(isServerUnreachable = false) }
                onLoginSuccess(serverUrl, token, user)
            } catch (e: Exception) {
                val isConnErr = isConnectionError(e)
                _state.update { it.copy(
                    isLoading = false,
                    isServerUnreachable = isConnErr,
                    isTailscaleError = isConnErr,
                    error = "Connection failed: ${e.message}"
                ) }
            }
        } else if (_state.value.savedUsername.isNotBlank() && _state.value.savedPassword.isNotBlank()) {
            login(_state.value.savedUsername, _state.value.savedPassword)
        } else {
            _state.update { it.copy(isLoading = false, isLoggedIn = false, isServerUnreachable = false) }
        }
    }

    private fun extractErrorMessage(e: Throwable): String {
        if (e is retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                if (!errorBody.isNullOrBlank()) {
                    val jsonObj = gson.fromJson(errorBody, com.google.gson.JsonObject::class.java)
                    if (jsonObj.has("error")) {
                        return jsonObj.get("error").asString
                    }
                }
            } catch (ex: Exception) { /* fallback */ }
            return when (e.code()) {
                401 -> "Invalid username or password"
                400 -> "Bad request to server"
                404 -> "Server route not found"
                500 -> "Server error"
                else -> "HTTP ${e.code()} error"
            }
        }
        if (e is java.net.ConnectException) {
            return "Cannot connect to ${_state.value.serverUrl.ifBlank { "server" }}. Check server IP / Tailscale."
        }
        if (e is java.net.UnknownHostException) {
            return "Unable to resolve server address: ${_state.value.serverUrl}"
        }
        if (e is java.net.SocketTimeoutException) {
            return "Connection timed out connecting to ${_state.value.serverUrl}"
        }
        return e.message ?: "Unknown connection error"
    }

    fun login(username: String, password: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null, isTailscaleError = false, isOfflineMode = false) }
        try {
            val response = api.login(LoginRequest(username, password))
            dataStore.edit { 
                it[PrefsKeys.TOKEN] = response.token
                it[PrefsKeys.SAVED_USERNAME] = username
                it[PrefsKeys.SAVED_PASSWORD] = password
                it[PrefsKeys.SAVED_USER_JSON] = gson.toJson(response.user)
                it[PrefsKeys.OFFLINE_MODE] = false
            }
            _state.update { it.copy(savedUsername = username, savedPassword = password, isOfflineMode = false) }
            onLoginSuccess(_state.value.serverUrl, response.token, response.user)
        } catch (e: Exception) {
            val isConnError = isConnectionError(e)
            val errMsg = extractErrorMessage(e)
            
            _state.update { it.copy(
                isLoading = false, 
                error = errMsg,
                isTailscaleError = isConnError
            ) }
        }
    }

    private suspend fun onLoginSuccess(serverUrl: String, token: String, user: User) {
        dataStore.edit { it[PrefsKeys.OFFLINE_MODE] = false }
        if (_state.value.isOfflineMode) {
            _state.update { it.copy(isOfflineMode = false) }
        }
        if (_state.value.backgroundSyncEnabled) {
            startSyncService()
        } else {
            wsManager.connect(serverUrl, token)
        }
        val mappedUser = mapUser(user)
        
        try {
            dataStore.edit { it[PrefsKeys.SAVED_USER_JSON] = gson.toJson(mappedUser) }
        } catch (e: Exception) { /* ignore store error */ }

        // Load initial habits before hiding loading screen to avoid "No habits" flash
        try {
            val habits = api.getHabits()
            val history = try { api.getHabitHistory() } catch(e: Exception) { _state.value.history }
            _state.update { it.copy(
                isLoggedIn = true,
                user = mappedUser,
                isOfflineMode = false,
                isServerUnreachable = false,
                habits = habits.sortedBy { h -> h.order },
                history = history
            ) }
            updateOfflineStats()
        } catch (e: Exception) {
            val connErr = isConnectionError(e)
            _state.update { it.copy(
                isLoggedIn = true, 
                user = mappedUser, 
                isOfflineMode = false,
                isServerUnreachable = connErr,
                isTailscaleError = connErr,
                error = "Failed to load data: ${e.message}"
            ) }
        } finally {
            _state.update { it.copy(isLoading = false) }
        }
        
        refreshAll() // Refresh the rest in the background
    }

    fun register(username: String, password: String, role: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null, isTailscaleError = false) }
        try {
            val response = api.register(RegisterRequest(username, password, role))
            dataStore.edit { 
                it[PrefsKeys.TOKEN] = response.token
                it[PrefsKeys.SAVED_USERNAME] = username
                it[PrefsKeys.SAVED_PASSWORD] = password
                it[PrefsKeys.SAVED_USER_JSON] = gson.toJson(response.user)
            }
            _state.update { it.copy(savedUsername = username, savedPassword = password) }
            onLoginSuccess(_state.value.serverUrl, response.token, response.user)
        } catch (e: Exception) {
            val isConnError = e is java.net.ConnectException || 
                             e is java.net.UnknownHostException || 
                             e is java.net.SocketTimeoutException ||
                             e.message?.contains("Unable to resolve host", ignoreCase = true) == true
            
            _state.update { it.copy(
                isLoading = false, 
                error = "Registration failed: ${e.message}",
                isTailscaleError = isConnError
            ) }
        }
    }

    private fun getUploadPart(uriString: String, baseName: String): MultipartBody.Part {
        val uri = Uri.parse(uriString)
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: throw Exception("Failed to read image")
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when {
            mimeType.contains("png", ignoreCase = true) -> ".png"
            mimeType.contains("gif", ignoreCase = true) -> ".gif"
            mimeType.contains("webp", ignoreCase = true) -> ".webp"
            mimeType.contains("svg", ignoreCase = true) -> ".svg"
            else -> ".jpg"
        }
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", "$baseName$extension", requestBody)
    }

    fun uploadAvatar(uriString: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            val updatedUser = _state.value.user?.copy(avatarUrl = uriString)
            _state.update { it.copy(user = updatedUser, notification = "Avatar updated!") }
            persistOfflineData()
            return@launch
        }
        try {
            val part = getUploadPart(uriString, "avatar_${_state.value.user?.id}")
            val response = api.uploadAvatar(part)
            val updatedUser = api.getMe()
            _state.update { it.copy(user = mapUser(updatedUser), notification = "Avatar updated!") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Upload failed: ${e.message}") }
        }
    }

    fun logout() = viewModelScope.launch {
        stopSyncService()
        dataStore.edit { 
            it.remove(PrefsKeys.TOKEN)
            it.remove(PrefsKeys.OFFLINE_MODE)
            it.remove(PrefsKeys.SAVED_USER_JSON)
        }
        wsManager.disconnect()
        _state.update { 
            AppState(
                isLoading = false, 
                serverUrl = _state.value.serverUrl, 
                isOfflineMode = false,
                savedUsername = _state.value.savedUsername,
                savedPassword = _state.value.savedPassword
            ) 
        }
    }

    fun openServerSetup() = _state.update { it.copy(isEditingServer = true) }

    fun cancelServerSetup() = _state.update { it.copy(isEditingServer = false) }

    fun setServerUrl(url: String) = viewModelScope.launch {
        var trimmed = url.trim().trimEnd('/')
        if (trimmed.isNotBlank()) {
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed = "http://$trimmed"
            }
            if (!trimmed.substringAfter("://").contains(":")) {
                trimmed = "$trimmed:3000"
            }
        }
        val urlChanged = trimmed != _state.value.serverUrl

        dataStore.edit { 
            it[PrefsKeys.SERVER_URL] = trimmed
            it[PrefsKeys.OFFLINE_MODE] = false
        }

        if (urlChanged || _state.value.isLoggedIn) {
            dataStore.edit {
                it.remove(PrefsKeys.TOKEN)
                it.remove(PrefsKeys.SAVED_USER_JSON)
            }
            stopSyncService()
            wsManager.disconnect()
        }

        _state.update { it.copy(
            serverUrl = trimmed, 
            isEditingServer = false,
            isLoggedIn = if (urlChanged) false else it.isLoggedIn,
            isOfflineMode = false,
            isServerUnreachable = false,
            error = null
        ) }
        if (trimmed.isNotBlank() && _state.value.isLoggedIn) {
            retryConnection()
        }
    }

    fun setDarkMode(mode: String) = viewModelScope.launch {
        dataStore.edit { it[PrefsKeys.DARK_MODE] = mode }
        _state.update { it.copy(darkModeConfig = mode) }
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[PrefsKeys.DYNAMIC_COLOR] = enabled }
        _state.update { it.copy(dynamicColor = enabled) }
    }

    fun setAccentColor(color: String) = viewModelScope.launch {
        dataStore.edit { it[PrefsKeys.ACCENT_COLOR] = color }
        _state.update { it.copy(accentColor = color) }
    }

    fun setBackgroundSync(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[PrefsKeys.BACKGROUND_SYNC] = enabled }
        _state.update { it.copy(backgroundSyncEnabled = enabled) }
        if (enabled && _state.value.isLoggedIn && !_state.value.isOfflineMode) {
            startSyncService()
        } else {
            stopSyncService()
        }
    }

    private fun startSyncService() {
        try {
            val intent = Intent(context, WebSocketService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopSyncService() {
        try {
            val intent = Intent(context, WebSocketService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSystemNotification(title: String, message: String) {
        if (!_state.value.backgroundSyncEnabled) {
            notificationHelper.showNotification(title, message)
        }
    }

    fun enterOfflineMode() = viewModelScope.launch {
        stopSyncService()
        dataStore.edit { it[PrefsKeys.OFFLINE_MODE] = true }
        
        val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
        
        val habitsJson = prefs.getString("habits", "[]")
        val rewardsJson = prefs.getString("rewards", "[]")
        val punishmentsJson = prefs.getString("punishments", "[]")
        val historyJson = prefs.getString("history", "[]")
        val assignedJson = prefs.getString("assigned_punishments", "[]")
        val checkInsJson = prefs.getString("check_ins", "[]")
        val pendingJson = prefs.getString("pending_submissions", "[]")
        val userPoints = prefs.getInt("user_points", 0)
        val avatarUrl = prefs.getString("user_avatar_url", null)
        val username = prefs.getString("user_username", "Solo User")

        val habits = try { gson.fromJson(habitsJson, Array<Habit>::class.java).toList() } catch(e: Exception) { emptyList() }
        val rewards = try { gson.fromJson(rewardsJson, Array<Reward>::class.java).toList() } catch(e: Exception) { emptyList() }
        val punishments = try { gson.fromJson(punishmentsJson, Array<Punishment>::class.java).toList() } catch(e: Exception) { emptyList() }
        val history = try { gson.fromJson(historyJson, Array<HabitCompletion>::class.java).toList() } catch(e: Exception) { emptyList() }
        val assigned = try { gson.fromJson(assignedJson, Array<AssignedPunishment>::class.java).toList() } catch(e: Exception) { emptyList() }
        val checkIns = try { gson.fromJson(checkInsJson, Array<MonthlyCheckIn>::class.java).toList() } catch(e: Exception) { emptyList() }
        val pending = try { gson.fromJson(pendingJson, Array<HabitSubmission>::class.java).toList() } catch(e: Exception) { emptyList() }
        val allSubmissionsJson = prefs.getString("all_submissions", pendingJson)
        val allSubmissionsList = try { gson.fromJson(allSubmissionsJson, Array<HabitSubmission>::class.java).toList() } catch(e: Exception) { pending }

        _state.update { it.copy(
            isOfflineMode = true,
            isLoggedIn = true,
            isLoading = false,
            user = User(
                id = "offline_user",
                username = username ?: "Solo User",
                role = "solo",
                points = userPoints,
                pairCode = null,
                avatarUrl = avatarUrl
            ),
            habits = habits,
            rewards = rewards,
            punishments = punishments,
            history = history,
            assignedPunishments = assigned,
            checkIns = checkIns,
            pendingSubmissions = pending,
            allSubmissions = allSubmissionsList
        ) }
        updateOfflineStats()
    }

    // ─── Pairing & Partner Switcher ──────────────────────────────────────────

    fun selectPartner(partnerId: String) = viewModelScope.launch {
        dataStore.edit { it[PrefsKeys.SELECTED_PARTNER_ID] = partnerId }
        _state.update { it.copy(selectedPartnerId = partnerId) }
        refreshAll()
    }

    fun pairWithPartner(pairCode: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { it.copy(error = "Pairing requires a server connection.") }
            return@launch
        }
        try {
            val response = api.pair(PairRequest(pairCode))
            val updatedUser = api.getMe()
            val mappedUser = mapUser(updatedUser)
            val newSelected = mappedUser.partners.find { it.pairCode == pairCode.uppercase() }?.id ?: mappedUser.partnerId
            _state.update { s ->
                s.copy(user = mappedUser, selectedPartnerId = newSelected ?: s.selectedPartnerId)
            }
            refreshAll()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Pairing failed: ${e.message}") }
        }
    }

    fun unpair(targetPartnerId: String? = null) = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        try {
            val pid = targetPartnerId ?: _state.value.selectedPartnerId ?: _state.value.user?.partnerId
            api.unpair(UnpairRequest(partnerId = pid))
            val updatedUser = api.getMe()
            val mappedUser = mapUser(updatedUser)
            val newSelected = mappedUser.partners.firstOrNull()?.id
            _state.update { s ->
                s.copy(user = mappedUser, selectedPartnerId = newSelected)
            }
            refreshAll()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Unpairing failed: ${e.message}") }
        }
    }

    // ─── Habits ──────────────────────────────────────────────────────────────

    fun refreshHabits() = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            updateOfflineStats()
            return@launch
        }
        _state.update { it.copy(isRefreshing = true) }
        try {
            val habits = api.getHabits()
            val history = try { api.getHabitHistory() } catch(e: Exception) { _state.value.history }
            
            // In unified mode, /habits returns everything for the pair
            _state.update { it.copy(
                habits = habits.sortedBy { h -> h.order }, 
                partnerHabits = emptyList(), 
                history = history,
                isServerUnreachable = false
            ) }

            // Also refresh user data just in case points/partner changed
            val me = api.getMe()
            _state.update { it.copy(user = mapUser(me)) }

            updateOfflineStats() // Recalculate auto punishments based on server data too
            refreshSubmissions()
        } catch (e: Exception) {
            val connErr = isConnectionError(e)
            _state.update { it.copy(
                error = "Failed to load habits: ${e.message}",
                isServerUnreachable = if (connErr) true else it.isServerUnreachable,
                isTailscaleError = if (connErr) true else it.isTailscaleError
            ) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun pauseHabit(habitId: String) = viewModelScope.launch {
        val habit = (_state.value.habits + _state.value.partnerHabits).find { it.id == habitId } ?: return@launch
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(habits = s.habits.map {
                    if (it.id == habitId) it.copy(isPaused = true) else it
                })
            }
            updateOfflineStats()
            return@launch
        }
        try {
            val response = api.updateHabit(habitId, CreateHabitRequest(
                title = habit.title,
                description = habit.description,
                frequency = habit.frequency,
                weeklyTargetCount = habit.weeklyTargetCount,
                pointsReward = habit.pointsReward,
                pointsPenalty = habit.pointsPenalty,
                penaltyPunishmentId = habit.penaltyPunishmentId,
                lastPenaltyResetDate = habit.lastPenaltyResetDate,
                dueDate = habit.dueDate,
                requiresPhoto = habit.requiresPhoto,
                isNsfw = habit.isNsfw,
                isPositive = habit.isPositive,
                isPaused = true
            ))
            _state.update { s ->
                s.copy(
                    habits = s.habits.map { if (it.id == habitId) response else it },
                    partnerHabits = s.partnerHabits.map { if (it.id == habitId) response else it }
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to pause habit: ${e.message}") }
        }
    }

    fun resumeHabit(habitId: String) = viewModelScope.launch {
        val habit = (_state.value.habits + _state.value.partnerHabits).find { it.id == habitId } ?: return@launch
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(habits = s.habits.map {
                    if (it.id == habitId) it.copy(isPaused = false) else it
                })
            }
            updateOfflineStats()
            return@launch
        }
        try {
            val response = api.updateHabit(habitId, CreateHabitRequest(
                title = habit.title,
                description = habit.description,
                frequency = habit.frequency,
                weeklyTargetCount = habit.weeklyTargetCount,
                pointsReward = habit.pointsReward,
                pointsPenalty = habit.pointsPenalty,
                penaltyPunishmentId = habit.penaltyPunishmentId,
                lastPenaltyResetDate = habit.lastPenaltyResetDate,
                dueDate = habit.dueDate,
                requiresPhoto = habit.requiresPhoto,
                isNsfw = habit.isNsfw,
                isPositive = habit.isPositive,
                isPaused = false
            ))
            _state.update { s ->
                s.copy(
                    habits = s.habits.map { if (it.id == habitId) response else it },
                    partnerHabits = s.partnerHabits.map { if (it.id == habitId) response else it }
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to resume habit: ${e.message}") }
        }
    }

    fun createHabit(title: String, description: String?, frequency: String, pointsReward: Int, isPositive: Boolean, penaltyPunishmentId: String? = null, requiresPhoto: Boolean = false, isNsfw: Boolean = false, weeklyTargetCount: Int = 1, dueDate: String? = null) =
        viewModelScope.launch {
            if (_state.value.isOfflineMode) {
                val newHabit = Habit(
                    id = java.util.UUID.randomUUID().toString(),
                    ownerId = "offline_user",
                    title = title,
                    description = description,
                    frequency = frequency,
                    weeklyTargetCount = weeklyTargetCount,
                    pointsReward = pointsReward,
                    pointsPenalty = 0,
                    penaltyPunishmentId = penaltyPunishmentId,
                    lastPenaltyResetDate = null,
                    dueDate = dueDate,
                    requiresPhoto = requiresPhoto,
                    isNsfw = isNsfw,
                    isPositive = isPositive,
                    isPaused = false,
                    completed = false,
                    order = _state.value.habits.size,
                    createdAt = System.currentTimeMillis()
                )
                _state.update { it.copy(habits = it.habits + newHabit) }
                updateOfflineStats()
                return@launch
            }
            try {
                val habit = api.createHabit(CreateHabitRequest(
                    title = title,
                    description = description,
                    frequency = frequency,
                    weeklyTargetCount = weeklyTargetCount,
                    pointsReward = pointsReward,
                    pointsPenalty = 0,
                    penaltyPunishmentId = penaltyPunishmentId,
                    dueDate = dueDate,
                    requiresPhoto = requiresPhoto,
                    isNsfw = isNsfw,
                    isPositive = isPositive,
                    isPaused = false,
                    order = _state.value.habits.size
                ))
                _state.update { it.copy(habits = it.habits + habit) }
                refreshHabits()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to create habit: ${e.message}") }
            }
        }

    fun updateHabit(habitId: String, title: String, description: String?, frequency: String, pointsReward: Int, isPositive: Boolean, penaltyPunishmentId: String? = null, requiresPhoto: Boolean = false, isNsfw: Boolean = false, weeklyTargetCount: Int = 1, dueDate: String? = null) =
        viewModelScope.launch {
            if (_state.value.isOfflineMode) {
                _state.update { s ->
                    s.copy(
                        habits = s.habits.map {
                            if (it.id == habitId) it.copy(
                                title = title,
                                description = description,
                                frequency = frequency,
                                weeklyTargetCount = weeklyTargetCount,
                                pointsReward = pointsReward,
                                isPositive = isPositive,
                                penaltyPunishmentId = penaltyPunishmentId,
                                dueDate = dueDate,
                                requiresPhoto = requiresPhoto,
                                isNsfw = isNsfw
                            ) else it
                        },
                        pendingSubmissions = s.pendingSubmissions.map {
                            if (it.habitId == habitId) it.copy(isNsfw = isNsfw) else it
                        },
                        allSubmissions = s.allSubmissions.map {
                            if (it.habitId == habitId) it.copy(isNsfw = isNsfw) else it
                        }
                    )
                }
                updateOfflineStats()
                return@launch
            }
            try {
                val currentHabit = (_state.value.habits + _state.value.partnerHabits).find { it.id == habitId }
                val habit = api.updateHabit(habitId, CreateHabitRequest(
                    title = title,
                    description = description,
                    frequency = frequency,
                    weeklyTargetCount = weeklyTargetCount,
                    pointsReward = pointsReward,
                    pointsPenalty = currentHabit?.pointsPenalty ?: 0,
                    penaltyPunishmentId = penaltyPunishmentId,
                    lastPenaltyResetDate = currentHabit?.lastPenaltyResetDate,
                    dueDate = dueDate,
                    requiresPhoto = requiresPhoto,
                    isNsfw = isNsfw,
                    isPositive = isPositive,
                    isPaused = currentHabit?.isPaused ?: false,
                    order = currentHabit?.order ?: 0
                ))
                _state.update { s ->
                    s.copy(
                        habits = s.habits.map { if (it.id == habitId) habit else it },
                        partnerHabits = s.partnerHabits.map { if (it.id == habitId) habit else it }
                    )
                }
                refreshHabits()
                refreshSubmissions()
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to update habit: ${e.message}") }
            }
        }

    fun resetTask(habitId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(
                    habits = s.habits.map {
                        if (it.id == habitId) it.copy(completed = false) else it
                    },
                    history = s.history.filterNot { it.habitId == habitId }
                )
            }
            updateOfflineStats()
            return@launch
        }

        // Optimistic UI update: remove task completions from history without deducting points
        val previousState = _state.value
        _state.update { s ->
            s.copy(
                habits = s.habits.map {
                    if (it.id == habitId) it.copy(completed = false) else it
                },
                history = s.history.filterNot { it.habitId == habitId }
            )
        }

        try {
            val response = api.resetTask(habitId)
            _state.update { s ->
                val currentUser = s.user ?: return@update s
                if (currentUser.role == "dominant") {
                    s.copy(user = updatePartnerPoints(currentUser, s.selectedPartnerId, response.points))
                } else {
                    s.copy(user = currentUser.copy(points = response.points))
                }
            }
            refreshHabits()
            refreshStats()
        } catch (e: Exception) {
            _state.update { previousState.copy(error = "Failed to reset task: ${e.message}") }
        }
    }

    fun deleteHabit(habitId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { it.copy(habits = it.habits.filter { h -> h.id != habitId }) }
            updateOfflineStats()
            return@launch
        }
        try {
            api.deleteHabit(habitId)
            _state.update { it.copy(habits = it.habits.filter { h -> h.id != habitId }) }
            refreshHabits()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to delete habit: ${e.message}") }
        }
    }

    fun moveHabit(fromIndex: Int, toIndex: Int) {
        val allHabits = _state.value.habits
        val activeHabits = allHabits.filter { !it.isPaused }
        
        if (fromIndex in activeHabits.indices && toIndex in activeHabits.indices) {
            val habitToMove = activeHabits[fromIndex]
            val targetHabit = activeHabits[toIndex]
            
            val fullList = allHabits.toMutableList()
            val fromIdxFull = fullList.indexOf(habitToMove)
            val toIdxFull = fullList.indexOf(targetHabit)
            
            if (fromIdxFull != -1 && toIdxFull != -1) {
                val item = fullList.removeAt(fromIdxFull)
                fullList.add(toIdxFull, item)
                val updatedList = fullList.mapIndexed { index, habit -> habit.copy(order = index) }
                _state.update { it.copy(habits = updatedList) }
                
                if (!_state.value.isOfflineMode) {
                    viewModelScope.launch {
                        try {
                            api.reorderHabits(updatedList.map { it.id })
                        } catch (e: Exception) {
                            _state.update { it.copy(error = "Failed to sync order: ${e.message}") }
                        }
                    }
                } else {
                    updateOfflineStats()
                }
            }
        }
    }

    fun moveReward(fromIndex: Int, toIndex: Int) {
        val list = _state.value.rewards.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            val updatedList = list.mapIndexed { index, reward -> reward.copy(order = index) }
            _state.update { it.copy(rewards = updatedList) }
            
            if (!_state.value.isOfflineMode) {
                viewModelScope.launch {
                    try {
                        api.reorderRewards(updatedList.map { it.id })
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Failed to sync order: ${e.message}") }
                    }
                }
            } else {
                updateOfflineStats()
            }
        }
    }

    fun movePunishment(fromIndex: Int, toIndex: Int) {
        val list = _state.value.punishments.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            val updatedList = list.mapIndexed { index, punishment -> punishment.copy(order = index) }
            _state.update { it.copy(punishments = updatedList) }
            
            if (!_state.value.isOfflineMode) {
                viewModelScope.launch {
                    try {
                        api.reorderPunishments(updatedList.map { it.id })
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Failed to sync order: ${e.message}") }
                    }
                }
            } else {
                updateOfflineStats()
            }
        }
    }

    fun toggleHabit(habitId: String, date: String = LocalDate.now().toString()) = viewModelScope.launch {
        val habit = (_state.value.habits + _state.value.partnerHabits).find { it.id == habitId } ?: return@launch
        val targetLocalDate = try { LocalDate.parse(date) } catch(e: Exception) { LocalDate.now() }
        val isCompleted = isHabitCompleted(habit, _state.value.history, targetLocalDate)
        if (!habit.isPositive) {
            // Avoid habit:
            // if currently checked (default) -> tapping it marks incomplete / miss (completeHabit)
            // if currently marked incomplete (red X) -> tapping it clears miss (uncompleteHabit)
            if (isCompleted) completeHabit(habitId, date)
            else uncompleteHabit(habitId, date)
        } else {
            if (isCompleted) uncompleteHabit(habitId, date)
            else completeHabit(habitId, date)
        }
    }

    fun completeHabit(habitId: String, date: String = LocalDate.now().toString()) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            val habit = _state.value.habits.find { it.id == habitId } ?: _state.value.partnerHabits.find { it.id == habitId }
            habit?.let { h ->
                val newCompletion = HabitCompletion(habitId, date)
                val pointsChange = if (!h.isPositive) -h.pointsPenalty else h.pointsReward
                _state.update { s -> s.copy(
                    history = s.history + newCompletion,
                    user = s.user?.copy(points = maxOf(0, (s.user.points) + pointsChange))
                ) }
                updateOfflineStats()
            }
            return@launch
        }

        // Optimistic UI update
        val previousState = _state.value
        _state.update { s ->
            s.copy(
                habits = s.habits.map {
                    if (it.id == habitId) it.copy(completed = true) else it
                },
                history = if (s.history.none { it.habitId == habitId && it.date == date }) {
                    s.history + HabitCompletion(habitId, date)
                } else s.history
            )
        }

        try {
            val response = api.completeHabit(habitId, CompleteHabitRequest(date))
            _state.update { s ->
                val currentUser = s.user ?: return@update s
                if (currentUser.role == "dominant") {
                    s.copy(user = updatePartnerPoints(currentUser, s.selectedPartnerId, response.points))
                } else {
                    s.copy(user = currentUser.copy(points = response.points))
                }
            }
            refreshHabits()
            refreshStats()
        } catch (e: Exception) {
            // Handle 409 Conflict - usually means it's already completed on server
            if (e is retrofit2.HttpException && e.code() == 409) {
                // Keep the optimistic state and just sync
                refreshHabits()
                refreshStats()
            } else {
                // Revert optimistic update on failure
                _state.update { previousState.copy(error = "Failed: ${e.message}") }
            }
        }
    }

    fun uncompleteHabit(habitId: String, date: String = LocalDate.now().toString()) = viewModelScope.launch {
        val habitCompletions = _state.value.history.filter { it.habitId == habitId }
        val targetDate = if (habitCompletions.any { it.date == date }) {
            date
        } else {
            habitCompletions.maxOfOrNull { it.date } ?: date
        }

        if (_state.value.isOfflineMode) {
            val habit = _state.value.habits.find { it.id == habitId } ?: _state.value.partnerHabits.find { it.id == habitId }
            habit?.let { h ->
                val pointsChange = if (!h.isPositive) h.pointsPenalty else -h.pointsReward
                _state.update { s -> s.copy(
                    history = s.history.filterNot { it.habitId == habitId && it.date == targetDate },
                    user = s.user?.copy(points = maxOf(0, (s.user.points) + pointsChange))
                ) }
                updateOfflineStats()
            }
            return@launch
        }

        // Optimistic UI update
        val previousState = _state.value
        val targetHabit = (_state.value.habits + _state.value.partnerHabits).find { it.id == habitId }
        val pointsToDeduct = targetHabit?.pointsReward ?: 0
        _state.update { s ->
            val currentUser = s.user ?: return@update s
            s.copy(
                user = if (currentUser.role == "submissive") currentUser.copy(points = maxOf(0, currentUser.points - pointsToDeduct)) else currentUser,
                habits = s.habits.map {
                    if (it.id == habitId) it.copy(completed = false) else it
                },
                history = s.history.filterNot { it.habitId == habitId && it.date == targetDate }
            )
        }

        try {
            val response = api.uncompleteHabit(habitId, CompleteHabitRequest(targetDate))
            _state.update { s ->
                val currentUser = s.user ?: return@update s
                if (currentUser.role == "dominant") {
                    s.copy(user = updatePartnerPoints(currentUser, s.selectedPartnerId, response.points))
                } else {
                    s.copy(user = currentUser.copy(points = response.points))
                }
            }
            refreshHabits()
            refreshStats()
        } catch (e: Exception) {
            // Revert optimistic update on failure
            _state.update { previousState.copy(error = "Failed: ${e.message}") }
        }
    }

    // ─── Submissions ─────────────────────────────────────────────────────────

    fun refreshSubmissions() = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        try {
            val pending = api.getPendingSubmissions().map { it.copy(photoUrl = makeUrlAbsolute(it.photoUrl) ?: "") }
            val all = try { 
                api.getAllSubmissions().map { it.copy(photoUrl = makeUrlAbsolute(it.photoUrl) ?: "") }
            } catch (e: Exception) { 
                pending 
            }
            _state.update { it.copy(pendingSubmissions = pending, allSubmissions = all, isServerUnreachable = false) }
        } catch (e: Exception) {
            val connErr = isConnectionError(e)
            _state.update { it.copy(
                error = "Failed to load submissions: ${e.message}",
                isServerUnreachable = if (connErr) true else it.isServerUnreachable,
                isTailscaleError = if (connErr) true else it.isTailscaleError
            ) }
        }
    }

    fun submitHabitPhoto(habitId: String, uriString: String, date: String = LocalDate.now().toString()) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            val habit = _state.value.habits.find { it.id == habitId } ?: return@launch
            val submission = HabitSubmission(
                id = java.util.UUID.randomUUID().toString(),
                habitId = habitId,
                userId = "offline_user",
                username = _state.value.user?.username ?: "Solo User",
                habitTitle = habit.title,
                date = date,
                photoUrl = uriString, // Uri string works fine locally with Coil
                status = "approved",
                submittedAt = System.currentTimeMillis(),
                isNsfw = habit.isNsfw
            )
            _state.update { it.copy(
                pendingSubmissions = it.pendingSubmissions + submission,
                allSubmissions = it.allSubmissions + submission
            ) }
            completeHabit(habitId, date)
            _state.update { it.copy(notification = "Photo proof submitted & habit completed!") }
            return@launch
        }
        try {
            _state.update { it.copy(isLoading = true) }
            
            // 1. Upload the image to the Pi server
            val part = getUploadPart(uriString, "habit_${habitId}")
            val uploadResponse = api.uploadHabitImage(part)
            
            // 2. Submit the resulting URL as proof
            api.submitHabitPhoto(habitId, uploadResponse.url, date)
            
            _state.update { it.copy(isLoading = false, notification = "Photo submitted for review!") }
            refreshSubmissions()
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Submission failed: ${e.message}") }
        }
    }

    fun approveSubmission(submissionId: String) = viewModelScope.launch {
        val sub = _state.value.pendingSubmissions.find { it.id == submissionId } ?: _state.value.allSubmissions.find { it.id == submissionId }
        val submissionDate = sub?.date ?: LocalDate.now().toString()

        if (_state.value.isOfflineMode) {
            if (sub != null) {
                completeHabit(sub.habitId, sub.date)
            }
            _state.update { s ->
                s.copy(
                    pendingSubmissions = s.pendingSubmissions.filter { it.id != submissionId },
                    allSubmissions = s.allSubmissions.map { item ->
                        if (item.id == submissionId) item.copy(status = "approved") else item
                    },
                    notification = "Submission approved!"
                )
            }
            persistOfflineData()
            return@launch
        }
        try {
            val response = api.approveSubmission(submissionId, submissionDate, CompleteHabitRequest(submissionDate))
            _state.update { s ->
                val currentUser = s.user ?: return@update s
                val updatedUser = if (currentUser.role == "dominant") {
                    updatePartnerPoints(currentUser, s.selectedPartnerId, response.points)
                } else {
                    currentUser.copy(points = response.points)
                }
                val subHabitId = sub?.habitId
                val updatedHistory = if (subHabitId != null && s.history.none { it.habitId == subHabitId && it.date == submissionDate }) {
                    s.history + HabitCompletion(subHabitId, submissionDate)
                } else {
                    s.history
                }
                s.copy(
                    user = updatedUser,
                    history = updatedHistory,
                    pendingSubmissions = s.pendingSubmissions.filter { it.id != submissionId },
                    allSubmissions = s.allSubmissions.map { item ->
                        if (item.id == submissionId) item.copy(status = "approved") else item
                    },
                    notification = "Submission approved!"
                )
            }
            refreshHabits()
            refreshStats()
            refreshSubmissions()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Approval failed: ${e.message}") }
        }
    }

    fun rejectSubmission(submissionId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(
                    pendingSubmissions = s.pendingSubmissions.filter { it.id != submissionId },
                    allSubmissions = s.allSubmissions.map { item ->
                        if (item.id == submissionId) item.copy(status = "rejected") else item
                    },
                    notification = "Submission rejected"
                )
            }
            persistOfflineData()
            return@launch
        }
        try {
            api.rejectSubmission(submissionId)
            _state.update { it.copy(
                pendingSubmissions = it.pendingSubmissions.filter { it.id != submissionId },
                notification = "Submission rejected"
            ) }
            refreshSubmissions()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Rejection failed: ${e.message}") }
        }
    }

    fun deleteSubmission(submissionId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(
                    pendingSubmissions = s.pendingSubmissions.filter { it.id != submissionId },
                    allSubmissions = s.allSubmissions.filter { it.id != submissionId },
                    notification = "Proof submission deleted"
                )
            }
            persistOfflineData()
            return@launch
        }
        try {
            api.deleteSubmission(submissionId)
            _state.update { s ->
                s.copy(
                    pendingSubmissions = s.pendingSubmissions.filter { it.id != submissionId },
                    allSubmissions = s.allSubmissions.filter { it.id != submissionId },
                    notification = "Proof submission deleted"
                )
            }
            refreshSubmissions()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Delete submission failed: ${e.message}") }
        }
    }

    // ─── Check-ins ───────────────────────────────────────────────────────────

    fun refreshCheckIns() = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        try {
            val checkIns = api.getCheckIns()
            _state.update { it.copy(checkIns = checkIns, isServerUnreachable = false) }
        } catch (e: Exception) {
            val connErr = isConnectionError(e)
            _state.update { it.copy(
                error = "Failed to load check-ins: ${e.message}",
                isServerUnreachable = if (connErr) true else it.isServerUnreachable,
                isTailscaleError = if (connErr) true else it.isTailscaleError
            ) }
        }
    }

    fun submitCheckIn(reflections: String, goalProgress: String, rating: Int) = viewModelScope.launch {
        val month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        if (_state.value.isOfflineMode) {
            val newCheckIn = MonthlyCheckIn(
                id = java.util.UUID.randomUUID().toString(),
                month = month,
                userId = "offline_user",
                username = _state.value.user?.username ?: "Solo User",
                reflections = reflections,
                goalProgress = goalProgress,
                rating = rating,
                createdAt = System.currentTimeMillis()
            )
            _state.update { it.copy(checkIns = it.checkIns + newCheckIn, notification = "Check-in submitted!") }
            persistOfflineData()
            return@launch
        }
        try {
            val checkIn = api.submitCheckIn(MonthlyCheckInRequest(month, reflections, goalProgress, rating))
            _state.update { it.copy(checkIns = it.checkIns + checkIn, notification = "Check-in submitted!") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Check-in failed: ${e.message}") }
        }
    }

    private fun persistOfflineData() {
        if (!_state.value.isOfflineMode) return
        val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("habits", gson.toJson(_state.value.habits))
            putString("rewards", gson.toJson(_state.value.rewards))
            putString("punishments", gson.toJson(_state.value.punishments))
            putString("history", gson.toJson(_state.value.history))
            putString("assigned_punishments", gson.toJson(_state.value.assignedPunishments))
            putString("check_ins", gson.toJson(_state.value.checkIns))
            putString("pending_submissions", gson.toJson(_state.value.pendingSubmissions))
            putString("all_submissions", gson.toJson(_state.value.allSubmissions))
            putInt("user_points", _state.value.user?.points ?: 0)
            putString("user_avatar_url", _state.value.user?.avatarUrl)
            putString("user_username", _state.value.user?.username ?: "Solo User")
            apply()
        }
    }

    private fun updateOfflineStats() {
        val habits = _state.value.habits
        var history = _state.value.history
        val punishments = _state.value.punishments
        val today = LocalDate.now()

        // 0. Offline end-of-day evaluation for untouched avoid habits
        if (_state.value.isOfflineMode) {
            val mutableHistory = history.toMutableList()

            habits.filter { !it.isPaused && !it.isPositive }.forEach { habit ->
                val habitStart = java.time.Instant.ofEpochMilli(habit.createdAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                var d = habitStart
                while (d.isBefore(today)) {
                    val dateStr = d.toString()
                    val hasRecord = mutableHistory.any { it.habitId == habit.id && it.date == dateStr }
                    if (!hasRecord) {
                        mutableHistory.add(HabitCompletion(habit.id, dateStr))
                    }
                    d = d.plusDays(1)
                }
            }

            if (mutableHistory.size != history.size) {
                history = mutableHistory
                _state.update { s -> s.copy(
                    history = mutableHistory
                ) }
            }
        }
        
        // Filter history to positive habits only for statistics
        val positiveHabitIds = habits.filter { it.isPositive }.map { it.id }.toSet()
        val positiveHistory = history.filter { positiveHabitIds.contains(it.habitId) }

        // 1. Regular stats
        val completionsByDay = positiveHistory.groupBy { it.date }.map { (date, list) ->
            DayCompletion(date, list.size)
        }.sortedBy { it.date }

        val positiveDailyHabitIds = habits.filter { it.isPositive && it.frequency == "daily" }.map { it.id }.toSet()
        var streak = 0
        if (positiveDailyHabitIds.isNotEmpty()) {
            var checkDate = LocalDate.now()
            while (positiveHistory.any { it.date == checkDate.toString() && positiveDailyHabitIds.contains(it.habitId) }) {
                streak++
                checkDate = checkDate.minusDays(1)
            }
        }

        val calculatedStats = Stats(
            totalHabits = habits.size,
            totalCompletionsLast30Days = positiveHistory.count { 
                val habitDate = try { LocalDate.parse(it.date) } catch(e: Exception) { null }
                habitDate?.isAfter(LocalDate.now().minusDays(30)) ?: false
            },
            currentPoints = _state.value.user?.points ?: 0,
            streak = streak,
            completionsByDay = completionsByDay,
            recentCompletions = positiveHistory.takeLast(10).map { comp ->
                val h = habits.find { it.id == comp.habitId }
                RecentCompletion(comp.habitId, 0, h?.title ?: "Unknown", h?.frequency ?: "daily")
            }
        )

        // 2. Auto-punishments for missed habits
        val autoPunishments = mutableListOf<AutoPunishment>()

        habits.filter { !it.isPaused }.forEach { habit ->
            val pId = habit.penaltyPunishmentId
            if (pId != null) {
                val punishment = punishments.find { it.id == pId }
                if (punishment != null) {
                    val habitStart = java.time.Instant.ofEpochMilli(habit.createdAt)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    
                    val resetDate = habit.lastPenaltyResetDate?.let { 
                        try { LocalDate.parse(it) } catch(e: Exception) { null } 
                    } ?: habitStart
                    
                    val missedDates = mutableListOf<String>()
                    var d = resetDate
                    while (d.isBefore(today)) {
                        try {
                            if (habit.frequency == "daily") {
                                val done = if (!habit.isPositive) {
                                    !history.any { it.habitId == habit.id && it.date == d.toString() }
                                } else {
                                    history.any { it.habitId == habit.id && it.date == d.toString() }
                                }
                                if (!done) missedDates.add(d.toString())
                                d = d.plusDays(1)
                            } else if (habit.frequency == "weekly") {
                                // Find Monday of the week for date 'd'
                                var monday = d
                                while (monday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
                                    monday = monday.minusDays(1)
                                }
                                val sunday = monday.plusDays(6)
                                
                                // Only process if the entire week has passed (Sunday is before today)
                                if (sunday.isBefore(today)) {
                                    val completionsInWeek = history.count { 
                                        it.habitId == habit.id && 
                                        !LocalDate.parse(it.date).isBefore(monday) && 
                                        !LocalDate.parse(it.date).isAfter(sunday) 
                                    }
                                    if (completionsInWeek < habit.weeklyTargetCount) {
                                        missedDates.add("Week of ${monday.format(DateTimeFormatter.ofPattern("MMM d"))}")
                                    }
                                    d = sunday.plusDays(1) // Move to next Monday
                                } else {
                                    break // Current week not finished
                                }
                            } else {
                                d = d.plusDays(1)
                            }
                        } catch(e: Exception) {
                            d = d.plusDays(1)
                        }
                    }
                    
                    if (missedDates.isNotEmpty()) {
                        autoPunishments.add(AutoPunishment(
                            habitId = habit.id,
                            habitTitle = habit.title,
                            punishmentId = pId,
                            punishmentTitle = punishment.title,
                            missedDates = missedDates,
                            count = missedDates.size
                        ))
                    }
                }
            }
        }

        _state.update { s ->
            s.copy(
                stats = if (s.isOfflineMode) calculatedStats else s.stats,
                autoPunishments = autoPunishments
            )
        }
        
        if (_state.value.isOfflineMode) {
            persistOfflineData()
        }

        taskDueDateChecker.checkTaskDueDates(context, _state.value.habits + _state.value.partnerHabits, _state.value.history)
    }

    fun clearPunishment(habitId: String) = viewModelScope.launch {
        // Sets the reset date to today, so all previous misses are ignored
        val today = LocalDate.now().toString()
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(habits = s.habits.map {
                    if (it.id == habitId) it.copy(lastPenaltyResetDate = today) else it
                })
            }
            updateOfflineStats()
            return@launch
        }
        // In online mode, we'd need a specific API for this.
        // For now, let's assume we update the habit on the server if possible.
        try {
            val habit = (_state.value.habits + _state.value.partnerHabits).find { it.id == habitId } ?: return@launch
            api.updateHabit(habitId, CreateHabitRequest(
                title = habit.title,
                description = habit.description,
                frequency = habit.frequency,
                weeklyTargetCount = habit.weeklyTargetCount,
                pointsReward = habit.pointsReward,
                pointsPenalty = habit.pointsPenalty,
                penaltyPunishmentId = habit.penaltyPunishmentId,
                lastPenaltyResetDate = today,
                dueDate = habit.dueDate,
                requiresPhoto = habit.requiresPhoto,
                isNsfw = habit.isNsfw,
                isPositive = habit.isPositive,
                isPaused = habit.isPaused,
                order = habit.order
            ))
            refreshHabits()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to clear penalty: ${e.message}") }
        }
    }

    // ─── Rewards ─────────────────────────────────────────────────────────────

    fun refreshRewards() = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        _state.update { it.copy(isRefreshing = true) }
        try {
            val rewards = api.getRewards()
            val mapped = rewards.map { it.copy(imageUrl = makeUrlAbsolute(it.imageUrl)) }
            _state.update { it.copy(rewards = mapped.sortedBy { r -> r.order }, isServerUnreachable = false) }
            updateOfflineStats()
        } catch (e: Exception) {
            val connErr = isConnectionError(e)
            _state.update { it.copy(
                error = "Failed to load rewards: ${e.message}",
                isServerUnreachable = if (connErr) true else it.isServerUnreachable,
                isTailscaleError = if (connErr) true else it.isTailscaleError
            ) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private fun makeUrlAbsolute(url: String?): String? {
        if (url == null || url.isBlank()) return null
        if (!isImageUrl(url)) return url
        if (url.startsWith("http")) return url
        if (url.startsWith("content://") || url.startsWith("file://")) return url
        
        var base = _state.value.serverUrl.trim().trimEnd('/')
        if (base.isEmpty()) base = "http://10.0.2.2:3000"
        if (!base.startsWith("http")) base = "http://$base"
        if (!base.substringAfter("://").contains(":")) base = "$base:3000"
        
        val relativePath = if (url.startsWith("/")) url else "/$url"
        return base + relativePath
    }

    fun createReward(title: String, description: String?, cost: Int, uriString: String? = null, type: String = "recurring") = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            val newReward = Reward(
                id = java.util.UUID.randomUUID().toString(),
                ownerId = "offline_user",
                title = title,
                description = description,
                cost = cost,
                imageUrl = uriString,
                type = type,
                redeemed = false,
                createdAt = System.currentTimeMillis()
            )
            _state.update { it.copy(rewards = it.rewards + newReward) }
            return@launch
        }
        try {
            var uploadedUrl: String? = uriString
            if (uriString != null && (uriString.startsWith("content://") || uriString.startsWith("file://"))) {
                val part = getUploadPart(uriString, "reward_${System.currentTimeMillis()}")
                uploadedUrl = api.uploadRewardImage(part).url
            }
            
            val reward = api.createReward(CreateRewardRequest(title, description, cost, uploadedUrl, type, _state.value.rewards.size))
            val mapped = reward.copy(imageUrl = makeUrlAbsolute(reward.imageUrl))
            _state.update { it.copy(rewards = it.rewards + mapped) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to create reward: ${e.message}") }
        }
    }

    fun updateReward(rewardId: String, title: String, description: String?, cost: Int, uriString: String? = null, type: String = "recurring") = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(rewards = s.rewards.map {
                    if (it.id == rewardId) it.copy(
                        title = title,
                        description = description,
                        cost = cost,
                        imageUrl = uriString,
                        type = type
                    ) else it
                })
            }
            return@launch
        }
        try {
            var uploadedUrl: String? = uriString
            if (uriString != null && (uriString.startsWith("content://") || uriString.startsWith("file://"))) {
                val part = getUploadPart(uriString, "reward_${rewardId}")
                uploadedUrl = api.uploadRewardImage(part).url
            }

            val currentReward = _state.value.rewards.find { it.id == rewardId }
            val reward = api.updateReward(rewardId, CreateRewardRequest(title, description, cost, uploadedUrl, type, currentReward?.order ?: 0))
            val mapped = reward.copy(imageUrl = makeUrlAbsolute(reward.imageUrl))
            _state.update { s ->
                s.copy(rewards = s.rewards.map { if (it.id == rewardId) mapped else it })
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to update reward: ${e.message}") }
        }
    }

    fun deleteReward(rewardId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s -> s.copy(rewards = s.rewards.filter { r -> r.id != rewardId }) }
            persistOfflineData()
            return@launch
        }
        val previousRewards = _state.value.rewards
        _state.update { s -> s.copy(rewards = s.rewards.filter { r -> r.id != rewardId }) }
        try {
            api.deleteReward(rewardId)
            refreshRewards()
        } catch (e: Exception) {
            _state.update { it.copy(rewards = previousRewards, error = "Failed to delete reward: ${e.message}") }
        }
    }

    fun redeemReward(rewardId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            val reward = _state.value.rewards.find { it.id == rewardId }
            reward?.let { r ->
                if ((_state.value.user?.points ?: 0) >= r.cost) {
                    _state.update { s -> s.copy(
                        user = s.user?.copy(points = s.user.points - r.cost),
                        rewards = s.rewards.map {
                            if (it.id == rewardId && it.type == "one_time") it.copy(redeemed = true) else it
                        },
                        notification = "Reward redeemed!"
                    ) }
                    updateOfflineStats()
                } else {
                    _state.update { it.copy(error = "Not enough points!") }
                }
            }
            return@launch
        }
        try {
            val response = api.redeemReward(rewardId)
            _state.update { s ->
                val currentUser = s.user ?: return@update s
                val updatedUser = if (currentUser.role == "dominant") {
                    updatePartnerPoints(currentUser, s.selectedPartnerId, response.points)
                } else {
                    currentUser.copy(points = response.points)
                }
                s.copy(
                    user = updatedUser,
                    rewards = s.rewards.map {
                        if (it.id == rewardId && it.type == "one_time") it.copy(redeemed = true) else it
                    },
                    notification = "Reward redeemed!"
                )
            }
            refreshRewards()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to redeem: ${e.message}") }
        }
    }

    fun resetReward(rewardId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(
                    rewards = s.rewards.map {
                        if (it.id == rewardId) it.copy(redeemed = false) else it
                    },
                    notification = "Reward reset"
                )
            }
            return@launch
        }
        try {
            api.resetReward(rewardId)
            _state.update { s ->
                s.copy(
                    rewards = s.rewards.map {
                        if (it.id == rewardId) it.copy(redeemed = false) else it
                    },
                    notification = "Reward reset"
                )
            }
            refreshRewards()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to reset reward: ${e.message}") }
        }
    }

    // ─── Punishments ─────────────────────────────────────────────────────────

    fun refreshPunishments() = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        _state.update { it.copy(isRefreshing = true) }
        try {
            val punishments = api.getPunishments()
            val assigned = api.getAssignedPunishments()
            _state.update { it.copy(punishments = punishments.sortedBy { p -> p.order }, assignedPunishments = assigned, isServerUnreachable = false) }
            updateOfflineStats()
        } catch (e: Exception) {
            val connErr = isConnectionError(e)
            _state.update { it.copy(
                error = "Failed to load punishments: ${e.message}",
                isServerUnreachable = if (connErr) true else it.isServerUnreachable,
                isTailscaleError = if (connErr) true else it.isTailscaleError
            ) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    fun createPunishment(title: String, description: String?) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            val p = Punishment(
                id = java.util.UUID.randomUUID().toString(),
                ownerId = "offline_user",
                title = title,
                description = description,
                createdAt = System.currentTimeMillis()
            )
            _state.update { it.copy(punishments = it.punishments + p) }
            updateOfflineStats()
            return@launch
        }
        try {
            val p = api.createPunishment(CreatePunishmentRequest(title, description, _state.value.punishments.size))
            _state.update { it.copy(punishments = it.punishments + p) }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to create punishment: ${e.message}") }
        }
    }

    fun updatePunishment(punishmentId: String, title: String, description: String?) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(punishments = s.punishments.map {
                    if (it.id == punishmentId) it.copy(
                        title = title,
                        description = description
                    ) else it
                })
            }
            updateOfflineStats()
            return@launch
        }
        try {
            val currentP = _state.value.punishments.find { it.id == punishmentId }
            val p = api.updatePunishment(punishmentId, CreatePunishmentRequest(title, description, currentP?.order ?: 0))
            _state.update { s ->
                s.copy(punishments = s.punishments.map { if (it.id == punishmentId) p else it })
            }
            refreshHabits() // Habits might reference this punishment
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to update punishment: ${e.message}") }
        }
    }

    fun deletePunishment(punishmentId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { it.copy(punishments = it.punishments.filter { it.id != punishmentId }) }
            updateOfflineStats()
            return@launch
        }
        try {
            api.deletePunishment(punishmentId)
            _state.update { it.copy(punishments = it.punishments.filter { it.id != punishmentId }) }
            refreshHabits()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to delete punishment: ${e.message}") }
        }
    }

    fun assignPunishment(punishmentId: String, reason: String?) = viewModelScope.launch {
        val targetPartnerId = _state.value.selectedPartnerId ?: _state.value.user?.partnerId
        if (_state.value.isOfflineMode) {
            val p = _state.value.punishments.find { it.id == punishmentId }
            p?.let {
                val assigned = AssignedPunishment(
                    id = java.util.UUID.randomUUID().toString(),
                    punishmentId = it.id,
                    assignedTo = targetPartnerId ?: "offline_user",
                    assignedBy = _state.value.user?.id ?: "offline_user",
                    reason = reason,
                    completed = false,
                    assignedAt = System.currentTimeMillis(),
                    title = it.title,
                    description = it.description
                )
                _state.update { s -> s.copy(assignedPunishments = s.assignedPunishments + assigned, notification = "Punishment assigned") }
            }
            return@launch
        }
        try {
            api.assignPunishment(punishmentId, AssignPunishmentRequest(assignToId = targetPartnerId, reason = reason))
            refreshPunishments()
            _state.update { it.copy(notification = "Punishment assigned") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to assign: ${e.message}") }
        }
    }

    fun completePunishment(assignmentId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            _state.update { s ->
                s.copy(assignedPunishments = s.assignedPunishments.filter { it.id != assignmentId })
            }
            return@launch
        }
        try {
            api.completePunishment(assignmentId)
            _state.update { s ->
                s.copy(assignedPunishments = s.assignedPunishments.filter { it.id != assignmentId })
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to complete: ${e.message}") }
        }
    }

    // ─── Points ──────────────────────────────────────────────────────────────

    fun adjustPoints(amount: Int, reason: String?, targetUserId: String? = null) = viewModelScope.launch {
        val target = targetUserId ?: _state.value.selectedPartnerId ?: _state.value.user?.partnerId
        if (_state.value.isOfflineMode) {
            _state.update { s -> s.copy(
                user = s.user?.copy(points = maxOf(0, (s.user.points) + amount)),
                notification = "Points adjusted"
            ) }
            return@launch
        }
        try {
            val response = api.adjustPoints(AdjustPointsRequest(amount = amount, reason = reason, userId = target))
            // Refresh user to get updated points (this will also refresh partner data)
            val user = api.getMe()
            _state.update { it.copy(user = mapUser(user), notification = "Points adjusted") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to adjust points: ${e.message}") }
        }
    }

    // ─── Stats ───────────────────────────────────────────────────────────────

    fun refreshStats() = viewModelScope.launch {
        if (_state.value.isOfflineMode) {
            updateOfflineStats()
            return@launch
        }
        _state.update { it.copy(isRefreshing = true) }
        try {
            val stats = api.getStats()
            val history = try { api.getHabitHistory() } catch(e: Exception) { _state.value.history }
            _state.update { it.copy(stats = stats, history = history, isServerUnreachable = false) }
            updateOfflineStats() // Calculate auto punishments from history
        } catch (e: Exception) {
            val connErr = isConnectionError(e)
            _state.update { it.copy(
                error = "Failed to load stats: ${e.message}",
                isServerUnreachable = if (connErr) true else it.isServerUnreachable,
                isTailscaleError = if (connErr) true else it.isTailscaleError
            ) }
        } finally {
            _state.update { it.copy(isRefreshing = false) }
        }
    }
    
    private val habitsCount: Int get() = _state.value.habits.size

    // ─── WebSocket event handler ─────────────────────────────────────────────

    private fun handleWsEvent(event: WsEvent) {
        if (_state.value.isOfflineMode) return
        when (event.type) {
            "HABIT_COMPLETED", "HABIT_UNCOMPLETED", "SUBMISSION_APPROVED", "SUBMISSION_REJECTED" -> {
                event.points?.let { pts ->
                    _state.update { s ->
                        val currentUser = s.user ?: return@update s
                        if (event.userId == currentUser.id) {
                            s.copy(user = currentUser.copy(points = pts))
                        } else {
                            s.copy(user = updatePartnerPoints(currentUser, event.userId, pts))
                        }
                    }
                }
                viewModelScope.launch {
                    refreshHabits()
                    refreshStats()
                    refreshSubmissions()
                }
                val name = if (event.userId == _state.value.user?.id) "You" else "Your partner"
                val text = when(event.type) {
                    "HABIT_COMPLETED" -> "$name completed a habit! 🎉"
                    "HABIT_UNCOMPLETED" -> "$name uncompleted a habit"
                    "SUBMISSION_APPROVED" -> "✅ Proof for ${event.habitTitle ?: "habit"} approved!"
                    else -> "❌ Proof for ${event.habitTitle ?: "habit"} rejected"
                }
                _state.update { it.copy(notification = text) }
                showSystemNotification("Habit Update", text)
            }

            "HABIT_CREATED", "HABIT_UPDATED", "HABIT_PAUSED", "HABIT_RESUMED" -> {
                viewModelScope.launch { refreshHabits() }
                if (event.habit?.ownerId != _state.value.user?.id) {
                    val text = when(event.type) {
                        "HABIT_PAUSED" -> "Your partner paused: ${event.habit?.title}"
                        "HABIT_RESUMED" -> "Your partner resumed: ${event.habit?.title}"
                        else -> "Your partner added/updated a habit: ${event.habit?.title}"
                    }
                    _state.update { it.copy(notification = text) }
                    showSystemNotification("Habit Update", text)
                }
            }

            "HABIT_DELETED" -> event.habitId?.let { id ->
                _state.update { s ->
                    s.copy(
                        habits = s.habits.filter { it.id != id },
                        partnerHabits = s.partnerHabits.filter { it.id != id }
                    )
                }
            }

            "HABITS_REORDERED" -> viewModelScope.launch { refreshHabits() }
            "REWARDS_REORDERED" -> viewModelScope.launch { refreshRewards() }
            "PUNISHMENTS_REORDERED" -> viewModelScope.launch { refreshPunishments() }

            "REWARD_REDEEMED" -> {
                val title = event.rewardTitle ?: "a reward"
                val text = "🎁 Your partner redeemed: $title"
                _state.update { it.copy(notification = text) }
                showSystemNotification("Reward Redeemed", text)
                event.points?.let { pts ->
                    _state.update { s ->
                        val currentUser = s.user ?: return@update s
                        s.copy(user = updatePartnerPoints(currentUser, event.userId, pts))
                    }
                }
            }

            "PUNISHMENT_ASSIGNED" -> {
                val text = "⚠️ You received a punishment!"
                _state.update { it.copy(notification = text) }
                showSystemNotification("New Punishment", text)
                viewModelScope.launch { refreshPunishments() }
            }

            "HABIT_SUBMITTED" -> {
                val text = "${event.username} submitted proof for: ${event.habitTitle}"
                _state.update { it.copy(notification = text) }
                showSystemNotification("Proof Submitted", text)
                viewModelScope.launch { refreshSubmissions() }
            }

            "PUNISHMENT_COMPLETED" -> {
                _state.update { it.copy(notification = "✅ Punishment completed") }
            }

            "POINTS_ADJUSTED" -> {
                viewModelScope.launch {
                    val me = api.getMe()
                    _state.update { it.copy(user = mapUser(me), notification = "Points updated") }
                }
            }

            "PAIRED" -> {
                viewModelScope.launch {
                    val user = api.getMe()
                    _state.update { it.copy(user = mapUser(user), notification = "Paired with partner! 🔗") }
                }
            }

            "AVATAR_UPDATED" -> {
                val updatedUrl = makeUrlAbsolute(event.avatarUrl)
                _state.update { s ->
                    val currentUser = s.user
                    if (currentUser != null && event.userId != null) {
                        if (event.userId == currentUser.id) {
                            s.copy(user = currentUser.copy(avatarUrl = updatedUrl))
                        } else {
                            s.copy(user = updatePartnerAvatar(currentUser, event.userId, updatedUrl))
                        }
                    } else s
                }
            }
        }
    }

    // ─── Utilities ───────────────────────────────────────────────────────────

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearNotification() = _state.update { it.copy(notification = null) }

    fun openTailscale() {
        val pkg = "com.tailscale.ipn"
        var intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to Play Store if Tailscale is not installed
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (ex: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    fun refreshAll() = viewModelScope.launch {
        refreshHabits()
        refreshRewards()
        refreshPunishments()
        refreshSubmissions()
        refreshCheckIns()
        refreshStats()
        refreshAdminUsers()
    }

    // ─── Admin / Server Settings ─────────────────────────────────────────────

    fun refreshAdminUsers() = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        try {
            val users = api.getAdminUsers()
            _state.update { it.copy(adminUsers = users) }
        } catch (e: Exception) {
            // Ignore admin fetch errors on non-admin accounts or older server builds
        }
    }

    fun updateAdminUser(userId: String, username: String?, role: String?, points: Int?) = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        try {
            api.updateAdminUser(userId, UpdateUserRequest(username, role, points))
            refreshAdminUsers()
            val me = api.getMe()
            _state.update { it.copy(user = mapUser(me), notification = "User updated successfully") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to update user: ${e.message}") }
        }
    }

    fun changeAdminUserPassword(userId: String, newPassword: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        try {
            api.changeAdminUserPassword(userId, ChangePasswordRequest(newPassword))
            _state.update { it.copy(notification = "Password updated successfully") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to change password: ${e.message}") }
        }
    }

    fun deleteAdminUser(userId: String) = viewModelScope.launch {
        if (_state.value.isOfflineMode) return@launch
        try {
            api.deleteAdminUser(userId)
            refreshAdminUsers()
            val me = api.getMe()
            _state.update { it.copy(user = mapUser(me), notification = "User deleted successfully") }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to delete user: ${e.message}") }
        }
    }
}
