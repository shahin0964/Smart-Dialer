package com.smartdialer.app.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground Service for Active IP Calls.
 * Guarantees that Android OS does not put CPU or Network to sleep or silence the microphone
 * when the phone screen turns off (via timeout, power button, or proximity sensor).
 */
class IpCallService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra(EXTRA_NUMBER) ?: ""
        val name = intent?.getStringExtra(EXTRA_NAME) ?: ""
        val statusText = intent?.getStringExtra(EXTRA_STATUS) ?: "Call in progress"

        val notification = CallNotificationManager.createOngoingNotification(
            this,
            number,
            name,
            statusText
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                }
                ServiceCompat.startForeground(
                    this,
                    CallNotificationManager.NOTIFICATION_ID_ONGOING,
                    notification,
                    fgsType
                )
            } else {
                startForeground(CallNotificationManager.NOTIFICATION_ID_ONGOING, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                startForeground(CallNotificationManager.NOTIFICATION_ID_ONGOING, notification)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null) {
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartDialer:IpCallPartialWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "SmartDialer:IpCallWifiLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_NUMBER = "extra_number"
        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_STATUS = "extra_status"

        fun start(context: Context, number: String, name: String?, status: String = "Call in progress") {
            val intent = Intent(context, IpCallService::class.java).apply {
                putExtra(EXTRA_NUMBER, number)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_STATUS, status)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, IpCallService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
