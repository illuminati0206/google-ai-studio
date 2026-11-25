package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "GitHubUpdateService"
private const val PREFS_NAME = "atelier_github_update_prefs"
private const val KEY_GITHUB_REPO = "github_repo_slug"
private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

data class GitHubReleaseCheckResult(
    val repoSlug: String,
    val currentVersionName: String,
    val currentVersionCode: Int,
    val latestTag: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedDate: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long,
    val isUpdateAvailable: Boolean
)

class GitHubUpdateService {

    fun loadSavedRepo(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GITHUB_REPO, "").orEmpty().trim()
    }

    fun saveRepo(context: Context, repoSlug: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GITHUB_REPO, repoSlug.trim()).apply()
    }

    /**
     * Normalizes user input such as "username/repo", "https://github.com/username/repo",
     * or "https://github.com/username/repo/releases" into strict "owner/repo" format.
     */
    fun normalizeAndValidateRepo(rawInput: String): String? {
        val trimmed = rawInput.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removePrefix("/")
            .removeSuffix(".git")
            .trimEnd('/')

        val parts = trimmed.split("/").filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val slug = "${parts[0]}/${parts[1]}"
        return if (REPO_SLUG_REGEX.matches(slug)) slug else null
    }

    suspend fun checkLatestRelease(rawRepoInput: String): GitHubReleaseCheckResult =
        withContext(Dispatchers.IO) {
            val repoSlug = normalizeAndValidateRepo(rawRepoInput)
                ?: throw IllegalArgumentException(
                    "Invalid GitHub repository format. Enter owner/repo (for example: yourname/atelier-app)."
                )

            val latestEndpoint = "https://api.github.com/repos/$repoSlug/releases/latest"
            val latestResponse = httpGetJson(latestEndpoint)

            val releaseJson: JSONObject = when (latestResponse.first) {
                200 -> JSONObject(latestResponse.second)
                404 -> {
                    // Fallback to /releases?per_page=1 in case only pre-releases exist
                    val listResponse = httpGetJson("https://api.github.com/repos/$repoSlug/releases?per_page=1")
                    if (listResponse.first == 200) {
                        val arr = JSONArray(listResponse.second)
                        if (arr.length() > 0) {
                            arr.getJSONObject(0)
                        } else {
                            throw IllegalStateException(
                                "No GitHub Releases found in $repoSlug yet. Publish a Release on GitHub with your new APK to enable update alerts."
                            )
                        }
                    } else if (listResponse.first == 404) {
                        throw IllegalStateException(
                            "Repository \"$repoSlug\" was not found or is private. Make sure the repo name is correct and public."
                        )
                    } else {
                        throw IllegalStateException(
                            "GitHub API returned HTTP ${listResponse.first}. Please try again shortly."
                        )
                    }
                }
                403 -> throw IllegalStateException(
                    "GitHub API rate limit reached for this network. Please retry in a few minutes."
                )
                else -> throw IllegalStateException(
                    "GitHub API returned HTTP ${latestResponse.first}. Please verify your internet connection and repo name."
                )
            }

            val tagName = releaseJson.optString("tag_name", "").trim()
            if (tagName.isBlank()) {
                throw IllegalStateException("Latest GitHub Release is missing a version tag (e.g. v1.6).")
            }

            val releaseTitle = releaseJson.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = releaseJson.optString("body", "").trim()
            val publishedDate = releaseJson.optString("published_at", "").take(10)
            val htmlUrl = releaseJson.optString("html_url", "https://github.com/$repoSlug/releases")
            val safeReleasePageUrl = sanitizeGitHubUrl(htmlUrl)
                ?: "https://github.com/$repoSlug/releases"

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L

            val assets = releaseJson.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val downloadUrl = asset.optString("browser_download_url", "")
                        val safeDownload = sanitizeGitHubUrl(downloadUrl)
                        if (safeDownload != null) {
                            apkUrl = safeDownload
                            apkName = name
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }
            }

            val currentVersionName = BuildConfig.VERSION_NAME
            val currentVersionCode = BuildConfig.VERSION_CODE
            val updateAvailable = isRemoteVersionNewer(
                remoteTag = tagName,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode
            )

            GitHubReleaseCheckResult(
                repoSlug = repoSlug,
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                latestTag = tagName,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                publishedDate = publishedDate,
                releasePageUrl = safeReleasePageUrl,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                isUpdateAvailable = updateAvailable
            )
        }

    /**
     * Opens a verified GitHub HTTPS URL in the user's external browser (zero restricted permissions,
     * Play-policy compliant).
     */
    fun openVerifiedGitHubUrl(context: Context, url: String): Boolean {
        val safeUrl = sanitizeGitHubUrl(url) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open GitHub release URL", e)
            false
        }
    }

    private fun sanitizeGitHubUrl(url: String): String? {
        val trimmed = url.trim()
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        val host = parsed.host?.lowercase() ?: return null
        if (scheme != "https") return null
        val allowedHost = host == "github.com" ||
            host == "www.github.com" ||
            host.endsWith(".githubusercontent.com")
        return if (allowedHost) trimmed else null
    }

    private fun httpGetJson(endpoint: String): Pair<Int, String> {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Atelier-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            } else {
                ""
            }
            code to body
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /**
         * Compares tags such as "v1.6", "1.6.1", or "v1.6-c7" against currentVersionName / currentVersionCode.
         */
        fun isRemoteVersionNewer(
            remoteTag: String,
            currentVersionName: String,
            currentVersionCode: Int
        ): Boolean {
            val cleanedTag = remoteTag.trim().removePrefix("v").removePrefix("V")
            val versionPart = cleanedTag.substringBefore("-")
            val remoteParts = extractNumericParts(versionPart)
            val currentParts = extractNumericParts(currentVersionName.trim().removePrefix("v").removePrefix("V"))

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r != c) return r > c
            }

            // Same semantic version — check optional "-c<buildCode>" suffix (matching apk-releases/v1.5-c6)
            val buildMatch = Regex("(?i)(?:-c|build[_-]?)(\\d+)").find(cleanedTag)
            val remoteBuildCode = buildMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (remoteBuildCode != null) {
                return remoteBuildCode > currentVersionCode
            }
            return false
        }

        private fun extractNumericParts(version: String): List<Int> {
            return Regex("\\d+")
                .findAll(version)
                .mapNotNull { it.value.toIntOrNull() }
                .toList()
        }
    }
}
