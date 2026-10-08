# ===== kotlinx.serialization =====
# 保留 @Serializable 类生成的序列化器。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 本项目的所有 @Serializable 模型（含嵌套）都保留序列化器。
-keep,includedescriptorclasses class com.swtmaxx.kamusic.native.**$$serializer { *; }
-keepclassmembers class com.swtmaxx.kamusic.native.** {
    *** Companion;
}
-keepclasseswithmembers class com.swtmaxx.kamusic.native.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===== kotlinx.coroutines =====
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===== Media3 / ExoPlayer =====
-dontwarn androidx.media3.**

# ===== 枚举与反射入口 =====
# 让 R8 能安全移除未使用的资源 id 引用
-keep class com.swtmaxx.kamusic.native.data.model.** { *; }
