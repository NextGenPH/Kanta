# ── GENERAL CONFIGURATION ───────────────────────────────────────────────────

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, SourceFile, LineNumberTable

# Keep our main activities
-keep class com.sns.kanta.** { *; }

# ── DATA & MODELS (CRITICAL FOR GSON/ROOM) ──────────────────────────────────

# Keep all models used for JSON serialization and their original names
-keep class com.sns.kanta.model.** { *; }
-keepclassmembers class com.sns.kanta.model.** { <fields>; }
-keepnames class com.sns.kanta.model.** { *; }

# Keep all Room entities, DAOs and Database
-keep class com.sns.kanta.data.local.** { *; }
-keepclassmembers class com.sns.kanta.data.local.** { *; }

# ── LIBRARIES ───────────────────────────────────────────────────────────────

# YouTube Player
-keep class com.pierfrancescosoffritti.** { *; }

# Retrofit 3 & OkHttp
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# GSON
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Android Security Crypto (Tink)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Facebook Shimmer
-keep class com.facebook.shimmer.** { *; }

# Glide & Transformations
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class jp.wasabeef.glide.transformations.** { *; }
-dontwarn com.bumptech.glide.**

# ── ROOM SPECIFIC ───────────────────────────────────────────────────────────

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── OPTIMIZATION & CLEANUP ──────────────────────────────────────────────────

# Remove debug, verbose, and info logs in release to protect code and save size
# We KEEP Log.e() and Log.w() for production troubleshooting
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Standard R8/ProGuard safety
-dontwarn android.support.**
-dontwarn androidx.**

# Relaxed obfuscation for higher stability in first release
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
