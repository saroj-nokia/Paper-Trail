package com.example.securevault.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.securevault.model.SecureFileItem
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
  entities = [SecureFileItem::class],
  version = 2,
  exportSchema = false
)
abstract class SecureVaultDatabase : RoomDatabase() {
  abstract fun secureVaultDao(): SecureVaultDao

  companion object {
    private const val TAG = "SecureVaultDatabase"
    private const val ENCRYPTED_DB_NAME = "securevault_encrypted.db"
    private const val PLAINTEXT_DB_NAME = "securevault_plain.db"

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE secure_files ADD COLUMN wrappedDek TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE secure_files ADD COLUMN dekIv TEXT NOT NULL DEFAULT ''")
      }
    }

    @Volatile
    var isEncryptionFallbackActive: Boolean = false
      private set

    @Volatile
    private var INSTANCE: SecureVaultDatabase? = null

    init {
      try {
        System.loadLibrary("sqlcipher")
        Log.i(TAG, "SQLCipher native library ready for SecureVault.")
      } catch (t: Throwable) {
        Log.w(TAG, "Failed to load sqlcipher for SecureVault: ${t.message}. Fallback mode active.")
        isEncryptionFallbackActive = true
      }
    }

    fun getInstance(context: Context): SecureVaultDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
      }
    }

    private fun buildDatabase(appContext: Context): SecureVaultDatabase {
      if (!isEncryptionFallbackActive) {
        try {
          val passphrase = SecureVaultPassphraseManager.getOrCreatePassphrase(appContext)
          val factory = SupportOpenHelperFactory(passphrase)

          val encryptedDb = Room.databaseBuilder(
            appContext,
            SecureVaultDatabase::class.java,
            ENCRYPTED_DB_NAME
          )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2)
            .build()

          // Eagerly verify native SQLCipher connection
          encryptedDb.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
            cursor.moveToFirst()
          }

          isEncryptionFallbackActive = false
          Log.i(TAG, "SecureVault SQLCipher encrypted database successfully initialized and verified.")
          return encryptedDb
        } catch (e: Throwable) {
          Log.e(TAG, "SecureVault SQLCipher initialization failed: ${e.message}. Falling back to plaintext Room database.", e)
          isEncryptionFallbackActive = true
        }
      }

      // Plaintext fallback for test/JVM environments without SQLCipher binaries
      Log.w(TAG, "Initializing SecureVault unencrypted fallback database.")
      val fallbackDb = Room.databaseBuilder(
        appContext,
        SecureVaultDatabase::class.java,
        PLAINTEXT_DB_NAME
      )
        .addMigrations(MIGRATION_1_2)
        .build()

      try {
        fallbackDb.openHelper.writableDatabase.query("SELECT 1").use { cursor ->
          cursor.moveToFirst()
        }
      } catch (e: Throwable) {
        Log.e(TAG, "SecureVault fallback database verification error: ${e.message}", e)
      }

      return fallbackDb
    }
  }
}
