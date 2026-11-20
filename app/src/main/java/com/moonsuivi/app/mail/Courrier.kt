package com.moonsuivi.app.mail

import android.content.Context
import android.text.Html
import com.moonsuivi.app.data.Canal
import com.moonsuivi.app.data.ConfigMail
import com.moonsuivi.app.data.Contact
import com.moonsuivi.app.data.Echange
import com.moonsuivi.app.data.Prefs
import com.moonsuivi.app.data.Repo
import com.moonsuivi.app.data.emailsDe
import com.sun.mail.imap.IMAPFolder
import java.util.Date
import java.util.Properties
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SentDateTerm

/**
 * Envoi (SMTP) et suivi (IMAP) des e-mails.
 * Seuls les e-mails échangés avec des contacts de MoonSuivi (adresse connue) sont importés.
 * Toutes les fonctions sont bloquantes : à appeler hors du fil principal.
 */
object Courrier {

    /** Réglages préremplis pour les fournisseurs courants. */
    val FOURNISSEURS: List<Pair<String, ConfigMail>> = listOf(
        "Gmail" to ConfigMail("imap.gmail.com", 993, "smtp.gmail.com", 465),
        "Orange" to ConfigMail("imap.orange.fr", 993, "smtp.orange.fr", 465),
        "Free" to ConfigMail("imap.free.fr", 993, "smtp.free.fr", 465),
        "SFR" to ConfigMail("imap.sfr.fr", 993, "smtp.sfr.fr", 465),
        "Yahoo" to ConfigMail("imap.mail.yahoo.com", 993, "smtp.mail.yahoo.com", 465),
        "OVH" to ConfigMail("ssl0.ovh.net", 993, "ssl0.ovh.net", 465)
    )

    private fun session(c: ConfigMail): Session {
        val p = Properties()
        p["mail.store.protocol"] = "imaps"
        p["mail.imaps.host"] = c.imapHote
        p["mail.imaps.port"] = c.imapPort.toString()
        p["mail.imaps.connectiontimeout"] = "20000"
        p["mail.imaps.timeout"] = "30000"
        p["mail.smtp.host"] = c.smtpHote
        p["mail.smtp.port"] = c.smtpPort.toString()
        p["mail.smtp.auth"] = "true"
        if (c.smtpPort == 465) p["mail.smtp.ssl.enable"] = "true" else p["mail.smtp.starttls.enable"] = "true"
        p["mail.smtp.connectiontimeout"] = "20000"
        p["mail.smtp.timeout"] = "30000"
        return Session.getInstance(p)
    }

    private fun connecter(s: Session, c: ConfigMail): Store {
        val store = s.getStore("imaps")
        store.connect(c.imapHote, c.imapPort, c.utilisateur, c.motDePasse)
        return store
    }

    /** Vérifie que les identifiants fonctionnent (IMAP). */
    fun tester(c: ConfigMail) {
        val store = connecter(session(c), c)
        store.close()
    }

