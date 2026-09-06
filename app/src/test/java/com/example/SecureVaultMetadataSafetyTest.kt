package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.securevault.data.SecureVaultMetadataSafety
import com.example.securevault.model.SecureFileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureVaultMetadataSafetyTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    runBlocking {
      context = ApplicationProvider.getApplicationContext()
      SecureVaultMetadataSafety.clearShadowSnapshot(context)
    }
  }

  @Test
  fun `test shadow key alias is isolated`() {
    assertEquals("securevault_shadow_key", SecureVaultMetadataSafety.KEYSTORE_KEY_ALIAS)
  }

  @Test
  fun `test record and read shadow snapshot roundtrip`() {
    runBlocking {
      val items = listOf(
      SecureFileItem(
        id = 101L,
        originalFileName = "tax_2025.pdf",
        mimeType = "application/pdf",
        fileSizeBytes = 2048576L,
        encryptedBlobPath = "vault_blobs/blob_101.enc",
        dateAdded = 1700000000000L,
        iv = "sample_iv_123",
        wrappedDek = "sample_wrapped_dek_456",
        dekIv = "sample_dek_iv_789"
      ),
      SecureFileItem(
        id = 102L,
        originalFileName = "passport.jpg",
        mimeType = "image/jpeg",
        fileSizeBytes = 4096000L,
        encryptedBlobPath = "vault_blobs/blob_102.enc",
        dateAdded = 1700000050000L,
        iv = "sample_iv_abc",
        wrappedDek = "sample_wrapped_dek_def",
        dekIv = "sample_dek_iv_ghi"
      )
    )

    SecureVaultMetadataSafety.recordShadowSnapshot(context, items)

    val file = SecureVaultMetadataSafety.getShadowFile(context)
    assertTrue(file.exists())
    assertNotEquals(0L, file.length())

    val readItems = SecureVaultMetadataSafety.readShadowSnapshot(context)
    assertEquals(2, readItems.size)
    assertEquals("tax_2025.pdf", readItems[0].originalFileName)
    assertEquals(101L, readItems[0].id)
    assertEquals("passport.jpg", readItems[1].originalFileName)
    assertEquals(102L, readItems[1].id)

    SecureVaultMetadataSafety.clearShadowSnapshot(context)
    assertFalse(file.exists())
    val emptyItems = SecureVaultMetadataSafety.readShadowSnapshot(context)
    assertTrue(emptyItems.isEmpty())
    }
  }
}
