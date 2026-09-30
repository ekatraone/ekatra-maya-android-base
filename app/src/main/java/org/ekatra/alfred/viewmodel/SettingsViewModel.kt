package org.ekatra.alfred.viewmodel

import org.ekatra.alfred.persona.ActivePersona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ekatra.alfred.data.local.UserProfileDao
import org.ekatra.alfred.data.model.UserProfile
import org.ekatra.alfred.data.repository.ChatRepository
import org.ekatra.alfred.data.sync.SyncManager
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

data class SettingsUiState(
    val userProfile: UserProfile? = null,
    val pendingSyncCount: Int = 0,
    val isExporting: Boolean = false,
    val exportComplete: Boolean = false,
    val isServerRunning: Boolean = false,
    val isServerLoading: Boolean = false,
    val serverUrl: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userProfileDao: UserProfileDao,
    private val chatRepository: ChatRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        observeSyncQueue()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val user = firebaseAuth.currentUser
            if (user != null) {
                val profile = userProfileDao.getProfile(user.uid)
                _uiState.update { it.copy(userProfile = profile) }
            }
        }
    }

    private fun observeSyncQueue() {
        viewModelScope.launch {
            syncManager.pendingSyncCount.collect { count ->
                _uiState.update { it.copy(pendingSyncCount = count) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            val uid = firebaseAuth.currentUser?.uid
            firebaseAuth.signOut()
            if (uid != null) userProfileDao.deleteProfile(uid)
            _uiState.update { it.copy(userProfile = null) }
        }
    }

    fun exportDataZip(onResult: (ByteArray) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val json = chatRepository.exportAllAsJson()
            val zipBytes = createZipFromJson(json)
            _uiState.update { it.copy(isExporting = false, exportComplete = true) }
            onResult(zipBytes)
        }
    }

    private fun createZipFromJson(json: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zipOutputStream ->
            val entry = ZipEntry("${ActivePersona.key}_ai_export.json")
            zipOutputStream.putNextEntry(entry)
            zipOutputStream.write(json.toByteArray(Charsets.UTF_8))
            zipOutputStream.closeEntry()
        }
        return outputStream.toByteArray()
    }

    fun deleteCloudData() {
        viewModelScope.launch {
            syncManager.deleteAllCloudData()
        }
    }

    fun setServerState(isRunning: Boolean, url: String) {
        _uiState.update { it.copy(isServerRunning = isRunning, isServerLoading = false, serverUrl = url) }
    }

    fun setServerLoading(loading: Boolean) {
        _uiState.update { it.copy(isServerLoading = loading) }
    }
}
