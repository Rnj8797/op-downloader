# OP Downloader - R8 / ProGuard Security & Obfuscation Configuration

# 1. Strip all debugging and log statements in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 2. Obfuscation & Shrinking Configuration
-repackageclasses 'com.opdownloader.app.obf'
-allowaccessmodification
-mergeinterfacesaggressively

# 3. Preserve Android Keystore & Cryptographic Providers
-keep class com.opdownloader.app.data.security.KeystoreManager { *; }
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }

# 4. Preserve Room Database Entities and DAOs
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# 5. Preserve Kotlin Coroutines and Flow
-keepnames class kotlinx.coroutines.** { *; }
-keepclassmembernames class kotlinx.coroutines.** { *; }

# 6. Preserve WorkManager Workers
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

# 7. Preserve Material 3 and Jetpack Compose Runtime
-keep class androidx.compose.** { *; }
-keep class androidx.compose.material3.** { *; }

# 8. Prevent Insecure Serialization Leaks
-dontwarn java.lang.invoke.**
