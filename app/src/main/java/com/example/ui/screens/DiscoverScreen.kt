package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AsyncUiState
import com.example.data.model.DiscountAlert
import com.example.data.model.StyleUpgradeSuggestion
import com.example.data.model.SustainableBrandMatch
import com.example.ui.components.AtelierAiLoadingCard
import com.example.ui.components.AtelierErrorCard
import com.example.ui.components.AtelierTagPill
import com.example.ui.components.EditorialSectionHeader
import com.example.ui.theme.AtelierObsidian
import com.example.ui.viewmodel.AtelierViewModel
import com.example.util.ReminderScheduler

@Composable
fun DiscoverScreen(
    viewModel: AtelierViewModel
) {
    val context = LocalContext.current
    val discoverState by viewModel.discoverState.collectAsStateWithLifecycle()
    val alertsState by viewModel.discountAlertsState.collectAsStateWithLifecycle()
    val wardrobeState by viewModel.wardrobeState.collectAsStateWithLifecycle()

    val alerts = (alertsState as? AsyncUiState.Success)?.data.orEmpty()
    val wardrobeCount = (wardrobeState as? AsyncUiState.Success)?.data?.size ?: 0
    var showCustomAlertDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Editorial Sustainable Fashion Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_eco_banner),
                    contentDescription = "Sustainable atelier textiles",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    AtelierObsidian.copy(alpha = 0.25f),
                                    AtelierObsidian.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp)
                ) {
                    AtelierTagPill(
                        text = "CONSCIOUS STYLE UPGRADES & ECO RADAR",
                        containerColor = Color(0xFF3D5A47),
                        contentColor = Color(0xFFFAF6F0),
                        icon = Icons.Outlined.Eco
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Buy Smarter. Pair With What You Own.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFFFAF6F0)
                    )
                    Text(
                        text = "AI matches new upgrade pieces to your $wardrobeCount existing clothes & tracks seasonal discounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE7DFD5)
                    )
                }
            }
        }

        // Action Trigger for AI Style Upgrades & Eco Brand Discovery
        item {
            EditorialSectionHeader(
                eyebrow = "WARDROBE GAP INTELLIGENCE",
                title = "What to Buy Next",
                actionLabel = "Refresh AI",
                onActionClick = { viewModel.loadStyleUpgradesAndEcoBrands(forceRefresh = true) }
            )
        }

        item {
            when (val state = discoverState) {
                is AsyncUiState.Idle -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Discover High-Impact Style Upgrades",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Atelier inspects your old clothes, body fit, and skin undertone to suggest the exact new pieces worth buying — paired with certified sustainable brands & seasonal deals.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.loadStyleUpgradesAndEcoBrands() },
                                modifier = Modifier.testTag("generate_upgrades_button")
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyze My Closet & Suggest Upgrades")
                            }
                        }
                    }
                }
                is AsyncUiState.Loading -> {
                    AtelierAiLoadingCard(
                        title = "Finding Smart Upgrades & Eco Brands...",
                        subtitle = "Pairing new 2026 pieces with your existing clothes and seasonal sales."
                    )
                }
                is AsyncUiState.Error -> {
                    AtelierErrorCard(
                        message = state.message,
                        onRetry = { viewModel.loadStyleUpgradesAndEcoBrands(forceRefresh = true) }
                    )
                }
                is AsyncUiState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        AtelierTagPill(
                            text = state.data.trendHeadline,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            icon = Icons.Outlined.AutoAwesome
                        )
                        state.data.upgrades.forEach { upgrade ->
                            UpgradeRecommendationCard(
                                upgrade = upgrade,
                                onTrackDeal = {
                                    viewModel.addDiscountAlert(
                                        itemName = upgrade.itemName,
                                        brandName = upgrade.sustainableBrand,
                                        category = upgrade.category,
                                        targetDiscountPercent = 25,
                                        currentDiscountPercent = 15,
                                        ecoCertified = true,
                                        seasonalTag = upgrade.seasonalDealHint
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        EditorialSectionHeader(
                            eyebrow = "SUSTAINABLE FASHION LABELS",
                            title = "Conscious Brands for You"
                        )
                        state.data.sustainableBrands.forEach { brand ->
                            SustainableBrandCard(
                                brand = brand,
                                onSetBrandAlert = {
                                    viewModel.addDiscountAlert(
                                        itemName = brand.specialty,
                                        brandName = brand.brandName,
                                        category = "Capsule Edit",
                                        targetDiscountPercent = brand.discountPercent.coerceIn(10, 60),
                                        currentDiscountPercent = brand.discountPercent,
                                        ecoCertified = true,
                                        seasonalTag = brand.seasonalOffer
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Real-Time Seasonal Discount Alerts Watchlist (Firestore-backed)
        item {
            EditorialSectionHeader(
                eyebrow = "REAL-TIME SEASONAL ALERTS",
                title = "Discount Radar (${alerts.size})",
                actionLabel = "+ Track Item",
                onActionClick = { showCustomAlertDialog = true }
            )
        }

        if (alerts.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "No Seasonal Price Alerts Yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap \"Track Deal\" on any AI upgrade above or add a custom garment/brand to receive seasonal sale notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showCustomAlertDialog = true },
                            modifier = Modifier.testTag("add_custom_alert_button")
                        ) {
                            Icon(Icons.Outlined.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Seasonal Discount Alert")
                        }
                    }
                }
            }
        } else {
            items(alerts, key = { it.id }) { alert ->
                DiscountAlertCard(
                    alert = alert,
                    onToggle = { active -> viewModel.toggleDiscountAlert(alert, active) },
                    onTestAlert = {
                        ReminderScheduler.triggerInstantTestNotification(
                            context = context,
                            title = "Seasonal Deal Live: ${alert.brandName}",
                            body = "${alert.itemName} is trending at ${alert.currentDiscountPercent}% off (${alert.seasonalTag}). Target: ${alert.targetDiscountPercent}%."
                        )
                    },
                    onDelete = { viewModel.deleteDiscountAlert(alert.id) }
                )
            }
        }
    }

    if (showCustomAlertDialog) {
        CreateDiscountAlertDialog(
            onDismiss = { showCustomAlertDialog = false },
            onCreate = { item, brand, target, tag ->
                viewModel.addDiscountAlert(
                    itemName = item,
                    brandName = brand,
                    category = "Outerwear",
                    targetDiscountPercent = target,
                    currentDiscountPercent = (target - 5).coerceAtLeast(10),
                    ecoCertified = true,
                    seasonalTag = tag
                )
                showCustomAlertDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpgradeRecommendationCard(
    upgrade: StyleUpgradeSuggestion,
    onTrackDeal: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AtelierTagPill(
                    text = "${upgrade.category} • ${upgrade.colorName}",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = upgrade.estimatedPriceRange,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = upgrade.itemName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = upgrade.whyItUpgrades,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (upgrade.pairsWithExisting.isNotEmpty()) {
                Text(
                    text = "Pairs with your existing clothes:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    upgrade.pairsWithExisting.forEach { oldPiece ->
                        AtelierTagPill(text = oldPiece)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AtelierTagPill(
                    text = "${upgrade.sustainableBrand} (${upgrade.ecoCertification})",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    icon = Icons.Outlined.Eco
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = upgrade.seasonalDealHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onTrackDeal) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Track Deal")
                }
            }
        }
    }
}

@Composable
private fun SustainableBrandCard(
    brand: SustainableBrandMatch,
    onSetBrandAlert: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Text(brand.brandName, style = MaterialTheme.typography.titleMedium)
                }
                AtelierTagPill(
                    text = "Up to ${brand.discountPercent}% Off",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = Icons.Outlined.LocalOffer
                )
            }
            Text(
                text = "${brand.specialty} • ${brand.ecoCertification}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = brand.whyItFitsUser,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = brand.seasonalOffer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onSetBrandAlert) {
                    Text("Alert Me")
                }
            }
        }
    }
}

@Composable
private fun DiscountAlertCard(
    alert: DiscountAlert,
    onToggle: (Boolean) -> Unit,
    onTestAlert: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.brandName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (alert.ecoCertified) {
                        Icon(Icons.Outlined.Eco, contentDescription = "Sustainable brand", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                    }
                }
                Text(alert.itemName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Target: ${alert.targetDiscountPercent}%+ off • Now: ${alert.currentDiscountPercent}% • ${alert.seasonalTag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onTestAlert, contentPadding = PaddingValues(0.dp)) {
                    Text("Preview Alert Notification", style = MaterialTheme.typography.labelMedium)
                }
            }
            Switch(checked = alert.activeAlert, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete alert")
            }
        }
    }
}

@Composable
private fun CreateDiscountAlertDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Int, String) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var brandName by remember { mutableStateOf("") }
    var targetDiscount by remember { mutableIntStateOf(25) }
    var seasonalTag by remember { mutableStateOf("Winter Archive Sale") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = { Text("New Seasonal Discount Alert", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Garment to Watch") },
                    placeholder = { Text("e.g. Organic Linen Trench") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = { Text("Sustainable Brand") },
                    placeholder = { Text("e.g. Raw Mango, Reformation, COS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = seasonalTag,
                    onValueChange = { seasonalTag = it },
                    label = { Text("Seasonal Campaign") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Notify at $targetDiscount%+ discount", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = targetDiscount.toFloat(),
                    onValueChange = { targetDiscount = it.toInt() },
                    valueRange = 10f..70f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (itemName.isNotBlank() && brandName.isNotBlank()) {
                        onCreate(itemName.trim(), brandName.trim(), targetDiscount, seasonalTag.trim())
                    }
                }
            ) {
                Text("Save Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
