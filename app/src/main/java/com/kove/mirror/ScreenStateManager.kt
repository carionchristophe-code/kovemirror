package com.kove.mirror

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager

class ScreenStateManager(private val context: Context) {

    interface Listener {
        fun onScreenTurnedOn()
        fun onScreenTurnedOff()
    }

    private var listener: Listener? = null
    private var isRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    DebugLogger.info("📱 ScreenStateManager: SCREEN_OFF detected")
                    listener?.onScreenTurnedOff()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    DebugLogger.info("📱 ScreenStateManager: SCREEN_ON / USER_PRESENT detected")
                    listener?.onScreenTurnedOn()
                }
            }
        }
    }

    fun isScreenInteractive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: true
    }

    fun register(listener: Listener) {
        this.listener = listener
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            context.registerReceiver(screenReceiver, filter)
            isRegistered = true
            DebugLogger.info("📱 ScreenStateManager registered")
        }
    }

    fun unregister() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (_: Exception) {}
            isRegistered = false
            listener = null
            DebugLogger.info("📱 ScreenStateManager unregistered")
        }
    }
}
