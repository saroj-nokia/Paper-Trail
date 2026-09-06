package com.example

import com.example.security.KeystoreCipherProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeystoreCipherProviderTest {

  @Test
  fun `test getOrCreateKey returns non-null secret key`() {
    val key = KeystoreCipherProvider.getOrCreateKey("test_alias_1")
    assertNotNull(key)
    assertEquals("AES", key.algorithm)
  }

  @Test
  fun `test symmetric encryption and decryption with byte arrays`() {
    val alias = "test_bytes_alias"
    val originalText = "HardwareBackedConfidentialPassphrase123!@#"
    val originalBytes = originalText.toByteArray(Charsets.UTF_8)

    val encrypted = KeystoreCipherProvider.encrypt(alias, originalBytes)
    assertNotNull(encrypted)
    assertNotEquals(0, encrypted.size)
    // IV (12 bytes) + at least 1 byte ciphertext + 16 bytes GCM auth tag
    assert(encrypted.size >= 12 + 16)

    val decrypted = KeystoreCipherProvider.decrypt(alias, encrypted)
    assertArrayEquals(originalBytes, decrypted)
    assertEquals(originalText, String(decrypted, Charsets.UTF_8))
  }

  @Test
  fun `test symmetric string encryption and decryption`() {
    val alias = "test_string_alias"
    val testString = "database_encryption_passphrase_value_998877"

    val encryptedBase64 = KeystoreCipherProvider.encryptString(alias, testString)
    assertNotNull(encryptedBase64)
    assertNotEquals(testString, encryptedBase64)

    val decryptedString = KeystoreCipherProvider.decryptString(alias, encryptedBase64)
    assertEquals(testString, decryptedString)
  }

  @Test
  fun `test empty string encryption and decryption`() {
    val alias = "test_empty_alias"
    val empty = ""

    val encryptedBase64 = KeystoreCipherProvider.encryptString(alias, empty)
    val decrypted = KeystoreCipherProvider.decryptString(alias, encryptedBase64)
    assertEquals(empty, decrypted)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `test decrypt with data shorter than IV throws IllegalArgumentException`() {
    val alias = "test_short_alias"
    val tooShort = ByteArray(5) { 0 }
    KeystoreCipherProvider.decrypt(alias, tooShort)
  }
}
