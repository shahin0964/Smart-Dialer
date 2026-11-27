package com.smartdialer.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object NetworkTypeHelper {

    private const val NETWORK_TYPE_LTE_CA_VAL = 19

    fun getNetworkLabel(context: Context): String {
        return getCallNetworkLabel(context = context, isIpCall = true)
    }

    /**
     * Returns a human-readable network badge tailored to the call route:
     * - SIP / IP calls: "Wi-Fi" (when on Wi-Fi) or "Data" (when on mobile data)
     * - SIM calls: cellular radio generation ("2G", "3G", "4G", "4.5G", "5G") or "Wi-Fi Call" for VoWiFi
     */
    fun getCallNetworkLabel(
        context: Context,
        isIpCall: Boolean,
        simSlot: Int = 0,
        subscriptionId: Int = -1,
        isWifiCalling: Boolean = false,
        overrideNetworkType: Int = 0
    ): String {
        return if (isIpCall) {
            getIpTransportLabel(context)
        } else {
            if (isWifiCalling) return "Wi-Fi Call"
            getCellularType(context, simSlot, subscriptionId, overrideNetworkType)
        }
    }

    /**
     * Live reactive stream of the active call's network label (updates mid-call if Wi-Fi <-> Data
     * switches on a SIP call, or if cellular radio switches between 5G / 4.5G / 4G / 3G / 2G on a SIM call).
     */
    fun observeCallNetworkLabel(
        context: Context,
        isIpCall: Boolean,
        simSlot: Int = 0,
        subscriptionId: Int = -1,
        isWifiCalling: Boolean = false
    ): Flow<String> = callbackFlow {
        val appCtx = context.applicationContext
        var currentOverride = 0

        fun emitCurrent() {
            trySend(
                getCallNetworkLabel(
                    context = appCtx,
                    isIpCall = isIpCall,
                    simSlot = simSlot,
                    subscriptionId = subscriptionId,
                    isWifiCalling = isWifiCalling,
                    overrideNetworkType = currentOverride
                )
            )
        }

        emitCurrent()

        if (isIpCall) {
            val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val netCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = emitCurrent()
                override fun onLost(network: Network) = emitCurrent()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = emitCurrent()
            }
            if (cm != null) {
                try {
                    cm.registerDefaultNetworkCallback(netCallback)
                } catch (_: Exception) {
                }
            }
            awaitClose {
                if (cm != null) {
                    try {
                        cm.unregisterNetworkCallback(netCallback)
                    } catch (_: Exception) {
                    }
                }
            }
        } else {
            val tm = resolveTelephonyManager(appCtx, simSlot, subscriptionId)
            val hasPhoneState = ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            var registeredCallback: Any? = null

            if (tm != null && hasPhoneState) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                        override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                            currentOverride = displayInfo.overrideNetworkType
                            emitCurrent()
                        }
                    }
                    try {
                        tm.registerTelephonyCallback(appCtx.mainExecutor, cb)
                        registeredCallback = cb
                    } catch (_: Exception) {
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    @Suppress("DEPRECATION")
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onDisplayInfoChanged(displayInfo: TelephonyDisplayInfo) {
                            currentOverride = displayInfo.overrideNetworkType
                            emitCurrent()
                        }
                    }
                    try {
                        @Suppress("DEPRECATION")
                        tm.listen(listener, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED)
                        registeredCallback = listener
                    } catch (_: Exception) {
                    }
                }
            }

            // Lightweight periodic check for voiceNetworkType transitions (e.g. VoLTE -> 3G CSFB)
            val pollJob = launch {
                while (isActive) {
                    delay(2000)
                    emitCurrent()
                }
            }

            awaitClose {
                pollJob.cancel()
                if (tm != null && registeredCallback != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            tm.unregisterTelephonyCallback(registeredCallback as TelephonyCallback)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            @Suppress("DEPRECATION")
                            tm.listen(registeredCallback as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }.distinctUntilChanged()

    private fun getIpTransportLabel(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "Data"
            val network = cm.activeNetwork ?: return "Offline"
            val caps = cm.getNetworkCapabilities(network) ?: return "Offline"

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Online"
            }
        } catch (_: Exception) {
            "Data"
        }
    }

    private fun resolveTelephonyManager(
        context: Context,
        simSlot: Int,
        subscriptionId: Int
    ): TelephonyManager? {
        val base = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        val targetSubId = if (subscriptionId > 0) {
            subscriptionId
        } else {
            findSubscriptionIdForSlot(context, simSlot)
        }
        return if (targetSubId > 0) {
            try {
                base.createForSubscriptionId(targetSubId)
            } catch (_: Exception) {
                base
            }
        } else {
            base
        }
    }

    private fun findSubscriptionIdForSlot(context: Context, simSlot: Int): Int {
        if (simSlot < 0) return -1
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return -1
        }
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            sm?.activeSubscriptionInfoList?.firstOrNull { it.simSlotIndex == simSlot }?.subscriptionId ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    @Suppress("DEPRECATION")
    private fun getCellularType(
        context: Context,
        simSlot: Int,
        subscriptionId: Int,
        overrideNetworkType: Int
    ): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "SIM"
        }
        return try {
            val tm = resolveTelephonyManager(context, simSlot, subscriptionId) ?: return "SIM"
            val voiceType = tm.voiceNetworkType
            val dataType = tm.dataNetworkType

            // For active/CSFB calls, if voiceNetworkType is known, prefer it for the voice radio
            val rawType = if (voiceType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                voiceType
            } else {
                dataType
            }

            if (rawType == TelephonyManager.NETWORK_TYPE_IWLAN) {
                return "Wi-Fi Call"
            }

            // If voice bearer fell back to 2G or 3G (e.g. no VoLTE), reflect actual voice generation
            val baseLabel = mapRawCellularType(rawType)
            if (baseLabel == "2G" || baseLabel == "3G") {
                return baseLabel
            }

            // On LTE / 5G, refine using overrideNetworkType (5G NSA/SA, 4.5G LTE+/LTE-CA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                when (overrideNetworkType) {
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> return "5G"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> return "4.5G"
                }
            }

            baseLabel
        } catch (_: Exception) {
            "SIM"
        }
    }

    @Suppress("DEPRECATION")
    private fun mapRawCellularType(netType: Int): String {
        return when (netType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            NETWORK_TYPE_LTE_CA_VAL -> "4.5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_TD_SCDMA,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            else -> "SIM"
        }
    }
}
