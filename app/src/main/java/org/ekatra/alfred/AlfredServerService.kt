package org.ekatra.alfred

import org.ekatra.alfred.persona.ActivePersona

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.ekatra.alfred.data.AppSettings
import org.ekatra.alfred.data.RemoteConfigManager
import org.ekatra.alfred.data.repository.ChatRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Named

/**
 * Foreground service to keep the server running
 */
@AndroidEntryPoint
class AlfredServerService : Service() {

    @Inject lateinit var llamaEngine: LlamaEngine
    @Inject lateinit var inferenceEngine: InferenceEngine
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var remoteConfigManager: RemoteConfigManager
    @Inject @Named("inference") lateinit var inferenceMutex: Mutex

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "AlfredServerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alfred_server_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting server service")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        val app = application as EkatraApp

        // Ensure model is loaded before accepting requests
        if (!llamaEngine.isReady()) {
            Log.d(TAG, "Model not loaded (state=${llamaEngine.state}), loading now...")
            serviceScope.launch {
                val loaded = ensureModelLoaded(app)
                applyRemoteConfigPrompt()
                if (loaded) {
                    startServerIfNeeded(app)
                } else {
                    Log.e(TAG, "Failed to load model, server will return errors")
                    startServerIfNeeded(app) // start anyway so /status shows "loading"
                }
            }
        } else {
            applyRemoteConfigPrompt()
            startServerIfNeeded(app)
        }
        
        return START_STICKY
    }

    private fun applyRemoteConfigPrompt() {
        val prompt = ActivePersona.resolveSystemPrompt(remoteConfigManager.systemPrompt.trim())
        llamaEngine.setSystemPrompt(prompt)
        Log.d(TAG, "Applied system prompt (${prompt.length} chars)")
    }

    private suspend fun ensureModelLoaded(app: EkatraApp): Boolean {
        val settings = AppSettings(applicationContext)
        val selectedModel = settings.getSelectedModel()
        val modelFile = File(filesDir, "models/${selectedModel.filename}")

        if (!modelFile.exists()) {
            // Try recovery
            val recovered = settings.recoverDownloadedModel(applicationContext)
            if (recovered != null) {
                val recoveredFile = File(filesDir, "models/${recovered.filename}")
                if (recoveredFile.exists()) {
                    Log.d(TAG, "Loading recovered model: ${recovered.filename}")
                    return llamaEngine.loadModel(recoveredFile)
                }
            }
            Log.e(TAG, "No model file found at ${modelFile.absolutePath}")
            return false
        }

        Log.d(TAG, "Loading model: ${selectedModel.filename}")
        return llamaEngine.loadModel(modelFile)
    }

    private fun startServerIfNeeded(app: EkatraApp) {
        if (app.alfredServer == null) {
            app.alfredServer = AlfredServer(inferenceEngine, chatRepository, applicationContext, inferenceMutex)
            app.alfredServer?.start()
            Log.d(TAG, "Server started on ${app.alfredServer?.getServerUrl()}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        val app = application as EkatraApp
        app.alfredServer?.stop()
        app.alfredServer = null
        Log.d(TAG, "Server service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.server_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.server_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, NativeChatActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.server_notification_title))
            .setContentText(getString(R.string.server_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
