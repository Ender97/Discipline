package com.accord.discipline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WebSocketService : Service() {

    @Inject
    lateinit var wsManager: WebSocketManager

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val SYNC_CHANNEL_ID = "discipline_sync_channel"
    private val SYNC_NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundNotification()

        serviceScope.launch {
            val prefs = dataStore.data.first()
            val token = prefs[PrefsKeys.TOKEN]
            val serverUrl = prefs[PrefsKeys.SERVER_URL] ?: ""
            val isSyncEnabled = prefs[PrefsKeys.BACKGROUND_SYNC] ?: false

            if (isSyncEnabled && !token.isNullOrBlank() && serverUrl.isNotBlank()) {
                wsManager.connect(serverUrl, token)
            }
        }

        // Observe WebSocket events and trigger notifications when background service is running
        serviceScope.launch {
            wsManager.events.collect { event ->
                handleWsEvent(event)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SYNC_CHANNEL_ID,
                "Background Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection active for real-time notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("Discipline Sync Active")
            .setContentText("Listening for real-time notifications")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                SYNC_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else 0
            )
        } else {
            startForeground(SYNC_NOTIFICATION_ID, notification)
        }
    }

    private fun handleWsEvent(event: WsEvent) {
        when (event.type) {
            "HABIT_COMPLETED" -> {
                notificationHelper.showNotification("Habit Update", "Your partner completed a habit! 🎉")
            }
            "HABIT_UNCOMPLETED" -> {
                notificationHelper.showNotification("Habit Update", "Your partner uncompleted a habit")
            }
            "SUBMISSION_APPROVED" -> {
                notificationHelper.showNotification("Habit Update", "✅ Proof for ${event.habitTitle ?: "habit"} approved!")
            }
            "SUBMISSION_REJECTED" -> {
                notificationHelper.showNotification("Habit Update", "❌ Proof for ${event.habitTitle ?: "habit"} rejected")
            }
            "HABIT_PAUSED" -> {
                notificationHelper.showNotification("Habit Update", "Your partner paused: ${event.habit?.title}")
            }
            "HABIT_RESUMED" -> {
                notificationHelper.showNotification("Habit Update", "Your partner resumed: ${event.habit?.title}")
            }
            "HABIT_CREATED", "HABIT_UPDATED" -> {
                event.habit?.title?.let { title ->
                    notificationHelper.showNotification("Habit Update", "Your partner added/updated a habit: $title")
                }
            }
            "REWARD_REDEEMED" -> {
                val title = event.rewardTitle ?: "a reward"
                notificationHelper.showNotification("Reward Redeemed", "🎁 Your partner redeemed: $title")
            }
            "PUNISHMENT_ASSIGNED" -> {
                notificationHelper.showNotification("New Punishment", "⚠️ You received a punishment!")
            }
            "HABIT_SUBMITTED" -> {
                notificationHelper.showNotification("Proof Submitted", "${event.username} submitted proof for: ${event.habitTitle}")
            }
        }
    }
}
