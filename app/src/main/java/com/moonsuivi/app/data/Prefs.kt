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
}
