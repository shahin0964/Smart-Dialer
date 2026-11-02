package com.smartdialer.app.data.model

import java.io.File

data class AppReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val publishedAt: String,
    val htmlUrl: String
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpToDate(
        val currentVersion: String,
        val latestRelease: AppReleaseInfo? = null
    ) : UpdateStatus()
    data class UpdateAvailable(
        val currentVersion: String,
        val latestRelease: AppReleaseInfo
    ) : UpdateStatus()
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val release: AppReleaseInfo
    ) : UpdateStatus()
    data class Downloaded(
        val apkFile: File,
        val release: AppReleaseInfo
    ) : UpdateStatus()
    data class Error(
        val message: String
    ) : UpdateStatus()
}
