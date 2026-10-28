# ONNX Runtime uses JNI; keep its classes.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.phisherman.kavach.**$$serializer { *; }
-keepclassmembers class com.phisherman.kavach.** {
    *** Companion;
}
