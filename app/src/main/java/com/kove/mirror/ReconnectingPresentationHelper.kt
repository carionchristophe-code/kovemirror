package com.kove.mirror

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
import kotlinx.coroutines.*

class ReconnectingPresentationHelper(
    private val context: Context,
    private val onReconnected: () -> Unit
) {
    private val TAG = "KoveReconnectHelper"
    private var reconnectJob: Job? = null
    private var isMonitoring = false
    private var retryCount = 0
    private val maxRetries = 10

    fun startMonitoring(virtualDisplay: VirtualDisplay?) {
        isMonitoring = true
        retryCount = 0
        Log.i(TAG, "Démarrage du watchdog d'affichage TFT Kove 800 Pro")
    }

    fun handleDisplayDisconnected() {
        if (!isMonitoring) return
        Log.w(TAG, "Déconnexion de l'écran TFT détectée. Lancement de la reconnexion automatique...")
        
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.Default).launch {
            while (isMonitoring && retryCount < maxRetries) {
                retryCount++
                val delayMs = (1000L * (1 shl (retryCount - 1).coerceAtMost(4))) // 1s, 2s, 4s, 8s max
                Log.d(TAG, "Tentative de reconnexion #$retryCount dans ${delayMs}ms...")
                delay(delayMs)

                try {
                    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val displays = displayManager.displays
                    // Vérifier si un écran distant/TFT est de nouveau présent
                    if (displays.size > 1) {
                        Log.i(TAG, "Écran TFT retrouvé avec succès !")
                        withContext(Dispatchers.Main) {
                            onReconnected()
                        }
                        retryCount = 0
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur durant la tentative de reconnexion: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        isMonitoring = false
        reconnectJob?.cancel()
        Log.i(TAG, "Watchdog TFT arrêté")
    }
}
