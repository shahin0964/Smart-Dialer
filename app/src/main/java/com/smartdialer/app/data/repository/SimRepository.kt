package com.smartdialer.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.smartdialer.app.data.model.SimInfo

class SimRepository(private val context: Context) {

    fun getSimCards(): List<SimInfo> {
        val simList = mutableListOf<SimInfo>()

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList

                if (!activeList.isNullOrEmpty()) {
                    activeList.forEachIndexed { index, info ->
                        val slot = info.simSlotIndex
                        val name = info.displayName?.toString() ?: "SIM ${slot + 1}"
                        val carrier = info.carrierName?.toString() ?: "Carrier"
                        simList.add(
                            SimInfo(
                                subscriptionId = info.subscriptionId,
                                slotIndex = slot,
                                displayName = name,
                                carrierName = carrier
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback default two SIMs if system returned fewer or not permitted
        if (simList.isEmpty()) {
            simList.add(SimInfo(subscriptionId = 1, slotIndex = 0, displayName = "SIM 1", carrierName = "Default"))
            simList.add(SimInfo(subscriptionId = 2, slotIndex = 1, displayName = "SIM 2", carrierName = "Default"))
        } else if (simList.size == 1) {
            val first = simList[0]
            val secondSlot = if (first.slotIndex == 0) 1 else 0
            simList.add(SimInfo(subscriptionId = 2, slotIndex = secondSlot, displayName = "SIM 2", carrierName = "SIM 2"))
        }

        return simList.sortedBy { it.slotIndex }
    }
}
