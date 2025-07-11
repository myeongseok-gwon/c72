# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\soft\android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontwarn com.imagealgorithmlab.**
-dontwarn com.hsm.**
-dontwarn com.rscja.deviceapi.**
-keep class com.hsm.** {*; }
-keep class com.rscja.deviceapi.** {*; }

#导出数据jar
-keep class common.** { *; }
-keep class jxl.** { *; }
-keep class aavax.**{*;}
-keep class com.**{*;}
-keep class org.**{*;}
-keep class schemaorg_apache_xmlbeans.system.**{*;}
-keep interface org.**{*;}