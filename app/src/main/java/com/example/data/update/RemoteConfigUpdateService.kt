package com.example.data.update

import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

private const val TAG = "RemoteConfigUpdate"

data class RemoteConfigUpdateInfo(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val minSupportedVersionCode: Int,
    val latestVersionName: String,
    val releaseNotes: String,
    val isUpdateAvailable: Boolean,
    val isForceUpdate: Boolean,
    val fetchedFromServer: Boolean,
    val lastFetchTimeMillis: Long
)

class RemoteConfigUpdateService {

    companion object {
        const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        const val KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
        const val KEY_LATEST_VERSION_NAME = "latest_version_name"
        const val KEY_UPDATE_RELEASE_NOTES = "update_release_notes"
        const val KEY_FORCE_UPDATE_ENABLED = "force_update_enabled"
    }

    private val remoteConfig: FirebaseRemoteConfig by lazy {
        Firebase.remoteConfig.apply {
            val settings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3600L
                fetchTimeoutInSeconds = 15L
            }
            setConfigSettingsAsync(settings)
            setDefaultsAsync(R.xml.remote_config_defaults)
        }
    }

    /**
     * Fetches and activates Firebase Remote Config parameters, comparing `latest_version_code`
     * against the installed `BuildConfig.VERSION_CODE`.
     */
    suspend fun checkLatestVersion(forceFetch: Boolean = false): RemoteConfigUpdateInfo {
        var fetchedFromServer = false
        try {
            if (forceFetch) {
                remoteConfig.fetch(0L).await()
                remoteConfig.activate().await()
            } else {
                remoteConfig.fetchAndActivate().await()
            }
            fetchedFromServer = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config network fetch fell back to cached/default values", e)
        }

        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME

        val latestCode = remoteConfig.getLong(KEY_LATEST_VERSION_CODE).toInt()
            .takeIf { it > 0 } ?: currentCode
        val minSupportedCode = remoteConfig.getLong(KEY_MIN_SUPPORTED_VERSION_CODE).toInt()
            .coerceAtLeast(1)
        val latestName = remoteConfig.getString(KEY_LATEST_VERSION_NAME)
            .ifBlank { currentName }
        val releaseNotes = remoteConfig.getString(KEY_UPDATE_RELEASE_NOTES).trim()
        val forceFlag = remoteConfig.getBoolean(KEY_FORCE_UPDATE_ENABLED)

        val updateAvailable = latestCode > currentCode
        val forceRequired = updateAvailable && (forceFlag || currentCode < minSupportedCode)

        return RemoteConfigUpdateInfo(
            currentVersionCode = currentCode,
            currentVersionName = currentName,
            latestVersionCode = latestCode,
            minSupportedVersionCode = minSupportedCode,
            latestVersionName = latestName,
            releaseNotes = releaseNotes,
            isUpdateAvailable = updateAvailable,
            isForceUpdate = forceRequired,
            fetchedFromServer = fetchedFromServer,
            lastFetchTimeMillis = remoteConfig.info.fetchTimeMillis
        )
    }
}
