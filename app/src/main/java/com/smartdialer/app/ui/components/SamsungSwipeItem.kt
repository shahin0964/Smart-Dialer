package com.smartdialer.app.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartdialer.app.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Samsung-style swipeable list item container:
 * - Swipe Right (Left to Right) -> Green Call glow background & action
 * - Swipe Left (Right to Left) -> Cyan/Blue Message glow background & action
 *
 * When swipe threshold is reached, releasing the item glides all the way across
 * with a luminous glow sweep before triggering the action, then smoothly returns.
 */
@Composable
fun SamsungSwipeItem(
    modifier: Modifier = Modifier,
    onSwipeRight: () -> Unit, // Call
    onSwipeLeft: () -> Unit,  // SMS
    content: @Composable () -> Unit
) {
    val theme = LocalAppTheme.current
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val view = LocalView.current
    var hasHapticFired by remember { mutableStateOf(false) }
    var itemWidthPx by remember { mutableFloatStateOf(1080f) }

    val density = LocalDensity.current
    val minThresholdPx = with(density) { 90.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { size ->
                if (size.width > 0) {
                    itemWidthPx = size.width.toFloat()
                }
            }
    ) {
        val currentOffset = offsetX.value
        val triggerThreshold = (itemWidthPx * 0.32f).coerceAtLeast(minThresholdPx)

        if (currentOffset != 0f) {
            val isRight = currentOffset > 0
            val progress = (abs(currentOffset) / triggerThreshold).coerceIn(0f, 1.6f)
            val isTriggered = abs(currentOffset) >= triggerThreshold

            // Luminous glowing gradients
            val backgroundBrush = if (isRight) {
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF047857), // Deep emerald
                        Color(0xFF10B981), // Vibrant green
                        if (isTriggered) Color(0xFF6EE7B7) else Color(0xFF34D399) // Radiant glowing edge
                    )
                )
            } else {
                Brush.horizontalGradient(
                    colors = listOf(
                        if (isTriggered) Color(0xFF38BDF8) else Color(0xFF06B6D4), // Cyan glow
                        Color(0xFF2563EB), // Vibrant blue
                        Color(0xFF1D4ED8)  // Deep royal blue
                    )
                )
            }

            val icon = if (isRight) Icons.Default.Call else Icons.Default.Chat
            val label = if (isRight) "Call" else "Message"
            val alignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(backgroundBrush)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                // Outer glowing aura behind icon
                val iconScale = 0.85f + (0.35f * progress.coerceAtMost(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isRight) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (isTriggered) 0.30f else 0.16f))
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale)
                            )
                        }
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
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (isTriggered) 0.30f else 0.16f))
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(iconScale)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(12.dp))
                .background(theme.background)
                .pointerInput(itemWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasHapticFired = false
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                val finalOffset = offsetX.value
                                val triggerThreshold = (itemWidthPx * 0.32f).coerceAtLeast(minThresholdPx)

                                if (finalOffset >= triggerThreshold) {
                                    // Smoothly glide all the way across to the right edge with a glow sweep!
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    offsetX.animateTo(
                                        targetValue = itemWidthPx,
                                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                    )
                                    onSwipeRight()
                                    delay(160)
                                    // Reset smoothly back to center
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else if (finalOffset <= -triggerThreshold) {
                                    // Smoothly glide all the way across to the left edge with a glow sweep!
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    offsetX.animateTo(
                                        targetValue = -itemWidthPx,
                                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                    )
                                    onSwipeLeft()
                                    delay(160)
                                    // Reset smoothly back to center
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else {
                                    // Did not pass threshold: spring back to resting position
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val triggerThreshold = (itemWidthPx * 0.32f).coerceAtLeast(minThresholdPx)
                                // Smooth drag bounded by full item width
                                val newTarget = (offsetX.value + dragAmount).coerceIn(-itemWidthPx, itemWidthPx)
                                offsetX.snapTo(newTarget)

                                if (abs(newTarget) >= triggerThreshold && !hasHapticFired) {
                                    hasHapticFired = true
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                } else if (abs(newTarget) < triggerThreshold) {
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
