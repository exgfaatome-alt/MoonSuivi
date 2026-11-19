package com.moonsuivi.app.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import com.moonsuivi.app.data.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enregistreur audio partagé par le service d'accessibilité et le service de secours.
 * Les fichiers .m4a sont stockés dans Android/data/com.moonsuivi.app/files/Enregistrements.
 */
object Recorder {
    private var rec: MediaRecorder? = null
    private var chemin: String? = null

    val enCours: Boolean get() = rec != null

    @Synchronized
    fun start(ctx: Context): Boolean {
        if (rec != null) return true
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false

        val dossier = File(ctx.getExternalFilesDir(null), "Enregistrements").apply { mkdirs() }
        val nom = "appel_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".m4a"
        val fichier = File(dossier, nom)

        val sources = listOf(
            Prefs.audioSource(ctx),
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        ).distinct()

        for (src in sources) {
            val r = nouveauRecorder(ctx)
            try {
                r.setAudioSource(src)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(64_000)
                r.setAudioSamplingRate(44_100)
                r.setOutputFile(fichier.absolutePath)
                r.prepare()
                r.start()
                rec = r
                chemin = fichier.absolutePath
                Prefs.setRecording(ctx, fichier.absolutePath, System.currentTimeMillis())
                return true
            } catch (e: Exception) {
                runCatching { r.release() }
                fichier.delete()
            }
        }
        return false
    }

    @Synchronized
    fun stop(ctx: Context) {
        val r = rec ?: return
        val f = chemin
        try {
            r.stop()
        } catch (e: Exception) {
            // Enregistrement trop court ou invalide : on le supprime
            f?.let { File(it).delete() }
            Prefs.setRecording(ctx, null, 0L)
        }
        runCatching { r.release() }
        rec = null
        chemin = null
    }

    @Suppress("DEPRECATION")
    private fun nouveauRecorder(ctx: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
}
