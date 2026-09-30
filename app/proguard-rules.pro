# Ekatra Alfred ProGuard Rules

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep LlamaEngine
-keep class org.ekatra.alfred.LlamaEngine { *; }
-keep class org.ekatra.alfred.AlfredServer { *; }

# NanoHTTPD
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.auth.** { *; }

# Firestore
-keep class com.google.firebase.firestore.** { *; }

# Data models (Firestore deserialization)
-keep class org.ekatra.alfred.data.model.UserProfile { *; }
-keep class org.ekatra.alfred.data.model.ChatAnalytics { *; }
