package com.smartdialer.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.smartdialer.app.data.model.AppReleaseInfo
import com.smartdialer.app.data.model.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object OtaUpdateManager {

    private const val GITHUB_REPO_OWNER = "shahin0964"
    private const val GITHUB_REPO_NAME = "Smart-Dialer"
    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    /**
     * Retrieves the installed app's current version name (e.g., "1.0.14").
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Compares latest GitHub release tag with current installed app version.
     * Returns true if latestTag is strictly newer than currentVersion.
     */
    fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        val cleanLatest = latestTag.removePrefix("v").trim()
        val cleanCurrent = currentVersion.removePrefix("v").trim()

        if (cleanLatest.equals(cleanCurrent, ignoreCase = true)) {
            return false
        }

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }

        return false
    }

    /**
     * Checks GitHub for the latest release and returns the UpdateStatus.
     */
    suspend fun checkForUpdates(context: Context): UpdateStatus = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersion(context)
        
        // 1. Try GitHub REST API first (provides rich data, changelog, and asset details)
        var releaseInfo: AppReleaseInfo? = fetchReleaseFromApi()

        // 2. If API fails (e.g. HTTP 403 Rate Limited on cellular CGNAT), fallback to GitHub Web Redirect (Zero rate limits)
        if (releaseInfo == null) {
            releaseInfo = fetchReleaseFromWeb()
        }

        // 3. If web redirect also fails, try Fastly CDN Raw version.json (Zero rate limits)
        if (releaseInfo == null) {
            releaseInfo = fetchReleaseFromRaw()
        }

        if (releaseInfo == null) {
            return@withContext UpdateStatus.Error("আপডেট চেক করা সম্ভব হয়নি। অনুগ্রহ করে ইন্টারনেট সংযোগ পরীক্ষা করুন।")
        }

        val hasUpdate = isNewerVersion(releaseInfo.tagName, currentVersion)
        if (hasUpdate) {
            UpdateStatus.UpdateAvailable(currentVersion, releaseInfo)
        } else {
            UpdateStatus.UpToDate(currentVersion, releaseInfo)
        }
    }

    private fun fetchReleaseFromApi(): AppReleaseInfo? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_LATEST_RELEASE_API)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "SmartDialer-Android")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseString = reader.use { it.readText() }
                val json = JSONObject(responseString)

                val tagName = json.optString("tag_name", "").trim()
                if (tagName.isNotBlank()) {
                    val releaseName = json.optString("name", tagName)
                    val releaseBody = json.optString("body", "No release notes provided.").trim()
                    val publishedAt = json.optString("published_at", "")
                    val htmlUrl = json.optString("html_url", "")

                    var apkUrl = ""
                    var apkName = ""
                    var apkSize = 0L

                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                apkName = name
                                apkSize = asset.optLong("size", 0L)
                                if (name.equals("SmartDialer-latest.apk", ignoreCase = true)) {
                                    break
                                }
                            }
                        }
                    }

                    if (apkUrl.isBlank()) {
                        apkUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tagName/SmartDialer-latest.apk"
                        apkName = "SmartDialer-$tagName.apk"
                    }

                    return AppReleaseInfo(
                        tagName = tagName,
                        name = releaseName,
                        body = releaseBody,
                        apkUrl = apkUrl,
                        apkName = apkName,
                        apkSize = apkSize,
                        publishedAt = publishedAt,
                        htmlUrl = htmlUrl
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun fetchReleaseFromWeb(): AppReleaseInfo? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest")
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            }

            val location = connection.getHeaderField("Location") ?: connection.url.toString()
            if (location.isNotBlank() && location.contains("/releases/tag/")) {
                val tag = location.trimEnd('/').substringAfterLast("/").trim()
                if (tag.isNotBlank()) {
                    val apkUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tag/SmartDialer-latest.apk"
                    return AppReleaseInfo(
                        tagName = tag,
                        name = "Smart Dialer $tag",
                        body = "Official release $tag from GitHub repository.",
                        apkUrl = apkUrl,
                        apkName = "SmartDialer-latest.apk",
                        apkSize = 0L,
                        publishedAt = "",
                        htmlUrl = location
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    private fun fetchReleaseFromRaw(): AppReleaseInfo? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://raw.githubusercontent.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/main/version.json")
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SmartDialer-Android")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseString)
                val tagName = json.optString("tagName", "").trim()
                if (tagName.isNotBlank()) {
                    val apkUrl = json.optString("apkUrl", "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tagName/SmartDialer-latest.apk")
                    return AppReleaseInfo(
                        tagName = tagName,
                        name = json.optString("name", "Smart Dialer $tagName"),
                        body = json.optString("body", "Official release $tagName from GitHub."),
                        apkUrl = apkUrl,
                        apkName = "SmartDialer-latest.apk",
                        apkSize = 0L,
                        publishedAt = "",
                        htmlUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/tag/$tagName"
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        return null
    }

    /**
     * Downloads the APK file with real-time byte and percentage progress.
     */
    suspend fun downloadApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (progressPercent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) {
                updateDir.mkdirs()
            }

            val targetFileName = if (release.apkName.isNotBlank()) release.apkName else "SmartDialer-${release.tagName}.apk"
            val targetFile = File(updateDir, targetFileName)
            val tmpFile = File(updateDir, "$targetFileName.tmp")

            if (tmpFile.exists()) {
                tmpFile.delete()
            }

            var currentUrl = release.apkUrl
            var connection: HttpURLConnection? = null
            var redirects = 0

            // Follow HTTP redirects (GitHub releases redirect to AWS S3 storage)
            while (true) {
                val u = URL(currentUrl)
                connection = (u.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "SmartDialer-Android")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl.isNullOrBlank() || redirects++ > 5) {
                        return@withContext Result.failure(Exception("Too many redirects downloading APK"))
                    }
                    currentUrl = newUrl
                } else if (status in 200..299) {
                    break
                } else {
                    connection.disconnect()
                    return@withContext Result.failure(Exception("Server returned HTTP $status while downloading APK"))
                }
            }

            val conn = connection ?: return@withContext Result.failure(Exception("Failed to open connection"))
            val totalBytes = if (conn.contentLengthLong > 0) conn.contentLengthLong else release.apkSize
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastReportTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastReportTime > 120) {
                            lastReportTime = currentTime
                            val percent = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            onProgress(percent, downloadedBytes, totalBytes)
                        }
                    }
                }
            }
            conn.disconnect()

            if (targetFile.exists()) {
                targetFile.delete()
            }

            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }

            onProgress(100, downloadedBytes, totalBytes)
            Result.success(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Prompts the system PackageInstaller to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+, check if installing from unknown apps is permitted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
