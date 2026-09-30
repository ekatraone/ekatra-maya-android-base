package org.ekatra.alfred

import org.ekatra.alfred.ui.chat.ChatScreen
import org.ekatra.alfred.ui.chat.EkatraChatTheme
import org.ekatra.alfred.ui.PersonaDirection
import org.ekatra.alfred.data.AvailableModels
import org.ekatra.alfred.persona.ActivePersona
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.io.File
import java.net.NetworkInterface
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ekatra.alfred.data.AppSettings
import org.ekatra.alfred.data.sync.SyncWorker


/**
 * NativeChatActivity - Pure Compose UI for faster performance
 * 
 * No WebView overhead - direct Kotlin UI updates.
 */
@AndroidEntryPoint
class NativeChatActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "NativeChatActivity"
    }
    
    @Inject lateinit var settings: AppSettings

    // Text-to-Speech engine — uses system voice settings
    var tts: TextToSpeech? = null
        private set

    private val modelPath: File? by lazy {
        // If the currently selected model file is missing, try to recover by detecting any downloaded model
        val selectedModel = settings.getSelectedModel()
        val directFile = File(filesDir, "models/${selectedModel.filename}")
        if (directFile.exists()) {
            // Some shipped llama.cpp builds fail to load certain models; the persona may name a
            // known-compatible fallback to switch to if it is already downloaded.
            val fallback = ActivePersona.modelLoadFallbacks[selectedModel.id]?.let { AvailableModels.getModelById(it) }
            if (fallback != null) {
                val fallbackFile = File(filesDir, "models/${fallback.filename}")
                if (fallbackFile.exists()) {
                    settings.setSelectedModel(fallback.id)
                    Log.w(TAG, "${selectedModel.id} unsupported on this runtime; falling back to ${fallback.id}")
                    return@lazy fallbackFile
                }
            }
            return@lazy directFile
        }

        val recovered = settings.recoverDownloadedModel(this)
        if (recovered != null) {
            Log.w(TAG, "Selected model missing; switched to downloaded model ${recovered.filename}")
            return@lazy File(filesDir, "models/${recovered.filename}")
        }

        null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize TTS engine (uses whatever voice/speed the user set in system TTS settings)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                Log.d(TAG, "TTS initialized: ${Locale.getDefault()}")
            } else {
                Log.w(TAG, "TTS initialization failed with status: $status")
            }
        }

        if (modelPath == null) {
            Log.e(TAG, "Model missing; redirecting to download")
            startActivity(Intent(this, ModelDownloadActivity::class.java))
            finish()
            return
        }

        setContent {
            PersonaDirection {
                EkatraChatTheme {
                    ChatScreen(
                        modelFile = modelPath!!,
                        onSettingsClick = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        onHotspotToggle = { enable ->
                            if (enable) {
                                startService(Intent(this, AlfredServerService::class.java))
                                Log.d(TAG, "Hotspot/server toggled on")
                            } else {
                                stopService(Intent(this, AlfredServerService::class.java))
                                Log.d(TAG, "Hotspot/server toggled off")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger immediate sync when app comes to foreground
        SyncWorker.triggerImmediateSync(this)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}


@Suppress("UNUSED_PARAMETER")
fun getWifiHotspotIpAddress(context: android.content.Context): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            // Check for wlan0 (WiFi) or ap0 (hotspot) interfaces
            if (intf.name.startsWith("wlan") || intf.name.startsWith("ap")) {
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress ?: "192.168.43.1"
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("NativeChatActivity", "Failed to get IP", e)
    }
    return "192.168.43.1" // Default hotspot IP
}
