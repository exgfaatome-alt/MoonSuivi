package com.moonsuivi.app.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.moonsuivi.app.data.Prefs
import com.moonsuivi.app.data.Repo

/**
 * Reçoit les changements d'état du téléphone (sonnerie, décroché, raccroché).
 * - Décroché : lance l'enregistrement de secours si le service d'accessibilité n'est pas actif.
 * - Raccroché : arrête l'enregistrement puis enregistre l'appel dans l'historique.
 */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val etat = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        // Android envoie souvent l'événement deux fois : on ignore les doublons
        if (etat == Prefs.lastState(context)) return
        Prefs.setLastState(context, etat)
        Repo.init(context)

        when (etat) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (Prefs.autoRecord(context) && !CallAccessibilityService.running) {
                    runCatching {
                        context.startForegroundService(Intent(context, RecordingService::class.java))
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                context.stopService(Intent(context, RecordingService::class.java))
                val pending = goAsync()
                Thread {
                    try {
                        // Laisse le temps à Android d'écrire l'appel dans son journal
                        Thread.sleep(2500)
                        CallLogger.sync(context.applicationContext, System.currentTimeMillis() - 24L * 3600 * 1000)
                    } catch (e: Exception) {
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
