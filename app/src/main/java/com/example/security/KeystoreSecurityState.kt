package com.example.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global state holder tracking hardware security / Android Keystore availability.
 *
 * If KeystoreCipherProvider fails or throws [KeystoreUnavailableException],
 * the failure is captured here to prevent app crash and block navigation into
 * the unencrypted/vulnerable app state.
 */
object KeystoreSecurityState {
  private val _keystoreFailure = MutableStateFlow<Throwable?>(null)
  val keystoreFailure: StateFlow<Throwable?> = _keystoreFailure.asStateFlow()

  fun recordFailure(throwable: Throwable) {
    _keystoreFailure.value = throwable
  }

  fun clearFailure() {
    _keystoreFailure.value = null
  }
}
