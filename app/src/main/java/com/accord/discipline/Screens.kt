package com.accord.discipline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerSelector(
    user: User?,
    selectedPartner: Partner?,
    onSelectPartner: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val partners = user?.partners ?: emptyList()
    if (partners.size > 1) {
        Box(modifier = modifier) {
            FilterChip(
                selected = true,
                onClick = { expanded = true },
                label = {
                    Text(
                        selectedPartner?.let { "👤 ${it.username}" } ?: "Select Partner",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                }
            )

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                partners.forEach { partner ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!partner.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = partner.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp).clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("${partner.username} • ${partner.points}⭐")
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectPartner(partner.id)
                        },
                        leadingIcon = if (partner.id == selectedPartner?.id) {
                            { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                        } else null
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading / Splash Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDDBF9)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = R.mipmap.ic_launcher_round,
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Discipline",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Server URL Setup Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ServerSetupScreen(
    currentUrl: String, 
    onSave: (String) -> Unit, 
    onSkip: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    if (onCancel != null) {
        androidx.activity.compose.BackHandler {
            onCancel()
        }
    }

    var url by remember(currentUrl) { mutableStateOf(currentUrl) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (onCancel != null) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Cancel")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Router, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text("Connect to your Raspberry Pi", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Enter your Pi's local IP address", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.42:3000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text("Run 'hostname -I' on your Pi to find its IP", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (url.isNotBlank()) {
                    val previewUrl = if (url.startsWith("http")) url else "http://$url"
                    val finalUrl = if (previewUrl.substringAfter("://").contains(":")) previewUrl else "$previewUrl:3000"
                    Spacer(Modifier.height(16.dp))
                    Text("Connecting to: $finalUrl", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = { onSave(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Save & Continue")
                }
                if (onCancel != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now (Use Solo Mode)")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Server Unreachable Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ServerUnreachableScreen(
    serverUrl: String,
    error: String?,
    isLoading: Boolean,
    onRetry: () -> Unit,
    onOpenTailscale: () -> Unit,
    onChangeServer: () -> Unit,
    onUseOffline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = "Server Unreachable",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "Cannot Connect to Server",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Unable to reach your Raspberry Pi at:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = serverUrl,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        error?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Connection Error",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 1. Retry Connection Button
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry Connection")
            }
        }
        
        Spacer(Modifier.height(12.dp))

        // 2. Open Tailscale App Button
        Button(
            onClick = onOpenTailscale,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Tailscale")
        }

        Spacer(Modifier.height(12.dp))

        // 3. Change Server Address Button
        OutlinedButton(
            onClick = onChangeServer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Change Server Address")
        }

        Spacer(Modifier.height(16.dp))

        // 4. Use Offline Mode Option
        TextButton(
            onClick = onUseOffline,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use Solo Mode (Offline)", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Login / Register Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoginScreen(viewModel: MainViewModel, state: AppState, onChangeServer: () -> Unit, onSkip: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf(state.savedUsername) }
    var password by remember { mutableStateOf(state.savedPassword) }
    var role by remember { mutableStateOf("submissive") }

    LaunchedEffect(state.savedUsername, state.savedPassword) {
        if (username.isEmpty() && state.savedUsername.isNotEmpty()) {
            username = state.savedUsername
        }
        if (password.isEmpty() && state.savedPassword.isNotEmpty()) {
            password = state.savedPassword
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            AsyncImage(
                model = R.mipmap.ic_launcher_round,
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(16.dp))
            Text("Discipline", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Private • Local • Yours", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors
            )

            if (!isLogin) {
                Spacer(Modifier.height(16.dp))
                Text("I am the:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("dominant", "submissive").forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                        
                        if (state.isTailscaleError) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { viewModel.openTailscale() },
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Launch, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open Tailscale", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (isLogin) viewModel.login(username, password)
                    else viewModel.register(username, password, role)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (isLogin) "Login" else "Create Account")
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { isLogin = !isLogin; viewModel.clearError() }) {
                Text(if (isLogin) "New here? Create account" else "Have an account? Login")
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChangeServer) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Change server (${state.serverUrl})", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSkip) {
                    Text("Use Solo Mode", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Habits Screen
// ─────────────────────────────────────────────────────────────────────────────

fun getWeeklyCompletionsCount(habitId: String, history: List<HabitCompletion>, today: LocalDate = LocalDate.now()): Int {
    var monday = today
    while (monday.dayOfWeek != java.time.DayOfWeek.MONDAY) {
        monday = monday.minusDays(1)
    }
    val sunday = monday.plusDays(6)

    return history.count { completion ->
        completion.habitId == habitId && try {
            val date = LocalDate.parse(completion.date)
            !date.isBefore(monday) && !date.isAfter(sunday)
        } catch (e: Exception) {
            false
        }
    }
}

fun isHabitCompleted(habit: Habit, history: List<HabitCompletion>, today: LocalDate = LocalDate.now()): Boolean {
    val hasEntryToday = history.any { it.habitId == habit.id && it.date == today.toString() }
    if (!habit.isPositive) {
        // "Avoid" habits are checked/successful by default unless marked incomplete
        return !hasEntryToday
    }

    if (habit.frequency == "weekly") {
        val targetCount = maxOf(1, habit.weeklyTargetCount)
        val weeklyCompletions = getWeeklyCompletionsCount(habit.id, history, today)
        return weeklyCompletions >= targetCount || hasEntryToday
    } else if (habit.frequency == "task") {
        return history.any { it.habitId == habit.id }
    } else {
        return hasEntryToday
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(viewModel: MainViewModel, state: AppState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var habitToEdit by remember { mutableStateOf<Habit?>(null) }
    var habitToDelete by remember { mutableStateOf<Habit?>(null) }
    var showCheckInDialog by remember { mutableStateOf(false) }
    
    val currentPartner = state.selectedPartner
    val isDominant = state.user?.role == "dominant"
    val isOfflineMode = state.isOfflineMode
    val canManage = isDominant || isOfflineMode
    val displayPoints = if (isDominant && !isOfflineMode && currentPartner != null) currentPartner.points else state.user?.points ?: 0
    val today = LocalDate.now()
    val currentMonth = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val hasCheckInThisMonth = state.checkIns.any { it.userId == state.user?.id && it.month == currentMonth }
    val isCheckInWindow = today.dayOfMonth in 1..7

    val allHabits = remember(state.habits, state.partnerHabits) {
        state.habits + state.partnerHabits
    }
    val activeHabits = remember(allHabits) {
        allHabits.filter { !it.isPaused }
    }
    val pausedHabits = remember(allHabits) {
        allHabits.filter { it.isPaused }
    }
    var showPausedHabits by remember { mutableStateOf(false) }
    var showCompletedTasks by remember { mutableStateOf(false) }
    val punishmentNames = remember(state.punishments) {
        state.punishments.associate { it.id to it.title }
    }
    val partnerSubmissions = remember(state.pendingSubmissions, state.user) {
        state.pendingSubmissions.filter { it.userId != state.user?.id }
    }

    // Partition unpaused items into active items and completed tasks
    val (activeItems, completedTasks) = remember(activeHabits, state.history) {
        val active = mutableListOf<Habit>()
        val completedT = mutableListOf<Habit>()

        activeHabits.forEach { habit ->
            if (habit.frequency == "task") {
                val taskCompletion = state.history.find { it.habitId == habit.id }
                if (taskCompletion != null) {
                    completedT.add(habit)
                } else {
                    active.add(habit)
                }
            } else {
                active.add(habit)
            }
        }
        Pair(active, completedT)
    }

    LaunchedEffect(Unit) { viewModel.refreshHabits() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habits & Tasks") },
                actions = {
                    IconButton(onClick = { viewModel.refreshHabits() }) {
                        if (state.isRefreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add habit or task")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Error Display
                state.error?.let {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            onClick = { viewModel.clearError() }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Pending Approvals (for dominant)
                if (isDominant && partnerSubmissions.isNotEmpty()) {
                    item {
                        Text(
                            "Pending Approvals",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(partnerSubmissions) { submission ->
                        SubmissionCard(
                            submission = submission,
                            onApprove = { viewModel.approveSubmission(submission.id) },
                            onReject = { viewModel.rejectSubmission(submission.id) },
                            onDelete = { viewModel.deleteSubmission(submission.id) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Monthly Check-in Prompt
                if (isCheckInWindow && !hasCheckInThisMonth) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { showCheckInDialog = true },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.EventNote, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Monthly Check-in Available",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Reflect on your shared goals for ${today.format(DateTimeFormatter.ofPattern("MMMM"))}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Unified Habits & Active Tasks list
                if (activeItems.isNotEmpty()) {
                    item {
                        Text(
                            "Habits & Tasks",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    itemsIndexed(activeItems, key = { _, habit -> habit.id }) { index, habit ->
                        val isCompleted = isHabitCompleted(habit, state.history, today)
                        val weeklyCount = if (habit.frequency == "weekly") getWeeklyCompletionsCount(habit.id, state.history, today) else 0
                        val habitPendingDates = remember(state.pendingSubmissions, habit.id) {
                            state.pendingSubmissions
                                .filter { it.habitId == habit.id }
                                .map { it.date }
                                .distinct()
                                .sorted()
                        }
                        HabitCard(
                            habit = habit.copy(completed = isCompleted),
                            pendingDates = habitPendingDates,
                            penaltyName = punishmentNames[habit.penaltyPunishmentId],
                            readOnly = isDominant && !isOfflineMode, // In solo mode, user can check off habits
                            weeklyCount = weeklyCount,
                            onComplete = { viewModel.completeHabit(habit.id) },
                            onUncomplete = {
                                if (habit.frequency == "task") viewModel.resetTask(habit.id)
                                else viewModel.uncompleteHabit(habit.id)
                            },
                            onResetTask = { viewModel.resetTask(habit.id) },
                            onEdit = { if (canManage) habitToEdit = habit },
                            onDelete = { if (canManage) habitToDelete = habit },
                            onPause = { viewModel.pauseHabit(habit.id) },
                            onResume = { viewModel.resumeHabit(habit.id) },
                            onSubmitPhoto = { url -> viewModel.submitHabitPhoto(habit.id, url) },
                            isDominant = canManage,
                            onMoveUp = if (index > 0 && canManage) {
                                { viewModel.moveHabit(index, index - 1) }
                            } else null,
                            onMoveDown = if (index < activeItems.size - 1 && canManage) {
                                { viewModel.moveHabit(index, index + 1) }
                            } else null
                        )
                    }
                }

                // Minimized Completed Tasks section at bottom of list
                if (completedTasks.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCompletedTasks = !showCompletedTasks }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Completed Tasks (${completedTasks.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (showCompletedTasks) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showCompletedTasks) {
                        items(completedTasks, key = { it.id }) { habit ->
                            val habitPendingDates = remember(state.pendingSubmissions, habit.id) {
                                state.pendingSubmissions
                                    .filter { it.habitId == habit.id }
                                    .map { it.date }
                                    .distinct()
                                    .sorted()
                            }
                            HabitCard(
                                habit = habit.copy(completed = true),
                                pendingDates = habitPendingDates,
                                penaltyName = punishmentNames[habit.penaltyPunishmentId],
                                readOnly = true,
                                weeklyCount = 0,
                                onComplete = {},
                                onUncomplete = { viewModel.resetTask(habit.id) },
                                onResetTask = { viewModel.resetTask(habit.id) },
                                onEdit = { if (canManage) habitToEdit = habit },
                                onDelete = { if (canManage) habitToDelete = habit },
                                onPause = { viewModel.pauseHabit(habit.id) },
                                onResume = { viewModel.resumeHabit(habit.id) },
                                onSubmitPhoto = { url -> viewModel.submitHabitPhoto(habit.id, url) },
                                isDominant = canManage
                            )
                        }
                    }
                }

                if (pausedHabits.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPausedHabits = !showPausedHabits }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Paused Habits (${pausedHabits.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (showPausedHabits) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (showPausedHabits) {
                        items(pausedHabits, key = { it.id }) { habit ->
                            val isCompleted = isHabitCompleted(habit, state.history, today)
                            val weeklyCount = if (habit.frequency == "weekly") getWeeklyCompletionsCount(habit.id, state.history, today) else 0
                            HabitCard(
                                habit = habit.copy(completed = isCompleted),
                                penaltyName = punishmentNames[habit.penaltyPunishmentId],
                                readOnly = true,
                                weeklyCount = weeklyCount,
                                onComplete = {},
                                onUncomplete = {},
                                onResetTask = { viewModel.resetTask(habit.id) },
                                onEdit = { if (canManage) habitToEdit = habit },
                                onDelete = { if (canManage) habitToDelete = habit },
                                onPause = {},
                                onResume = { viewModel.resumeHabit(habit.id) },
                                onSubmitPhoto = {},
                                isDominant = canManage
                            )
                        }
                    }
                }

                if (allHabits.isEmpty() && canManage) {
                    item { EmptyState("No habits or tasks yet", if (isOfflineMode) "Tap + to add your first habit or task" else "Tap + to add a habit or task for your partner") }
                }
            }
        }
    }

    if (showAddDialog) {
        HabitDialog(
            punishments = state.punishments,
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, frequency, points, isPositive, penaltyId, requiresPhoto, isNsfw, weeklyTarget, dueDate ->
                viewModel.createHabit(title, desc, frequency, points, isPositive, penaltyId, requiresPhoto, isNsfw, weeklyTarget, dueDate)
                showAddDialog = false
            }
        )
    }

    habitToEdit?.let { habit ->
        HabitDialog(
            habit = habit,
            punishments = state.punishments,
            onDismiss = { habitToEdit = null },
            onSave = { title, desc, frequency, points, isPositive, penaltyId, requiresPhoto, isNsfw, weeklyTarget, dueDate ->
                viewModel.updateHabit(habit.id, title, desc, frequency, points, isPositive, penaltyId, requiresPhoto, isNsfw, weeklyTarget, dueDate)
                habitToEdit = null
            }
        )
    }

    habitToDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = { Text("Delete Habit") },
            text = { Text("Are you sure you want to delete '${habit.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHabit(habit.id)
                    habitToDelete = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showCheckInDialog) {
        MonthlyCheckInDialog(
            onDismiss = { showCheckInDialog = false },
            onSubmit = { reflections, goals, rating ->
                viewModel.submitCheckIn(reflections, goals, rating)
                showCheckInDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitCard(
    habit: Habit,
    pendingDates: List<String> = emptyList(),
    penaltyName: String? = null,
    readOnly: Boolean = false,
    weeklyCount: Int = 0,
    onComplete: () -> Unit,
    onUncomplete: () -> Unit,
    onResetTask: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSubmitPhoto: (String) -> Unit,
    isDominant: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPhotoDialog by remember { mutableStateOf(false) }
    
    val isPaused = habit.isPaused
    val cardColors = if (isPaused) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    } else if (habit.completed) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else if (!habit.isPositive) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Default click handled by checkbox or row */ },
                onLongClick = { if (isDominant) showMenu = true }
            ), 
        colors = cardColors,
        border = if (habit.isPositive && !habit.completed && !isPaused) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!readOnly && !isPaused) {
                if (!habit.isPositive) {
                    if (habit.completed) {
                        Checkbox(
                            checked = true,
                            onCheckedChange = {
                                if (habit.requiresPhoto) showPhotoDialog = true
                                else onComplete()
                            }
                        )
                    } else {
                        IconButton(
                            onClick = { onUncomplete() }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Marked Miss (X)",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Checkbox(
                        checked = habit.completed, 
                        onCheckedChange = { 
                            if (it) {
                                if (habit.requiresPhoto) showPhotoDialog = true
                                else onComplete()
                            } else onUncomplete() 
                        }
                    )
                }
            } else {
                val icon = if (isPaused) Icons.Default.PauseCircleFilled 
                          else if (habit.completed) Icons.Default.CheckCircle 
                          else if (!habit.isPositive) Icons.Default.Close
                          else Icons.Default.RadioButtonUnchecked
                
                val tint = if (isPaused) MaterialTheme.colorScheme.outline
                          else if (habit.completed) MaterialTheme.colorScheme.primary
                          else if (!habit.isPositive) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.outline
                
                Icon(icon, null, tint = tint, modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val prefix = if (habit.isPositive) "Do: " else "Avoid: "
                    Text(
                        prefix, 
                        fontWeight = FontWeight.Bold, 
                        color = if (isPaused) MaterialTheme.colorScheme.outline
                                else if (habit.isPositive) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.error
                    )
                    Text(
                        habit.title + if (isPaused) " (Paused)" else "", 
                        fontWeight = FontWeight.Medium,
                        style = if (isPaused) MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline) else LocalTextStyle.current
                    )
                    if (habit.requiresPhoto && !isPaused) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.PhotoCamera, "Requires Photo", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                habit.description?.let { 
                    Text(
                        it, 
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                }
                if (!isPaused) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val frequencyLabel = if (habit.frequency == "weekly") {
                            "Weekly ($weeklyCount/${habit.weeklyTargetCount})"
                        } else if (habit.frequency == "task") {
                            "Task"
                        } else {
                            habit.frequency.replaceFirstChar { it.uppercase() }
                        }
                        AssistChip(onClick = {}, label = { Text(frequencyLabel, style = MaterialTheme.typography.labelSmall) })

                        if (habit.frequency == "task" && !habit.dueDate.isNullOrBlank()) {
                            val dueStr = habit.dueDate
                            val isOverdue = remember(dueStr, habit.completed) {
                                try {
                                    !habit.completed && LocalDate.parse(dueStr).isBefore(LocalDate.now())
                                } catch (e: Exception) { false }
                            }
                            val formattedDue = remember(dueStr) {
                                try {
                                    LocalDate.parse(dueStr).format(DateTimeFormatter.ofPattern("MMM d"))
                                } catch (e: Exception) { dueStr }
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(if (isOverdue) "Overdue: $formattedDue" else "Due: $formattedDue", style = MaterialTheme.typography.labelSmall) },
                                colors = if (isOverdue) AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error) else AssistChipDefaults.assistChipColors()
                            )
                        }

                        if (habit.pointsReward > 0) {
                            AssistChip(onClick = {}, label = { Text("+${habit.pointsReward}⭐", style = MaterialTheme.typography.labelSmall) })
                        }
                        if (penaltyName != null) {
                            AssistChip(
                                onClick = {}, 
                                label = { Text("⚠️ $penaltyName", style = MaterialTheme.typography.labelSmall) },
                                colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
                            )
                        }
                        if (habit.requiresPhoto && !readOnly) {
                            AssistChip(
                                onClick = { showPhotoDialog = true },
                                label = { Text(if (pendingDates.isNotEmpty()) "Add Proof" else "Submit Proof", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(14.dp)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    if (pendingDates.isNotEmpty()) {
                        val formattedDates = remember(pendingDates) {
                            pendingDates.map { dateStr ->
                                try {
                                    LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("MMM d"))
                                } catch (e: Exception) {
                                    dateStr
                                }
                            }.joinToString(", ")
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Pending approval for: $formattedDates",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            if (isDominant) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!habit.completed) {
                            DropdownMenuItem(
                                text = { Text("Mark Complete") },
                                onClick = {
                                    showMenu = false
                                    onComplete()
                                },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Mark Incomplete") },
                                onClick = {
                                    showMenu = false
                                    onUncomplete()
                                },
                                leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, null) }
                            )
                        }

                        if (habit.frequency == "task") {
                            DropdownMenuItem(
                                text = { Text("Reset Task") },
                                onClick = {
                                    showMenu = false
                                    onResetTask?.invoke() ?: onUncomplete()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                        }

                        if (isPaused) {
                            DropdownMenuItem(
                                text = { Text("Resume Habit") },
                                onClick = { showMenu = false; onResume() },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, null) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Pause Habit") },
                                onClick = { showMenu = false; onPause() },
                                leadingIcon = { Icon(Icons.Default.Pause, null) }
                            )
                        }
                        
                        HorizontalDivider()

                        onMoveUp?.let {
                            DropdownMenuItem(
                                text = { Text("Move Up") },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }
                            )
                        }
                        onMoveDown?.let {
                            DropdownMenuItem(
                                text = { Text("Move Down") },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }

    if (showPhotoDialog) {
        PhotoSubmissionDialog(
            habitTitle = habit.title,
            onDismiss = { showPhotoDialog = false },
            onSubmit = { url ->
                onSubmitPhoto(url)
                showPhotoDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDialog(habit: Habit? = null, punishments: List<Punishment> = emptyList(), onDismiss: () -> Unit, onSave: (String, String?, String, Int, Boolean, String?, Boolean, Boolean, Int, String?) -> Unit) {
    var title by remember(habit) { mutableStateOf(habit?.title ?: "") }
    var description by remember(habit) { mutableStateOf(habit?.description ?: "") }
    var frequency by remember(habit) { mutableStateOf(habit?.frequency ?: "daily") }
    var points by remember(habit) { mutableStateOf(habit?.pointsReward?.toString() ?: "10") }
    var isPositive by remember(habit) { mutableStateOf(habit?.isPositive ?: true) }
    var penaltyPunishmentId by remember(habit) { mutableStateOf(habit?.penaltyPunishmentId) }
    var requiresPhoto by remember(habit) { mutableStateOf(habit?.requiresPhoto ?: false) }
    var isNsfw by remember(habit) { mutableStateOf(habit?.isNsfw ?: false) }
    var weeklyTargetCount by remember(habit) { mutableStateOf(habit?.weeklyTargetCount?.toString() ?: "1") }
    var dueDate by remember(habit) { mutableStateOf(habit?.dueDate ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habit == null) "New Habit / Task" else "Edit Habit / Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isPositive,
                        onClick = { isPositive = true },
                        label = { Text("Do This", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (isPositive) { { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) } } else null
                    )
                    FilterChip(
                        selected = !isPositive,
                        onClick = { 
                            isPositive = false
                            frequency = "daily"
                        },
                        label = { Text("Don't Do This", maxLines = 1) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (!isPositive) { { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) } } else null
                    )
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = points, 
                    onValueChange = { points = it }, 
                    label = { Text(if (isPositive) "Points reward" else "Reward for abstaining") }, 
                    modifier = Modifier.fillMaxWidth()
                )
                if (isPositive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("daily", "weekly", "task").forEach { f ->
                            FilterChip(
                                selected = frequency == f,
                                onClick = { frequency = f },
                                label = { Text(if (f == "task") "Task" else f.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
                
                if (frequency == "weekly") {
                    OutlinedTextField(
                        value = weeklyTargetCount, 
                        onValueChange = { weeklyTargetCount = it }, 
                        label = { Text("Target completions per week") }, 
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (frequency == "task") {
                    val formattedDueDate = remember(dueDate) {
                        if (dueDate.isNotBlank()) {
                            try {
                                LocalDate.parse(dueDate).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                            } catch (e: Exception) { dueDate }
                        } else ""
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                    ) {
                        OutlinedTextField(
                            value = formattedDueDate,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Due Date (Optional)") },
                            placeholder = { Text("Tap to select due date") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (dueDate.isNotBlank()) {
                                        IconButton(onClick = { dueDate = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear date", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(Icons.Default.Event, contentDescription = "Select due date")
                                    }
                                }
                            }
                        )
                    }
                }
                
                // Penalty selector
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        val selectedPunishment = punishments.find { it.id == penaltyPunishmentId }
                        Text(selectedPunishment?.let { "Penalty: ${it.title}" } ?: "Select Punishment if Missed")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { penaltyPunishmentId = null; expanded = false })
                        punishments.forEach { p ->
                            DropdownMenuItem(text = { Text(p.title) }, onClick = { penaltyPunishmentId = p.id; expanded = false })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = requiresPhoto,
                        onCheckedChange = { checked ->
                            requiresPhoto = checked
                            if (!checked) isNsfw = false
                        }
                    )
                    Text("Requires Photo Proof")
                }

                AnimatedVisibility(visible = requiresPhoto) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Checkbox(
                            checked = isNsfw,
                            onCheckedChange = { isNsfw = it },
                            enabled = requiresPhoto
                        )
                        Text("NSFW Photo (Blur Thumbnail)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onSave(
                    title,
                    description.takeIf { it.isNotBlank() },
                    frequency,
                    points.toIntOrNull() ?: 0,
                    isPositive,
                    penaltyPunishmentId,
                    requiresPhoto,
                    isNsfw && requiresPhoto,
                    weeklyTargetCount.toIntOrNull() ?: 1,
                    dueDate.takeIf { it.isNotBlank() }
                )
            }) {
                Text(if (habit == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val initialMs = remember(dueDate) {
            try {
                if (dueDate.isNotBlank()) {
                    LocalDate.parse(dueDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } else {
                    System.currentTimeMillis()
                }
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMs)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        val selectedLocalDate = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.of("UTC"))
                            .toLocalDate()
                        dueDate = selectedLocalDate.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rewards Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RewardsScreen(viewModel: MainViewModel, state: AppState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var rewardToEdit by remember { mutableStateOf<Reward?>(null) }
    var rewardToDelete by remember { mutableStateOf<Reward?>(null) }
    var showPointsDialog by remember { mutableStateOf(false) }
    var showRedeemedRewards by remember { mutableStateOf(false) }
    
    val currentPartner = state.selectedPartner
    val isDominant = state.user?.role == "dominant"
    val isOfflineMode = state.isOfflineMode
    val canManage = isDominant || isOfflineMode
    val displayPoints = if (isDominant && !isOfflineMode && currentPartner != null) currentPartner.points else state.user?.points ?: 0

    val activeRewards = remember(state.rewards) {
        state.rewards.filter { it.type != "one_time" || !it.redeemed }
    }
    val redeemedOneTimeRewards = remember(state.rewards) {
        state.rewards.filter { it.type == "one_time" && it.redeemed }
    }

    LaunchedEffect(Unit) { viewModel.refreshRewards() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards") },
                actions = {
                    IconButton(onClick = { viewModel.refreshRewards() }) {
                        if (state.isRefreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refresh")
                    }
                    Box(modifier = Modifier
                        .padding(end = 16.dp)
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { if (canManage) showPointsDialog = true }
                        )
                    ) {
                        Text("$displayPoints ⭐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            if (canManage) FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add reward") }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(activeRewards, key = { _, reward -> reward.id }) { index, reward ->
                    RewardCard(
                        reward = reward,
                        canAfford = displayPoints >= reward.cost,
                        isOwner = reward.ownerId == state.user?.id || isOfflineMode,
                        isDominant = isDominant,
                        isOfflineMode = isOfflineMode,
                        partnerName = state.user?.partner?.username,
                        onRedeem = { viewModel.redeemReward(reward.id) },
                        onReset = { viewModel.resetReward(reward.id) },
                        onEdit = { rewardToEdit = reward },
                        onDelete = { rewardToDelete = reward },
                        onMoveUp = if (index > 0) {
                            { viewModel.moveReward(index, index - 1) }
                        } else null,
                        onMoveDown = if (index < activeRewards.size - 1) {
                            { viewModel.moveReward(index, index + 1) }
                        } else null
                    )
                }

                if (redeemedOneTimeRewards.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRedeemedRewards = !showRedeemedRewards }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Redeemed Rewards (${redeemedOneTimeRewards.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                if (showRedeemedRewards) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showRedeemedRewards) {
                        items(redeemedOneTimeRewards, key = { it.id }) { reward ->
                            RewardCard(
                                reward = reward,
                                canAfford = false,
                                isOwner = reward.ownerId == state.user?.id || isOfflineMode,
                                isDominant = isDominant,
                                isOfflineMode = isOfflineMode,
                                partnerName = state.user?.partner?.username,
                                onRedeem = {},
                                onReset = { viewModel.resetReward(reward.id) },
                                onEdit = { rewardToEdit = reward },
                                onDelete = { rewardToDelete = reward }
                            )
                        }
                    }
                }

                if (state.rewards.isEmpty()) {
                    item {
                        EmptyState(
                            "No rewards yet",
                            if (canManage) "Tap + to add a reward" else "Your partner will add rewards for you"
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RewardDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, cost, uri, type ->
                viewModel.createReward(title, desc, cost, uri, type)
                showAddDialog = false
            }
        )
    }

    rewardToEdit?.let { reward ->
        RewardDialog(
            reward = reward,
            onDismiss = { rewardToEdit = null },
            onSave = { title, desc, cost, uri, type ->
                viewModel.updateReward(reward.id, title, desc, cost, uri, type)
                rewardToEdit = null
            }
        )
    }

    if (rewardToDelete != null) {
        AlertDialog(
            onDismissRequest = { rewardToDelete = null },
            title = { Text("Delete Reward") },
            text = { Text("Are you sure you want to delete '${rewardToDelete?.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    rewardToDelete?.let { viewModel.deleteReward(it.id) }
                    rewardToDelete = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { rewardToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showPointsDialog) {
        PointsAdjustmentDialog(
            onDismiss = { showPointsDialog = false },
            onApply = { amount, reason ->
                viewModel.adjustPoints(amount, reason, state.user?.partnerId)
                showPointsDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RewardCard(
    reward: Reward,
    canAfford: Boolean,
    isOwner: Boolean,
    isDominant: Boolean,
    isOfflineMode: Boolean = false,
    partnerName: String? = null,
    onRedeem: () -> Unit,
    onReset: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showConfirmRedeem by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }

    val canManageCard = isOwner || isDominant || isOfflineMode

    Card(modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = { },
            onLongClick = { if (canManageCard) showMenu = true }
        )) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val imgUrl = reward.imageUrl
            if (!imgUrl.isNullOrBlank()) {
                if (isImageUrl(imgUrl)) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFullscreen = true },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(imgUrl, fontSize = 32.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎁", fontSize = 32.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(reward.title, fontWeight = FontWeight.Medium)
                reward.description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("${reward.cost} ⭐", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (reward.type == "one_time") {
                        AssistChip(
                            onClick = {},
                            label = { Text("One-time", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            if (!isDominant || isOfflineMode) {
                if (reward.type == "one_time" && reward.redeemed) {
                    Button(onClick = {}, enabled = false) { Text("Redeemed ✅") }
                } else {
                    Button(onClick = { showConfirmRedeem = true }, enabled = canAfford) { Text("Redeem") }
                }
            }
            if (canManageCard) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (isDominant && !isOfflineMode) {
                            val isRedeemedOneTime = reward.type == "one_time" && reward.redeemed
                            val redeemLabel = if (!partnerName.isNullOrBlank()) "Redeem for $partnerName" else "Redeem for Submissive"
                            DropdownMenuItem(
                                text = { Text(if (isRedeemedOneTime) "Already Redeemed" else redeemLabel) },
                                onClick = {
                                    showMenu = false
                                    showConfirmRedeem = true
                                },
                                enabled = !isRedeemedOneTime && canAfford,
                                leadingIcon = { Icon(Icons.Default.CardGiftcard, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            HorizontalDivider()
                        }
                        if (reward.type == "one_time" && onReset != null) {
                            DropdownMenuItem(
                                text = { Text("Reset Reward") },
                                onClick = {
                                    showMenu = false
                                    onReset?.invoke()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                        }
                        onMoveUp?.let {
                            DropdownMenuItem(
                                text = { Text("Move Up") },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }
                            )
                        }
                        onMoveDown?.let {
                            DropdownMenuItem(
                                text = { Text("Move Down") },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }

    if (showConfirmRedeem) {
        val partnerText = partnerName ?: "submissive"
        AlertDialog(
            onDismissRequest = { showConfirmRedeem = false },
            title = { Text("Redeem Reward") },
            text = {
                Text(
                    if (isDominant && !isOfflineMode)
                        "Spend ${reward.cost} ⭐ for '${reward.title}' on behalf of $partnerText?"
                    else
                        "Spend ${reward.cost} ⭐ for '${reward.title}'?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRedeem()
                    showConfirmRedeem = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRedeem = false }) { Text("Cancel") }
            }
        )
    }

    if (showFullscreen && isImageUrl(reward.imageUrl)) {
        reward.imageUrl?.let { FullscreenImageDialog(it) { showFullscreen = false } }
    }
}

@Composable
fun RewardDialog(reward: Reward? = null, onDismiss: () -> Unit, onSave: (String, String?, Int, String?, String) -> Unit) {
    var title by remember { mutableStateOf(reward?.title ?: "") }
    var description by remember { mutableStateOf(reward?.description ?: "") }
    var cost by remember { mutableStateOf(reward?.cost?.toString() ?: "50") }
    var type by remember(reward) { mutableStateOf(reward?.type ?: "recurring") }

    val initialUrl = reward?.imageUrl
    // mode: 0 = Default (🎁), 1 = Emoji, 2 = Photo
    var selectedMode by remember {
        mutableStateOf(
            when {
                initialUrl.isNullOrBlank() -> 0
                isImageUrl(initialUrl) -> 2
                else -> 1
            }
        )
    }

    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var existingImageUrl by remember { mutableStateOf<String?>(if (isImageUrl(initialUrl)) initialUrl else null) }
    var emojiText by remember { mutableStateOf(if (!initialUrl.isNullOrBlank() && !isImageUrl(initialUrl)) initialUrl else "🎁") }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedImageUri = uri
                selectedMode = 2
            }
        }
    )

    val commonEmojis = listOf(
        "🎁", "🍕", "☕", "🎮", "🎬", "🍔", "🍿", "🚗",
        "✈️", "🛍️", "🍰", "🍷", "🍦", "💆", "🎨", "📚",
        "🎟️", "🏖️", "🏆", "⭐", "💰", "🎧"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reward == null) "New Reward" else "Edit Reward") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Icon / Picture", style = MaterialTheme.typography.labelMedium)

                // Mode selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == 0,
                        onClick = { selectedMode = 0 },
                        label = { Text("Default", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == 1,
                        onClick = {
                            selectedMode = 1
                            if (emojiText.isBlank()) emojiText = "🎁"
                        },
                        label = { Text("Emoji", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == 2,
                        onClick = {
                            selectedMode = 2
                            if (selectedImageUri == null && existingImageUrl == null) {
                                pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        label = { Text("Photo", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Media display/selector based on mode
                when (selectedMode) {
                    2 -> { // Photo mode
                        val displayImage = selectedImageUri ?: existingImageUrl
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayImage != null) {
                                AsyncImage(
                                    model = displayImage,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        selectedImageUri = null
                                        existingImageUrl = null
                                        selectedMode = 0
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Tap to pick photo", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        if (displayImage != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Change Photo")
                                }
                                TextButton(
                                    onClick = {
                                        selectedImageUri = null
                                        existingImageUrl = null
                                        selectedMode = 0
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove Photo")
                                }
                            }
                        }
                    }

                    1 -> { // Emoji mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emojiText.ifBlank { "🎁" }, fontSize = 40.sp)
                        }

                        OutlinedTextField(
                            value = emojiText,
                            onValueChange = { emojiText = it },
                            label = { Text("Emoji / Icon") },
                            placeholder = { Text("Type an emoji e.g. 🍕") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Quick Select:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            commonEmojis.forEach { emoji ->
                                FilterChip(
                                    selected = emojiText == emoji,
                                    onClick = { emojiText = emoji },
                                    label = { Text(emoji, fontSize = 18.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                emojiText = "🎁"
                                selectedMode = 0
                            }) {
                                Text("Clear Emoji")
                            }
                        }
                    }

                    else -> { // Default mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎁", fontSize = 40.sp)
                        }
                    }
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Cost (points)") }, modifier = Modifier.fillMaxWidth())

                Text("Reward Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "recurring",
                        onClick = { type = "recurring" },
                        label = { Text("Recurring") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "one_time",
                        onClick = { type = "one_time" },
                        label = { Text("One-Time") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    val finalImageOrEmoji: String? = when (selectedMode) {
                        2 -> selectedImageUri?.toString() ?: existingImageUrl
                        1 -> emojiText.trim().ifBlank { null }
                        else -> null
                    }
                    onSave(
                        title,
                        description.takeIf { it.isNotBlank() },
                        cost.toIntOrNull() ?: 0,
                        finalImageOrEmoji,
                        type
                    )
                }
            }) {
                Text(if (reward == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Punishments Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PunishmentsScreen(viewModel: MainViewModel, state: AppState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<String?>(null) }
    var punishmentToEdit by remember { mutableStateOf<Punishment?>(null) }
    var punishmentToDelete by remember { mutableStateOf<Punishment?>(null) }
    var autoPunishmentsExpanded by remember { mutableStateOf(false) }
    val currentPartner = state.selectedPartner
    val isDominant = state.user?.role == "dominant"
    val isOfflineMode = state.isOfflineMode
    val canManage = isDominant || isOfflineMode

    var assignedPunishmentsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshPunishments() }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Punishments") },
                actions = {
                    IconButton(onClick = { viewModel.refreshPunishments() }) {
                        if (state.isRefreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            if (canManage) FloatingActionButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, "Add") }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {

                // Manually assigned punishments card (orange themed, collapsed by default)
                if (state.assignedPunishments.isNotEmpty()) {
                    item {
                        Card(
                            onClick = { assignedPunishmentsExpanded = !assignedPunishmentsExpanded },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF3E0),
                                contentColor = Color(0xFFE65100)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AssignmentLate,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Assigned Punishments",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Text(
                                        "${state.assignedPunishments.size} active assignment(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFEF6C00)
                                    )
                                }
                                Icon(
                                    if (assignedPunishmentsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (assignedPunishmentsExpanded) "Collapse" else "Expand",
                                    tint = Color(0xFFE65100)
                                )
                            }
                        }
                    }

                    if (assignedPunishmentsExpanded) {
                        items(state.assignedPunishments, key = { it.id }) { assignment ->
                            AssignedPunishmentCard(
                                assignment = assignment,
                                canManage = canManage,
                                onComplete = { viewModel.completePunishment(assignment.id) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Automated punishments (missed habits)
                if (state.autoPunishments.isNotEmpty()) {
                    item {
                        Card(
                            onClick = { autoPunishmentsExpanded = !autoPunishmentsExpanded },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Automated Penalties",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    val totalMisses = state.autoPunishments.sumOf { it.count }
                                    Text(
                                        "$totalMisses misses across ${state.autoPunishments.size} habits",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Icon(
                                    if (autoPunishmentsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (autoPunishmentsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    if (autoPunishmentsExpanded) {
                        items(state.autoPunishments, key = { it.habitId + it.punishmentId }) { auto ->
                            AutoPunishmentCard(auto, onClear = { viewModel.clearPunishment(auto.habitId) })
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // Punishment catalog
                if (canManage) {
                    item {
                        Text(
                            "Catalog",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                itemsIndexed(state.punishments, key = { _, p -> p.id }) { index, punishment ->
                    PunishmentCard(
                        punishment = punishment,
                        isDominant = canManage,
                        onAssign = { showAssignDialog = punishment.id },
                        onEdit = { punishmentToEdit = punishment },
                        onDelete = { punishmentToDelete = punishment },
                        onMoveUp = if (index > 0) {
                            { viewModel.movePunishment(index, index - 1) }
                        } else null,
                        onMoveDown = if (index < state.punishments.size - 1) {
                            { viewModel.movePunishment(index, index + 1) }
                        } else null
                    )
                }
                if (state.punishments.isEmpty()) {
                    item {
                        EmptyState(
                            "No punishments",
                            if (canManage) "Tap + to define punishments, then link them to habits to track missed days automatically" else "No punishments defined yet"
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PunishmentDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, desc ->
                viewModel.createPunishment(title, desc)
                showAddDialog = false
            }
        )
    }

    punishmentToEdit?.let { punishment ->
        PunishmentDialog(
            punishment = punishment,
            onDismiss = { punishmentToEdit = null },
            onSave = { title, desc ->
                viewModel.updatePunishment(punishment.id, title, desc)
                punishmentToEdit = null
            }
        )
    }

    punishmentToDelete?.let { punishment ->
        AlertDialog(
            onDismissRequest = { punishmentToDelete = null },
            title = { Text("Delete Punishment") },
            text = { Text("Are you sure you want to delete '${punishment.title}'?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePunishment(punishment.id)
                    punishmentToDelete = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { punishmentToDelete = null }) { Text("Cancel") }
            }
        )
    }

    showAssignDialog?.let { punishmentId ->
        AssignPunishmentDialog(
            onDismiss = { showAssignDialog = null },
            onAssign = { reason ->
                viewModel.assignPunishment(punishmentId, reason)
                showAssignDialog = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PunishmentCard(punishment: Punishment, isDominant: Boolean, onAssign: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onMoveUp: (() -> Unit)? = null, onMoveDown: (() -> Unit)? = null) {
    var showMenu by remember { mutableStateOf(false) }
    Card(modifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = { },
            onLongClick = { if (isDominant) showMenu = true }
        )) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚠️", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(punishment.title, fontWeight = FontWeight.Medium)
                punishment.description?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (isDominant) {
                IconButton(onClick = onAssign) { Icon(Icons.Default.Send, "Assign") }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        onMoveUp?.let {
                            DropdownMenuItem(
                                text = { Text("Move Up") },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }
                            )
                        }
                        onMoveDown?.let {
                            DropdownMenuItem(
                                text = { Text("Move Down") },
                                onClick = { showMenu = false; it() },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssignedPunishmentCard(
    assignment: AssignedPunishment,
    canManage: Boolean,
    onComplete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0),
            contentColor = Color(0xFF3E2723)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    assignment.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Assigned", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFFFFE0B2),
                        labelColor = Color(0xFFE65100)
                    )
                )
            }
            
            assignment.description?.let {
                if (it.isNotBlank()) {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D4037))
                }
            }

            assignment.reason?.let {
                if (it.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Reason: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFFD84315))
                }
            }

            if (canManage) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE65100),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark Complete")
                    }
                }
            }
        }
    }
}

@Composable
fun AutoPunishmentCard(auto: AutoPunishment, onClear: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(auto.punishmentTitle, fontWeight = FontWeight.Bold)
                    Text("Habit: ${auto.habitTitle}", style = MaterialTheme.typography.bodySmall)
                }
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(auto.count.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Missed on: ${auto.missedDates.joinToString(", ") { 
                try {
                    LocalDate.parse(it).format(DateTimeFormatter.ofPattern("MMM d"))
                } catch(e: Exception) {
                    it // Fallback for "Week of ..." strings
                }
            }}",
                style = MaterialTheme.typography.labelSmall)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear All Misses")
                }
            }
        }
    }
}

@Composable
fun PunishmentDialog(punishment: Punishment? = null, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var title by remember { mutableStateOf(punishment?.title ?: "") }
    var description by remember { mutableStateOf(punishment?.description ?: "") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (punishment == null) "New Punishment" else "Edit Punishment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (title.isNotBlank()) onSave(title, description.takeIf { it.isNotBlank() }) }) { 
            Text(if (punishment == null) "Add" else "Save")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AssignPunishmentDialog(onDismiss: () -> Unit, onAssign: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Assign Punishment") },
        text = { OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onAssign(reason.takeIf { it.isNotBlank() }) }) { Text("Assign") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats Screen
// ─────────────────────────────────────────────────────────────────────────────

fun isSubmissionOnDate(sub: HabitSubmission, targetDateStr: String): Boolean {
    if (sub.photoUrl.isBlank() || sub.photoUrl.contains("undefined") || sub.photoUrl.contains("null")) return false
    if (sub.submittedAt < 1672531200000L) return false // Ignore invalid dates prior to Jan 2023
    if (sub.date == targetDateStr) return true
    try {
        val localDate = java.time.Instant.ofEpochMilli(sub.submittedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        if (localDate.toString() == targetDateStr) return true
    } catch (e: Exception) {}
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: MainViewModel, state: AppState) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }
    var proofToDelete by remember { mutableStateOf<HabitSubmission?>(null) }
    val isDominant = state.user?.role == "dominant"
    val stats = state.stats
    
    val sheetState = rememberModalBottomSheetState()
    var showStatsDetail by remember { mutableStateOf(false) }

    val availableProofs = remember(state.allSubmissions, state.pendingSubmissions) {
        (state.allSubmissions + state.pendingSubmissions)
            .filter { 
                it.photoUrl.isNotBlank() && 
                it.submittedAt > 1672531200000L && 
                !it.photoUrl.contains("undefined") && 
                !it.photoUrl.contains("null")
            }
            .distinctBy { it.id }
            .sortedByDescending { it.submittedAt }
    }
    
    LaunchedEffect(Unit) { 
        viewModel.refreshStats()
        viewModel.refreshSubmissions()
    }

    val currentPartner = state.selectedPartner

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Stats") },
                actions = {
                    IconButton(onClick = { viewModel.refreshStats(); viewModel.refreshSubmissions() }) {
                        if (state.isRefreshing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (stats != null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard("🔥", "Streak", "${stats.streak} d", modifier = Modifier.weight(1f))
                            StatCard("⭐", "Points", "${stats.currentPoints}", modifier = Modifier.weight(1f))
                            StatCard("📊", "30 Days", "${stats.totalCompletionsLast30Days}", modifier = Modifier.weight(1f), onClick = { showStatsDetail = true })
                        }
                    }
                    
                    item {
                        HabitCalendar(
                            selectedDate = selectedDate,
                            history = state.history,
                            onDateSelected = { selectedDate = it }
                        )
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("History for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}", 
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                
                                val allHabits = state.habits + state.partnerHabits
                                val habitsToShow = allHabits.filter { habit ->
                                    if (habit.isPaused) return@filter false
                                    if (habit.frequency == "task") {
                                        val taskCompletion = state.history.find { it.habitId == habit.id }
                                        if (taskCompletion != null) {
                                            val completionDate = try { LocalDate.parse(taskCompletion.date) } catch(e: Exception) { null }
                                            if (completionDate != null && selectedDate.isAfter(completionDate)) {
                                                return@filter false
                                            }
                                        }
                                    }
                                    true
                                }
                                
                                if (habitsToShow.isEmpty()) {
                                    Text("No active habits", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    habitsToShow.forEach { habit ->
                                        val isCompleted = isHabitCompleted(habit, state.history, selectedDate)
                                        val dateSubmissions = (state.allSubmissions + state.pendingSubmissions)
                                            .filter { it.habitId == habit.id && isSubmissionOnDate(it, selectedDate.toString()) && it.photoUrl.isNotBlank() }
                                            .distinctBy { it.id }

                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (!habit.isPositive) {
                                                    IconButton(
                                                        onClick = { viewModel.toggleHabit(habit.id, selectedDate.toString()) },
                                                        enabled = isDominant || state.isOfflineMode
                                                    ) {
                                                        if (isCompleted) {
                                                            Icon(Icons.Default.CheckCircle, contentDescription = "Avoided", tint = MaterialTheme.colorScheme.primary)
                                                        } else {
                                                            Icon(Icons.Default.Close, contentDescription = "Missed", tint = MaterialTheme.colorScheme.error)
                                                        }
                                                    }
                                                } else {
                                                    Checkbox(
                                                        checked = isCompleted,
                                                        onCheckedChange = { viewModel.toggleHabit(habit.id, selectedDate.toString()) },
                                                        enabled = isDominant || state.isOfflineMode
                                                    )
                                                }
                                                Text(habit.title, style = MaterialTheme.typography.bodyMedium)
                                                if (habit.pointsReward > 0) {
                                                    Spacer(Modifier.weight(1f))
                                                    Text("+${habit.pointsReward}⭐", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }

                                            if (dateSubmissions.isNotEmpty()) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(start = 48.dp, top = 4.dp)
                                                ) {
                                                    dateSubmissions.forEach { sub ->
                                                        ProofPhotoThumbnail(
                                                            photoUrl = sub.photoUrl,
                                                            isNsfw = sub.isNsfw,
                                                            modifier = Modifier.size(56.dp),
                                                            onClick = { fullscreenUrl = sub.photoUrl }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bar chart
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showStatsDetail = true }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Last 30 Days", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Default.OpenInFull, contentDescription = "Expand", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(12.dp))
                                val maxCount = stats.completionsByDay.maxOfOrNull { it.count } ?: 1
                                if (maxCount > 0) {
                                    Row(modifier = Modifier.fillMaxWidth().height(80.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                        // Show last 30 days of data
                                        val last30Days = (0..29).map { LocalDate.now().minusDays(it.toLong()).toString() }.reversed()
                                        last30Days.forEach { dateStr ->
                                            val dayStat = stats.completionsByDay.find { it.date == dateStr }
                                            val count = dayStat?.count ?: 0
                                            val fraction = count.toFloat() / maxCount
                                            Box(modifier = Modifier.weight(1f).fillMaxHeight(),
                                                contentAlignment = Alignment.BottomCenter) {
                                                Box(modifier = Modifier.fillMaxWidth()
                                                    .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                                    .background(if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                                            }
                                        }
                                    }
                                } else {
                                    Text("No completions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Photo Proof Gallery Section
                    if (availableProofs.isNotEmpty()) {
                        item {
                            Text("Submitted Proofs (${availableProofs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                        }
                        
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(availableProofs) { proof ->
                                    Card(
                                        modifier = Modifier.width(140.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Box {
                                                ProofPhotoThumbnail(
                                                    photoUrl = proof.photoUrl,
                                                    isNsfw = proof.isNsfw,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(110.dp),
                                                    onClick = { fullscreenUrl = proof.photoUrl }
                                                )
                                                Surface(
                                                    color = when(proof.status) {
                                                        "approved" -> MaterialTheme.colorScheme.primary
                                                        "rejected" -> MaterialTheme.colorScheme.error
                                                        else -> MaterialTheme.colorScheme.tertiary
                                                    },
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                                ) {
                                                    Text(
                                                        text = proof.status.replaceFirstChar { it.uppercase() },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = proof.habitTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = try { LocalDate.parse(proof.date).format(DateTimeFormatter.ofPattern("MMM d, yyyy")) } catch(e: Exception) { proof.date },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                IconButton(
                                                    onClick = { proofToDelete = proof },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete proof",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Monthly Check-ins Section
                    item {
                        Text("Monthly Reflections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    }

                    if (state.checkIns.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text("No reflections yet.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(state.checkIns.sortedByDescending { it.month }) { checkIn ->
                            CheckInCard(checkIn)
                        }
                    }
                } else {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            if (showStatsDetail && stats != null) {
                ModalBottomSheet(
                    onDismissRequest = { showStatsDetail = false },
                    sheetState = sheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .navigationBarsPadding()
                    ) {
                        Text("Last 30 Days Detail", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))

                        // Larger Bar Graph with Dates
                        val maxCount = stats.completionsByDay.maxOfOrNull { it.count } ?: 1
                        val last30DaysStrings = (0..29).map { LocalDate.now().minusDays(it.toLong()).toString() }.reversed()
                        
                        Column(modifier = Modifier.fillMaxWidth().height(160.dp).padding(bottom = 16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                last30DaysStrings.forEach { dateStr ->
                                    val count = stats.completionsByDay.find { it.date == dateStr }?.count ?: 0
                                    val fraction = count.toFloat() / maxCount
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth()
                                                .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                                .background(if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                    }
                                }
                            }
                            // Date labels (Show every 5th day to avoid overlap)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                last30DaysStrings.forEachIndexed { index, dateStr ->
                                    if (index % 5 == 0 || index == 29) {
                                        val date = LocalDate.parse(dateStr)
                                        Text(
                                            text = date.format(DateTimeFormatter.ofPattern("M/d")),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .wrapContentWidth(unbounded = true),
                                            textAlign = TextAlign.Center,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val last30Days = (0..29).map { LocalDate.now().minusDays(it.toLong()) }
                            items(last30Days) { date ->
                                val dateStr = date.toString()
                                val dayHistory = state.history.filter { it.date == dateStr }
                                val dayProofs = (state.allSubmissions + state.pendingSubmissions)
                                    .filter { isSubmissionOnDate(it, dateStr) && it.photoUrl.isNotBlank() }
                                    .distinctBy { it.id }
                                val count = dayHistory.size
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), 
                                            style = MaterialTheme.typography.bodyLarge, 
                                            fontWeight = FontWeight.Bold)
                                        
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = if (count > 0) "$count ✅" else "None",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (count > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    if (dayHistory.isNotEmpty()) {
                                        val habitNames = dayHistory.mapNotNull { hist ->
                                            state.habits.find { it.id == hist.habitId }?.title ?: 
                                            state.partnerHabits.find { it.id == hist.habitId }?.title
                                        }.distinct().joinToString(", ")
                                        
                                        if (habitNames.isNotBlank()) {
                                            Text(
                                                text = habitNames,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }

                                    if (dayProofs.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 6.dp)
                                        ) {
                                            dayProofs.forEach { proof ->
                                                ProofPhotoThumbnail(
                                                    photoUrl = proof.photoUrl,
                                                    isNsfw = proof.isNsfw,
                                                    modifier = Modifier.size(48.dp),
                                                    onClick = { fullscreenUrl = proof.photoUrl }
                                                )
                                            }
                                        }
                                    }
                                    
                                    HorizontalDivider(
                                        modifier = Modifier.padding(top = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            proofToDelete?.let { proof ->
                AlertDialog(
                    onDismissRequest = { proofToDelete = null },
                    title = { Text("Delete Proof Submission") },
                    text = { Text("Are you sure you want to delete this submission proof for '${proof.habitTitle}'?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteSubmission(proof.id)
                                proofToDelete = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { proofToDelete = null }) { Text("Cancel") }
                    }
                )
            }

            fullscreenUrl?.let { url ->
                FullscreenImageDialog(imageUrl = url) { fullscreenUrl = null }
            }
        }
    }
}

@Composable
fun HabitCalendar(selectedDate: LocalDate, history: List<HabitCompletion>, onDateSelected: (LocalDate) -> Unit) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7 // 1 (Mon) to 7 (Sun) -> 0 (Sun) to 6 (Sat)
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, null) }
                Text(
                    text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, null) }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Days of week header
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                    Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, 
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            // Calendar grid
            val totalCells = ((daysInMonth + firstDayOfMonth + 6) / 7) * 7
            for (i in 0 until totalCells step 7) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (j in 0 until 7) {
                        val dayIndex = i + j
                        val dayNumber = dayIndex - firstDayOfMonth + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = currentMonth.atDay(dayNumber)
                            val isSelected = date == selectedDate
                            val hasCompletions = history.any { it.date == date.toString() }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { onDateSelected(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNumber.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasCompletions) {
                                        Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(
                                            if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        ))
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatCard(emoji: String, label: String, value: String, subLabel: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val content = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 28.sp)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            subLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier) { content() }
    } else {
        Card(modifier = modifier) { content() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile / Settings Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: MainViewModel, state: AppState, onChangeServer: () -> Unit) {
    var showPairDialog by remember { mutableStateOf(false) }
    var showPointsDialog by remember { mutableStateOf(false) }
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    var userToChangePassword by remember { mutableStateOf<User?>(null) }
    var userToDelete by remember { mutableStateOf<User?>(null) }

    val isDominant = state.user?.role == "dominant"
    val isOfflineMode = state.isOfflineMode

    LaunchedEffect(isOfflineMode) {
        if (!isOfflineMode) {
            viewModel.refreshAdminUsers()
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> 
            uri?.let { viewModel.uploadAvatar(it.toString()) }
        }
    )

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding)) {
            state.user?.let { user ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { 
                                            avatarPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (user.avatarUrl != null) {
                                        AsyncImage(
                                            model = user.avatarUrl,
                                            contentDescription = "My Avatar",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp))
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(user.username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        if (isOfflineMode) "Solo Mode (Offline)" else user.role.replaceFirstChar { it.uppercase() },
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            if (isOfflineMode) {
                                Text("Solo Mode Active", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("All habits, rewards, and progress are saved locally on this device. You have full permissions to manage and complete habits.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Your pair code:", style = MaterialTheme.typography.labelSmall)
                                Text(user.pairCode ?: "—", style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 4.sp)
                                Text("Share this with your partner", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Partner status (Online mode only)
                if (!isOfflineMode) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Partners (${user.partners.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                if (user.partners.isNotEmpty()) {
                                    user.partners.forEach { partner ->
                                        val isSelected = partner.id == state.selectedPartner?.id
                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (partner.avatarUrl != null) {
                                                        AsyncImage(
                                                            model = partner.avatarUrl,
                                                            contentDescription = "Partner Avatar",
                                                            modifier = Modifier.fillMaxSize().clickable { fullscreenUrl = partner.avatarUrl },
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    } else {
                                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(partner.username, fontWeight = FontWeight.Bold)
                                                    Text("${partner.points} ⭐ points", style = MaterialTheme.typography.bodySmall)
                                                }
                                                if (isSelected) {
                                                    AssistChip(
                                                        onClick = { },
                                                        label = { Text("Active", style = MaterialTheme.typography.labelSmall) },
                                                        leadingIcon = { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                } else {
                                                    TextButton(onClick = { viewModel.selectPartner(partner.id) }) {
                                                        Text("Switch")
                                                    }
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                IconButton(onClick = { viewModel.unpair(partner.id) }) {
                                                    Icon(Icons.Default.LinkOff, "Unpair", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(onClick = { showPairDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Pair New Partner")
                                    }
                                } else {
                                    Text("Not paired with anyone", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { showPairDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Enter Partner's Code")
                                    }
                                }
                            }
                        }
                    }
                }

                // Adjust points
                if (isOfflineMode || (isDominant && user.partner != null)) {
                    item {
                        OutlinedButton(onClick = { showPointsDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manually Adjust Points")
                        }
                    }
                }

                // Server Connection Status
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (!state.isOfflineMode) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (state.isOfflineMode) Icons.Default.CloudOff else Icons.Default.CloudDone,
                                null,
                                tint = if (state.isOfflineMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    if (state.isOfflineMode) "Solo Mode (Offline)" else "Connected to Server",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (state.isOfflineMode) "Data is stored locally on device" else state.serverUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Collapsible Server User Management Section (by Change Server)
                if (!isOfflineMode) {
                    item {
                        ServerUserManagementCard(
                            viewModel = viewModel,
                            state = state,
                            onEditUser = { userToEdit = it },
                            onChangePassword = { userToChangePassword = it },
                            onDeleteUser = { userToDelete = it }
                        )
                    }
                }

                // Notifications & Background Sync Section
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Notifications & Background Sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Persistent Background Sync", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Keep connection active in background for real-time notifications when app is closed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.backgroundSyncEnabled,
                                    onCheckedChange = { viewModel.setBackgroundSync(it) }
                                )
                            }
                        }
                    }
                }

                // Theming Section
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Theming", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            
                            Text("Theme Mode", style = MaterialTheme.typography.labelMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("system", "light", "dark").forEach { mode ->
                                    val isSelected = state.darkModeConfig == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setDarkMode(mode) },
                                        label = { Text(mode.replaceFirstChar { it.uppercase() }) },
                                        modifier = Modifier.weight(1f),
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dynamic Colors", style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        "Use system accent colors (Android 12+)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.dynamicColor,
                                    onCheckedChange = { viewModel.setDynamicColor(it) }
                                )
                            }

                            if (!state.dynamicColor) {
                                Spacer(Modifier.height(16.dp))
                                Text("Accent Color", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val colorMap = listOf(
                                        "purple" to Color(0xFF6650a4),
                                        "red" to Color(0xFFBA1A1A),
                                        "green" to Color(0xFF386A20),
                                        "blue" to Color(0xFF005FAF),
                                        "orange" to Color(0xFF8B5000),
                                        "teal" to Color(0xFF006A6A)
                                    )
                                    
                                    colorMap.forEach { (name, color) ->
                                        val isSelected = state.accentColor == name
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .clickable { viewModel.setAccentColor(name) }
                                                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (isOfflineMode) {
                    Button(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Router, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Exit Solo Mode / Connect to Server")
                    }
                } else {
                    OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Router, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Change Server")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.enterOfflineMode() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Switch to Solo Mode (Offline)")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.logout() }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Log Out")
                    }
                }
            }
        }
    }

    if (showPairDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showPairDialog = false },
            title = { Text("Enter Partner's Code") },
            text = { OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Pair Code") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = { viewModel.pairWithPartner(code); showPairDialog = false }) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { showPairDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPointsDialog) {
        PointsAdjustmentDialog(
            onDismiss = { showPointsDialog = false },
            onApply = { amount, reason ->
                viewModel.adjustPoints(amount, reason, if (isOfflineMode) null else state.user?.partner?.id)
                showPointsDialog = false
            }
        )
    }

    userToEdit?.let { u ->
        EditUserDialog(
            user = u,
            onDismiss = { userToEdit = null },
            onSave = { newUsername, newRole, newPoints ->
                viewModel.updateAdminUser(u.id, newUsername, newRole, newPoints)
                userToEdit = null
            }
        )
    }

    userToChangePassword?.let { u ->
        ChangePasswordDialog(
            user = u,
            onDismiss = { userToChangePassword = null },
            onSave = { newPassword ->
                viewModel.changeAdminUserPassword(u.id, newPassword)
                userToChangePassword = null
            }
        )
    }

    userToDelete?.let { u ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete User") },
            text = { Text("Are you sure you want to delete user '${u.username}' from the server?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAdminUser(u.id)
                        userToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancel") }
            }
        )
    }

    fullscreenUrl?.let { url ->
        FullscreenImageDialog(url) { fullscreenUrl = null }
    }
}

@Composable
fun ServerUserManagementCard(
    viewModel: MainViewModel,
    state: AppState,
    onEditUser: (User) -> Unit,
    onChangePassword: (User) -> Unit,
    onDeleteUser: (User) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        expanded = !expanded
                        if (expanded) {
                            viewModel.refreshAdminUsers()
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Server User Management",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expanded) {
                        IconButton(
                            onClick = { viewModel.refreshAdminUsers() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, "Refresh Users", modifier = Modifier.size(18.dp))
                        }
                    }
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                val users = state.adminUsers
                if (users.isEmpty()) {
                    Text(
                        "No users found or loading...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.refreshAdminUsers() }) { Text("Load Server Users") }
                } else {
                    users.forEach { user ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.username, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${user.role.replaceFirstChar { it.uppercase() }} • ${user.points} ⭐",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onEditUser(user) }) {
                                    Icon(Icons.Default.Edit, "Edit User", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onChangePassword(user) }) {
                                    Icon(Icons.Default.Key, "Change Password", modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { onDeleteUser(user) }) {
                                    Icon(Icons.Default.Delete, "Delete User", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditUserDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var username by remember { mutableStateOf(user.username) }
    var role by remember { mutableStateOf(user.role) }
    var pointsStr by remember { mutableStateOf(user.points.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User: ${user.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Role", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("submissive", "dominant").forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = pointsStr,
                    onValueChange = { pointsStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Points ⭐") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pts = pointsStr.toIntOrNull() ?: user.points
                onSave(username, role, pts)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password for ${user.username}") },
        text = {
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (newPassword.isNotBlank()) {
                    onSave(newPassword)
                }
            }) { Text("Update Password") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CameraCaptureDialog(
    onDismiss: () -> Unit,
    onImageCaptured: (android.net.Uri) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val imageCapture = remember { androidx.camera.core.ImageCapture.Builder().build() }
    var lensFacing by remember { mutableIntStateOf(androidx.camera.core.CameraSelector.LENS_FACING_BACK) }
    val previewView = remember { androidx.camera.view.PreviewView(context) }
    var flashMode by remember { mutableIntStateOf(androidx.camera.core.ImageCapture.FLASH_MODE_OFF) }

    var hasCameraPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(lensFacing, hasCameraPermission) {
        if (hasCameraPermission) {
            val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = androidx.camera.core.CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            window?.let {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false)
            }
            onDispose {}
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (hasCameraPermission) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Top control bar (Close & Flash)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Camera")
                    }

                    if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                        IconButton(
                            onClick = {
                                flashMode = when (flashMode) {
                                    androidx.camera.core.ImageCapture.FLASH_MODE_OFF -> androidx.camera.core.ImageCapture.FLASH_MODE_ON
                                    androidx.camera.core.ImageCapture.FLASH_MODE_ON -> androidx.camera.core.ImageCapture.FLASH_MODE_AUTO
                                    else -> androidx.camera.core.ImageCapture.FLASH_MODE_OFF
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            val icon = when (flashMode) {
                                androidx.camera.core.ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                androidx.camera.core.ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            }
                            Icon(icon, contentDescription = "Flash")
                        }
                    }
                }

                // Bottom control bar (Capture & Switch Camera)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 128.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.size(56.dp))

                    // Capture button
                    FloatingActionButton(
                        onClick = {
                            imageCapture.flashMode = flashMode
                            val photoFile = java.io.File(
                                context.cacheDir,
                                "captured_${System.currentTimeMillis()}.jpg"
                            )
                            val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            imageCapture.takePicture(
                                outputOptions,
                                androidx.core.content.ContextCompat.getMainExecutor(context),
                                object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                                        onImageCaptured(android.net.Uri.fromFile(photoFile))
                                    }

                                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                        exception.printStackTrace()
                                    }
                                }
                            )
                        },
                        modifier = Modifier.size(72.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = "Capture Photo",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Switch Camera button
                    FloatingActionButton(
                        onClick = {
                            lensFacing = if (lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                                androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                            } else {
                                androidx.camera.core.CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Camera, null, modifier = Modifier.size(64.dp), tint = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Camera permission is required to take photos", color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoSubmissionDialog(habitTitle: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showCamera by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) selectedImageUri = uri }
    )

    if (showCamera) {
        CameraCaptureDialog(
            onDismiss = { showCamera = false },
            onImageCaptured = { uri ->
                selectedImageUri = uri
                showCamera = false
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Submit Proof: $habitTitle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Take a photo or choose one from your gallery as proof of completion.")
                    
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { 
                                if (selectedImageUri == null) showCamera = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected proof",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Attach Photo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Options: Take Photo & Gallery
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCamera = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Camera, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Camera")
                        }

                        OutlinedButton(
                            onClick = {
                                pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gallery")
                        }
                    }
                    
                    if (selectedImageUri != null) {
                        Text("Photo attached ✅", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedImageUri?.let { onSubmit(it.toString()) } },
                    enabled = selectedImageUri != null
                ) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}

@Composable
fun ProofPhotoThumbnail(
    photoUrl: String,
    isNsfw: Boolean,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop,
    onClick: () -> Unit
) {
    var isError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { if (!isError) onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (!isError && photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Proof photo",
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isNsfw) Modifier.blur(24.dp) else Modifier
                    ),
                contentScale = contentScale,
                onError = { isError = true }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "Image unavailable",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubmissionCard(
    submission: HabitSubmission,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var showFullscreen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    if (onDelete != null) showDeleteConfirm = true
                }
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(submission.username, fontWeight = FontWeight.Bold)
                    Text(submission.habitTitle, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    try { LocalDate.parse(submission.date).format(DateTimeFormatter.ofPattern("MMM d")) } catch(e: Exception) { submission.date }, 
                    style = MaterialTheme.typography.labelSmall
                )
                if (onDelete != null) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(28.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete submission",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            
            ProofPhotoThumbnail(
                photoUrl = submission.photoUrl,
                isNsfw = submission.isNsfw,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                onClick = { showFullscreen = true }
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("Approve") }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text("Reject") 
                }
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Pending Proof") },
            text = { Text("Are you sure you want to delete this pending proof submission for '${submission.habitTitle}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showFullscreen) {
        FullscreenImageDialog(submission.photoUrl) { showFullscreen = false }
    }
}

@Composable
fun CheckInCard(checkIn: MonthlyCheckIn) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = LocalDate.parse("${checkIn.month}-01").format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                repeat(5) { index ->
                    Icon(
                        imageVector = if (index < checkIn.rating) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (index < checkIn.rating) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("By: ${checkIn.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Reflections:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(checkIn.reflections, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Shared Goals Progress:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(checkIn.goalProgress, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MonthlyCheckInDialog(onDismiss: () -> Unit, onSubmit: (String, String, Int) -> Unit) {
    var reflections by remember { mutableStateOf("") }
    var goalProgress by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly Check-in: ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("How was your month? Reflect on your shared goals and habit consistency.")
                
                OutlinedTextField(
                    value = reflections,
                    onValueChange = { reflections = it },
                    label = { Text("Reflections") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                OutlinedTextField(
                    value = goalProgress,
                    onValueChange = { goalProgress = it },
                    label = { Text("Progress on Shared Goals") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                
                Column {
                    Text("Overall Rating", style = MaterialTheme.typography.labelMedium)
                    Row {
                        repeat(5) { index ->
                            IconButton(onClick = { rating = index + 1 }) {
                                Icon(
                                    imageVector = if (index < rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (index < rating) Color(0xFFFFD700) else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (reflections.isNotBlank() && goalProgress.isNotBlank()) onSubmit(reflections, goalProgress, rating) },
                enabled = reflections.isNotBlank() && goalProgress.isNotBlank()
            ) { Text("Submit Check-in") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PointsAdjustmentDialog(onDismiss: () -> Unit, onApply: (Int, String?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var isAddition by remember { mutableStateOf(true) }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Adjust Points") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true },
                        label = { Text("Add (+)") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (isAddition) { { Icon(Icons.Default.Add, null) } } else null
                    )
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false },
                        label = { Text("Subtract (-)") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (!isAddition) { { Icon(Icons.Default.Remove, null) } } else null
                    )
                }

                OutlinedTextField(
                    value = amount, 
                    onValueChange = { if (it.all { char -> char.isDigit() }) amount = it },
                    label = { Text("Points Amount") }, 
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                OutlinedTextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toIntOrNull() ?: 0
                if (value != 0) {
                    onApply(if (isAddition) value else -value, reason.takeIf { r -> r.isNotBlank() })
                }
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🌱", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun FullscreenImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}
