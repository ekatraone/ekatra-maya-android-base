package org.ekatra.alfred

import org.ekatra.alfred.ui.PersonaDirection
import org.ekatra.alfred.ui.BackButton
import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.ekatra.alfred.ui.HostModeSection
import org.ekatra.alfred.viewmodel.SettingsViewModel

@AndroidEntryPoint
class HostModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PersonaDirection {
                EkatraSettingsTheme {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsState()

                    HostModeScreen(
                        isServerRunning = uiState.isServerRunning,
                        isServerLoading = uiState.isServerLoading,
                        serverUrl = uiState.serverUrl,
                        onBackPressed = { finish() },
                        onStartServer = {
                            viewModel.setServerLoading(true)
                            val svcIntent = Intent(this, AlfredServerService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(svcIntent)
                            } else {
                                startService(svcIntent)
                            }
                            val handler = Handler(Looper.getMainLooper())
                            val pollRunnable = object : Runnable {
                                var attempts = 0
                                override fun run() {
                                    val app = application as EkatraApp
                                    val url = app.alfredServer?.getServerUrl() ?: ""
                                    if (url.isNotBlank()) {
                                        viewModel.setServerState(true, url)
                                    } else if (attempts < 30) {
                                        attempts++
                                        handler.postDelayed(this, 1000)
                                    } else {
                                        if (url.isNotBlank()) viewModel.setServerState(true, url)
                                        else viewModel.setServerLoading(false)
                                    }
                                }
                            }
                            handler.postDelayed(pollRunnable, 2000)
                        },
                        onStopServer = {
                            stopService(Intent(this, AlfredServerService::class.java))
                            viewModel.setServerState(false, "")
                        },
                        onOpenHotspot = {
                            try {
                                startActivity(Intent("android.settings.TETHER_SETTINGS"))
                            } catch (e: Exception) {
                                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostModeScreen(
    isServerRunning: Boolean,
    isServerLoading: Boolean,
    serverUrl: String,
    onBackPressed: () -> Unit,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onOpenHotspot: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.host_mode), fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onClick = onBackPressed) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF111318)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            HostModeSection(
                isServerRunning = isServerRunning,
                isServerLoading = isServerLoading,
                serverUrl = serverUrl,
                onStartServer = onStartServer,
                onStopServer = onStopServer,
                onOpenHotspot = onOpenHotspot
            )
        }
    }
}
