package com.smartdialer.app.ui.dialogs

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdialer.app.telecom.CallRecorderManager
import com.smartdialer.app.telecom.CallRecordingItem
import com.smartdialer.app.ui.theme.IpCallPurple
import com.smartdialer.app.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Embedded In-App Recordings List + MediaPlayer controller for CallRecordingDialog.
 */
@Composable
fun CallRecordingsPlayerList(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current

    var recordings by remember { mutableStateOf(CallRecorderManager.getRecordings(context)) }
    var playingFilePath by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progressRatio by remember { mutableFloatStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        playingFilePath = null
        isPlaying = false
        progressRatio = 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            releasePlayer()
        }
    }

    LaunchedEffect(isPlaying, playingFilePath) {
        while (isPlaying && mediaPlayer != null) {
            try {
                val mp = mediaPlayer ?: break
                val dur = mp.duration.coerceAtLeast(1)
                progressRatio = (mp.currentPosition.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } catch (_: Exception) {
            }
            delay(250)
        }
    }

    fun togglePlay(item: CallRecordingItem) {
        if (playingFilePath == item.file.absolutePath) {
            val mp = mediaPlayer
            if (mp != null) {
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                } else {
                    mp.start()
                    isPlaying = true
                }
                return
            }
        }

        releasePlayer()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(item.file.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlaying = false
                    progressRatio = 0f
                }
                start()
            }
            mediaPlayer = mp
            playingFilePath = item.file.absolutePath
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
            releasePlayer()
        }
    }

    if (recordings.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No call recordings yet.\nTap 'Record Call' during an IP or cellular call.",
                fontSize = 12.sp,
                color = theme.textMuted
            )
        }
    } else {
        val dateFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recordings, key = { it.file.absolutePath }) { item ->
                val isCurrent = playingFilePath == item.file.absolutePath
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, if (isCurrent) theme.primary else theme.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    onClick = { togglePlay(item) },
                                    shape = CircleShape,
                                    color = if (isCurrent && isPlaying) theme.primary else theme.primaryUltraLight,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = if (isCurrent && isPlaying) Color.White else theme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.displayTitle,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.isIpCall) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = IpCallPurple.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "IP",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = IpCallPurple,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${item.durationFormatted} • ${item.sizeFormatted} • ${dateFmt.format(Date(item.timestampMillis))}",
                                        fontSize = 10.sp,
                                        color = theme.textMuted
                                    )
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { CallRecorderManager.shareRecording(context, item.file) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = theme.primary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (isCurrent) releasePlayer()
                                        item.file.delete()
                                        recordings = CallRecorderManager.getRecordings(context)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (isCurrent) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = progressRatio,
                                onValueChange = { newVal ->
                                    progressRatio = newVal
                                    mediaPlayer?.let { mp ->
                                        val targetMs = (mp.duration * newVal).toInt()
                                        mp.seekTo(targetMs)
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.primary,
                                    activeTrackColor = theme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
