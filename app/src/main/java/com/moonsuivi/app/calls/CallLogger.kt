package com.moonsuivi.app.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import com.moonsuivi.app.data.Appel
import com.moonsuivi.app.data.Prefs
import com.moonsuivi.app.data.Repo
import com.moonsuivi.app.data.TypeAppel
import java.io.File

/**
 * Copie les appels du journal Android dans l'appli, associe le contact
 * et rattache l'enregistrement audio correspondant s'il existe.
 */
object CallLogger {
    fun sync(ctx: Context, depuis: Long): Int {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return 0
        Repo.init(ctx)
        var n = 0
        val proj = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, proj,
            CallLog.Calls.DATE + " > ?", arrayOf(depuis.toString()),
            CallLog.Calls.DATE + " DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val numero = c.getString(0) ?: ""
                val type = when (c.getInt(1)) {
                    CallLog.Calls.INCOMING_TYPE -> TypeAppel.ENTRANT
                    CallLog.Calls.OUTGOING_TYPE -> TypeAppel.SORTANT
                    CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> TypeAppel.MANQUE
                    else -> TypeAppel.AUTRE
                }
                val date = c.getLong(2)
                val duree = c.getLong(3)
                if (Repo.appelExiste(date)) continue

                var fichier: String? = null
                val rp = Prefs.recPath(ctx)
                val rs = Prefs.recStart(ctx)
                if (rp != null && rs >= date - 10_000 && rs <= date + duree * 1000 + 120_000 && File(rp).exists()) {
                    fichier = rp
                    Prefs.setRecording(ctx, null, 0L)
                }

                Repo.saveAppel(
                    Appel(
                        numero = numero,
                        contactId = Repo.findContactByPhone(numero)?.id,
                        type = type,
                        date = date,
                        dureeSec = duree,
                        fichier = fichier
                    ),
                    notifier = false
                )
                n++
            }
        }
        if (n > 0) Repo.notifyChanged()
        return n
    }
}
