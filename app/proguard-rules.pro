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
# Gson uses reflection on model classes; release builds minify with R8.
# Keep all network DTO fields so JSON (de)serialization survives minification.
-keep class com.dokeraj.androtainer.models.retrofit.** { <fields>; }
-keep class com.dokeraj.androtainer.models.** { <fields>; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson must invoke constructors for collection subclasses so their backing storage is initialized.
-keepclassmembers class com.dokeraj.androtainer.models.retrofit.PEndpointsResponse {
    <init>();
}
-keepclassmembers class com.dokeraj.androtainer.models.retrofit.PContainersResponse {
    <init>();
}
-keepclassmembers class com.dokeraj.androtainer.models.logos.Logos {
    <init>();
}

# Room reflectively instantiates WorkManager's generated database implementation.
# Keep its zero-argument constructor in minified release builds.
-keepclassmembers class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}
