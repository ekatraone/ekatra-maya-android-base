package org.ekatra.alfred

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.systemBarsPadding
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.viewModels
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import org.ekatra.alfred.ui.BackButton
import org.ekatra.alfred.ui.PersonaDirection
import dagger.hilt.android.AndroidEntryPoint
import org.ekatra.alfred.data.AppSettings
import org.ekatra.alfred.viewmodel.AuthStep
import org.ekatra.alfred.viewmodel.ProfileViewModel
import java.io.File

@AndroidEntryPoint
class ProfileActivity : ComponentActivity() {

    private val viewModel: ProfileViewModel by viewModels()

    companion object {
        const val EXTRA_EDIT_PROFILE = "edit_profile"
        private const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appSettings = AppSettings(this)

        val isEditMode = intent.getBooleanExtra(EXTRA_EDIT_PROFILE, false)

        setContent {
            PersonaDirection {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF2563EB),
                        background = Color(0xFFF6F6F8)
                    )
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    // If opened from Settings, load existing profile for editing
                    LaunchedEffect(isEditMode) {
                        if (isEditMode) {
                            viewModel.loadExistingProfile()
                        }
                    }

                    // Navigate when authenticated (only for login flow, not edit mode)
                    LaunchedEffect(uiState.authStep) {
                        if (uiState.authStep == AuthStep.AUTHENTICATED && !isEditMode) {
                            startActivity(Intent(this@ProfileActivity, ModelDownloadActivity::class.java))
                            finish()
                        }
                    }

                    when (uiState.authStep) {
                        AuthStep.EMAIL_INPUT -> EmailInputScreen(
                            email = uiState.email,
                            password = uiState.password,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onEmailChange = viewModel::updateEmail,
                            onPasswordChange = viewModel::updatePassword,
                            onSignIn = viewModel::signInWithEmail,
                            onCreateAccount = viewModel::createAccountWithEmail,
                            onGoogleSignIn = { launchGoogleSignIn() }
                        )
                        AuthStep.PROFILE_FORM -> ProfileFormScreen(
                            name = uiState.name,
                            country = uiState.country,
                            grade = uiState.grade,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onNameChange = viewModel::updateName,
                            onCountryChange = viewModel::updateCountry,
                            onGradeChange = viewModel::updateGrade,
                            onSave = viewModel::saveProfile
                        )
                        AuthStep.EDIT_PROFILE -> EditProfileScreen(
                            name = uiState.name,
                            email = uiState.email,
                            country = uiState.country,
                            grade = uiState.grade,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onNameChange = viewModel::updateName,
                            onCountryChange = viewModel::updateCountry,
                            onGradeChange = viewModel::updateGrade,
                            onSave = {
                                viewModel.saveProfile()
                                // After saving in edit mode, go back to settings
                            },
                            onBack = { finish() },
                            onSaved = uiState.authStep == AuthStep.AUTHENTICATED
                        )
                        AuthStep.AUTHENTICATED -> {
                            if (isEditMode) {
                                // Profile saved successfully in edit mode — go back
                                LaunchedEffect(Unit) { finish() }
                            } else {
                                // Will navigate away via LaunchedEffect
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchGoogleSignIn() {
        val option = GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        lifecycleScope.launch {
            try {
                val credential = CredentialManager.create(this@ProfileActivity)
                    .getCredential(this@ProfileActivity, request)
                    .credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    viewModel.signInWithGoogle(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                } else {
                    Log.w(TAG, "Unexpected credential type: ${credential.type}")
                    showGoogleSignInError(null)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.d(TAG, "Google sign-in cancelled by user")
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google sign-in failed", e)
                showGoogleSignInError(e.message)
            }
        }
    }

    private fun showGoogleSignInError(detail: String?) {
        val message = getString(R.string.google_sign_in_failed) + (detail?.let { ": $it" } ?: "")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

// ==================== Email Input Screen ====================

@Composable
fun EmailInputScreen(
    email: String,
    password: String,
    isLoading: Boolean,
    error: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var isCreatingAccount by remember { mutableStateOf(false) }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = if (screenWidth < 360) 16.dp else 24.dp
    val emojiSize = if (screenWidth < 360) 36.sp else 48.sp
    val titleSize = if (screenWidth < 360) 20.sp else 24.sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6F8))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Text("📘", fontSize = emojiSize)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.welcome_title), fontSize = titleSize, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.welcome_subtitle), fontSize = 14.sp, color = Color(0xFF6B7280))

        Spacer(Modifier.height(24.dp))

        // Login / Sign Up header
        Text(
            text = if (isCreatingAccount) stringResource(R.string.create_account) else stringResource(R.string.sign_in),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111318),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.email_address)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.password)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        error?.let {
            Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = if (isCreatingAccount) onCreateAccount else onSignIn,
            enabled = !isLoading && email.isNotBlank() && password.length >= 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text(if (isCreatingAccount) stringResource(R.string.create_account) else stringResource(R.string.sign_in))
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { isCreatingAccount = !isCreatingAccount }) {
            Text(
                if (isCreatingAccount) stringResource(R.string.have_account_sign_in) else stringResource(R.string.no_account_create),
                color = Color(0xFF2563EB)
            )
        }

        // Google Sign-In (OAuth clients now configured)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text("  " + stringResource(R.string.or_divider) + "  ", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoogleSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.sign_in_with_google))
        }

        Spacer(Modifier.weight(1f))
    }
}

// ==================== Profile Form Screen ====================

@Composable
fun ProfileFormScreen(
    name: String,
    country: String,
    grade: String,
    isLoading: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onGradeChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = if (screenWidth < 360) 16.dp else 24.dp
    val emojiSize = if (screenWidth < 360) 36.sp else 48.sp
    val titleSize = if (screenWidth < 360) 20.sp else 24.sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6F8))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Text("👋", fontSize = emojiSize)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.profile_form_title), fontSize = titleSize, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.profile_form_subtitle), fontSize = 14.sp, color = Color(0xFF6B7280))

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.your_name)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = country,
            onValueChange = onCountryChange,
            label = { Text(stringResource(R.string.country)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = grade,
            onValueChange = onGradeChange,
            label = { Text(stringResource(R.string.grade_optional)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        error?.let {
            Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onSave,
            enabled = !isLoading && name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text(stringResource(R.string.get_started))
        }

        Spacer(Modifier.weight(1f))
    }
}

// ==================== Edit Profile Screen (from Settings) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    name: String,
    email: String,
    country: String,
    grade: String,
    isLoading: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onGradeChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onSaved: Boolean = false
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = if (screenWidth < 360) 16.dp else 24.dp

    // Auto-close after save
    LaunchedEffect(onSaved) {
        if (onSaved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile), fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF111318)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // Email (read-only)
            OutlinedTextField(
                value = email,
                onValueChange = {},
                label = { Text(stringResource(R.string.email)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = false
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.your_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = country,
                onValueChange = onCountryChange,
                label = { Text(stringResource(R.string.country)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = grade,
                onValueChange = onGradeChange,
                label = { Text(stringResource(R.string.grade)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            error?.let {
                Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSave,
                enabled = !isLoading && name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text(stringResource(R.string.save_changes))
            }
        }
    }
}
