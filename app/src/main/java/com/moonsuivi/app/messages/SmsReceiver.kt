package com.moonsuivi.app.messages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.moonsuivi.app.data.Canal
import com.moonsuivi.app.data.Echange
import com.moonsuivi.app.data.Repo

/** Enregistre dans MoonSuivi les SMS reçus de vos contacts (les autres sont ignorés). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val numero = msgs[0].originatingAddress ?: return
        val texte = msgs.joinToString("") { it.messageBody ?: "" }
        Repo.init(context)
        val contact = Repo.findContactByPhone(numero) ?: return
        Repo.saveEchange(
            Echange(
                canal = com.moonsuivi.app.data.Canal.SMS_RECU,
                contactId = contact.id,
                adresse = numero,
                contenu = texte,
                date = msgs[0].timestampMillis
            )
        )
    }
}
