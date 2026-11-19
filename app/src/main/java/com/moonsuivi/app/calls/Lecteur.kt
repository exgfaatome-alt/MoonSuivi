package com.moonsuivi.app.calls

import android.media.MediaPlayer
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** Lecture des enregistrements dans l'appli. */
object Lecteur {
    private var mp: MediaPlayer? = null
    val enCours: MutableState<String?> = mutableStateOf(null)

    fun basculer(chemin: String) {
        if (enCours.value == chemin) {
            stop(); return
        }
        stop()
        try {
            mp = MediaPlayer().apply {
                setDataSource(chemin)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            enCours.value = chemin
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        runCatching { mp?.release() }
        mp = null
        enCours.value = null
    }
}
