package org.ekatra.alfred.data

import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import org.ekatra.alfred.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Uploads opt-in thumbs-down feedback to Firestore.
 * Path: feedback/{anonId}/items/{autoId}
 *
 * Only invoked when:
 *  - User explicitly tapped thumbs-down on an AI response, AND
 *  - User confirmed in the dialog, AND
 *  - settings.hasFeedbackConsent() is true (granted in PrivacyConsentActivity).
 */
@Singleton
class FeedbackUploader @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val settings: AppSettings
) {
    companion object {
        private const val TAG = "FeedbackUploader"
        private const val MAX_QUESTION_CHARS = 2000
        private const val MAX_RESPONSE_CHARS = 4000
    }

    /**
     * Uploads a single thumbs-down feedback record. Returns true on success.
     * Caller must verify [AppSettings.hasFeedbackConsent] before calling.
     */
    suspend fun upload(
        question: String,
        response: String,
        modelId: String,
        subject: String?,
        sessionId: Long,
        messageId: Long
    ): Boolean {
        if (!settings.hasFeedbackConsent()) {
            Log.w(TAG, "Feedback consent not granted; skipping upload")
            return false
        }
        return try {
            val anonId = settings.getAnonId()
            val data = mapOf(
                "question" to question.take(MAX_QUESTION_CHARS),
                "response" to response.take(MAX_RESPONSE_CHARS),
                "model_id" to modelId,
                "subject" to (subject ?: "general"),
                "session_id" to sessionId,
                "message_id" to messageId,
                "rating" to "down",
                "device_model" to Build.MODEL,
                "android_sdk" to Build.VERSION.SDK_INT,
                "app_version" to BuildConfig.VERSION_NAME,
                "timestamp" to System.currentTimeMillis()
            )
            val collection = firestore.collection("feedback")
                .document(anonId)
                .collection("items")
            suspendCancellableCoroutine<Boolean> { cont ->
                collection.add(data)
                    .addOnSuccessListener {
                        Log.d(TAG, "Feedback uploaded for session=$sessionId message=$messageId")
                        if (cont.isActive) cont.resume(true)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Feedback upload failed: ${e.message}")
                        if (cont.isActive) cont.resume(false)
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Feedback upload failed: ${e.message}")
            false
        }
    }
}
