package com.example.ui.screens.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.PerforatedReceiptCard
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.LocalFrostedGlassEnabled
import com.example.ui.theme.LocalHazeState
import com.example.ui.theme.frostedGlassSource
import com.example.ui.theme.frostedGlassTopBar

val CATEGORY_OPTIONS = listOf(
  "All",
  "Electronics",
  "Groceries",
  "Subscriptions",
  "Home & Tools",
  "Dining",
  "Healthcare",
  "Automotive",
  "General"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
  viewModel: VaultViewModel,
  onNavigateToCapture: () -> Unit,
  onNavigateToItemDetail: (Long) -> Unit
) {
  val items by viewModel.filteredItems.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
  val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
  val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

  var sortMenuExpanded by remember { mutableStateOf(false) }
  val frostedGlassEnabled = LocalFrostedGlassEnabled.current
  val hazeState = LocalHazeState.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Vault Ledger",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        actions = {
          Box {
            IconButton(
              onClick = { sortMenuExpanded = true },
              modifier = Modifier.testTag("sort_button")
            ) {
              Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = "Sort Options"
              )
            }

            DropdownMenu(
              expanded = sortMenuExpanded,
              onDismissRequest = { sortMenuExpanded = false }
            ) {
              SortOption.values().forEach { option ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = option.label,
                      fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                      color = if (option == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                  },
                  onClick = {
                    viewModel.setSortOption(option)
                    sortMenuExpanded = false
                  }
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = if (frostedGlassEnabled) Color.Transparent else MaterialTheme.colorScheme.background
        ),
        modifier = if (frostedGlassEnabled) {
          Modifier.frostedGlassTopBar(hazeState, enabled = true)
        } else {
          Modifier
        }
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = onNavigateToCapture,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("fab_scan_receipt")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = "Scan Receipt")
          Spacer(modifier = Modifier.width(8.dp))
          Text("Scan OCR", fontWeight = FontWeight.Bold)
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    val topPadding = if (frostedGlassEnabled) 0.dp else paddingValues.calculateTopPadding()
    val extraTopPadding = if (frostedGlassEnabled) paddingValues.calculateTopPadding() else 0.dp

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(top = topPadding)
        .frostedGlassSource(hazeState, enabled = frostedGlassEnabled)
    ) {
      if (extraTopPadding > 0.dp) {
        Spacer(modifier = Modifier.height(extraTopPadding))
      }
      // 1. Search Bar
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { viewModel.setSearchQuery(it) },
        placeholder = { Text("Search merchant, category, notes, or OCR text...") },
        leadingIcon = {
          Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.setSearchQuery("") }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
          focusedBorderColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp)
          .testTag("vault_search_input")
      )

      // 2. Tab Navigation: All, Receipts, Warranties, Subscriptions
      ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.background,
        indicator = { tabPositions ->
          if (selectedTab.ordinal < tabPositions.size) {
            TabRowDefaults.SecondaryIndicator(
              modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
      ) {
        VaultTab.values().forEach { tab ->
          Tab(
            selected = selectedTab == tab,
            onClick = { viewModel.setTab(tab) },
            text = {
              Text(
                text = tab.title,
                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
              )
            }
          )
        }
      }

      // 3. Category Filter Chips Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        CATEGORY_OPTIONS.forEach { category ->
          val isSelected = if (category == "All") selectedCategory == null || selectedCategory == "All"
          else selectedCategory.equals(category, ignoreCase = true)

          FilterChip(
            selected = isSelected,
            onClick = {
              if (category == "All") viewModel.setCategory(null)
              else viewModel.setCategory(if (isSelected) null else category)
            },
            label = { Text(category, style = MaterialTheme.typography.labelSmall) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
          )
        }
      }

      // 4. Ledger Count Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${items.size} RECORDS FOUND",
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )

        Text(
          text = sortOption.label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // 5. Items List or Empty State
      if (items.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = if (searchQuery.isNotEmpty()) "No matching records" else "Vault is empty",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
              text = if (searchQuery.isNotEmpty())
                "Try searching for another store name or keyword."
              else
                "Scan your first receipt or record a warranty/subscription to start your encrypted ledger.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp),
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(items, key = { it.id }) { item ->
            PerforatedReceiptCard(
              item = item,
              onClick = { onNavigateToItemDetail(item.id) }
            )
          }
        }
      }
    }
  }
}
