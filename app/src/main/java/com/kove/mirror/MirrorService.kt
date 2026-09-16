package com.kove.mirror

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log

class MirrorService : Service() {

    companion object {
        private const val TAG = "KoveMirrorService"

        // Instance singleton requise par HandlebarOverlayService
        var instance: MirrorService? = null

        // Constantes requises par MainActivity et KovePresentation
        const val ACTION_START_STREAM = "com.kove.mirror.ACTION_START_STREAM"
        const val ACTION_STOP_STREAM = "com.kove.mirror.ACTION_STOP_STREAM"
        const val ACTION_RELAUNCH_STREAM = "com.kove.mirror.ACTION_RELAUNCH_STREAM"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA_INTENT = "extra_data_intent"
        const val EXTRA_TARGET_APP = "extra_target_app"

        // Constantes d'affichage d'origine Kove
        var DISPLAY_MODE: Int = 0
        var PHONE_ASPECT_RATIO: Float = 1.77f
        var TFT_PADDING: Boolean = false
        var TFT_TOP_PADDING_DP: Int = 0
        var TFT_BOTTOM_PADDING_DP: Int = 0

        // Résolution TFT Kove 800 Pro
        const val TFT_WIDTH = 600
        const val TFT_HEIGHT = 1024
        const val TFT_DPI = 160

        // Variables de cache pour reconnexion sans dialogue
        var cachedResultCode: Int = 0
        var cachedIntentData: Intent? = null
        var isStreamingActive: Boolean = false
        var isEnabled: Boolean = false
        var selectedTargetApp: String? = null

        // Méthodes statiques d'origine appelées par MainActivity
        fun startControlOnlyService(context: Context) {
            val intent = Intent(context, MirrorService::class.java).apply {
                action = "ACTION_START_CONTROL_ONLY"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isEnabled = true
        }

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, MirrorService::class.java).apply {
                action = ACTION_START_STREAM
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA_INTENT, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isEnabled = true
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MirrorService::class.java).apply {
                action = ACTION_STOP_STREAM
            }
            context.stopService(intent)
            isEnabled = false
        }

        fun updatePadding(topDp: Int, bottomDp: Int) {
            TFT_TOP_PADDING_DP = topDp
            TFT_BOTTOM_PADDING_DP = bottomDp
        }
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_STREAM -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA_INTENT)
                selectedTargetApp = intent.getStringExtra(EXTRA_TARGET_APP)

                if (resultCode != 0 && data != null) {
                    cachedResultCode = resultCode
                    cachedIntentData = data.clone() as Intent
                    startScreenStream(resultCode, data)
                }
            }

            ACTION_RELAUNCH_STREAM -> {
                Log.i(TAG, "Relance du flux vidéo demandée (Commodo [ENT] ou Watchdog)")
                relaunchStreamFromCache()
            }

            ACTION_STOP_STREAM -> {
                stopScreenStream()
            }
        }
        return START_STICKY
    }

    /**
     * Négociation BLE MTU 512 (Kove 800 Pro SV=2.0.4)
     */
    fun setupBleMtuNegotiation(gatt: BluetoothGatt) {
        Log.i(TAG, "Connexion BLE détectée : Négociation du MTU 512...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val success = gatt.requestMtu(512)
            Log.d(TAG, "Demande MTU 512 envoyée avec succès: $success")
        }
    }

    private fun startScreenStream(resultCode: Int, data: Intent) {
        try {
            stopScreenStream()

            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e(TAG, "Impossible d'initialiser MediaProjection")
                return
            }

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

            virtualDisplay = displayManager.createVirtualDisplay(
                "KoveTFTDisplay",
                TFT_WIDTH,
                TFT_HEIGHT,
                TFT_DPI,
                null,
                displayFlags
            )

            isStreamingActive = true
            isEnabled = true
            Log.i(TAG, "Flux vidéo Kove démarré avec succès !")

            virtualDisplay?.display?.let { display ->
                launchSecondaryAppIfSelected(display.displayId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erreur au démarrage du flux vidéo: ${e.message}")
            isStreamingActive = false
        }
    }

    private fun relaunchStreamFromCache() {
        if (cachedIntentData != null && cachedResultCode != 0) {
            Log.i(TAG, "Relance du flux avec le jeton en cache...")
            startScreenStream(cachedResultCode, cachedIntentData!!)
        } else {
            Log.w(TAG, "Aucun jeton en cache. Démarrez la projection depuis l'interface au préalable.")
        }
    }

    private fun launchSecondaryAppIfSelected(displayId: Int) {
        val targetPackage = when (selectedTargetApp) {
            "DMD2" -> "com.drivemode.android"
            "OSMAND" -> "net.osmand.plus"
            "GMAPS" -> "com.google.android.apps.maps"
            else -> null
        }

        if (targetPackage != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                if (launchIntent != null) {
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = displayId
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    startActivity(launchIntent, options.toBundle())
                    Log.i(TAG, "Application $targetPackage lancée sur l'écran TFT (Display #$displayId)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors du lancement de l'application sur le TFT: ${e.message}")
            }
        }
    }

    private fun stopScreenStream() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjection = null
            isStreamingActive = false
            isEnabled = false
            Log.i(TAG, "Flux vidéo arrêté")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt du flux: ${e.message}")
        }
    }

    private fun startForegroundNotification() {
        val channelId = "kove_mirror_stream"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "KoveMirror Streaming",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("KoveMirror Pro 2026")
                .setContentText("Streaming Kove 800 Pro actif")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("KoveMirror Pro 2026")
                .setContentText("Streaming Kove 800 Pro actif")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build()
        }

        startForeground(101, notification)
    }

    override fun onDestroy() {
        stopScreenStream()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
