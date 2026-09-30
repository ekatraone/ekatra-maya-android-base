package org.ekatra.alfred

import org.ekatra.alfred.ui.PersonaDirection
import org.ekatra.alfred.ui.BackButton
import org.ekatra.alfred.persona.ActivePersona
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.ekatra.alfred.viewmodel.SettingsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PersonaDirection {
                EkatraSettingsTheme {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsState()

                    SettingsScreen(
                        userName = uiState.userProfile?.name ?: stringResource(R.string.settings_guest),
                        userPhone = uiState.userProfile?.phoneNumber ?: "",
                        userEmail = uiState.userProfile?.let { "" } ?: "",  // email shown from profile
                        pendingSyncCount = uiState.pendingSyncCount,
                        onBackPressed = { finish() },
                        onChatClick = {
                            startActivity(Intent(this, NativeChatActivity::class.java))
                        },
                        onHomeClick = {
                            startActivity(Intent(this, NativeChatActivity::class.java))
                            finish()
                        },
                        onProfileClick = {
                            startActivity(Intent(this, ProfileActivity::class.java).apply {
                                putExtra(ProfileActivity.EXTRA_EDIT_PROFILE, true)
                            })
                        },
                        onNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            })
                        },
                        onVoiceSettings = {
                            try {
                                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                            } catch (e: Exception) {
                                Toast.makeText(this, getString(R.string.settings_tts_unavailable), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onHostModeClick = {
                            startActivity(Intent(this, HostModeActivity::class.java))
                        },
                        onLogOut = {
                            viewModel.signOut()
                            startActivity(Intent(this, ProfileActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                        },
                        onExportData = {
                            viewModel.exportDataZip { zipBytes ->
                                try {
                                    val exportsDir = File(cacheDir, "exports")
                                    if (!exportsDir.exists()) exportsDir.mkdirs()

                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    val zipFile = File(exportsDir, "${ActivePersona.key}_ai_export_$timestamp.zip")
                                    zipFile.writeBytes(zipBytes)

                                    val contentUri = FileProvider.getUriForFile(
                                        this,
                                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                                        zipFile
                                    )

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(Intent.EXTRA_STREAM, contentUri)
                                        clipData = ClipData.newUri(contentResolver, "${ActivePersona.key} export", contentUri)
                                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.settings_export_subject, getString(R.string.app_name)))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.settings_export_chooser)).apply {
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(chooserIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, getString(R.string.settings_export_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteCloudData = {
                            val profile = uiState.userProfile
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

                            val subject = getString(R.string.settings_delete_subject, getString(R.string.app_name))
                            val body = getString(
                                R.string.settings_delete_body,
                                getString(R.string.app_name),
                                now,
                                profile?.uid ?: getString(R.string.settings_unknown),
                                profile?.name ?: getString(R.string.settings_unknown),
                                profile?.phoneNumber ?: getString(R.string.settings_unknown)
                            )

                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:privacy@ekatra.org")
                                putExtra(Intent.EXTRA_SUBJECT, subject)
                                putExtra(Intent.EXTRA_TEXT, body)
                            }

                            try {
                                startActivity(Intent.createChooser(emailIntent, getString(R.string.settings_delete_chooser)))
                            } catch (e: Exception) {
                                Toast.makeText(this, getString(R.string.settings_no_email_app), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EkatraSettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF608A9A),
            background = Color(0xFFF6F6F8),
            surface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String,
    userPhone: String,
    userEmail: String = "",
    pendingSyncCount: Int,
    onBackPressed: () -> Unit,
    onChatClick: () -> Unit,
    onHomeClick: () -> Unit = {},
    onProfileClick: () -> Unit,
    onNotificationSettings: () -> Unit = {},
    onVoiceSettings: () -> Unit = {},
    onAccessibilitySettings: () -> Unit = {},
    onHostModeClick: () -> Unit = {},
    onLogOut: () -> Unit,
    onExportData: () -> Unit,
    onDeleteCloudData: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { BackButton(onClick = onBackPressed) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF111318)
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = "Settings",
                onChatClick = onChatClick,
                onHomeClick = onHomeClick
            )
        }
    ) { padding ->
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val contentPadding = if (screenWidth < 360) 12.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            // User header card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF2563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            userName.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(userName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (userPhone.isNotBlank()) {
                            Text(userPhone, fontSize = 13.sp, color = Color(0xFF6B7280))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sync status badge
            if (pendingSyncCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7)
                ) {
                    Text(
                        stringResource(R.string.settings_pending_sync, pendingSyncCount),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = Color(0xFF92400E)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Account & Preferences Section
            SectionTitle(stringResource(R.string.settings_section_account))
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsItem(
                icon = "👤",
                title = stringResource(R.string.settings_profile),
                subtitle = stringResource(R.string.settings_profile_sub),
                onClick = onProfileClick
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsItem(
                icon = "🔔",
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_sub),
                onClick = onNotificationSettings
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 24.dp),
                color = Color(0xFFE0E0E0)
            )
            
            // Assistant Section
            SectionTitle(stringResource(R.string.settings_section_assistant))
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsItem(
                icon = "🎙️",
                title = stringResource(R.string.settings_voice),
                subtitle = stringResource(R.string.settings_voice_sub),
                onClick = onVoiceSettings
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SettingsItem(
                icon = "♿",
                title = stringResource(R.string.settings_accessibility),
                subtitle = stringResource(R.string.settings_accessibility_sub),
                onClick = onAccessibilitySettings
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 24.dp),
                color = Color(0xFFE0E0E0)
            )

            // Host Mode
            SettingsItem(
                icon = "📡",
                title = stringResource(R.string.host_mode),
                subtitle = stringResource(R.string.settings_host_mode_sub),
                onClick = onHostModeClick
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 24.dp),
                color = Color(0xFFE0E0E0)
            )

            // Data & Privacy Section
            SectionTitle(stringResource(R.string.settings_section_data))
            Spacer(modifier = Modifier.height(12.dp))

            SettingsItem(
                icon = "📤",
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_sub),
                onClick = onExportData
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsItem(
                icon = "🗑️",
                title = stringResource(R.string.settings_delete_cloud),
                subtitle = stringResource(R.string.settings_delete_cloud_sub),
                onClick = onDeleteCloudData
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Log Out Button
            Button(
                onClick = onLogOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFEE2E2),
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.settings_log_out), modifier = Modifier.padding(vertical = 8.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME),
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF6B7280),
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF0F1F4)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 24.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF111318)
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280)
                )
            }
            
            // Chevron
            Text("›", fontSize = 24.sp, color = Color(0xFF9CA3AF))
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: String,
    onChatClick: () -> Unit = {},
    onHomeClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavItem("💬", stringResource(R.string.nav_chat), selectedTab == "Chat", onClick = onChatClick)
            NavItem("🏠", stringResource(R.string.nav_home), selectedTab == "Home", onClick = onHomeClick)
            NavItem("⚙️", stringResource(R.string.settings_title), selectedTab == "Settings")
        }
    }
}

@Composable
fun NavItem(icon: String, label: String, isSelected: Boolean, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            icon,
            fontSize = 24.sp,
            color = if (isSelected) Color(0xFF608A9A) else Color(0xFF9CA3AF)
        )
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color(0xFF608A9A) else Color(0xFF9CA3AF)
        )
    }
}
