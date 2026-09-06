package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.securevault.security.MasterCredentialType
import com.example.securevault.security.MasterCredentialVerifyResult
import com.example.securevault.security.SecureVaultBiometricTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureVaultBiometricTrackerTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    SecureVaultBiometricTracker.resetForTesting()
    context.getSharedPreferences(SecureVaultBiometricTracker.PREFS_FILE, Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences(SecureVaultBiometricTracker.PREFS_FALLBACK_FILE, Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("securevault_system_integrity", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("securevault_system_integrity_fallback", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `test keystore key alias is isolated`() {
    assertEquals("securevault_credential_key", SecureVaultBiometricTracker.KEYSTORE_KEY_ALIAS)
  }

  @Test
  fun `test unconfigured state returns not configured`() {
    assertFalse(SecureVaultBiometricTracker.isExplicitlyConfigured(context))
    assertFalse(SecureVaultBiometricTracker.isMasterPassphraseSet(context))
    val result = SecureVaultBiometricTracker.verifyMasterCredential(context, "123456")
    assertTrue(result is MasterCredentialVerifyResult.NotConfigured)
  }

  @Test
  fun `test configure PIN and verify success`() {
    SecureVaultBiometricTracker.setMasterCredential(context, "123456", MasterCredentialType.PIN)
    assertTrue(SecureVaultBiometricTracker.isExplicitlyConfigured(context))
    assertTrue(SecureVaultBiometricTracker.isMasterPassphraseSet(context))
    assertEquals(MasterCredentialType.PIN, SecureVaultBiometricTracker.getMasterCredentialType(context))

    val resultSuccess = SecureVaultBiometricTracker.verifyMasterCredential(context, "123456")
    assertTrue(resultSuccess is MasterCredentialVerifyResult.Success)

    val resultFail = SecureVaultBiometricTracker.verifyMasterCredential(context, "654321")
    assertTrue(resultFail is MasterCredentialVerifyResult.InvalidCredential)
    assertEquals(4, (resultFail as MasterCredentialVerifyResult.InvalidCredential).attemptsRemaining)
  }

  @Test
  fun `test configure Passphrase and verify success`() {
    SecureVaultBiometricTracker.setMasterPassphrase(context, "correct-horse-battery")
    assertTrue(SecureVaultBiometricTracker.isExplicitlyConfigured(context))
    assertEquals(MasterCredentialType.PASSPHRASE, SecureVaultBiometricTracker.getMasterCredentialType(context))

    assertTrue(SecureVaultBiometricTracker.verifyMasterPassphrase(context, "correct-horse-battery"))
    assertFalse(SecureVaultBiometricTracker.verifyMasterPassphrase(context, "wrong-passphrase-attempt"))
  }

  @Test
  fun `test rate limiting and lockout logic`() {
    SecureVaultBiometricTracker.setMasterCredential(context, "987654", MasterCredentialType.PIN)

    // 4 failed attempts
    for (i in 1..4) {
      val res = SecureVaultBiometricTracker.verifyMasterCredential(context, "000000")
      assertTrue(res is MasterCredentialVerifyResult.InvalidCredential)
      assertEquals(5 - i, (res as MasterCredentialVerifyResult.InvalidCredential).attemptsRemaining)
    }

    // 5th attempt triggers lockout
    val lockoutRes = SecureVaultBiometricTracker.verifyMasterCredential(context, "000000")
    assertTrue(lockoutRes is MasterCredentialVerifyResult.LockedOut)
    val remaining = SecureVaultBiometricTracker.getRemainingLockoutSeconds(context)
    assertTrue(remaining > 0L)

    // While locked out, even the correct PIN is blocked
    val blockedRes = SecureVaultBiometricTracker.verifyMasterCredential(context, "987654")
    assertTrue(blockedRes is MasterCredentialVerifyResult.LockedOut)
  }

  @Test
  fun `test change master credential`() {
    SecureVaultBiometricTracker.setMasterCredential(context, "111222", MasterCredentialType.PIN)

    // Wrong old credential
    val (failSuccess, _) = SecureVaultBiometricTracker.changeMasterCredential(
      context,
      "999999",
      "333444",
      MasterCredentialType.PIN
    )
    assertFalse(failSuccess)

    // Correct old credential
    val (changeSuccess, _) = SecureVaultBiometricTracker.changeMasterCredential(
      context,
      "111222",
      "333444",
      MasterCredentialType.PIN
    )
    assertTrue(changeSuccess)

    // Verify new credential works
    val verifyRes = SecureVaultBiometricTracker.verifyMasterCredential(context, "333444")
    assertTrue(verifyRes is MasterCredentialVerifyResult.Success)
  }

  @Test
  fun `test migration from legacy fallback prefs`() {
    val legacyPrefs = context.getSharedPreferences("securevault_system_integrity_fallback", Context.MODE_PRIVATE)
    // Legacy setup
    legacyPrefs.edit()
      .putString("master_passphrase_salt", android.util.Base64.encodeToString(ByteArray(16) { 1 }, android.util.Base64.NO_WRAP))
      .putString("master_credential_type", MasterCredentialType.PIN.name)
      .putBoolean("master_credential_explicitly_configured", true)
      .putInt("failed_master_credential_attempts", 2)
      .apply()

    SecureVaultBiometricTracker.resetForTesting()

    assertTrue(SecureVaultBiometricTracker.isExplicitlyConfigured(context))
    assertEquals(MasterCredentialType.PIN, SecureVaultBiometricTracker.getMasterCredentialType(context))
    assertEquals(2, SecureVaultBiometricTracker.getFailedAttemptsCount(context))
  }
}
