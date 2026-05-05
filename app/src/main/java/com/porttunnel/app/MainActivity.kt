package com.porttunnel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.porttunnel.app.ui.screens.HomeScreen
import com.porttunnel.app.ui.theme.PortTunnelTheme
import com.porttunnel.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bind to TunnelService (starts service if not already running)
        viewModel.startService(this)

        setContent {
            PortTunnelTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        // Only unbind; the service stays alive while tunnels are active
        viewModel.stopService(this)
        super.onDestroy()
    }
}
