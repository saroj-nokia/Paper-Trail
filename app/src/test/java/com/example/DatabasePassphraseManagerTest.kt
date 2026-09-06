package com.example

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.DatabasePassphraseManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabasePassphraseManagerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    // Clean up preferences before test
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      context.deleteSharedPreferences(DatabasePassphraseManager.PREFS_FILE)
      context.deleteSharedPreferences(DatabasePassphraseManager.PREFS_FALLBACK_FILE)
      context.deleteSharedPreferences("papertrail_secure_vault_prefs")
      context.deleteSharedPreferences("papertrail_secure_vault_prefs_fallback")
    }
  }

  @Test
  fun `test getOrCreatePassphrase returns 32-byte key and returns identical key on second call`() {
    val key1 = DatabasePassphraseManager.getOrCreatePassphrase(context)
    assertNotNull(key1)
    assertEquals(32, key1.size)

    val key2 = DatabasePassphraseManager.getOrCreatePassphrase(context)
    assertArrayEquals(key1, key2)
  }

  @Test
  fun `test transparent migration from legacy fallback preferences`() {
    val expectedBytes = ByteArray(32) { (it * 3).toByte() }
    val samplePassphraseBase64 = android.util.Base64.encodeToString(expectedBytes, android.util.Base64.NO_WRAP)

    // Pre-populate legacy fallback file
    val legacyPrefs = context.getSharedPreferences("papertrail_secure_vault_prefs_fallback", Context.MODE_PRIVATE)
    legacyPrefs.edit().putString("vault_db_encryption_key_v1", samplePassphraseBase64).commit()

    // Call getOrCreatePassphrase
    val migrated = DatabasePassphraseManager.getOrCreatePassphrase(context)
    assertArrayEquals(expectedBytes, migrated)

    // Verify subsequent call gets the same key from modern prefs
    val subsequent = DatabasePassphraseManager.getOrCreatePassphrase(context)
    assertArrayEquals(expectedBytes, subsequent)

    // Verify old legacy prefs is deleted or cleared
    val legacyCheck = context.getSharedPreferences("papertrail_secure_vault_prefs_fallback", Context.MODE_PRIVATE)
    assertEquals(null, legacyCheck.getString("vault_db_encryption_key_v1", null))
  }
}
