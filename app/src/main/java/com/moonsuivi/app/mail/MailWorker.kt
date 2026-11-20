package com.moonsuivi.app.mail

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moonsuivi.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Relève automatiquement les e-mails toutes les 30 minutes (si réseau disponible). */
class MailWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Filet de sécurité : relance le service permanent s'il a été arrêté
        com.moonsuivi.app.service.ServiceActif.demarrer(applicationContext)
        try {
            if (Prefs.mail(applicationContext).auto) Courrier.synchroniser(applicationContext)
        } catch (e: Exception) {
            // l'erreur est mémorisée dans les réglages et affichée à l'utilisateur
        }
        Result.success()
    }

    companion object {
        private const val NOM = "releve-mail"

        fun planifier(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            val c = Prefs.mail(ctx)
            if ((c.configuree && c.auto) || Prefs.resterActif(ctx)) {
                val req = PeriodicWorkRequestBuilder<MailWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build()
                wm.enqueueUniquePeriodicWork(NOM, ExistingPeriodicWorkPolicy.UPDATE, req)
            } else {
                wm.cancelUniqueWork(NOM)
            }
        }
    }
}
