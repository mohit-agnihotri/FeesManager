# ── Razorpay ──────────────────────────────────────────────────────────────────
-keepclassmembers class com.razorpay.** { *; }
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**

# ── MPAndroidChart ────────────────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }

# ── iTextPDF ──────────────────────────────────────────────────────────────────
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# ── Firebase ──────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ── Kotlin coroutines (used internally by Firebase KTX) ──────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
