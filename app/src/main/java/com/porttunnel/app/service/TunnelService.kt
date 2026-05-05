package com.porttunnel.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.porttunnel.app.MainActivity
import com.porttunnel.app.R
import com.porttunnel.app.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Foreground service that manages all active SSH tunnel sessions.
 * Uses JSch to establish SSH connections and set up local port forwarding.
 */
class TunnelService : Service() {

    data class TunnelStatus(
        val profileId: Int,
        val isConnected: Boolean,
        val errorMessage: String? = null
    )

    private val sessions = mutableMapOf<Int, Session>()

    // StateFlow so the ViewModel can observe connection changes reactively
    private val _activeIds = MutableStateFlow<Set<Int>>(emptySet())
    val activeIds: StateFlow<Set<Int>> = _activeIds.asStateFlow()

    inner class TunnelBinder : Binder() {
        fun getService(): TunnelService = this@TunnelService
    }

    private val binder = TunnelBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Establishes an SSH tunnel for the given profile.
     * Must be called from a coroutine (runs on Dispatchers.IO internally).
     */
    suspend fun connect(profile: Profile, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Disconnect any existing session for this profile
                sessions[profile.id]?.safeDisconnect()
                sessions.remove(profile.id)

                val jsch = JSch()
                val session = jsch.getSession(profile.sshUsername, profile.sshHost, profile.sshPort)
                session.setPassword(password)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("PreferredAuthentications", "password")
                // Keep-alive to prevent timeouts on mobile networks
                session.serverAliveInterval = 60_000
                session.serverAliveCountMax = 3
                session.connect(30_000)

                // Local port forwarding: localPort -> sshHost -> remoteHost:remotePort
                session.setPortForwardingL(profile.localPort, "127.0.0.1", profile.remotePort)

                sessions[profile.id] = session
                refreshState()
            }
        }

    /** Stops the tunnel for the given profile ID. */
    fun disconnect(profileId: Int) {
        sessions[profileId]?.safeDisconnect()
        sessions.remove(profileId)
        refreshState()
        if (sessions.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Returns true if the SSH session for this profile is alive. */
    fun isConnected(profileId: Int): Boolean =
        sessions[profileId]?.isConnected == true

    private fun refreshState() {
        // Remove any sessions that dropped silently
        val dead = sessions.filterValues { !it.isConnected }.keys
        dead.forEach { id -> sessions.remove(id) }
        _activeIds.value = sessions.keys.toSet()
        updateNotification()
    }

    private fun updateNotification() {
        val count = sessions.count { it.value.isConnected }
        if (count > 0) {
            startForeground(NOTIFICATION_ID, buildNotification(count))
        }
    }

    private fun buildNotification(activeCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$activeCount 条隧道运行中")
            .setSmallIcon(R.drawable.ic_tunnel)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH 隧道服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示当前活跃的 SSH 隧道数量"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        sessions.values.forEach { it.safeDisconnect() }
        sessions.clear()
        super.onDestroy()
    }

    private fun Session.safeDisconnect() = try {
        disconnect()
    } catch (_: Exception) {}

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "tunnel_service_channel"
    }
}
