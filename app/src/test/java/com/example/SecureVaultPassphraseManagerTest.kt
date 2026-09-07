package com.example

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.DatabasePassphraseManager
import com.example.securevault.data.SecureVaultPassphraseManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureVaultPassphraseManagerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteSharedPreferences(SecureVaultPassphraseManager.PREFS_FILE)
  }

  @Test
  fun `test getOrCreatePassphrase returns 32-byte key and returns identical key on second call`() {
    val key1 = SecureVaultPassphraseManager.getOrCreatePassphrase(context)
    assertNotNull(key1)
    assertEquals(32, key1.size)

    val key2 = SecureVaultPassphraseManager.getOrCreatePassphrase(context)
    assertArrayEquals(key1, key2)
  }

  @Test
  fun `test distinct alias between DatabasePassphraseManager and SecureVaultPassphraseManager`() {
    assertNotEquals(
      DatabasePassphraseManager.KEYSTORE_KEY_ALIAS,
      SecureVaultPassphraseManager.KEYSTORE_KEY_ALIAS
    )
    assertEquals("securevault_db_passphrase_key", SecureVaultPassphraseManager.KEYSTORE_KEY_ALIAS)
  }
}
