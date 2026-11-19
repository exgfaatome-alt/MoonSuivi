package com.moonsuivi.app.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** Service de premier plan utilisé quand le service d'accessibilité n'est pas activé. */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Enregistrement des appels", NotificationManager.IMPORTANCE_LOW)
        )
        val notif = Notification.Builder(this, CANAL)
            .setContentTitle("Enregistrement de l'appel")
            .setContentText("MoonSuivi enregistre l'appel en cours")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        Recorder.start(this)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Recorder.stop(this)
        super.onDestroy()
    }

    companion object {
        private const val CANAL = "enregistrement"
    }
}
