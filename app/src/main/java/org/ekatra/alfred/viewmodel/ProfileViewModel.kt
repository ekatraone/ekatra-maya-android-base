package org.ekatra.alfred.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.ekatra.alfred.data.local.UserProfileDao
import org.ekatra.alfred.data.local.EkatraDatabase
import org.ekatra.alfred.data.model.UserProfile
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class AuthStep {
    EMAIL_INPUT,
    // PHONE_INPUT,  // TODO: Enable when Firebase Phone Auth is available
    // OTP_VERIFICATION,  // TODO: Enable when Firebase Phone Auth is available
    PROFILE_FORM,
    EDIT_PROFILE,
    AUTHENTICATED
}

data class ProfileUiState(
    val authStep: AuthStep = AuthStep.EMAIL_INPUT,
    val email: String = "",
    val password: String = "",
    // val phoneNumber: String = "",  // TODO: Enable when Firebase Phone Auth is available
    // val otpCode: String = "",  // TODO: Enable when Firebase Phone Auth is available
    val name: String = "",
    val country: String = "India",
    val grade: String = "",
    val preferredLanguage: String = "en",
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfile: UserProfile? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userProfileDao: UserProfileDao,
    private val database: EkatraDatabase,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // private var verificationId: String? = null  // TODO: Enable when Firebase Phone Auth is available
    // private var resendToken: PhoneAuthProvider.ForceResendingToken? = null  // TODO: Enable when Firebase Phone Auth is available

    init {
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                // Always check local DB first (instant, no network)
                val localProfile = userProfileDao.getProfile(currentUser.uid)
                if (localProfile != null) {
                    _uiState.update {
                        it.copy(authStep = AuthStep.AUTHENTICATED, userProfile = localProfile)
                    }
                    return@launch
                }

                // No local profile — try Firestore with a short timeout.
                // On slow/offline networks this won't block the UI.
                try {
                    val doc = withTimeoutOrNull(3_000L) {
                        firestore.collection("users").document(currentUser.uid).get().await()
                    }
                    if (doc != null && doc.exists()) {
                        val profile = doc.toObject(UserProfile::class.java)?.copy(uid = currentUser.uid)
                        if (profile != null) {
                            userProfileDao.upsert(profile)
                            _uiState.update {
                                it.copy(authStep = AuthStep.AUTHENTICATED, userProfile = profile)
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore profile fetch failed/timed out", e)
                }

                // Firestore didn't have it or timed out — show profile form
                _uiState.update { it.copy(authStep = AuthStep.PROFILE_FORM) }
            }
        }
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    // TODO: Enable when Firebase Phone Auth is available
    // fun updatePhone(phone: String) {
    //     _uiState.update { it.copy(phoneNumber = phone, error = null) }
    // }

    // fun updateOtp(otp: String) {
    //     _uiState.update { it.copy(otpCode = otp, error = null) }
    // }

    fun updateName(name: String) { _uiState.update { it.copy(name = name) } }
    fun updateCountry(country: String) { _uiState.update { it.copy(country = country) } }
    fun updateGrade(grade: String) { _uiState.update { it.copy(grade = grade) } }

    fun signInWithEmail() {
        val email = _uiState.value.email
        val password = _uiState.value.password

        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(error = "Enter a valid email address") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // TODO: Remove this bypass when Firebase Auth is enabled
                // TEMPORARY: Skip Firebase Auth if not configured
                try {
                    val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                    val user = result.user ?: throw Exception("Auth succeeded but user is null")
                    continueAfterAuth(user)
                } catch (firebaseException: Exception) {
                    if (firebaseException.message?.contains("CONFIGURATION_NOT_FOUND") == true) {
                        Log.w(TAG, "Firebase Auth not configured, using offline-only mode")
                        // Create a mock user for offline testing
                        createOfflineUser(email)
                    } else {
                        throw firebaseException
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Email sign-in failed", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Sign-in failed")
                }
            }
        }
    }

    fun createAccountWithEmail() {
        val email = _uiState.value.email
        val password = _uiState.value.password

        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(error = "Enter a valid email address") }
            return
        }

        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // TODO: Remove this bypass when Firebase Auth is enabled
                // TEMPORARY: Skip Firebase Auth if not configured
                try {
                    val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                    val user = result.user ?: throw Exception("Auth succeeded but user is null")
                    continueAfterAuth(user)
                } catch (firebaseException: Exception) {
                    if (firebaseException.message?.contains("CONFIGURATION_NOT_FOUND") == true) {
                        Log.w(TAG, "Firebase Auth not configured, creating offline-only account")
                        // Create a mock user for offline testing
                        createOfflineUser(email)
                    } else {
                        throw firebaseException
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Account creation failed", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Account creation failed")
                }
            }
        }
    }

    private suspend fun createOfflineUser(email: String) {
        // Generate a pseudo-unique ID from email
        val uid = "offline_${email.hashCode().toString().replace("-", "")}"
        
        // Check if user already exists
        val existingProfile = userProfileDao.getProfile(uid)
        if (existingProfile != null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    authStep = AuthStep.AUTHENTICATED,
                    userProfile = existingProfile
                )
            }
        } else {
            // New user, show profile form
            _uiState.update {
                it.copy(
                    isLoading = false,
                    authStep = AuthStep.PROFILE_FORM,
                    email = email
                )
            }
        }
    }

    // TODO: Enable when Firebase Phone Auth is available
    // fun sendOtp(activity: Activity) {
    //     val phone = _uiState.value.phoneNumber
    //     if (phone.length < 10) {
    //         _uiState.update { it.copy(error = "Enter a valid phone number") }
    //         return
    //     }
    //
    //     _uiState.update { it.copy(isLoading = true, error = null) }
    //
    //     val fullPhone = if (phone.startsWith("+")) phone else "+91$phone"
    //
    //     val options = PhoneAuthOptions.newBuilder(firebaseAuth)
    //         .setPhoneNumber(fullPhone)
    //         .setTimeout(60L, TimeUnit.SECONDS)
    //         .setActivity(activity)
    //         .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
    //             override fun onVerificationCompleted(credential: PhoneAuthCredential) {
    //                 Log.d(TAG, "Auto-verification completed")
    //                 signInWithCredential(credential)
    //             }
    //
    //             override fun onVerificationFailed(e: FirebaseException) {
    //                 Log.e(TAG, "Verification failed", e)
    //                 _uiState.update {
    //                     it.copy(isLoading = false, error = e.message ?: "Verification failed")
    //                 }
    //             }
    //
    //             override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
    //                 verificationId = vId
    //                 resendToken = token
    //                 _uiState.update {
    //                     it.copy(isLoading = false, authStep = AuthStep.OTP_VERIFICATION)
    //                 }
    //             }
    //         })
    //         .build()
    //
    //     PhoneAuthProvider.verifyPhoneNumber(options)
    // }
    //
    // fun verifyOtp() {
    //     val otp = _uiState.value.otpCode
    //     val vId = verificationId
    //
    //     if (otp.length != 6 || vId == null) {
    //         _uiState.update { it.copy(error = "Enter the 6-digit code") }
    //         return
    //     }
    //
    //     _uiState.update { it.copy(isLoading = true, error = null) }
    //     val credential = PhoneAuthProvider.getCredential(vId, otp)
    //     signInWithCredential(credential)
    // }

    fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: AuthCredential) {
        viewModelScope.launch {
            try {
                val result = firebaseAuth.signInWithCredential(credential).await()
                val user = result.user ?: throw Exception("Auth succeeded but user is null")
                continueAfterAuth(user)
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in failed", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Sign-in failed")
                }
            }
        }
    }

    private suspend fun continueAfterAuth(user: com.google.firebase.auth.FirebaseUser) {
        // Check if profile exists
        val existingProfile = userProfileDao.getProfile(user.uid)
        if (existingProfile != null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    authStep = AuthStep.AUTHENTICATED,
                    userProfile = existingProfile
                )
            }
        } else {
            migrateOfflineProfileIfPresent(user)

            val migratedProfile = userProfileDao.getProfile(user.uid)
            if (migratedProfile != null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authStep = AuthStep.AUTHENTICATED,
                        userProfile = migratedProfile
                    )
                }
                return
            }

            // Pre-fill from Firebase user
            _uiState.update {
                it.copy(
                    isLoading = false,
                    authStep = AuthStep.PROFILE_FORM,
                    name = user.displayName ?: "",
                    email = user.email ?: it.email
                )
            }
        }
    }

    private suspend fun migrateOfflineProfileIfPresent(user: com.google.firebase.auth.FirebaseUser) {
        val offlineProfile = userProfileDao.getLatestOfflineProfile() ?: return
        if (offlineProfile.uid == user.uid) return

        val mergedProfile = offlineProfile.copy(
            uid = user.uid,
            phoneNumber = user.phoneNumber ?: offlineProfile.phoneNumber,
            createdAt = offlineProfile.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis()
        )

        database.withTransaction {
            userProfileDao.upsert(mergedProfile)
            userProfileDao.migrateSessionUserId(offlineProfile.uid, user.uid)
            userProfileDao.migrateMessageUserId(offlineProfile.uid, user.uid)
            userProfileDao.migrateSavedAnswerUserId(offlineProfile.uid, user.uid)
            userProfileDao.deleteProfile(offlineProfile.uid)
        }
        Log.d(TAG, "Migrated offline profile ${offlineProfile.uid} -> ${user.uid}")
    }

    fun saveProfile() {
        val state = _uiState.value
        
        // Support offline-only mode if Firebase Auth is not configured
        val uid = firebaseAuth.currentUser?.uid 
            ?: "offline_${state.email.hashCode().toString().replace("-", "")}"

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Name is required") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val profile = UserProfile(
                uid = uid,
                name = state.name,
                phoneNumber = firebaseAuth.currentUser?.phoneNumber ?: "",  // Empty for email auth
                country = state.country,
                grade = state.grade,
                preferredLanguage = state.preferredLanguage,
                createdAt = System.currentTimeMillis(),
                lastActiveAt = System.currentTimeMillis()
            )

            // Save locally
            userProfileDao.upsert(profile)

            // Sync to Firestore (fire-and-forget — don't block the UI)
            if (firebaseAuth.currentUser != null) {
                firestore.collection("users").document(uid).set(profile)
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Firestore sync failed (will retry later)", e)
                    }
            } else {
                Log.d(TAG, "Offline-only mode, skipping Firestore sync")
            }

            // Set analytics user properties
            try {
                analyticsHelper.setUserProperties(profile)
                analyticsHelper.logProfileCompleted(profile.country, profile.grade)
            } catch (e: Exception) {
                Log.w(TAG, "Analytics failed", e)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    authStep = AuthStep.AUTHENTICATED,
                    userProfile = profile
                )
            }
        }
    }

    /**
     * Load existing profile for editing (called from Settings → Profile).
     */
    fun loadExistingProfile() {
        viewModelScope.launch {
            val uid = firebaseAuth.currentUser?.uid ?: return@launch
            val profile = userProfileDao.getProfile(uid) ?: return@launch
            _uiState.update {
                it.copy(
                    authStep = AuthStep.EDIT_PROFILE,
                    name = profile.name,
                    country = profile.country,
                    grade = profile.grade,
                    preferredLanguage = profile.preferredLanguage,
                    email = firebaseAuth.currentUser?.email ?: "",
                    userProfile = profile
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            firebaseAuth.signOut()
            val uid = _uiState.value.userProfile?.uid
            if (uid != null) userProfileDao.deleteProfile(uid)
            _uiState.update { ProfileUiState() }
        }
    }
}
