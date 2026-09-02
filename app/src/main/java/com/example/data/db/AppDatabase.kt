package com.example.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.VaultItem
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
  entities = [VaultItem::class],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun vaultDao(): VaultDao

  companion object {
    private const val TAG = "AppDatabase"
    private const val ENCRYPTED_DB_NAME = "papertrail_encrypted_vault.db"
    private const val PLAINTEXT_DB_NAME = "papertrail_plain_vault.db"

    @Volatile
    var isEncryptionFallbackActive: Boolean = false
      private set

    @Volatile
    private var INSTANCE: AppDatabase? = null

    init {
      try {
        System.loadLibrary("sqlcipher")
        Log.i(TAG, "SQLCipher native library loaded successfully.")
      } catch (t: Throwable) {
        Log.w(TAG, "Failed to load sqlcipher native library: ${t.message}. Will use plaintext fallback.")
        isEncryptionFallbackActive = true
      }
    }

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
      }
    }

    /**
     * Builds the database instance.
     *
     * Note on Schema Migrations:
     * AppDatabase currently uses fallbackToDestructiveMigration() for v1 schema development.
     * When upgrading schema from version 1 to 2+, implement explicit Migration(1, 2) objects
     * and add them via .addMigrations(...) instead of destructive migration to prevent user data loss.
     */
    private fun buildDatabase(appContext: Context): AppDatabase {
      if (!isEncryptionFallbackActive) {
        try {
          val passphrase = DatabasePassphraseManager.getOrCreatePassphrase(appContext)
          val factory = SupportOpenHelperFactory(passphrase)

          val encryptedDb = Room.databaseBuilder(appContext, AppDatabase::class.java, ENCRYPTED_DB_NAME)
            .openHelperFactory(factory)
            .build()

          // Eagerly verify native SQLCipher connection by running a trivial query synchronously.
          // This ensures that any UnsatisfiedLinkError or CryptoException is caught immediately
          // within this try/catch block rather than crashing lazily on a background coroutine thread.
          encryptedDb.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
            cursor.moveToFirst()
          }

          isEncryptionFallbackActive = false
          Log.i(TAG, "SQLCipher encrypted database successfully initialized and verified.")
          return encryptedDb
        } catch (e: Throwable) {
          Log.e(TAG, "SQLCipher encrypted database initialization failed: ${e.message}. Falling back to standard plaintext Room database.", e)
          isEncryptionFallbackActive = true
        }
      }

      // Plaintext fallback database
      Log.w(TAG, "Initializing unencrypted fallback database.")
      val fallbackDb = Room.databaseBuilder(appContext, AppDatabase::class.java, PLAINTEXT_DB_NAME)
        .build()

      try {
        fallbackDb.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
          cursor.moveToFirst()
        }
      } catch (e: Throwable) {
        Log.e(TAG, "Fallback database verification error: ${e.message}", e)
      }

      return fallbackDb
    }
  }
}
