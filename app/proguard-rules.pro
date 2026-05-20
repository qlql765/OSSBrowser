# 阿里云 OSS SDK 混淆规则
-keep class com.alibaba.sdk.android.oss.** { *; }
-dontwarn okio.**
-dontwarn org.apache.commons.codec.binary.**

# DataStore
-keep class androidx.datastore.** { *; }

# 保留 Kotlin 协程相关
-keepattributes *Annotation*
-keep class kotlinx.coroutines.** { *; }

# 保留序列化相关
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.ossbrowser.data.** { *; }
