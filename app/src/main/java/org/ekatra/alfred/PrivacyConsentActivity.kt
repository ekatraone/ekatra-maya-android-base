package org.ekatra.alfred

import androidx.compose.ui.res.stringResource
import org.ekatra.alfred.ui.PersonaDirection
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import org.ekatra.alfred.data.AppSettings
import javax.inject.Inject

/**
 * Privacy consent screen — shown before collecting analytics.
 * Required for GDPR / India's DPDP Act compliance.
 */
@AndroidEntryPoint
class PrivacyConsentActivity : ComponentActivity() {

    @Inject lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PersonaDirection {
                MaterialTheme(
                    colorScheme = lightColorScheme(
                        primary = Color(0xFF2563EB),
                        background = Color(0xFFF6F6F8)
                    )
                ) {
                    PrivacyConsentScreen(
                        onAccept = { feedbackOptIn ->
                            settings.setAnalyticsConsent(true)
                            settings.setFeedbackConsent(feedbackOptIn)
                            finish()
                        },
                        onDecline = {
                            settings.setAnalyticsConsent(false)
                            settings.setFeedbackConsent(false)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyConsentScreen(onAccept: (Boolean) -> Unit, onDecline: () -> Unit) {
    var feedbackOptIn by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F6F8))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("🔒", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.privacy_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.privacy_we_collect), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                BulletPoint(stringResource(R.string.privacy_collect_stats))
                BulletPoint(stringResource(R.string.privacy_collect_subjects))
                BulletPoint(stringResource(R.string.privacy_collect_device))
                BulletPoint(stringResource(R.string.privacy_collect_crashes))

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.privacy_we_dont_collect), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                BulletPoint(stringResource(R.string.privacy_no_messages))
                BulletPoint(stringResource(R.string.privacy_no_personal))
                BulletPoint(stringResource(R.string.privacy_no_location))

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.privacy_your_rights), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                BulletPoint(stringResource(R.string.privacy_right_export))
                BulletPoint(stringResource(R.string.privacy_right_delete))
                BulletPoint(stringResource(R.string.privacy_right_offline))
                BulletPoint(stringResource(R.string.privacy_right_change))

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.privacy_compliance),
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Optional: opt-in feedback uploads on thumbs-down
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = feedbackOptIn,
                    onCheckedChange = { feedbackOptIn = it }
                )
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.privacy_feedback_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        stringResource(R.string.privacy_feedback_body),
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onAccept(feedbackOptIn) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text(stringResource(R.string.privacy_accept))
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.privacy_decline), color = Color(0xFF6B7280))
        }
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("• ", color = Color(0xFF6B7280))
        Text(text, fontSize = 14.sp, color = Color(0xFF374151))
    }
}
