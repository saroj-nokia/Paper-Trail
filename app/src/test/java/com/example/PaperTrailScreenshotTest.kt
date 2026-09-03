package com.example

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.data.model.VaultItem
import com.example.ui.components.PerforatedReceiptCard
import com.example.ui.theme.PaperTrailTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaperTrailScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun receipt_card_render_test() {
    val sampleItem = VaultItem(
      id = 1L,
      storeName = "Best Buy",
      amount = 899.99,
      category = "Electronics",
      purchaseDate = 1716300000000L,
      isWarranty = true,
      warrantyExpirationDate = System.currentTimeMillis() + 864000000L,
      isSubscription = false
    )

    composeTestRule.setContent {
      PaperTrailTheme {
        PerforatedReceiptCard(
          item = sampleItem,
          onClick = {},
          modifier = Modifier.padding(16.dp)
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Best Buy").assertIsDisplayed()
    composeTestRule.onNodeWithText("Electronics").assertIsDisplayed()
  }
}
