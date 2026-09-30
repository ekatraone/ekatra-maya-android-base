package org.ekatra.alfred.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val uid: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val country: String = "India",
    val grade: String = "",
    val preferredLanguage: String = "en",
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val analyticsConsent: Boolean = false
)
