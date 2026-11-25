package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.signOutUser
import com.example.data.model.AsyncUiState
import com.example.data.model.AtelierConstants
import com.example.data.model.UserStyleProfile
import com.example.ui.components.GitHubUpdateDialog
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlannerScreen
import com.example.ui.screens.TryOnStudioScreen
import com.example.ui.screens.WardrobeScreen
import com.example.ui.viewmodel.AtelierViewModel

enum class AtelierDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Atelier", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    WARDROBE("wardrobe", "Closet", Icons.Filled.Checkroom, Icons.Outlined.Checkroom),
    STUDIO("studio", "3D Fit", Icons.Filled.ViewInAr, Icons.Outlined.ViewInAr),
    DISCOVER("discover", "Upgrade", Icons.Filled.Explore, Icons.Outlined.Explore),
    PLANNER("planner", "Planner", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth)
}

@Composable
fun AtelierRootApp(
    currentUserId: String,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AtelierViewModel = viewModel(
        key = currentUserId,
        factory = AtelierViewModel.provideFactory(context)
    )

    var currentTab by remember { mutableStateOf(AtelierDestination.HOME) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val githubRepo by viewModel.githubRepo.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    // BackHandler returns secondary tabs to Atelier Home cleanly
    BackHandler(enabled = currentTab != AtelierDestination.HOME) {
        currentTab = AtelierDestination.HOME
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        AtelierDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        AtelierDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        when (currentTab) {
                            AtelierDestination.HOME -> HomeScreen(
                                viewModel = viewModel,
                                onOpenProfileSheet = { showProfileDialog = true },
                                onOpenUpdateDialog = { showUpdateDialog = true },
                                onNavigateToWardrobe = { currentTab = AtelierDestination.WARDROBE },
                                onNavigateToStudio = { currentTab = AtelierDestination.STUDIO },
                                onNavigateToDiscover = { currentTab = AtelierDestination.DISCOVER },
                                onNavigateToPlanner = { currentTab = AtelierDestination.PLANNER }
                            )
                            AtelierDestination.WARDROBE -> WardrobeScreen(
                                viewModel = viewModel,
                                onTryIn3dStudio = { currentTab = AtelierDestination.STUDIO }
                            )
                            AtelierDestination.STUDIO -> TryOnStudioScreen(viewModel = viewModel)
                            AtelierDestination.DISCOVER -> DiscoverScreen(viewModel = viewModel)
                            AtelierDestination.PLANNER -> PlannerScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        val currentProfile = (profileState as? AsyncUiState.Success)?.data ?: UserStyleProfile()
        StyleProfileDialog(
            profile = currentProfile,
            onDismiss = { showProfileDialog = false },
            onOpenUpdateDialog = {
                showProfileDialog = false
                showUpdateDialog = true
            },
            onSave = { name, gender, prefs, budget ->
                viewModel.saveStylePreferences(name, gender, prefs, budget)
                showProfileDialog = false
            },
            onSignOut = {
                showProfileDialog = false
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope,
                    onSignOutComplete = onSignedOut
                )
            }
        )
    }

    if (showUpdateDialog) {
        GitHubUpdateDialog(
            savedRepo = githubRepo,
            updateState = updateCheckState,
            onDismiss = { showUpdateDialog = false },
            onCheckUpdates = { repo -> viewModel.saveGitHubRepoAndCheck(repo) },
            onOpenUrl = { url -> viewModel.openGitHubUpdateUrl(url) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleProfileDialog(
    profile: UserStyleProfile,
    onDismiss: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onSave: (String, String, List<String>, String) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var gender by remember(profile.gender) { mutableStateOf(profile.gender) }
    var selectedStyles by remember(profile.stylePreferences) { mutableStateOf(profile.stylePreferences.toSet()) }
    var budgetTier by remember(profile.budgetTier) { mutableStateOf(profile.budgetTier) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = { Text("Style Profile & Preferences", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Gender Presentation", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AtelierConstants.GENDERS.forEach { g ->
                        FilterChip(
                            selected = gender == g,
                            onClick = { gender = g },
                            label = { Text(g) }
                        )
                    }
                }

                Text("Aesthetic Preferences", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AtelierConstants.STYLE_AESTHETICS.forEach { aesthetic ->
                        val active = aesthetic in selectedStyles
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedStyles = if (active) selectedStyles - aesthetic else selectedStyles + aesthetic
                            },
                            label = { Text(aesthetic) }
                        )
                    }
                }

                Text("Budget & Values Tier", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AtelierConstants.BUDGET_TIERS.forEach { tier ->
                        FilterChip(
                            selected = budgetTier == tier,
                            onClick = { budgetTier = tier },
                            label = { Text(tier) }
                        )
                    }
                }

                HorizontalDivider()

                OutlinedButton(
                    onClick = onOpenUpdateDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_github_updates_button")
                ) {
                    Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                    Text("  Check GitHub App Updates")
                }

                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.Outlined.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.height(0.dp))
                    Text("  Sign Out of Google Account")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        displayName.trim().ifEmpty { "Style Member" },
                        gender,
                        selectedStyles.toList().ifEmpty { listOf("Minimalist") },
                        budgetTier
                    )
                },
                modifier = Modifier.testTag("save_style_profile_button")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
