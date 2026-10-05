package com.accord.discipline

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val TAG = "WebSocketManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var token: String? = null
    private var baseUrl: String? = null
    private var isConnecting = false
    private var reconnectAttempts = 0

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events

    fun connect(baseUrl: String, token: String) {
        this.baseUrl = baseUrl
        this.token = token
        reconnectAttempts = 0
        openSocket()
    }

    fun disconnect() {
        webSocket?.close(1000, "User logged out")
        webSocket = null
        token = null
        baseUrl = null
    }

    private fun openSocket() {
        if (isConnecting) return
        var base = baseUrl ?: return
        
        // Robust base URL standardization
        if (!base.startsWith("http")) base = "http://$base"
        if (!base.substringAfter("://").contains(":")) base = "$base:3000"

        val url = base.replace("http://", "ws://").replace("https://", "wss://")
            .trimEnd('/') + "/ws"
        val request = Request.Builder().url(url).build()
        isConnecting = true

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnecting = false
                reconnectAttempts = 0
                // Authenticate
                ws.send(gson.toJson(mapOf("type" to "AUTH", "token" to token)))
                // Start keepalive
                startPingLoop(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "WS message: $text")
                try {
                    val event = gson.fromJson(text, WsEvent::class.java)
                    scope.launch { _events.emit(event) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse WS event: $text", e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                isConnecting = false
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isConnecting = false
                if (code != 1000) scheduleReconnect() // Reconnect unless intentional close
            }
        })
    }

    private fun startPingLoop(ws: WebSocket) {
        scope.launch {
            while (true) {
                delay(30_000L) // Ping every 30 seconds
                if (ws.send(gson.toJson(mapOf("type" to "PING"))).not()) break
            }
        }
    }

    private fun scheduleReconnect() {
        if (token == null) return // Logged out, don't reconnect
        scope.launch {
            val delayMs = minOf(1000L * (1 shl reconnectAttempts), 30_000L) // Exponential backoff, max 30s
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt ${reconnectAttempts + 1})")
            delay(delayMs)
            reconnectAttempts++
            openSocket()
        }
    }
}
