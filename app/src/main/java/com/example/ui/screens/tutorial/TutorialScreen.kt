package com.example.ui.screens.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ReceiptPerforatedHeader
import com.example.ui.theme.ForestContainer
import com.example.ui.theme.ForestOnContainer
import com.example.ui.theme.ForestPrimary
import kotlinx.coroutines.launch

data class TutorialPageData(
  val title: String,
  val description: String,
  val icon: ImageVector,
  val badgeText: String
)

val tutorialPages = listOf(
  TutorialPageData(
    title = "Scan a receipt",
    description = "Tap Scan OCR, snap or pick a photo. Text is read entirely on-device — nothing leaves your phone.",
    icon = Icons.Default.DocumentScanner,
    badgeText = "ON-DEVICE OCR"
  ),
  TutorialPageData(
    title = "Everything stays local",
    description = "Your vault is encrypted on this device only. No account, no cloud, no bank linking.",
    icon = Icons.Default.CloudOff,
    badgeText = "ZERO CLOUD"
  ),
  TutorialPageData(
    title = "Track warranties & subscriptions",
    description = "Mark items as warranties or subscriptions to get reminders before they expire or renew.",
    icon = Icons.Default.NotificationsActive,
    badgeText = "AUTOMATED ALERTS"
  ),
  TutorialPageData(
    title = "Your data, protected",
    description = "Unlock with biometrics or PIN. Only you can open the vault.",
    icon = Icons.Default.Fingerprint,
    badgeText = "HARDWARE KEYSTORE"
  )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
  onFinishTutorial: () -> Unit
) {
  val pagerState = rememberPagerState { tutorialPages.size }
  val scope = rememberCoroutineScope()
  val isLastPage = pagerState.currentPage == tutorialPages.size - 1

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            text = "Paper Trail Walkthrough",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        },
        actions = {
          AnimatedVisibility(
            visible = !isLastPage,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            TextButton(
              onClick = onFinishTutorial,
              modifier = Modifier.testTag("tutorial_skip_button")
            ) {
              Text("Skip", fontWeight = FontWeight.SemiBold)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Pager Content
      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .testTag("tutorial_pager")
      ) { pageIndex ->
        val page = tutorialPages[pageIndex]

        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .border(
                1.5.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
              ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              ReceiptPerforatedHeader()

              Spacer(modifier = Modifier.height(24.dp))

              // Icon container
              Box(
                modifier = Modifier
                  .size(96.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = page.icon,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp),
                  tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }

              Spacer(modifier = Modifier.height(16.dp))

              // Badge
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(ForestContainer)
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = page.badgeText,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = ForestOnContainer,
                  letterSpacing = 1.sp
                )
              }

              Spacer(modifier = Modifier.height(16.dp))

              // Title
              Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
              )

              Spacer(modifier = Modifier.height(12.dp))

              // Description
              Text(
                text = page.description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
              )

              Spacer(modifier = Modifier.height(16.dp))
            }
          }
        }
      }

      // Bottom Control Bar: Indicators & Action Buttons
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Dot Indicators
        Row(
          modifier = Modifier.padding(vertical = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          repeat(tutorialPages.size) { index ->
            val isSelected = pagerState.currentPage == index
            val width by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = com.example.ui.theme.PaperTrailMotion.expressiveExpand(),
                label = "indicator_width"
            )
            val color by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                animationSpec = com.example.ui.theme.PaperTrailMotion.expressiveCollapse(),
                label = "indicator_color"
            )
            Box(
              modifier = Modifier
                .height(8.dp)
                .width(width)
                .clip(CircleShape)
                .background(color)
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (pagerState.currentPage > 0) {
            OutlinedButton(
              onClick = {
                scope.launch {
                  pagerState.animateScrollToPage(pagerState.currentPage - 1)
                }
              },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("tutorial_back_button")
            ) {
              Text("Back")
            }
          }

          Button(
            onClick = {
              if (isLastPage) {
                onFinishTutorial()
              } else {
                scope.launch {
                  pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
              }
            },
            modifier = Modifier
              .weight(if (pagerState.currentPage > 0) 1f else 2f)
              .height(48.dp)
              .testTag(if (isLastPage) "tutorial_get_started_button" else "tutorial_next_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
          ) {
            Text(
              text = if (isLastPage) "Get Started" else "Next",
              fontWeight = FontWeight.Bold
            )
            if (!isLastPage) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
  }
}
