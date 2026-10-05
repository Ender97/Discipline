package com.accord.discipline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var dataStore: androidx.datastore.core.DataStore<Preferences>

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = runBlocking { dataStore.data.first() }
            val isSyncEnabled = prefs[PrefsKeys.BACKGROUND_SYNC] ?: false
            val token = prefs[PrefsKeys.TOKEN]

            if (isSyncEnabled && !token.isNullOrBlank()) {
                val serviceIntent = Intent(context, WebSocketService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
