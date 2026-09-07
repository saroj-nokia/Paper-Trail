package com.example

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.DatabasePassphraseManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabasePassphraseManagerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteSharedPreferences(DatabasePassphraseManager.PREFS_FILE)
  }

  @Test
  fun `test getOrCreatePassphrase returns 32-byte key and returns identical key on second call`() {
    val key1 = DatabasePassphraseManager.getOrCreatePassphrase(context)
    assertNotNull(key1)
    assertEquals(32, key1.size)

    val key2 = DatabasePassphraseManager.getOrCreatePassphrase(context)
    assertArrayEquals(key1, key2)
  }
}
