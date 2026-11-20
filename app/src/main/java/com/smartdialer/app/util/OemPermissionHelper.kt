package com.smartdialer.app.util

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Helper to detect aggressive OEM battery/process killers (Xiaomi/MIUI/HyperOS, Oppo/ColorOS,
 * Realme/RealmeUI, Vivo/OriginOS) and guide users to enable Autostart, Background Popups,
 * and Full-Screen Intent permissions on Android 14/15+.
 */
object OemPermissionHelper {

    fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
    }

    fun isOppoOrRealme(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("oppo") || m.contains("realme") || b.contains("realme") || m.contains("oneplus") || b.contains("oneplus")
    }

    fun isVivo(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("vivo") || b.contains("iqoo")
    }

    fun isHuawei(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("huawei") || b.contains("honor")
    }

    fun isAggressiveOem(): Boolean {
        return isXiaomi() || isOppoOrRealme() || isVivo() || isHuawei()
    }

    /**
     * Checks if the app can use Full-Screen Intents on Android 14+ (API 34+).
     * On earlier versions, this permission is granted automatically if declared in manifest.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
    }

    /**
     * Checks if the app is whitelisted from battery optimizations (Doze mode & App Standby).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    /**
     * Requests user to disable battery optimizations for uninterrupted incoming calls.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * Opens system settings to allow Full-Screen Intents on Android 14+ (API 34+).
     */
    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to app notification settings
            }
        }
        openAppNotificationSettings(context)
    }

    /**
     * Attempts to open OEM-specific Autostart managers (Xiaomi, ColorOS, Vivo).
     */
    fun openOemAutostartSettings(context: Context) {
        val intents = mutableListOf<Intent>()

        if (isXiaomi()) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            )
        } else if (isOppoOrRealme()) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            )
        } else if (isVivo()) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.PurviewTabActivity"
                    )
                )
            )
        }

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next
            }
        }

        // Generic fallback
        openAppDetailsSettings(context)
    }

    /**
     * Attempts to open Xiaomi/ColorOS background popup / display over other apps settings.
     */
    fun openOemBackgroundPopupSettings(context: Context) {
        if (isXiaomi()) {
            try {
                val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback
            }
        }
        openAppDetailsSettings(context)
    }

    /**
     * Opens system notification settings for Smart-Dialer so user can enable
     * "Floating notifications" (banners) and "Lock screen notifications".
     */
    fun openAppNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
