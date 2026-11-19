package com.moonsuivi.app.calls

import android.accessibilityservice.AccessibilityService
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityEvent
import com.moonsuivi.app.data.Prefs
import com.moonsuivi.app.data.Repo

/**
 * Depuis Android 10, seules les applis disposant d'un service d'accessibilité actif
 * peuvent capter le micro pendant un appel. Ce service ne lit rien à l'écran :
 * il écoute uniquement l'état des appels pour démarrer/arrêter l'enregistrement.
 */
@Suppress("DEPRECATION")
class CallAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var running = false
    }

    private var telephonie: TelephonyManager? = null

    private val ecouteur = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            val ctx = this@CallAccessibilityService
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK ->
                    if (Prefs.autoRecord(ctx)) Recorder.start(ctx)
                TelephonyManager.CALL_STATE_IDLE ->
                    Recorder.stop(ctx)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        Repo.init(this)
        telephonie = getSystemService(TelephonyManager::class.java)
        try {
            telephonie?.listen(ecouteur, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        runCatching { telephonie?.listen(ecouteur, PhoneStateListener.LISTEN_NONE) }
        Recorder.stop(this)
        super.onDestroy()
    }
}
