package com.example

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SubscriptionCycle
import com.example.data.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Paper Trail", appName)
  }

  @Test
  fun `test subscription monthly equivalent cost calculation`() {
    val weeklySub = VaultItem(
      storeName = "Weekly News",
      amount = 5.0,
      category = "Subscriptions",
      purchaseDate = System.currentTimeMillis(),
      isSubscription = true,
      subscriptionCycle = SubscriptionCycle.WEEKLY.name
    )
    assertEquals(21.65, weeklySub.monthlyEquivalentCost, 0.1)

    val yearlySub = VaultItem(
      storeName = "Cloud Storage",
      amount = 120.0,
      category = "Subscriptions",
      purchaseDate = System.currentTimeMillis(),
      isSubscription = true,
      subscriptionCycle = SubscriptionCycle.YEARLY.name
    )
    assertEquals(10.0, yearlySub.monthlyEquivalentCost, 0.01)
  }

  @Test
  fun `test warranty days left calculation`() {
    val now = System.currentTimeMillis()
    val tenDaysAhead = now + (10L * 24 * 60 * 60 * 1000)

    val item = VaultItem(
      storeName = "Espresso Machine",
      amount = 499.0,
      category = "Home & Tools",
      purchaseDate = now,
      isWarranty = true,
      warrantyExpirationDate = tenDaysAhead
    )

    val daysLeft = item.daysUntilWarrantyExpires(now)
    assertEquals(10L, daysLeft)
  }

  @Test
  fun `test tutorial preferences default and mutation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Should initially be false
    val initial = com.example.data.TutorialPreferences.hasSeenTutorial(context)
    assertEquals(false, initial)

    // Mark as seen
    com.example.data.TutorialPreferences.setTutorialSeen(context, true)
    val after = com.example.data.TutorialPreferences.hasSeenTutorial(context)
    assertEquals(true, after)
  }

  @Test
  fun `test secure vault passphrase generation and persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val key1 = com.example.securevault.data.SecureVaultPassphraseManager.getOrCreatePassphrase(context)
    val key2 = com.example.securevault.data.SecureVaultPassphraseManager.getOrCreatePassphrase(context)
    assertEquals(32, key1.size)
    org.junit.Assert.assertArrayEquals(key1, key2)
  }

  @Test
  fun `test secure vault auth manager independent lock state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val authManager = com.example.securevault.security.SecureVaultAuthManager(context)
    // Starts locked
    assertEquals(false, authManager.isUnlocked.value)
    authManager.lock()
    assertEquals(false, authManager.isUnlocked.value)
  }

  @Test
  fun `test secure file item model properties with envelope encryption fields`() {
    val item = com.example.securevault.model.SecureFileItem(
      id = 1L,
      originalFileName = "tax_return_2025.pdf",
      mimeType = "application/pdf",
      fileSizeBytes = 204800L,
      encryptedBlobPath = "1234-uuid-blob",
      dateAdded = 1700000000000L,
      iv = "Y29udGVudEl2",
      wrappedDek = "d3JhcHBlZERlaw==",
      dekIv = "ZGVrV3JhcEl2"
    )
    assertEquals(1L, item.id)
    assertEquals("tax_return_2025.pdf", item.originalFileName)
    assertEquals("application/pdf", item.mimeType)
    assertEquals(204800L, item.fileSizeBytes)
    assertEquals("1234-uuid-blob", item.encryptedBlobPath)
    assertEquals("Y29udGVudEl2", item.iv)
    assertEquals("d3JhcHBlZERlaw==", item.wrappedDek)
    assertEquals("ZGVrV3JhcEl2", item.dekIv)
  }

  @Test
  fun `test secure vault envelope encryption and unwrap roundtrip`() {
    kotlinx.coroutines.runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = com.example.securevault.data.SecureVaultDatabase.getInstance(context)
      val repo = com.example.securevault.data.SecureVaultRepository(db.secureVaultDao(), context)

      // Create a sample temp file to import
      val sampleText = "Confidential Financial Statement - Envelope Encrypted"
      val tempFile = java.io.File(context.cacheDir, "sample_doc.txt").apply {
        writeText(sampleText)
      }
      val uri = android.net.Uri.fromFile(tempFile)

      val encryptCipher = com.example.securevault.data.SecureVaultKeyManager.initEncryptCipher()
      val importResult = repo.importFile(uri, encryptCipher)
      org.junit.Assert.assertTrue("Import should succeed", importResult.isSuccess)
      val savedItem = importResult.getOrThrow()

      org.junit.Assert.assertTrue("wrappedDek must not be empty", savedItem.wrappedDek.isNotEmpty())
      org.junit.Assert.assertTrue("dekIv must not be empty", savedItem.dekIv.isNotEmpty())
      org.junit.Assert.assertTrue("iv (content IV) must not be empty", savedItem.iv.isNotEmpty())

      // Decrypt using unwrapping Keystore decrypt cipher initialized with dekIv
      val dekIvBytes = android.util.Base64.decode(savedItem.dekIv, android.util.Base64.NO_WRAP)
      val decryptCipher = com.example.securevault.data.SecureVaultKeyManager.initDecryptCipher(dekIvBytes)

      val decResult = repo.decryptFile(savedItem, decryptCipher)
      org.junit.Assert.assertTrue("Decryption should succeed", decResult.isSuccess)

      when (val res = decResult.getOrThrow()) {
        is com.example.securevault.data.DecryptionResult.Success -> {
          val decryptedText = String(res.bytes, Charsets.UTF_8)
          assertEquals(sampleText, decryptedText)
        }
        is com.example.securevault.data.DecryptionResult.TooLargeToPreview -> {
          org.junit.Assert.fail("Small test file should not be marked too large")
        }
      }

      // Cleanup
      repo.deleteFile(savedItem)
      tempFile.delete()
    }
  }

  @Test
  fun `test security integrity audit returns valid non-null report`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val report = com.example.data.security.SecurityIntegrityAuditor.runFullAudit(context)
    org.junit.Assert.assertNotNull(report)
    org.junit.Assert.assertTrue(report.items.isNotEmpty())
    
    // SELinux check must be present and not null
    val selinuxItem = report.items.find { it.id == "selinux" }
    org.junit.Assert.assertNotNull(selinuxItem)
    assertEquals(com.example.data.security.IntegrityStatus.VERIFIED, selinuxItem?.status)

    // Storage encryption check must be present
    val storageItem = report.items.find { it.id == "storage_encryption" }
    org.junit.Assert.assertNotNull(storageItem)
  }

  @Test
  fun `test secure vault preview handles oversized file safely`() {
    val item = com.example.securevault.model.SecureFileItem(
      id = 2L,
      originalFileName = "large_archive.zip",
      mimeType = "application/zip",
      fileSizeBytes = 100L * 1024 * 1024,
      encryptedBlobPath = "5678-uuid-blob",
      dateAdded = 1700000000000L,
      iv = "dGVzdF9pdg=="
    )
    val oversizedPreview = com.example.securevault.ui.SecureVaultPreview(
      item = item,
      decryptedBytes = null,
      isTooLargeToPreview = true
    )
    assertEquals(true, oversizedPreview.isTooLargeToPreview)
    assertNull(oversizedPreview.decryptedBytes)

    val regularPreview = com.example.securevault.ui.SecureVaultPreview(
      item = item,
      decryptedBytes = "test content".toByteArray(),
      isTooLargeToPreview = false
    )
    assertEquals(false, regularPreview.isTooLargeToPreview)
    assertNotNull(regularPreview.decryptedBytes)
  }

  @Test
  fun `test MainActivity sets FLAG_SECURE on window`() {
    Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
      val activity = controller.get()
      val flags = activity.window.attributes.flags
      val hasFlagSecure = (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
      assertTrue("MainActivity window must have FLAG_SECURE set to protect against screenshot/thumbnail exposure", hasFlagSecure)
    }
  }
}
