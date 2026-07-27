# VineOS release-build ProGuard/R8 rules.
# Room, Hilt, and Compose ship their own consumer rules in their AARs, so
# this file only covers what's specific to VineOS itself.

# JNI: native methods are resolved by symbol name at runtime, so the
# declaring classes and method signatures must survive obfuscation.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.hexadecinull.vineos.native.VineRuntime { *; }

# kotlinx.serialization: keep the generated $serializer companions and the
# @Serializable model classes they reflect over.
-keepattributes *Annotation*, InnerClasses
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hexadecinull.vineos.data.models.**$$serializer { *; }
-keepclassmembers class com.hexadecinull.vineos.data.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.hexadecinull.vineos.data.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities and DAOs referenced by generated code, and the TypeConverter
# functions Room calls reflectively.
-keep class com.hexadecinull.vineos.data.models.VMInstance { *; }
-keepclassmembers class com.hexadecinull.vineos.data.repository.VineConverters {
    @androidx.room.TypeConverter <methods>;
}

# OkHttp/Okio pull in some platform-detection classes R8 can't resolve on
# Android; these are the standard suppressions from OkHttp's own docs.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil resolves some optional dependencies (Sketch, GIF, video) by reflection
# that this app doesn't include.
-dontwarn coil.**

# Keep enum valueOf/values for Room converters and Compose "when" exhaustiveness
# checks that reflect on enum classes.
-keepclassmembers enum com.hexadecinull.vineos.data.models.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
