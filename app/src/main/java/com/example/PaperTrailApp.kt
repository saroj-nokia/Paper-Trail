package com.example

import android.app.Application
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.notifications.ReminderScheduler
import com.example.data.repository.VaultRepository
import com.example.data.security.BiometricAuthManager
import com.example.security.KeystoreSecurityState
import com.example.security.KeystoreUnavailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaperTrailApp : Application() {

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val database: AppDatabase by lazy {
    AppDatabase.getInstance(this)
  }

  val repository: VaultRepository by lazy {
    VaultRepository(database.vaultDao(), this)
  }

  val authManager: BiometricAuthManager by lazy {
    BiometricAuthManager(this)
  }

  override fun onCreate() {
    super.onCreate()
    // Prewarm database on background thread immediately at process start
    prewarmDatabase()

    try {
      ReminderScheduler.schedulePeriodicReminders(this)
    } catch (e: Throwable) {
      // Handled for test / in-memory harnesses
    }
  }

  fun prewarmDatabase() {
    appScope.launch {
      retryPrewarm()
    }
  }

  suspend fun retryPrewarm(): Boolean {
    return try {
      // Reset instance in case a prior attempt failed midway
      AppDatabase.resetForTesting()
      AppDatabase.getInstance(this).vaultDao()
      KeystoreSecurityState.clearFailure()
      Log.i("PaperTrailApp", "Database successfully prewarmed and verified with Keystore.")
      true
    } catch (e: KeystoreUnavailableException) {
      Log.e("PaperTrailApp", "Keystore unavailable during database prewarm: ${e.message}", e)
      KeystoreSecurityState.recordFailure(e)
      false
    } catch (e: Throwable) {
      Log.w("PaperTrailApp", "Non-keystore initialization issue during prewarm: ${e.message}", e)
      false
    }
  }
}
