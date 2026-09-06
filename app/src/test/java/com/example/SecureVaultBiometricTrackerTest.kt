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
  }

  @Test
  fun `test keystore key alias is isolated`() {
    assertEquals("securevault_credential_key", SecureVaultBiometricTracker.KEYSTORE_KEY_ALIAS)
  }

  @Test
  fun `test unconfigured state returns not configured`() {
    assertFalse(SecureVaultBiometricTracker.isExplicitlyConfigured(context))
    val res = SecureVaultBiometricTracker.verifyMasterCredential(context, "123456")
    assertTrue(res is MasterCredentialVerifyResult.NotConfigured)
  }

  @Test
  fun `test set and verify master PIN`() {
    SecureVaultBiometricTracker.setMasterCredential(context, "123456", MasterCredentialType.PIN)

    assertTrue(SecureVaultBiometricTracker.isExplicitlyConfigured(context))
    assertTrue(SecureVaultBiometricTracker.isMasterPassphraseSet(context))
    assertEquals(MasterCredentialType.PIN, SecureVaultBiometricTracker.getMasterCredentialType(context))

    // Valid PIN
    val successRes = SecureVaultBiometricTracker.verifyMasterCredential(context, "123456")
    assertTrue(successRes is MasterCredentialVerifyResult.Success)

    // Invalid PIN
    val failRes = SecureVaultBiometricTracker.verifyMasterCredential(context, "654321")
    assertTrue(failRes is MasterCredentialVerifyResult.InvalidCredential)
  }

  @Test
  fun `test lockout enforcement after failed attempts`() {
    SecureVaultBiometricTracker.setMasterCredential(context, "987654", MasterCredentialType.PIN)

    // Fail 4 times (within free attempts)
    repeat(4) {
      val res = SecureVaultBiometricTracker.verifyMasterCredential(context, "000000")
      assertTrue(res is MasterCredentialVerifyResult.InvalidCredential)
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
}
