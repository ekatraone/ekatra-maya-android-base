package org.ekatra.alfred.viewmodel

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.ekatra.alfred.data.model.UserProfile
import org.ekatra.alfred.data.local.UserProfileDao
import org.ekatra.alfred.data.repository.ChatRepository
import org.ekatra.alfred.data.sync.SyncManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var auth: FirebaseAuth
    private lateinit var profileDao: UserProfileDao
    private lateinit var chatRepository: ChatRepository
    private lateinit var syncManager: SyncManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        auth = mockk(relaxed = true)
        profileDao = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)

        every { syncManager.pendingSyncCount } returns flowOf(3)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads profile and sync count on init`() = runTest {
        val profile = UserProfile(
            uid = "test-uid",
            name = "Test User",
            phoneNumber = "+911234567890",
            country = "India",
            grade = "10th"
        )

        val user = mockk<FirebaseUser> {
            every { uid } returns "test-uid"
        }
        every { auth.currentUser } returns user
        coEvery { profileDao.getProfile("test-uid") } returns profile

        viewModel = SettingsViewModel(auth, profileDao, chatRepository, syncManager)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Test User", state.userProfile?.name)
        assertEquals("+911234567890", state.userProfile?.phoneNumber)
    }

    @Test
    fun `signOut clears auth and profile`() = runTest {
        val user = mockk<FirebaseUser> {
            every { uid } returns "sign-out-uid"
        }
        every { auth.currentUser } returns user
        coEvery { profileDao.getProfile("sign-out-uid") } returns null

        viewModel = SettingsViewModel(auth, profileDao, chatRepository, syncManager)
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        verify { auth.signOut() }
        coVerify { profileDao.deleteProfile("sign-out-uid") }
    }

    @Test
    fun `exportData returns JSON`() = runTest {
        every { auth.currentUser } returns null
        coEvery { profileDao.getProfile(any()) } returns null
        coEvery { chatRepository.exportAllAsJson() } returns """{"sessions":[]}"""

        viewModel = SettingsViewModel(auth, profileDao, chatRepository, syncManager)
        advanceUntilIdle()

        var result: ByteArray? = null
        viewModel.exportDataZip { result = it }
        advanceUntilIdle()

        assertNotNull(result)
    }

    @Test
    fun `pendingSyncCount flows from syncManager`() = runTest {
        every { auth.currentUser } returns null
        coEvery { profileDao.getProfile(any()) } returns null

        viewModel = SettingsViewModel(auth, profileDao, chatRepository, syncManager)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.pendingSyncCount)
    }
}
