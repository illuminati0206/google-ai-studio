package com.example.data.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PlayInAppUpdate"

sealed interface PlayUpdateUiState {
    data object Idle : PlayUpdateUiState
    data object Checking : PlayUpdateUiState
    data class UpdateAvailable(
        val availableVersionCode: Int,
        val isImmediateAllowed: Boolean,
        val isFlexibleAllowed: Boolean,
        val stalenessDays: Int?
    ) : PlayUpdateUiState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val progressFraction: Float
    ) : PlayUpdateUiState
    data object Downloaded : PlayUpdateUiState
    data object Installing : PlayUpdateUiState
    data object UpToDate : PlayUpdateUiState
    data class NotAvailableFromPlay(val reason: String) : PlayUpdateUiState
    data class Error(val message: String) : PlayUpdateUiState
}

/**
 * Coordinates Google Play In-App Updates (both FLEXIBLE and IMMEDIATE flows),
 * download progress observation, flexible completion restart, and stalled-update recovery.
 */
class PlayInAppUpdateController(
    private val appContext: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)

    private val _state = MutableStateFlow<PlayUpdateUiState>(PlayUpdateUiState.Idle)
    val state: StateFlow<PlayUpdateUiState> = _state.asStateFlow()

    @Volatile
    private var latestAppUpdateInfo: AppUpdateInfo? = null

    private var listenerRegistered = false

    private val installStateListener = InstallStateUpdatedListener { installState ->
        when (installState.installStatus()) {
            InstallStatus.PENDING -> {
                _state.value = PlayUpdateUiState.Downloading(
                    bytesDownloaded = 0L,
                    totalBytesToDownload = 0L,
                    progressFraction = 0f
                )
            }
            InstallStatus.DOWNLOADING -> {
                val downloaded = installState.bytesDownloaded()
                val total = installState.totalBytesToDownload()
                val fraction = if (total > 0L) {
                    (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                _state.value = PlayUpdateUiState.Downloading(
                    bytesDownloaded = downloaded,
                    totalBytesToDownload = total,
                    progressFraction = fraction
                )
            }
            InstallStatus.DOWNLOADED -> {
                _state.value = PlayUpdateUiState.Downloaded
            }
            InstallStatus.INSTALLING -> {
                _state.value = PlayUpdateUiState.Installing
            }
            InstallStatus.INSTALLED -> {
                _state.value = PlayUpdateUiState.UpToDate
            }
            InstallStatus.FAILED -> {
                _state.value = PlayUpdateUiState.Error(
                    "Google Play update failed (code ${installState.installErrorCode()}). Tap Retry or update from Play Store."
                )
            }
            InstallStatus.CANCELED -> {
                _state.value = PlayUpdateUiState.Idle
            }
            else -> Unit
        }
    }

    fun registerListener() {
        if (!listenerRegistered) {
            appUpdateManager.registerListener(installStateListener)
            listenerRegistered = true
        }
    }

    fun unregisterListener() {
        if (listenerRegistered) {
            appUpdateManager.unregisterListener(installStateListener)
            listenerRegistered = false
        }
    }

    /**
     * Queries Google Play Store for available updates and reflects live availability/install status.
     */
    fun checkPlayUpdateAvailability() {
        _state.value = PlayUpdateUiState.Checking
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                latestAppUpdateInfo = info
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        _state.value = PlayUpdateUiState.Downloaded
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        _state.value = PlayUpdateUiState.UpdateAvailable(
                            availableVersionCode = info.availableVersionCode(),
                            isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                            isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                            stalenessDays = info.clientVersionStalenessDays()
                        )
                    }
                    else -> {
                        _state.value = PlayUpdateUiState.UpToDate
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Play In-App Update check unavailable", error)
                val installCode = (error as? InstallException)?.errorCode
                when (installCode) {
                    InstallErrorCode.ERROR_APP_NOT_OWNED,
                    InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
                    InstallErrorCode.ERROR_API_NOT_AVAILABLE,
                    InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED -> {
                        _state.value = PlayUpdateUiState.NotAvailableFromPlay(
                            "Google Play In-App Updates activate automatically when installed via Google Play or Play Internal App Sharing."
                        )
                    }
                    else -> {
                        _state.value = PlayUpdateUiState.NotAvailableFromPlay(
                            error.localizedMessage
                                ?: "Google Play update service is unavailable on this sideloaded build."
                        )
                    }
                }
            }
    }

    /**
     * Starts an in-app update directly inside the Atelier interface using Google Play Core's
     * ActivityResultLauncher flow (FLEXIBLE or IMMEDIATE).
     */
    fun startInAppUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        preferImmediate: Boolean,
        onFallbackOpenStore: () -> Unit
    ) {
        val cachedInfo = latestAppUpdateInfo
        if (cachedInfo != null &&
            (cachedInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                cachedInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS)
        ) {
            launchFlowForInfo(cachedInfo, launcher, preferImmediate, onFallbackOpenStore)
            return
        }

        _state.value = PlayUpdateUiState.Checking
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                latestAppUpdateInfo = info
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _state.value = PlayUpdateUiState.Downloaded
                } else if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    launchFlowForInfo(info, launcher, preferImmediate, onFallbackOpenStore)
                } else {
                    _state.value = PlayUpdateUiState.UpToDate
                    onFallbackOpenStore()
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to start Play in-app update; opening Play Store fallback", error)
                _state.value = PlayUpdateUiState.NotAvailableFromPlay(
                    "Direct Play In-App Update requires a Google Play installation. Opening Play Store page instead."
                )
                onFallbackOpenStore()
            }
    }

    private fun launchFlowForInfo(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        preferImmediate: Boolean,
        onFallbackOpenStore: () -> Unit
    ) {
        val chosenType = when {
            preferImmediate && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
            !preferImmediate && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            else -> null
        }

        if (chosenType == null) {
            onFallbackOpenStore()
            return
        }

        try {
            val options = AppUpdateOptions.newBuilder(chosenType).build()
            appUpdateManager.startUpdateFlowForResult(info, launcher, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Google Play update flow", e)
            _state.value = PlayUpdateUiState.Error(
                e.localizedMessage ?: "Could not launch Google Play in-app update."
            )
        }
    }

    /**
     * Checks for stalled IMMEDIATE updates or already-DOWNLOADED flexible updates on Activity resume,
     * per official Google Play In-App Updates guidelines.
     */
    fun handleResume(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                latestAppUpdateInfo = info
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        _state.value = PlayUpdateUiState.Downloaded
                    }
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                info,
                                launcher,
                                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to resume stalled immediate update", e)
                        }
                    }
                }
            }
            .addOnFailureListener {
                // Silent on resume for sideloaded/emulator debug builds
            }
    }

    fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
            .addOnFailureListener { e ->
                Log.e(TAG, "completeUpdate() failed", e)
                _state.value = PlayUpdateUiState.Error(
                    e.localizedMessage ?: "Failed to finalize update installation."
                )
            }
    }

    fun onActivityResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> {
                // Play flow accepted; InstallStateUpdatedListener will track progress
            }
            Activity.RESULT_CANCELED -> {
                Log.i(TAG, "User declined or canceled Google Play in-app update")
                val info = latestAppUpdateInfo
                if (info != null && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    _state.value = PlayUpdateUiState.UpdateAvailable(
                        availableVersionCode = info.availableVersionCode(),
                        isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                        isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                        stalenessDays = info.clientVersionStalenessDays()
                    )
                }
            }
            ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                _state.value = PlayUpdateUiState.Error(
                    "Google Play in-app update failed. Please retry or update via Google Play Store."
                )
            }
        }
    }

    fun openPlayStoreListing(context: Context): Boolean {
        val pkg = context.packageName
        return try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Could not open Play Store listing", e)
                false
            }
        }
    }
}
