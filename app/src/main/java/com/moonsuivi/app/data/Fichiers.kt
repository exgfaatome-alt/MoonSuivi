package com.moonsuivi.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fichiers des dossiers : stockés dans Android/data/com.moonsuivi.app/files/Dossiers/<sujet>/
 * (supprimés si l'appli est désinstallée).
 */
object Fichiers {
    const val TAILLE_MAX_PJ = 15L * 1024 * 1024

    fun dossier(ctx: Context, sujetId: Long): File =
        File(ctx.getExternalFilesDir(null), "Dossiers/$sujetId").apply { mkdirs() }

    fun dossierPiecesJointes(ctx: Context, echangeId: Long): File =
        File(ctx.getExternalFilesDir(null), "PiecesJointes/$echangeId").apply { mkdirs() }

    private fun horodatage(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())

    /** Fichier vide où l'appareil photo enregistrera la prise de vue. */
    fun nouvellePhoto(ctx: Context, sujetId: Long): File =
        nomUnique(dossier(ctx, sujetId), "photo_${horodatage()}.jpg")

    fun uriPour(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fichiers", f)

    fun nomSur(nom: String): String =
        nom.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().ifBlank { "fichier" }.take(120)

    fun nomUnique(dossier: File, nom: String): File {
        val propre = nomSur(nom)
        var f = File(dossier, propre)
        if (!f.exists()) return f
        val base = propre.substringBeforeLast('.', propre)
        val ext = propre.substringAfterLast('.', "")
        var i = 2
        while (f.exists()) {
            f = File(dossier, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    fun mimeDe(nom: String): String {
        val ext = nom.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** Copie un fichier choisi par l'utilisateur dans le dossier du sujet. Bloquant. */
    fun importer(ctx: Context, uri: Uri, sujetId: Long): Document? {
        val cr = ctx.contentResolver
        var nom = "fichier"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { nom = it }
        }
        val mime = cr.getType(uri) ?: mimeDe(nom)
        if (!nom.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { nom = "$nom.$it" }
        }
        val dest = nomUnique(dossier(ctx, sujetId), nom)
        val entree = cr.openInputStream(uri) ?: return null
        entree.use { e -> dest.outputStream().use { s -> e.copyTo(s) } }
        return Document(
            sujetId = sujetId,
            type = if (mime.startsWith("image/")) TypeDocument.PHOTO else TypeDocument.FICHIER,
            nom = dest.name,
            chemin = dest.absolutePath,
            mime = mime,
            taille = dest.length()
        )
    }

    fun ouvrir(ctx: Context, d: Document) {
        val f = File(d.chemin)
        if (!f.exists()) return
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriPour(ctx, f), d.mime.ifBlank { mimeDe(d.nom) })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            ctx.startActivity(Intent.createChooser(i, "Ouvrir avec…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun partager(ctx: Context, docs: List<Document>) {
        val uris = ArrayList(docs.map { File(it.chemin) }.filter { it.exists() }.map { uriPour(ctx, it) })
        if (uris.isEmpty()) return
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(docs.first().mime.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            ctx.startActivity(Intent.createChooser(i, "Partager…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** E-mail avec pièces jointes via l'appli e-mail du téléphone. */
    fun emailAvecPieces(ctx: Context, a: String, objet: String, texte: String, fichiers: List<File>) {
        val uris = ArrayList(fichiers.filter { it.exists() }.map { uriPour(ctx, it) })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("message/rfc822")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(a.trim()))
            .putExtra(Intent.EXTRA_SUBJECT, objet)
            .putExtra(Intent.EXTRA_TEXT, texte)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            ctx.startActivity(Intent.createChooser(i, "Envoyer l'e-mail avec…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun tailleLisible(octets: Long): String = when {
        octets < 1024 -> "$octets o"
        octets < 1024 * 1024 -> "${octets / 1024} Ko"
        else -> String.format(Locale.FRANCE, "%.1f Mo", octets / 1024.0 / 1024.0)
    }

    /** Miniature d'une image, remise à l'endroit d'après les données EXIF. Bloquant. */
    fun miniature(chemin: String, cote: Int): Bitmap? {
        val f = File(chemin)
        if (!f.exists()) return null
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(chemin, opt)
        if (opt.outWidth <= 0 || opt.outHeight <= 0) return null
        var echelle = 1
        while (opt.outWidth / (echelle * 2) >= cote && opt.outHeight / (echelle * 2) >= cote) echelle *= 2
        val bmp = BitmapFactory.decodeFile(chemin, BitmapFactory.Options().apply { inSampleSize = echelle })
            ?: return null
        val rotation = runCatching {
            when (ExifInterface(chemin).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rotation == 0f) return bmp
        val m = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
