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

# Gson 泛型保护（解决 TypeToken 问题）
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
# 保留 TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Bugly 混淆配置
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

# ViewModel 混淆保护（解决无法创建 ViewModel 实例问题）
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
# 保留项目中的 ViewModel 类（完整保护，防止内部代码被破坏）
-keep class com.qian.jianyin.viewmodel.** { *; }
-keep class com.qian.jianyin.MusicViewModel { *; }
-keep class com.qian.jianyin.HomeScreenViewModel { *; }

# 数据模型类保护（解决 Gson 反序列化 + normalize 方法问题）
-keep class com.qian.jianyin.data.model.** { *; }
-keep class com.qian.jianyin.Song { *; }
-keep class com.qian.jianyin.PlaybackMode { *; }
-keep class com.qian.jianyin.PlaybackState { *; }
-keep class com.qian.jianyin.LyricEntry { *; }
-keep class com.qian.jianyin.HomePlaylist { *; }
-keep class com.qian.jianyin.HomeSectionState { *; }
-keep class com.qian.jianyin.HomeUiState { *; }
-keepclassmembers class com.qian.jianyin.Song {
    <init>(...);
    <fields>;
    public static *** normalize(...);
}
# 保护所有项目数据类和内部类
-keep class com.qian.jianyin.** { *; }
-keepclassmembers class com.qian.jianyin.** {
    <init>(...);
    <fields>;
    <methods>;
}

# Compose 相关保护
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Kotlin 运行时保护
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keep class kotlin.Metadata { *; }