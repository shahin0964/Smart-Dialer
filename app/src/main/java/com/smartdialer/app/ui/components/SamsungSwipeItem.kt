package com.smartdialer.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Samsung-style swipeable list item container:
 * - Swipe Right (Left to Right) -> Green Call background & action
 * - Swipe Left (Right to Left) -> Blue Message background & action
 * Automatically snaps back with smooth spring animation after triggering.
 */
@Composable
fun SamsungSwipeItem(
    modifier: Modifier = Modifier,
    onSwipeRight: () -> Unit, // Call
    onSwipeLeft: () -> Unit,  // SMS
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val view = LocalView.current
    var hasHapticFired by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val maxDragPx = with(density) { 150.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    ) {
        val currentOffset = offsetX.value
        if (currentOffset != 0f) {
            val isRight = currentOffset > 0
            val bgColor = if (isRight) Color(0xFF2ECC71) else Color(0xFF3498DB)
            val icon = if (isRight) Icons.Default.Call else Icons.Default.Chat
            val label = if (isRight) "Call" else "Message"
            val alignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRight) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    } else {
                        Text(
                            text = label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(Color.White)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasHapticFired = false
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val finalOffset = offsetX.value
                                if (finalOffset > thresholdPx) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onSwipeRight()
                                } else if (finalOffset < -thresholdPx) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onSwipeLeft()
                                }
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newTarget = (offsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                                offsetX.snapTo(newTarget)

                                if (kotlin.math.abs(newTarget) >= thresholdPx && !hasHapticFired) {
                                    hasHapticFired = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else if (kotlin.math.abs(newTarget) < thresholdPx) {
                                    hasHapticFired = false
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
