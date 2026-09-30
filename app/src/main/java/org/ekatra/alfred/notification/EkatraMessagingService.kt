package org.ekatra.alfred.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.ekatra.alfred.NativeChatActivity
import org.ekatra.alfred.R

/**
 * Firebase Cloud Messaging service for developer-sent push notifications.
 *
 * Supports two notification types:
 * 1. **General announcements** — news, tips, feature updates
 * 2. **App update available** — prompts user to update
 *
 * Send from Firebase Console → Cloud Messaging, or via FCM HTTP API.
 *
 * Custom data keys:
 * - `type`: "update" | "announcement" | "tip"
 * - `title`: notification title
 * - `body`: notification body
 * - `action_url`: optional deep link or Play Store URL
 */
class EkatraMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "EkatraFCM"

        // Notification channels
        const val CHANNEL_ANNOUNCEMENTS = "announcements"
        const val CHANNEL_UPDATES = "app_updates"
        const val CHANNEL_TIPS = "learning_tips"

        private const val NOTIFICATION_ID_BASE = 5000

        fun createNotificationChannels(context: android.content.Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)

                val channels = listOf(
                    NotificationChannel(
                        CHANNEL_ANNOUNCEMENTS,
                        context.getString(R.string.notif_channel_announcements),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.notif_channel_announcements_desc)
                    },
                    NotificationChannel(
                        CHANNEL_UPDATES,
                        context.getString(R.string.notif_channel_updates),
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = context.getString(R.string.notif_channel_updates_desc)
                    },
                    NotificationChannel(
                        CHANNEL_TIPS,
                        context.getString(R.string.notif_channel_tips),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notif_channel_tips_desc)
                    }
                )

                channels.forEach { manager.createNotificationChannel(it) }
                Log.d(TAG, "Notification channels created")
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed: ${token.take(20)}...")
        // Token is automatically associated with the Firebase project.
        // For targeted notifications, you could save this to Firestore:
        // FirebaseFirestore.getInstance().collection("fcm_tokens").document(token).set(...)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message received from: ${message.from}")

        val data = message.data
        val notification = message.notification

        // Determine notification type
        val type = data["type"] ?: "announcement"
        val title = data["title"] ?: notification?.title ?: getString(R.string.app_name)
        val body = data["body"] ?: notification?.body ?: ""

        if (body.isBlank()) {
            Log.w(TAG, "Empty notification body, ignoring")
            return
        }

        val channelId = when (type) {
            "update" -> CHANNEL_UPDATES
            "tip" -> CHANNEL_TIPS
            else -> CHANNEL_ANNOUNCEMENTS
        }

        val emoji = when (type) {
            "update" -> "🆕"
            "tip" -> "💡"
            else -> "📢"
        }

        showNotification(
            channelId = channelId,
            title = "$emoji $title",
            body = body,
            notificationId = NOTIFICATION_ID_BASE + type.hashCode()
        )
    }

    private fun showNotification(
        channelId: String,
        title: String,
        body: String,
        notificationId: Int
    ) {
        // Tap opens the chat screen
        val intent = Intent(this, NativeChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
        Log.d(TAG, "Notification shown: $title")
    }
}
