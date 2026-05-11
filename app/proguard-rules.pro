# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Keep our main classes but obfuscate everything else
# Keep our main classes but obfuscate everything else
-keep class com.sns.kanta.Mainactivity { *; }
-keep class com.sns.kanta.queueing.QueueManager { *; }
-keep class com.sns.kanta.queueing.ReservationModel { *; }
-keep class com.sns.kanta.adapter.UpdateManager { *; }
-keep class com.sns.kanta.server.ApiService { *; }

# Keep YouTube Player classes
-keep class com.pierfrancescosoffritti.** { *; }

# Keep Retrofit and GSON
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn com.google.gson.**

# Remove all logs in release (IMPORTANT!)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Obfuscate class names aggressively
-flattenpackagehierarchy
-allowaccessmodification
-repackageclasses ''

# Remove debugging attributes
-dontoptimize
-dontpreverify

# Enable optimization
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5