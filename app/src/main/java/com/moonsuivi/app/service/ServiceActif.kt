package com.moonsuivi.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import com.moonsuivi.app.MainActivity
import com.moonsuivi.app.data.Prefs
import com.moonsuivi.app.data.Repo
import com.moonsuivi.app.mail.MailWorker

/**
 * Service permanent (notification discrète « MoonSuivi est actif ») :
 * empêche Android de fermer l'appli, pour que le suivi des appels, SMS et e-mails
 * continue même quand l'appli n'est pas ouverte.
 */
class ServiceActif : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.resterActif(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        Repo.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "MoonSuivi actif en arrière-plan", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val ouvrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = Notification.Builder(this, CANAL)
            .setContentTitle("MoonSuivi est actif")
            .setContentText("Suivi des appels, SMS et e-mails en cours")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(ouvrir)
            .setOngoing(true)
            .build()
        startForeground(ID_NOTIF, notif)
        return START_STICKY
    }

    companion object {
        private const val CANAL = "actif"
        private const val ID_NOTIF = 2

        fun demarrer(ctx: Context) {
            if (!Prefs.resterActif(ctx)) return
            runCatching { ctx.startForegroundService(Intent(ctx, ServiceActif::class.java)) }
        }

        fun arreter(ctx: Context) {
            ctx.stopService(Intent(ctx, ServiceActif::class.java))
        }

        fun batterieLibre(ctx: Context): Boolean =
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

        /** Demande à Android de ne pas limiter MoonSuivi pour économiser la batterie. */
        fun demanderBatterieLibre(ctx: Context) {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(i) }.onFailure {
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}

/** Relance MoonSuivi après un redémarrage du téléphone ou une mise à jour de l'appli. */
class DemarrageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, "android.intent.action.QUICKBOOT_POWERON" -> {
                Repo.init(context)
                ServiceActif.demarrer(context)
                MailWorker.planifier(context)
            }
        }
    }
}
