package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import com.example.ui.navigation.PaperTrailAppContent
import com.example.ui.screens.vault.VaultViewModel
import com.example.ui.theme.PaperTrailTheme

class MainActivity : FragmentActivity() {
  private val viewModel: VaultViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Protect against screenshots, screen recording, and Recent Apps thumbnail exposure.
    // Setting FLAG_SECURE app-wide prevents sensitive receipt, financial ledger, and
    // SecureVault cryptographic/file data from being captured or cached in OS snapshots.
    //
    // Note on UX Tradeoff vs. Scoped Approach:
    // A blanket app-wide FLAG_SECURE prevents user-initiated screenshots or screen recording
    // for bug reports/tutorials across all screens. An alternative is dynamically toggling
    // the flag when SecureVault-related screens are active (via DisposableEffect/Lifecycle).
    // However, app-wide protection is the simpler and safer default to eliminate timing gaps
    // and ensure ledger and document privacy.
    window.setFlags(
      WindowManager.LayoutParams.FLAG_SECURE,
      WindowManager.LayoutParams.FLAG_SECURE
    )

    enableEdgeToEdge()
    setContent {
      PaperTrailTheme {
        PaperTrailAppContent(viewModel = viewModel)
      }
    }
  }
}
