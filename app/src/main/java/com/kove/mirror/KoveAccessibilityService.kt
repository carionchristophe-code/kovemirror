package com.kove.mirror

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KoveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KoveHandlebar"
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Détecter un appui court sur un bouton du commodo
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                // Touche [ENT] / Validation du commodo gauche Kove 800
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (!MirrorService.isStreamingActive) {
                        Log.i(TAG, "Touche [ENT] pressée pendant une déconnexion -> Relance immédiate du flux !")
                        
                        // Envoi de l'ordre de relance directe au service
                        val relaunchIntent = Intent(this, MirrorService::class.java).apply {
                            action = MirrorService.ACTION_RELAUNCH_STREAM
                        }
                        startService(relaunchIntent)
                        return true // Consomme l'événement pour la moto
                    }
                }

                // Touche Flèche Haut (Navigation/Zoom)
                KeyEvent.KEYCODE_DPAD_UP -> {
                    Log.d(TAG, "Commodo: Flèche HAUT pressée")
                }

                // Touche Flèche Bas (Navigation/Dézoom)
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    Log.d(TAG, "Commodo: Flèche BAS pressée")
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Utilisé pour la lecture du contexte si nécessaire
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service d'accessibilité Kove interrompu")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service commodo guidon Kove 800 Pro connecté et prêt")
    }
}
