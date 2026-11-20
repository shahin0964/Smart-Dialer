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

    fun isSamsung(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("samsung") || b.contains("samsung")
    }

    fun isTranssion(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("transsion") || m.contains("infinix") || m.contains("tecno") || m.contains("itel") ||
                b.contains("infinix") || b.contains("tecno") || b.contains("itel")
    }

    fun isAggressiveOem(): Boolean {
        return isXiaomi() || isOppoOrRealme() || isVivo() || isHuawei() || isSamsung() || isTranssion()
    }

    fun getDeviceBrandName(): String {
        val m = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val b = Build.BRAND.replaceFirstChar { it.uppercase() }
        return when {
            isXiaomi() -> "Xiaomi / Redmi / Poco"
            isOppoOrRealme() -> "Oppo / Realme / OnePlus"
            isVivo() -> "Vivo / iQOO"
            isSamsung() -> "Samsung Galaxy"
            isHuawei() -> "Huawei / Honor"
            isTranssion() -> "Tecno / Infinix"
            m.isNotBlank() && m != "Unknown" -> "$m ($b)"
            else -> "Android Device"
        }
    }

    /**
     * Checks if the app can draw overlays (appear on top / pop-up window).
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Opens system settings to allow 'Display over other apps' (Appear on top).
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Fallback to app details
            }
        }
        openAppDetailsSettings(context)
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
     * With android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in the manifest,
     * this displays the system modal dialog with "Allow" and "Deny" buttons.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            android.widget.Toast.makeText(
                context,
                "Battery is already unrestricted for Smart-Dialer",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppDetailsSettings(context)
            }
            return
        }

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
        } else if (isSamsung()) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                )
            )
        } else if (isTranssion()) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.transsion.phonemaster",
                        "com.transsion.phonemaster.AutoStartListActivity"
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
        val intents = mutableListOf<Intent>()

        if (isXiaomi()) {
            intents.add(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                }
            )
            intents.add(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                }
            )
        } else if (isOppoOrRealme()) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                    )
                )
            )
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.permission.floatwindow.FloatWindowListActivity"
                    )
                )
            )
        } else if (isVivo()) {
            intents.add(
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.PurviewTabActivity"
                    )
                )
            )
        }

        // Overlay permission intent as universal fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intents.add(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
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
