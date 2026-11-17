package com.smartdialer.app.ui.dialogs

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smartdialer.app.data.repository.AppSettingsPreferences
import com.smartdialer.app.ui.theme.PurplePrimary
import com.smartdialer.app.ui.theme.PurpleUltraLight
import com.smartdialer.app.ui.theme.TextDark
import com.smartdialer.app.ui.theme.TextMuted

data class ThemeItem(
    val id: String,
    val name: String,
    val description: String,
    val gradientColors: List<Color>,
    val accentColor: Color
)

@Composable
fun ThemeSelectionDialog(
    appSettings: AppSettingsPreferences,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val view = LocalView.current
    var selectedTheme by remember { mutableStateOf(appSettings.callerTheme) }

    val themes = listOf(
        ThemeItem(
            id = "CLASSIC_DARK",
            name = "Classic Dark (Default)",
            description = "Deep navy gradient with sleek modern controls",
            gradientColors = listOf(Color(0xFF0B1021), Color(0xFF111827), Color(0xFF1E293B)),
            accentColor = Color(0xFF6366F1)
        ),
        ThemeItem(
            id = "IOS_GLASS",
            name = "iOS Glassmorphism",
            description = "Frosted translucent glass with Apple blur aesthetic",
            gradientColors = listOf(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF000000)),
            accentColor = Color(0xFF0A84FF)
        ),
        ThemeItem(
            id = "SAMSUNG_ONE_UI",
            name = "Samsung One UI",
            description = "Midnight slate with clean circular One UI controls",
            gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0B1120)),
            accentColor = Color(0xFF38BDF8)
        ),
        ThemeItem(
            id = "CYBERPUNK_AMOLED",
            name = "Cyberpunk AMOLED",
            description = "Pitch black OLED with neon cyan & magenta glow",
            gradientColors = listOf(Color(0xFF000000), Color(0xFF070714), Color(0xFF120324)),
            accentColor = Color(0xFF00F0FF)
        ),
        ThemeItem(
            id = "EMERALD_AURORA",
            name = "Emerald Aurora",
            description = "Deep forest teal & emerald gradient with luxury sheen",
            gradientColors = listOf(Color(0xFF042F2E), Color(0xFF064E3B), Color(0xFF021B1A)),
            accentColor = Color(0xFF10B981)
        ),
        ThemeItem(
            id = "SUNSET_VELVET",
            name = "Sunset Velvet",
            description = "Warm royal purple & ruby crimson gradient",
            gradientColors = listOf(Color(0xFF4C0519), Color(0xFF3B0764), Color(0xFF180A28)),
            accentColor = Color(0xFFE11D48)
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(PurpleUltraLight, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Themes",
                        tint = PurplePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Themes & Caller Screen",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Text(
                    text = "Choose your preferred calling screen & dialer skin",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                themes.forEach { theme ->
                    val isSelected = selectedTheme == theme.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) PurplePrimary else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                selectedTheme = theme.id
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFFBF8FF) else Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mini Live Preview swatch
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(theme.gradientColors))
                                    .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(theme.accentColor)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = TextDark
                                )
                                Text(
                                    text = theme.description,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(PurplePrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Text("Cancel", color = TextDark, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            appSettings.callerTheme = selectedTheme
                            onSaved()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                    ) {
                        Text("Apply Theme", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
