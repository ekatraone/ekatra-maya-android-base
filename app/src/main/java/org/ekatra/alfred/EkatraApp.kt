package org.ekatra.alfred

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import org.ekatra.alfred.data.RemoteConfigManager
import org.ekatra.alfred.data.sync.SyncWorker
import org.ekatra.alfred.notification.EkatraMessagingService
import javax.inject.Inject

/**
 * Ekatra Application - v1.1.0 with Hilt DI + WorkManager
 */
@HiltAndroidApp
class EkatraApp : Application(), Configuration.Provider {
    
    companion object {
        private const val TAG = "EkatraApp"
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var remoteConfigManager: RemoteConfigManager
    
    // Keep for AlfredServerService access
    var alfredServer: AlfredServer? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "${getString(R.string.app_name)} v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) starting...")
        
        // Create notification channels for FCM push notifications
        EkatraMessagingService.createNotificationChannels(this)
        
        // Log FCM token for debugging (token auto-registers with Firebase project)
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "FCM token fetched")
            }
        }
        
        // Schedule periodic background sync
        SyncWorker.schedule(this)
        
        // Fetch latest remote config values (non-blocking, defaults will be used if fails)
        try {
            remoteConfigManager.fetchAndActivate { activated ->
                Log.d(TAG, "Remote Config fetch completed: activated=$activated")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config fetch failed, continuing with defaults", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    override fun onTerminate() {
        super.onTerminate()
        alfredServer?.stop()
    }
}
