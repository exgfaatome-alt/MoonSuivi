package com.moonsuivi.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.moonsuivi.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class InfoMaj(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val notes: String
)

/**
 * Mise à jour depuis un site web.
 * Le site publie un fichier version.json :
 *   {"versionCode": 12, "versionName": "1.12", "apk": "https://.../MoonSuivi.apk", "notes": "..."}
 * Le workflow GitHub fourni le publie automatiquement dans les « Releases » du dépôt.
 */
object MiseAJour {

    val versionActuelle: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    /** Renvoie la nouvelle version si elle est plus récente, sinon null. Bloquant. */
    fun verifier(url: String): InfoMaj? {
        val c = ouvrir(url)
        val texte = try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        val j = JSONObject(texte)
        val info = InfoMaj(
            versionCode = j.getInt("versionCode"),
            versionName = j.optString("versionName", ""),
            apk = j.getString("apk"),
            notes = j.optString("notes", "")
        )
        return if (info.versionCode > BuildConfig.VERSION_CODE) info else null
    }

    /** Télécharge l'APK dans le cache de l'appli. Bloquant. */
    fun telecharger(ctx: Context, info: InfoMaj, progres: (Float) -> Unit): File {
        val dossier = File(ctx.cacheDir, "maj").apply { mkdirs() }
        val f = File(dossier, "MoonSuivi.apk")
        val c = ouvrir(info.apk)
        try {
            val total = c.contentLengthLong
            c.inputStream.use { entree ->
                f.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var lu = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        lu += n
                        if (total > 0) progres(lu.toFloat() / total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return f
    }

    /** Peut-on lancer l'installateur ? Sinon il faut autoriser « Installer des applis inconnues ». */
    fun installationAutorisee(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun ouvrirAutorisation(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    fun installer(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Suit les redirections (GitHub redirige vers son serveur de fichiers). */
    private fun ouvrir(adresse: String): HttpURLConnection {
        var url = URL(adresse)
        repeat(6) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "MoonSuivi")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("Redirection invalide")
                url = URL(url, loc)
            } else if (code == 200) {
                return c
            } else {
                c.disconnect()
                throw IOException(
                    if (code == 404) "Aucune version publiée trouvée (404). Le dépôt GitHub est-il public ?"
                    else "Erreur HTTP $code"
                )
            }
        }
        throw IOException("Trop de redirections")
    }
}
