package com.porttunnel.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.porttunnel.app.data.AppDatabase
import com.porttunnel.app.data.Profile
import com.porttunnel.app.service.TunnelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val profileDao = db.profileDao()

    /** All saved profiles, sorted by alias. */
    val profiles: StateFlow<List<Profile>> = profileDao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Service binding ----

    private var tunnelService: TunnelService? = null

    /** IDs of profiles whose tunnels are currently active. */
    private val _activeIds = MutableStateFlow<Set<Int>>(emptySet())
    val activeIds: StateFlow<Set<Int>> = _activeIds.asStateFlow()

    /** One-shot messages shown as snackbars in the UI. */
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    /** Whether a connect operation is in progress (shows loading indicator). */
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as TunnelService.TunnelBinder).getService()
            tunnelService = svc
            // Mirror the service's active-ID flow into our own
            viewModelScope.launch {
                svc.activeIds.collect { _activeIds.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            tunnelService = null
        }
    }

    // ---- Profile CRUD ----

    fun saveProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            if (profile.id == 0) profileDao.insertProfile(profile)
            else profileDao.updateProfile(profile)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch(Dispatchers.IO) {
            tunnelService?.disconnect(profile.id)
            profileDao.deleteProfile(profile)
        }
    }

    // ---- Tunnel management ----

    fun connect(profile: Profile, password: String) {
        val svc = tunnelService ?: run {
            viewModelScope.launch { _message.emit("服务未绑定，请稍后重试") }
            return
        }
        viewModelScope.launch {
            _isConnecting.value = true
            val result = svc.connect(profile, password)
            _isConnecting.value = false
            result.onSuccess {
                _message.emit("✓ 已连接：localhost:${profile.localPort} → ${profile.remotePort}")
            }.onFailure { e ->
                _message.emit("✗ 连接失败：${e.message}")
            }
        }
    }

    fun disconnect(profileId: Int) {
        tunnelService?.disconnect(profileId)
    }

    fun startService(context: Context) {
        val intent = Intent(context, TunnelService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun stopService(context: Context) {
        try {
            context.unbindService(serviceConnection)
        } catch (_: Exception) {}
        tunnelService = null
    }
}
