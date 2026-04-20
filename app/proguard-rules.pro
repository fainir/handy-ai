# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.claudeagent.phone.**$$serializer { *; }
-keepclassmembers class com.claudeagent.phone.** { *** Companion; }
-keepclasseswithmembers class com.claudeagent.phone.** { kotlinx.serialization.KSerializer serializer(...); }
