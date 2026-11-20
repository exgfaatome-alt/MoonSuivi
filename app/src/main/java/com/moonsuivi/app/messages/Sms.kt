package com.moonsuivi.app.messages

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager

object Sms {
    fun peutEnvoyer(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Envoie directement le SMS depuis l'appli. */
    @Suppress("DEPRECATION")
    fun envoyer(ctx: Context, numero: String, texte: String) {
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        val parties = sm.divideMessage(texte)
        sm.sendMultipartTextMessage(numero, null, parties, null, null)
    }

    /** Ouvre l'appli SMS du téléphone avec le message prérempli. */
    fun ouvrirAppli(ctx: Context, numero: String, texte: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(numero)))
            .putExtra("sms_body", texte)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }
}
