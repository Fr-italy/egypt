-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Kotlin serialization (tutto il package app, incluso documents)
-keep,includedescriptorclasses class com.frenky.egypt.**$$serializer { *; }
-keepclassmembers class com.frenky.egypt.** {
    *** Companion;
}
-keepclasseswithmembers class com.frenky.egypt.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.frenky.egypt.** { *; }

-keepclassmembers class kotlinx.serialization.json.** { *; }

# Compose / Activity
-keep class com.frenky.egypt.MainActivity { *; }
-keep class com.frenky.egypt.EgyptApp { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Google Play Services Location
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**
