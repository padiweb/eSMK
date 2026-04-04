# ProGuard rules untuk UjianSMKAltan

# Pertahankan semua class utama
-keep class com.smkaltan.ujian.** { *; }

# WebView JavaScript Interface - WAJIB agar @JavascriptInterface tidak di-strip
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Device Admin
-keep class com.smkaltan.ujian.ExamDeviceAdminReceiver { *; }

# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class **$WhenMappings { *; }

# Hilangkan log di release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
