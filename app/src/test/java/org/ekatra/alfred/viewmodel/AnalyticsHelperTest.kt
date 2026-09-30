package org.ekatra.alfred.viewmodel

import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.*
import org.ekatra.alfred.data.model.UserProfile
import org.junit.Before
import org.junit.Test

class AnalyticsHelperTest {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var analyticsHelper: AnalyticsHelper

    @Before
    fun setup() {
        firebaseAnalytics = mockk(relaxed = true)
        analyticsHelper = AnalyticsHelper(firebaseAnalytics)
    }

    @Test
    fun `logSessionStart logs correct event`() {
        analyticsHelper.logSessionStart("Math", "qwen-0.5b")

        verify {
            firebaseAnalytics.logEvent(eq("maya_session_start"), any())
        }
    }

    @Test
    fun `logSessionEnd logs correct event`() {
        analyticsHelper.logSessionEnd(120L, 10, "Science")

        verify {
            firebaseAnalytics.logEvent(eq("session_end"), any())
        }
    }

    @Test
    fun `logMessageSent logs correct event`() {
        analyticsHelper.logMessageSent("History", 42, isFollowup = true, turnIndex = 3)

        verify {
            firebaseAnalytics.logEvent(eq("message_sent"), any())
        }
    }

    @Test
    fun `logResponseReceived logs correct event`() {
        analyticsHelper.logResponseReceived(
            responseTimeMs = 350L,
            tokenCount = 200,
            modelId = "qwen-0.5b",
            wordCount = 32,
            hasCodeBlock = false,
            wasCancelled = false
        )

        verify {
            firebaseAnalytics.logEvent(eq("response_received"), any())
        }
    }

    @Test
    fun `logResponseRated logs correct event`() {
        analyticsHelper.logResponseRated("up", "session-123", "Math", "qwen-0.5b")

        verify {
            firebaseAnalytics.logEvent(eq("response_rated"), any())
        }
    }

    @Test
    fun `logAnswerSaved logs correct event`() {
        analyticsHelper.logAnswerSaved("Math")

        verify {
            firebaseAnalytics.logEvent(eq("answer_saved"), any())
        }
    }

    @Test
    fun `logAnswerShared logs correct event`() {
        analyticsHelper.logAnswerShared("Science", "WhatsApp")

        verify {
            firebaseAnalytics.logEvent(eq("answer_shared"), any())
        }
    }

    @Test
    fun `logModelSelected logs correct event`() {
        analyticsHelper.logModelSelected("qwen-0.5b", 500L)

        verify {
            firebaseAnalytics.logEvent(eq("model_selected"), any())
        }
    }

    @Test
    fun `logProfileCompleted logs correct event`() {
        analyticsHelper.logProfileCompleted("India", "10th")

        verify {
            firebaseAnalytics.logEvent(eq("profile_completed"), any())
        }
    }

    @Test
    fun `setUserProperties calls setUserId and setUserProperty`() {
        val profile = UserProfile(uid = "uid-123", country = "India", grade = "10th")
        analyticsHelper.setUserProperties(profile)

        verify {
            firebaseAnalytics.setUserId("uid-123")
            firebaseAnalytics.setUserProperty("country", "India")
            firebaseAnalytics.setUserProperty("grade", "10th")
        }
    }

    @Test
    fun `setModelProperty calls setUserProperty`() {
        analyticsHelper.setModelProperty("qwen-0.5b")

        verify {
            firebaseAnalytics.setUserProperty("model_id", "qwen-0.5b")
        }
    }
}
