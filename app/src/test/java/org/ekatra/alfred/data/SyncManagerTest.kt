package org.ekatra.alfred.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.ekatra.alfred.data.local.SyncQueueDao
import org.ekatra.alfred.data.model.ChatAnalytics
import org.ekatra.alfred.data.model.SyncQueueItem
import org.ekatra.alfred.data.sync.SyncManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private lateinit var context: Context
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var syncManager: SyncManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        syncQueueDao = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        auth = mockk(relaxed = true)

        // Mock connectivity — offline by default
        val connectivityManager = mockk<ConnectivityManager>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns null

        syncManager = SyncManager(context, syncQueueDao, firestore, auth)
    }

    @Test
    fun `enqueueAnalytics inserts to sync queue`() = runTest {
        val analytics = ChatAnalytics(
            sessionId = "session-1",
            userId = "uid-1",
            startTime = 1000L,
            endTime = 2000L,
            messageCount = 5,
            userMessageCount = 3,
            aiMessageCount = 2,
            subject = "Math",
            modelId = "qwen-0.5b",
            avgResponseTimeMs = 300L,
            deviceModel = "Pixel 7",
            gpuUsed = false,
            savedCount = 1,
            sharedCount = 0,
            thumbsUpCount = 2,
            thumbsDownCount = 0
        )

        coEvery { syncQueueDao.insert(any()) } returns 1L

        syncManager.enqueueAnalytics(analytics)

        coVerify { syncQueueDao.insert(match { it.type == "chat_analytics" }) }
    }

    @Test
    fun `syncPending does nothing when offline`() = runTest {
        // Default setup has no active network
        syncManager.syncPending()

        // Should not attempt to read from sync queue
        coVerify(exactly = 0) { syncQueueDao.getPending(any()) }
    }

    @Test
    fun `syncPending does nothing when not authenticated`() = runTest {
        // Make online
        val connectivityManager = mockk<ConnectivityManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        // But no auth
        every { auth.currentUser } returns null

        syncManager = SyncManager(context, syncQueueDao, firestore, auth)
        syncManager.syncPending()

        coVerify(exactly = 0) { syncQueueDao.getPending(any()) }
    }
}
