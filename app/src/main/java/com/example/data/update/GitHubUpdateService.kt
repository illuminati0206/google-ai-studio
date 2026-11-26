package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
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
private const val DEFAULT_GITHUB_REPO = "illuminati0206/google-ai-studio"
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
        val saved = prefs.getString(KEY_GITHUB_REPO, "").orEmpty().trim()
        return saved.ifBlank { DEFAULT_GITHUB_REPO }
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

    /**
     * Checks for updates in 3 automatic tiers:
     * 1. GitHub Releases (/releases/latest or /releases?per_page=1)
     * 2. Versioned APK archive folders pushed to /apk-releases/ on the default branch
     * 3. Version metadata in app/build.gradle.kts on the default branch
     */
    suspend fun checkLatestRelease(rawRepoInput: String): GitHubReleaseCheckResult =
        withContext(Dispatchers.IO) {
            val repoSlug = normalizeAndValidateRepo(rawRepoInput)
                ?: throw IllegalArgumentException(
                    "Invalid GitHub repository format. Enter owner/repo (for example: illuminati0206/google-ai-studio)."
                )

            // Tier 1: GitHub Releases API
            val releaseResult = tryFetchFromReleases(repoSlug)
            if (releaseResult != null) return@withContext releaseResult

            // Tier 2: Versioned apk-releases/ directory in the repo's default branch
            val archiveResult = tryFetchFromApkReleasesFolder(repoSlug)
            if (archiveResult != null) return@withContext archiveResult

            // Tier 3: Read versionName / versionCode directly from app/build.gradle.kts on default branch
            val gradleResult = tryFetchFromGradleFile(repoSlug)
            if (gradleResult != null) return@withContext gradleResult

            throw IllegalStateException(
                "No public GitHub Release or versioned build found in \"$repoSlug\". Make sure the repo is public and changes are pushed."
            )
        }

    private fun tryFetchFromReleases(repoSlug: String): GitHubReleaseCheckResult? {
        val latestResponse = httpGetJson("https://api.github.com/repos/$repoSlug/releases/latest")
        val releaseJson: JSONObject? = when (latestResponse.first) {
            200 -> JSONObject(latestResponse.second)
            404 -> {
                val listResponse = httpGetJson("https://api.github.com/repos/$repoSlug/releases?per_page=1")
                when (listResponse.first) {
                    200 -> {
                        val arr = JSONArray(listResponse.second)
                        if (arr.length() > 0) arr.getJSONObject(0) else null
                    }
                    403 -> throw IllegalStateException(
                        "GitHub API rate limit reached for this network. Please retry in a few minutes."
                    )
                    else -> null
                }
            }
            403 -> throw IllegalStateException(
                "GitHub API rate limit reached for this network. Please retry in a few minutes."
            )
            else -> null
        }

        if (releaseJson == null) return null
        val tagName = releaseJson.optString("tag_name", "").trim()
        if (tagName.isBlank()) return null

        val releaseTitle = releaseJson.optString("name", tagName).ifBlank { tagName }
        val releaseNotes = releaseJson.optString("body", "").trim()
        val publishedDate = releaseJson.optString("published_at", "").take(10)
        val htmlUrl = releaseJson.optString("html_url", "https://github.com/$repoSlug/releases")
        val safeReleasePageUrl = sanitizeGitHubUrl(htmlUrl) ?: "https://github.com/$repoSlug/releases"

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L

        val assets = releaseJson.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val safeDownload = sanitizeGitHubUrl(asset.optString("browser_download_url", ""))
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
        val updateAvailable = isRemoteVersionNewer(tagName, currentVersionName, currentVersionCode)

        return GitHubReleaseCheckResult(
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
     * Checks /apk-releases/ directly on the repo's default branch so pushing from AI Studio
     * automatically publishes updates without needing a manual GitHub Release.
     */
    private fun tryFetchFromApkReleasesFolder(repoSlug: String): GitHubReleaseCheckResult? {
        val dirResp = httpGetJson("https://api.github.com/repos/$repoSlug/contents/apk-releases")
        if (dirResp.first != 200) return null

        val entries = JSONArray(dirResp.second)
        val versionFolders = mutableListOf<Pair<String, String>>()
        for (i in 0 until entries.length()) {
            val obj = entries.optJSONObject(i) ?: continue
            if (obj.optString("type") == "dir") {
                val name = obj.optString("name", "")
                val htmlUrl = obj.optString("html_url", "https://github.com/$repoSlug/tree/main/apk-releases/$name")
                if (name.startsWith("v", ignoreCase = true)) {
                    versionFolders.add(name to htmlUrl)
                }
            }
        }
        if (versionFolders.isEmpty()) return null

        // Pick highest semantic version + build code folder (e.g. v1.8-c9)
        val latestFolder = versionFolders.maxWithOrNull { a, b -> compareVersionTags(a.first, b.first) }
            ?: return null

        val latestTag = latestFolder.first
        val folderPageUrl = sanitizeGitHubUrl(latestFolder.second)
            ?: "https://github.com/$repoSlug/tree/main/apk-releases/$latestTag"

        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = 0L

        val filesResp = httpGetJson("https://api.github.com/repos/$repoSlug/contents/apk-releases/$latestTag")
        if (filesResp.first == 200) {
            val files = JSONArray(filesResp.second)
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val download = sanitizeGitHubUrl(f.optString("download_url", ""))
                    if (download != null) {
                        apkUrl = download
                        apkName = name
                        apkSize = f.optLong("size", 0L)
                        break
                    }
                }
            }
        }

        val currentVersionName = BuildConfig.VERSION_NAME
        val currentVersionCode = BuildConfig.VERSION_CODE
        val updateAvailable = isRemoteVersionNewer(latestTag, currentVersionName, currentVersionCode)

        return GitHubReleaseCheckResult(
            repoSlug = repoSlug,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            latestTag = latestTag,
            releaseTitle = "Atelier $latestTag (GitHub apk-releases)",
            releaseNotes = if (apkUrl != null) {
                "Detected directly from apk-releases/$latestTag on GitHub. Tap Download APK to install."
            } else {
                "New build $latestTag detected in apk-releases/. Open GitHub to download or publish the APK."
            },
            publishedDate = "",
            releasePageUrl = folderPageUrl,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            isUpdateAvailable = updateAvailable
        )
    }

    /**
     * Fallback: parses versionName and versionCode directly from app/build.gradle.kts in the repo.
     */
    private fun tryFetchFromGradleFile(repoSlug: String): GitHubReleaseCheckResult? {
        val resp = httpGetJson("https://api.github.com/repos/$repoSlug/contents/app/build.gradle.kts")
        if (resp.first != 200) return null

        val json = JSONObject(resp.second)
        val encoded = json.optString("content", "").replace("\n", "")
        if (encoded.isBlank()) return null

        val decoded = runCatching {
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null

        val versionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(decoded)?.groupValues?.getOrNull(1)
            ?: return null
        val versionCode = Regex("""versionCode\s*=\s*(\d+)""").find(decoded)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return null

        val latestTag = "v$versionName-c$versionCode"
        val currentVersionName = BuildConfig.VERSION_NAME
        val currentVersionCode = BuildConfig.VERSION_CODE
        val updateAvailable = versionCode > currentVersionCode ||
            isRemoteVersionNewer(latestTag, currentVersionName, currentVersionCode)

        return GitHubReleaseCheckResult(
            repoSlug = repoSlug,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            latestTag = latestTag,
            releaseTitle = "Atelier v$versionName (Build $versionCode on GitHub main)",
            releaseNotes = "Detected from app/build.gradle.kts on GitHub. Create a GitHub Release with the APK attached for one-tap browser download.",
            publishedDate = "",
            releasePageUrl = "https://github.com/$repoSlug/releases",
            apkDownloadUrl = null,
            apkFileName = null,
            apkSizeBytes = 0L,
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
        fun compareVersionTags(tagA: String, tagB: String): Int {
            val cleanA = tagA.trim().removePrefix("v").removePrefix("V")
            val cleanB = tagB.trim().removePrefix("v").removePrefix("V")
            val partsA = extractNumericParts(cleanA.substringBefore("-"))
            val partsB = extractNumericParts(cleanB.substringBefore("-"))

            val maxLen = maxOf(partsA.size, partsB.size)
            for (i in 0 until maxLen) {
                val a = partsA.getOrElse(i) { 0 }
                val b = partsB.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }

            val buildA = extractBuildCode(cleanA) ?: 0
            val buildB = extractBuildCode(cleanB) ?: 0
            return buildA.compareTo(buildB)
        }

        /**
         * Compares tags such as "v1.6", "1.6.1", or "v1.8-c9" against currentVersionName / currentVersionCode.
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

            val remoteBuildCode = extractBuildCode(cleanedTag)
            if (remoteBuildCode != null) {
                return remoteBuildCode > currentVersionCode
            }
            return false
        }

        private fun extractBuildCode(cleanedTag: String): Int? {
            return Regex("(?i)(?:-c|build[_-]?)(\\d+)")
                .find(cleanedTag)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }

        private fun extractNumericParts(version: String): List<Int> {
            return Regex("\\d+")
                .findAll(version)
                .mapNotNull { it.value.toIntOrNull() }
                .toList()
        }
    }
}
