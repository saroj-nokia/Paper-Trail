# ProGuard & R8 configuration for Paper Trail

# SQLCipher native and openhelper rules
-keep class net.zetetic.database.** { *; }
-keepclassmembers class * extends net.zetetic.database.sqlcipher.SQLiteOpenHelper { *; }
-keep class androidx.sqlite.db.** { *; }
-dontwarn net.zetetic.**

# ML Kit (Common, Vision, Text Recognition & Component Injection)
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ML Kit Component Discovery & Dependency Injection (prevents Unsatisfied dependency in MlKitInitProvider)
-keep class com.google.firebase.components.** { *; }
-keep interface com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
    *;
}
-keep class * implements com.google.mlkit.common.internal.model.ModelLoader { *; }

# Google Play Services & Datatransport dependencies for ML Kit
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**

# Room Database & Entities
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Biometric & Security Crypto
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }
