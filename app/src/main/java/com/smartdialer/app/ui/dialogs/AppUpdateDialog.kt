package com.smartdialer.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartdialer.app.data.model.AppReleaseInfo
import com.smartdialer.app.data.model.UpdateStatus
import com.smartdialer.app.data.repository.OtaUpdateManager
import com.smartdialer.app.ui.theme.CallIncomingGreen
import com.smartdialer.app.ui.theme.CallMissedRed
import com.smartdialer.app.ui.theme.DividerColor
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextLightGray
import com.smartdialer.app.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Checking) }

    fun runUpdateCheck() {
        updateStatus = UpdateStatus.Checking
        scope.launch {
            updateStatus = OtaUpdateManager.checkForUpdates(context)
        }
    }

    LaunchedEffect(Unit) {
        runUpdateCheck()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PurpleUltraLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = PurplePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "App OTA Update",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "GitHub Auto-Update",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on UpdateStatus
                when (val status = updateStatus) {
                    is UpdateStatus.Checking -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 30.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = PurplePrimary,
                                strokeWidth = 3.5.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "গিটহাব থেকে নতুন আপডেট চেক করা হচ্ছে...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark
                            )
                            Text(
                                text = "Checking latest release from GitHub repository",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    is UpdateStatus.UpToDate -> {
                        UpToDateView(
                            currentVersion = status.currentVersion,
                            release = status.latestRelease,
                            onCheckAgain = { runUpdateCheck() },
                            onClose = onDismiss
                        )
                    }

                    is UpdateStatus.UpdateAvailable -> {
                        UpdateAvailableView(
                            currentVersion = status.currentVersion,
                            release = status.latestRelease,
                            onStartDownload = {
                                scope.launch {
                                    updateStatus = UpdateStatus.Downloading(
                                        progressPercent = 0,
                                        downloadedBytes = 0L,
                                        totalBytes = status.latestRelease.apkSize,
                                        release = status.latestRelease
                                    )
                                    val result = OtaUpdateManager.downloadApk(
                                        context = context,
                                        release = status.latestRelease
                                    ) { percent, downloaded, total ->
                                        updateStatus = UpdateStatus.Downloading(
                                            progressPercent = percent,
                                            downloadedBytes = downloaded,
                                            totalBytes = total,
                                            release = status.latestRelease
                                        )
                                    }
                                    result.fold(
                                        onSuccess = { file ->
                                            updateStatus = UpdateStatus.Downloaded(file, status.latestRelease)
                                        },
                                        onFailure = { error ->
                                            updateStatus = UpdateStatus.Error(
                                                error.localizedMessage ?: "Failed to download update"
                                            )
                                        }
                                    )
                                }
                            }
                        )
                    }

                    is UpdateStatus.Downloading -> {
                        DownloadingView(
                            progressPercent = status.progressPercent,
                            downloadedBytes = status.downloadedBytes,
                            totalBytes = status.totalBytes,
                            release = status.release
                        )
                    }

                    is UpdateStatus.Downloaded -> {
                        DownloadedView(
                            apkFile = status.apkFile,
                            release = status.release,
                            onInstall = {
                                OtaUpdateManager.installApk(context, status.apkFile)
                            }
                        )
                    }

                    is UpdateStatus.Error -> {
                        ErrorView(
                            message = status.message,
                            onRetry = { runUpdateCheck() }
                        )
                    }

                    UpdateStatus.Idle -> {
                        // Not reachable in this flow
                    }
                }
            }
        }
    }
}

@Composable
private fun UpToDateView(
    currentVersion: String,
    release: AppReleaseInfo?,
    onCheckAgain: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCFCE7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = CallIncomingGreen,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "অ্যাপটি আপ-টু-ডেট আছে",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Text(
            text = "Smart Dialer is on the latest version ($currentVersion)",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Release notes card
        if (release != null && release.body.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "রিলিজ নোট (Release Notes):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PurpleUltraLight
                        ) {
                            Text(
                                text = release.tagName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurplePrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    ReleaseNotesContent(body = release.body)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCheckAgain,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PurplePrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("পুনরায় চেক", fontSize = 13.sp, color = PurplePrimary)
            }

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("ঠিক আছে", fontSize = 13.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun UpdateAvailableView(
    currentVersion: String,
    release: AppReleaseInfo,
    onStartDownload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEDE9FE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = PurplePrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "নতুন আপডেট পাওয়া গেছে!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = "Current: $currentVersion ➔ New: ${release.tagName}",
                    fontSize = 12.sp,
                    color = PurplePrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Release notes card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF8FAFC),
            border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "নতুন যা যুক্ত ও ফিক্স হয়েছে:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )

                    val sizeMb = if (release.apkSize > 0) {
                        String.format("%.1f MB", release.apkSize / (1024f * 1024f))
                    } else {
                        "APK"
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = sizeMb,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CallIncomingGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                ReleaseNotesContent(body = release.body)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartDownload,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ডাউনলোড ও আপডেট করুন",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DownloadingView(
    progressPercent: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    release: AppReleaseInfo
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(PurpleUltraLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = PurplePrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "আপডেট ডাউনলোড হচ্ছে...",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        val downloadedMb = String.format("%.1f", downloadedBytes / (1024f * 1024f))
        val totalMb = if (totalBytes > 0) String.format("%.1f", totalBytes / (1024f * 1024f)) else "?"

        Text(
            text = "$downloadedMb MB / $totalMb MB ($progressPercent%)",
            fontSize = 12.sp,
            color = TextMuted,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(14.dp))

        LinearProgressIndicator(
            progress = (progressPercent / 100f).coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = PurplePrimary,
            trackColor = Color(0xFFE2E8F0)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "ডাউনলোড শেষ হওয়া পর্যন্ত অ্যাপ বন্ধ করবেন না",
            fontSize = 11.sp,
            color = TextLightGray
        )
    }
}

@Composable
private fun DownloadedView(
    apkFile: File,
    release: AppReleaseInfo,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCFCE7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = CallIncomingGreen,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "ডাউনলোড সম্পন্ন হয়েছে!",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Text(
            text = "${release.tagName} ইনস্টল করার জন্য প্রস্তুত",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onInstall,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CallIncomingGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "এখন ইনস্টল করুন (Install Now)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0xFFFEE2E2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = CallMissedRed,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "আপডেট চেক ব্যর্থ হয়েছে",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = message,
            fontSize = 12.sp,
            color = TextMuted,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("পুনরায় চেষ্টা করুন", fontSize = 13.sp, color = Color.White)
        }
    }
}

@Composable
private fun ReleaseNotesContent(body: String) {
    val cleanBody = body.replace("\r\n", "\n").trim()
    val lines = cleanBody.split("\n")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState())
    ) {
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            if (line.startsWith("---")) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = DividerColor
                )
            } else if (line.startsWith("#")) {
                val headerText = line.trimStart('#').trim()
                Text(
                    text = headerText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            } else if (line.startsWith("*") || line.startsWith("-")) {
                val bullet = line.substring(1).trim()
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurplePrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = bullet,
                        fontSize = 11.sp,
                        color = TextDark,
                        lineHeight = 15.sp
                    )
                }
            } else {
                Text(
                    text = line,
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}
