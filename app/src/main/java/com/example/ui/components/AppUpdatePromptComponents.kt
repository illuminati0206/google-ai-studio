package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shop
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.example.BuildConfig
import com.example.data.model.AsyncUiState
import com.example.data.update.GitHubReleaseCheckResult
import com.example.data.update.PlayUpdateUiState
import com.example.data.update.RemoteConfigUpdateInfo
import java.util.Locale

/**
 * Home banner triggered automatically when Firebase Remote Config detects a newer version code
 * or Google Play In-App Updates reports an available, downloading, or downloaded update.
 */
@Composable
fun PlayAndRemoteConfigUpdateBanner(
    remoteConfigInfo: RemoteConfigUpdateInfo?,
    playUpdateState: PlayUpdateUiState,
    onStartFlexibleUpdate: () -> Unit,
    onStartImmediateUpdate: () -> Unit,
    onCompleteFlexibleUpdate: () -> Unit,
    onOpenDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDownloaded = playUpdateState is PlayUpdateUiState.Downloaded
    val isDownloading = playUpdateState is PlayUpdateUiState.Downloading
    val isPlayAvailable = playUpdateState is PlayUpdateUiState.UpdateAvailable
    val isRemoteUpdate = remoteConfigInfo?.isUpdateAvailable == true

    if (!isDownloaded && !isDownloading && !isPlayAvailable && !isRemoteUpdate) return

    val headline = when {
        isDownloaded -> "Update Ready to Install"
        isDownloading -> "Downloading Update via Google Play..."
        remoteConfigInfo != null && isRemoteUpdate ->
            "Atelier v${remoteConfigInfo.latestVersionName} (Build ${remoteConfigInfo.latestVersionCode}) Available"
        playUpdateState is PlayUpdateUiState.UpdateAvailable ->
            "Google Play Update (Build ${playUpdateState.availableVersionCode}) Available"
        else -> "App Update Available"
    }

    val eyebrow = when {
        isDownloaded -> "IN-APP UPDATE DOWNLOADED"
        isDownloading -> "GOOGLE PLAY IN-APP UPDATE"
        isRemoteUpdate -> "FIREBASE REMOTE CONFIG UPDATE"
        else -> "GOOGLE PLAY UPDATE"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("in_app_update_banner"),
        shape = MaterialTheme.shapes.large,
        color = if (isDownloaded) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        border = BorderStroke(
            1.dp,
            if (isDownloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Outlined.RestartAlt else Icons.Outlined.InstallMobile,
                        contentDescription = "App update status",
                        tint = if (isDownloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = eyebrow,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDownloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDownloaded) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
                if (!isDownloaded && !isDownloading) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_remote_update_banner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss update prompt",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (playUpdateState is PlayUpdateUiState.Downloading) {
                val pct = (playUpdateState.progressFraction * 100).toInt()
                LinearProgressIndicator(
                    progress = { playUpdateState.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                val mbDone = playUpdateState.bytesDownloaded / (1024.0 * 1024.0)
                val mbTotal = playUpdateState.totalBytesToDownload / (1024.0 * 1024.0)
                Text(
                    text = if (playUpdateState.totalBytesToDownload > 0) {
                        String.format(Locale.US, "Downloading update: %d%% (%.1f / %.1f MB)", pct, mbDone, mbTotal)
                    } else {
                        "Preparing Google Play update download..."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else if (isDownloaded) {
                Text(
                    text = "The latest Atelier update has finished downloading. Restart to apply it now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Button(
                    onClick = onCompleteFlexibleUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("complete_flexible_update_button")
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restart & Install Update")
                }
            } else {
                val notes = remoteConfigInfo?.releaseNotes.orEmpty().ifBlank {
                    "Current Build: ${BuildConfig.VERSION_CODE} (v${BuildConfig.VERSION_NAME}). Update directly via Google Play."
                }
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (remoteConfigInfo?.isForceUpdate == true) {
                                onStartImmediateUpdate()
                            } else {
                                onStartFlexibleUpdate()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("banner_play_update_button")
                    ) {
                        Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Update Now")
                    }
                    OutlinedButton(
                        onClick = onOpenDetails,
                        modifier = Modifier.testTag("banner_open_update_center_button")
                    ) {
                        Text("Details")
                    }
                }
            }
        }
    }
}

/**
 * Shown when Firebase Remote Config indicates a mandatory update (`force_update_enabled` or
 * `BuildConfig.VERSION_CODE < min_supported_version_code`).
 */
@Composable
fun MandatoryRemoteConfigUpdateDialog(
    info: RemoteConfigUpdateInfo,
    onUpdateImmediate: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onDismissLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissLater,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        icon = {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = "Mandatory update required",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Update Available (v${info.latestVersionName})",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A newer version (Build ${info.latestVersionCode}) is required for optimal styling performance. You are currently on v${info.currentVersionName} (Build ${info.currentVersionCode}).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.releaseNotes.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateImmediate,
                modifier = Modifier.testTag("mandatory_update_now_button")
            ) {
                Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Update In-App")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onOpenPlayStore,
                modifier = Modifier.testTag("mandatory_open_play_store_button")
            ) {
                Text("Google Play")
            }
        }
    )
}

/**
 * Unified App Updates Center combining:
 * 1. Firebase Remote Config version code check
 * 2. Google Play In-App Updates API (Flexible + Immediate)
 * 3. GitHub Releases checker (for sideloaded/dev builds)
 */
@Composable
fun AppUpdatesCenterDialog(
    remoteConfigState: AsyncUiState<RemoteConfigUpdateInfo>,
    playUpdateState: PlayUpdateUiState,
    savedGitHubRepo: String,
    gitHubUpdateState: AsyncUiState<GitHubReleaseCheckResult>,
    onDismiss: () -> Unit,
    onRefreshRemoteConfigAndPlay: () -> Unit,
    onStartFlexiblePlayUpdate: () -> Unit,
    onStartImmediatePlayUpdate: () -> Unit,
    onCompleteFlexiblePlayUpdate: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onCheckGitHubRepo: (String) -> Unit,
    onOpenGitHubUrl: (String) -> Unit
) {
    var repoInput by remember(savedGitHubRepo) { mutableStateOf(savedGitHubRepo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("App Updates Center", style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Installed Version Header
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "INSTALLED VERSION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} • Code ${BuildConfig.VERSION_CODE}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        AtelierTagPill(
                            text = "Play + Remote Config",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // SECTION 1: Firebase Remote Config + Google Play In-App Updates
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Firebase Remote Config & Play Updates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onRefreshRemoteConfigAndPlay,
                    enabled = remoteConfigState !is AsyncUiState.Loading && playUpdateState !is PlayUpdateUiState.Checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_remote_config_play_button")
                ) {
                    if (remoteConfigState is AsyncUiState.Loading || playUpdateState is PlayUpdateUiState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking Remote Config & Play Store...")
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check Remote Config & Play Store")
                    }
                }

                // Remote Config Status Card
                when (val rc = remoteConfigState) {
                    is AsyncUiState.Success -> {
                        val info = rc.data
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (info.isUpdateAvailable) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (info.isUpdateAvailable) Icons.Outlined.SystemUpdate else Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (info.isUpdateAvailable) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                    )
                                    Text(
                                        text = if (info.isUpdateAvailable) {
                                            "Remote Config: Update to v${info.latestVersionName} (Code ${info.latestVersionCode})"
                                        } else {
                                            "Remote Config: Up to date (Code ${info.latestVersionCode})"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (info.isUpdateAvailable) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        }
                                    )
                                }
                                Text(
                                    text = "Source: ${if (info.fetchedFromServer) "Live Firebase Remote Config" else "Cached / Default Config"} • Min Supported Code: ${info.minSupportedVersionCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (info.isUpdateAvailable) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    }
                                )
                                if (info.isUpdateAvailable && info.releaseNotes.isNotBlank()) {
                                    Text(
                                        text = info.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    is AsyncUiState.Error -> {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = rc.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    else -> Unit
                }

                // Google Play In-App Updates Card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Google Play In-App Updates API",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        when (playUpdateState) {
                            is PlayUpdateUiState.Idle -> {
                                Text(
                                    text = "Tap the button above to query Google Play for direct in-app download & installation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is PlayUpdateUiState.Checking -> {
                                Text(
                                    text = "Querying Google Play Store availability...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is PlayUpdateUiState.UpdateAvailable -> {
                                Text(
                                    text = "Google Play update available (Version Code ${playUpdateState.availableVersionCode}). Choose how to install inside Atelier:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (playUpdateState.isFlexibleAllowed) {
                                        Button(
                                            onClick = onStartFlexiblePlayUpdate,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("start_flexible_play_update_button")
                                        ) {
                                            Text("Flexible")
                                        }
                                    }
                                    if (playUpdateState.isImmediateAllowed) {
                                        OutlinedButton(
                                            onClick = onStartImmediatePlayUpdate,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("start_immediate_play_update_button")
                                        ) {
                                            Text("Immediate")
                                        }
                                    }
                                }
                            }
                            is PlayUpdateUiState.Downloading -> {
                                LinearProgressIndicator(
                                    progress = { playUpdateState.progressFraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val pct = (playUpdateState.progressFraction * 100).toInt()
                                Text(
                                    text = "Downloading in-app update... $pct%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is PlayUpdateUiState.Downloaded -> {
                                Text(
                                    text = "Update downloaded! Restart Atelier to finish installation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Button(
                                    onClick = onCompleteFlexiblePlayUpdate,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dialog_complete_play_update_button")
                                ) {
                                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restart & Install Now")
                                }
                            }
                            is PlayUpdateUiState.Installing -> {
                                Text(
                                    text = "Installing update via Google Play...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is PlayUpdateUiState.UpToDate -> {
                                Text(
                                    text = "Google Play reports no newer release for this build.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is PlayUpdateUiState.NotAvailableFromPlay -> {
                                Text(
                                    text = playUpdateState.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = onOpenPlayStore,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("open_play_store_fallback_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Google Play Listing")
                                }
                            }
                            is PlayUpdateUiState.Error -> {
                                Text(
                                    text = playUpdateState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(
                                    onClick = onOpenPlayStore,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open Google Play Store")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // SECTION 2: GitHub Releases (Optional Sideloaded / Dev Channel)
                Text(
                    text = "GitHub Releases (Sideloaded / Dev Builds)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Repository") },
                    placeholder = { Text("username/repo or https://github.com/...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_input")
                )

                OutlinedButton(
                    onClick = { onCheckGitHubRepo(repoInput) },
                    enabled = gitHubUpdateState !is AsyncUiState.Loading && repoInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_github_updates_button")
                ) {
                    if (gitHubUpdateState is AsyncUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking GitHub...")
                    } else {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check GitHub Releases")
                    }
                }

                when (val gh = gitHubUpdateState) {
                    is AsyncUiState.Error -> {
                        Text(
                            text = gh.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is AsyncUiState.Success -> {
                        val rel = gh.data
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (rel.isUpdateAvailable) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (rel.isUpdateAvailable) {
                                        "GitHub Update Available: ${rel.latestTag}"
                                    } else {
                                        "GitHub Latest (${rel.repoSlug}): ${rel.latestTag} — Up to date"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (rel.isUpdateAvailable) {
                                    Button(
                                        onClick = { onOpenGitHubUrl(rel.apkDownloadUrl ?: rel.releasePageUrl) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("download_github_apk_button")
                                    ) {
                                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (rel.apkDownloadUrl != null) "Download APK in Browser" else "Open GitHub Release")
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
