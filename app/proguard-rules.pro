# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.smartattendance.**$$serializer { *; }
-keepclassmembers class com.smartattendance.** { *** Companion; }
-keepclasseswithmembers class com.smartattendance.** { kotlinx.serialization.KSerializer serializer(...); }

# ZXing
-keep class com.google.zxing.** { *; }

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature, Exceptions
