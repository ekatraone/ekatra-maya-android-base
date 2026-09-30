package org.ekatra.alfred.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import org.ekatra.alfred.data.local.SyncQueueDao
import org.ekatra.alfred.data.model.ChatAnalytics
import org.ekatra.alfred.data.model.SyncQueueItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first sync manager.
 * Queues analytics locally and batch-uploads to Firestore when online.
 * Respects battery/data saver modes.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueueDao: SyncQueueDao,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {
    companion object {
        private const val TAG = "SyncManager"
    }

    private val gson = Gson()

    val pendingSyncCount: Flow<Int> = syncQueueDao.getPendingCount()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun enqueueAnalytics(analytics: ChatAnalytics) {
        val payload = gson.toJson(analytics)
        syncQueueDao.insert(SyncQueueItem(type = "chat_analytics", payload = payload))
        Log.d(TAG, "Enqueued analytics for session ${analytics.sessionId}")
    }

    /**
     * Process all pending sync items. Called by SyncWorker.
     */
    suspend fun syncPending() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (!isOnline()) {
            Log.d(TAG, "Offline — skipping sync")
            return
        }

        val pending = syncQueueDao.getPending(50)
        Log.d(TAG, "Syncing ${pending.size} pending items")

        for (item in pending) {
            try {
                when (item.type) {
                    "chat_analytics" -> {
                        val analytics = gson.fromJson(item.payload, ChatAnalytics::class.java)
                        firestore.collection("users").document(uid)
                            .collection("analytics")
                            .document(analytics.sessionId)
                            .set(analytics)
                            .await()
                    }
                }
                syncQueueDao.delete(item)
                Log.d(TAG, "Synced item ${item.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for item ${item.id}", e)
                if (item.retryCount >= 5) {
                    syncQueueDao.delete(item) // Give up after 5 retries
                } else {
                    syncQueueDao.incrementRetry(item.id)
                }
            }
        }
    }

    /**
     * Delete all cloud data for the current user (privacy compliance).
     */
    suspend fun deleteAllCloudData() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        try {
            // Delete analytics subcollection
            val docs = firestore.collection("users").document(uid)
                .collection("analytics").get().await()
            for (doc in docs) {
                doc.reference.delete().await()
            }
            // Delete user document
            firestore.collection("users").document(uid).delete().await()
            // Clear local sync queue
            syncQueueDao.deleteAll()
            Log.d(TAG, "All cloud data deleted for $uid")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete cloud data", e)
        }
    }
}