    /** Envoie un e-mail et renvoie son Message-ID (pour éviter le doublon à la synchronisation). */
    fun envoyer(c: ConfigMail, destinataire: String, objet: String, texte: String): String {
        val s = session(c)
        val m = MimeMessage(s)
        m.setFrom(InternetAddress(c.utilisateur))
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinataire))
        m.setSubject(objet, "UTF-8")
        m.setText(texte, "UTF-8")
        m.sentDate = Date()
        m.saveChanges()
        val mid = m.messageID ?: "moon-${System.currentTimeMillis()}"
        Transport.send(m, c.utilisateur, c.motDePasse)
        // Gmail range lui-même les messages envoyés ; pour les autres on en dépose une copie dans « Envoyés »
        if (!c.smtpHote.contains("gmail", ignoreCase = true)) {
            runCatching {
                val store = connecter(s, c)
                try {
                    val envoyes = dossierEnvoyes(store)
                    if (envoyes != null) {
                        m.setFlag(Flags.Flag.SEEN, true)
                        envoyes.appendMessages(arrayOf<Message>(m))
                    }
                } finally {
                    runCatching { store.close() }
                }
            }
        }
        return mid
    }

    /** Importe les nouveaux e-mails reçus de / envoyés à vos contacts. Renvoie le nombre importé. */
    fun synchroniser(ctx: Context): Int {
        val c = Prefs.mail(ctx)
        if (!c.configuree) return 0
        Repo.init(ctx)
        val contacts = Repo.contacts().filter { emailsDe(it).isNotEmpty() }
        val maintenant = System.currentTimeMillis()
        if (contacts.isEmpty()) {
            Prefs.setMailDernierSync(ctx, maintenant)
            Prefs.setMailErreur(ctx, null)
            return 0
        }
        val dernier = Prefs.mailDernierSync(ctx)
        val depuis = if (dernier > 0) dernier - 24L * 3600 * 1000 else maintenant - 60L * 24 * 3600 * 1000

        var n = 0
        try {
            val store = connecter(session(c), c)
            try {
                n += lireDossier(store.getFolder("INBOX"), true, depuis, contacts)
                dossierEnvoyes(store)?.let { n += lireDossier(it, false, depuis, contacts) }
            } finally {
                runCatching { store.close() }
            }
            Prefs.setMailDernierSync(ctx, maintenant)
            Prefs.setMailErreur(ctx, null)
        } catch (e: Exception) {
            Prefs.setMailErreur(ctx, e.message ?: e.javaClass.simpleName)
            if (n > 0) Repo.notifyChanged()
            throw e
        }
        if (n > 0) Repo.notifyChanged()
        return n
    }

    private fun dossierEnvoyes(store: Store): Folder? {
        val tous = runCatching { store.defaultFolder.list("*").toList() }.getOrDefault(emptyList())
        tous.firstOrNull { f ->
            (f as? IMAPFolder)?.attributes?.any { it.equals("\\Sent", ignoreCase = true) } == true
        }?.let { return it }
        val noms = setOf(
            "sent", "sent items", "sent messages", "sent mail", "inbox.sent", "envoyés", "envoyes",
            "éléments envoyés", "messages envoyés", "inbox.envoyés", "[gmail]/sent mail", "[gmail]/messages envoyés"
        )
        return tous.firstOrNull { it.fullName.lowercase() in noms }
            ?: tous.firstOrNull { it.name.contains("sent", true) || it.name.contains("envoy", true) }
    }

    private fun adresses(a: Array<Address>?): List<String> =
        a?.mapNotNull { (it as? InternetAddress)?.address } ?: emptyList()

    private fun contactPour(adresse: String, contacts: List<Contact>): Contact? =
        contacts.firstOrNull { c -> emailsDe(c).any { it.equals(adresse, ignoreCase = true) } }

    private fun lireDossier(f: Folder, recu: Boolean, depuis: Long, contacts: List<Contact>): Int {
        f.open(Folder.READ_ONLY)
        try {
            val terme = if (recu) ReceivedDateTerm(ComparisonTerm.GE, Date(depuis))
            else SentDateTerm(ComparisonTerm.GE, Date(depuis))
            val messages = f.search(terme)
            if (messages.isEmpty()) return 0
            val fp = FetchProfile()
            fp.add(FetchProfile.Item.ENVELOPE)
            f.fetch(messages, fp)

            var n = 0
            for (m in messages) {
                try {
                    val liste = if (recu) adresses(m.from)
                    else adresses(m.getRecipients(Message.RecipientType.TO)) + adresses(m.getRecipients(Message.RecipientType.CC))
                    var contact: Contact? = null
                    var adresse = ""
                    for (a in liste) {
                        val ct = contactPour(a, contacts)
                        if (ct != null) {
                            contact = ct; adresse = a; break
                        }
                    }
                    if (contact == null) continue

                    val objet = m.subject ?: ""
                    val dateMsg = (if (recu) m.receivedDate else (m.sentDate ?: m.receivedDate))?.time
                        ?: System.currentTimeMillis()
                    val mid = (m as? MimeMessage)?.messageID ?: "${contact.id}-$dateMsg-$objet"
                    if (Repo.echangeExiste(mid)) continue

                    Repo.saveEchange(
                        Echange(
                            canal = if (recu) Canal.EMAIL_RECU else Canal.EMAIL_ENVOYE,
                            contactId = contact.id,
                            sujetId = Repo.sujetDuFil(contact.id, objet),
                            adresse = adresse,
                            objet = objet,
                            contenu = sansCitation(texteDe(m)).take(8000),
                            date = dateMsg,
                            messageId = mid
                        ),
                        notifier = false
                    )
                    n++
                } catch (e: Exception) {
                    // message illisible : on passe au suivant
                }
            }
            return n
        } finally {
            runCatching { f.close(false) }
        }
    }

    private fun texteDe(p: Part): String {
        try {
            if (p.isMimeType("text/plain")) return (p.content as? String) ?: ""
            if (p.isMimeType("text/html")) return htmlVersTexte((p.content as? String) ?: "")
            if (p.isMimeType("multipart/alternative")) {
                val mp = p.content as Multipart
                var html: String? = null
                for (i in 0 until mp.count) {
                    val b = mp.getBodyPart(i)
                    if (b.isMimeType("text/plain")) return texteDe(b)
                    if (html == null) html = texteDe(b)
                }
                return html ?: ""
            }
            if (p.isMimeType("multipart/*")) {
                val mp = p.content as Multipart
                for (i in 0 until mp.count) {
                    val t = texteDe(mp.getBodyPart(i))
                    if (t.isNotBlank()) return t
                }
                return ""
            }
            if (p.isMimeType("message/rfc822")) return texteDe(p.content as Part)
        } catch (e: Exception) {
            return ""
        }
        return ""
    }

    private fun htmlVersTexte(html: String): String {
        val propre = html.replace(Regex("(?is)<(style|script)[^>]*>.*?</\\1>"), "")
        return Html.fromHtml(propre, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace(' ', ' ')
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** Coupe l'historique cité en bas des réponses (« Le ... a écrit : », lignes « > »). */
    private fun sansCitation(texte: String): String {
        val lignes = texte.lines()
        val coupe = lignes.indexOfFirst { l ->
            val t = l.trim()
            t.startsWith(">") ||
                Regex("^(le|on) .{5,200}(a écrit|wrote)\\s*:?$", RegexOption.IGNORE_CASE).matches(t) ||
                t.startsWith("-----Original Message", true) ||
                t.startsWith("-----Message d'origine", true) ||
                t.startsWith("De :") && lignes.any { it.trim().startsWith("Objet :") }
        }
        val garde = if (coupe > 0) lignes.subList(0, coupe) else lignes
        return garde.joinToString("\n").trim()
    }
}
