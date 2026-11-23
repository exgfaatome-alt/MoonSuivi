package com.moonsuivi.app.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder

object Prefs {
    private fun p(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("moonsuivi", Context.MODE_PRIVATE)

    fun autoRecord(ctx: Context) = p(ctx).getBoolean("auto_record", false)
    fun setAutoRecord(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("auto_record", v).apply()

    fun audioSource(ctx: Context) =
        p(ctx).getInt("audio_source", MediaRecorder.AudioSource.VOICE_RECOGNITION)
    fun setAudioSource(ctx: Context, v: Int) = p(ctx).edit().putInt("audio_source", v).apply()

    fun lastState(ctx: Context): String? = p(ctx).getString("last_state", null)
    fun setLastState(ctx: Context, v: String) = p(ctx).edit().putString("last_state", v).apply()

    /** Dernier enregistrement réalisé, en attente d'être rattaché à l'appel du journal. */
    fun setRecording(ctx: Context, path: String?, start: Long) =
        p(ctx).edit().putString("rec_path", path).putLong("rec_start", start).commit()
    fun recPath(ctx: Context): String? = p(ctx).getString("rec_path", null)
    fun recStart(ctx: Context): Long = p(ctx).getLong("rec_start", 0L)

    // ---------- E-mail ----------
    fun mail(ctx: Context): ConfigMail {
        val x = p(ctx)
        return ConfigMail(
            imapHote = x.getString("imap_hote", "") ?: "",
            imapPort = x.getInt("imap_port", 993),
            smtpHote = x.getString("smtp_hote", "") ?: "",
            smtpPort = x.getInt("smtp_port", 465),
            utilisateur = x.getString("mail_user", "") ?: "",
            motDePasse = x.getString("mail_pass", "") ?: "",
            auto = x.getBoolean("mail_auto", true)
        )
    }

    fun setMail(ctx: Context, c: ConfigMail) = p(ctx).edit()
        .putString("imap_hote", c.imapHote.trim())
        .putInt("imap_port", c.imapPort)
        .putString("smtp_hote", c.smtpHote.trim())
        .putInt("smtp_port", c.smtpPort)
        .putString("mail_user", c.utilisateur.trim())
        .putString("mail_pass", c.motDePasse)
        .putBoolean("mail_auto", c.auto)
        .apply()

    fun mailDernierSync(ctx: Context): Long = p(ctx).getLong("mail_sync", 0L)
    fun setMailDernierSync(ctx: Context, v: Long) = p(ctx).edit().putLong("mail_sync", v).apply()
    fun mailErreur(ctx: Context): String? = p(ctx).getString("mail_erreur", null)
    fun setMailErreur(ctx: Context, v: String?) = p(ctx).edit().putString("mail_erreur", v).apply()

    // ---------- Appel lancé depuis un sujet / une tâche ----------
    fun setAppelEnAttente(ctx: Context, numero: String, sujetId: Long) = p(ctx).edit()
        .putString("appel_num", numero).putLong("appel_sujet", sujetId)
        .putLong("appel_date", System.currentTimeMillis()).apply()
    fun appelEnAttente(ctx: Context): Triple<String, Long, Long>? {
        val x = p(ctx)
        val n = x.getString("appel_num", null) ?: return null
        return Triple(n, x.getLong("appel_sujet", 0L), x.getLong("appel_date", 0L))
    }
    fun effacerAppelEnAttente(ctx: Context) = p(ctx).edit().remove("appel_num").apply()

    // ---------- Astuces ----------
    fun astuceVue(ctx: Context, cle: String): Boolean = p(ctx).getBoolean("astuce_$cle", false)
    fun setAstuceVue(ctx: Context, cle: String) = p(ctx).edit().putBoolean("astuce_$cle", true).apply()
    fun reafficherAstuces(ctx: Context) {
        val x = p(ctx)
        val e = x.edit()
        x.all.keys.filter { it.startsWith("astuce_") }.forEach { e.remove(it) }
        e.apply()
    }

    // ---------- Fonctionnement permanent ----------
    fun resterActif(ctx: Context): Boolean = p(ctx).getBoolean("rester_actif", true)
    fun setResterActif(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("rester_actif", v).apply()

    // ---------- Mises à jour ----------
    const val URL_MAJ_DEFAUT = "https://github.com/exgfaatome-alt/MoonSuivi/releases/latest/download/version.json"

    fun majUrl(ctx: Context): String = p(ctx).getString("maj_url", URL_MAJ_DEFAUT) ?: URL_MAJ_DEFAUT
    fun setMajUrl(ctx: Context, v: String) = p(ctx).edit().putString("maj_url", v.trim()).apply()
    fun majAuto(ctx: Context): Boolean = p(ctx).getBoolean("maj_auto", true)
    fun setMajAuto(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("maj_auto", v).apply()
    fun majDerniereVerif(ctx: Context): Long = p(ctx).getLong("maj_verif", 0L)
    fun setMajDerniereVerif(ctx: Context, v: Long) = p(ctx).edit().putLong("maj_verif", v).apply()
}

data class ConfigMail(
    val imapHote: String = "",
    val imapPort: Int = 993,
    val smtpHote: String = "",
    val smtpPort: Int = 465,
    val utilisateur: String = "",
    val motDePasse: String = "",
    val auto: Boolean = true
) {
    val configuree: Boolean
        get() = imapHote.isNotBlank() && smtpHote.isNotBlank() && utilisateur.isNotBlank() && motDePasse.isNotBlank()
}
