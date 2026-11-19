package com.moonsuivi.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

private class Db(ctx: Context) : SQLiteOpenHelper(ctx, "moonsuivi.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE sujets(id INTEGER PRIMARY KEY AUTOINCREMENT, titre TEXT NOT NULL, " +
                "description TEXT NOT NULL DEFAULT '', statut TEXT NOT NULL, priorite INTEGER NOT NULL DEFAULT 1, " +
                "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE contacts(id INTEGER PRIMARY KEY AUTOINCREMENT, nom TEXT NOT NULL, " +
                "societe TEXT NOT NULL DEFAULT '', telephone TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '', " +
                "notes TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE taches(id INTEGER PRIMARY KEY AUTOINCREMENT, titre TEXT NOT NULL, " +
                "description TEXT NOT NULL DEFAULT '', colonne TEXT NOT NULL, priorite INTEGER NOT NULL DEFAULT 1, " +
                "echeance INTEGER, sujet_id INTEGER, contact_id INTEGER, created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE appels(id INTEGER PRIMARY KEY AUTOINCREMENT, numero TEXT NOT NULL DEFAULT '', " +
                "contact_id INTEGER, sujet_id INTEGER, type TEXT NOT NULL, date INTEGER NOT NULL, " +
                "duree INTEGER NOT NULL DEFAULT 0, fichier TEXT, note TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, sujet_id INTEGER NOT NULL, " +
                "texte TEXT NOT NULL, date INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_taches_sujet ON taches(sujet_id)")
        db.execSQL("CREATE INDEX idx_taches_echeance ON taches(echeance)")
        db.execSQL("CREATE INDEX idx_appels_date ON appels(date)")
        db.execSQL("CREATE INDEX idx_notes_sujet ON notes(sujet_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

private fun Cursor.str(c: String): String = getString(getColumnIndexOrThrow(c)) ?: ""
private fun Cursor.lng(c: String): Long = getLong(getColumnIndexOrThrow(c))
private fun Cursor.lngN(c: String): Long? {
    val i = getColumnIndexOrThrow(c)
    return if (isNull(i)) null else getLong(i)
}
private fun Cursor.strN(c: String): String? {
    val i = getColumnIndexOrThrow(c)
    return if (isNull(i)) null else getString(i)
}

private fun ContentValues.putN(k: String, v: Long?) {
    if (v == null) putNull(k) else put(k, v)
}

/** Garde uniquement les 9 derniers chiffres (ignore +33 / 0). */
fun normaliserNumero(n: String): String = n.filter { it.isDigit() }.takeLast(9)

/**
 * Accès aux données (SQLite local). Chaque écriture incrémente [version],
 * ce qui provoque le rafraîchissement automatique des écrans.
 */
object Repo {
    private lateinit var db: Db
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    fun init(ctx: Context) {
        if (!::db.isInitialized) synchronized(this) {
            if (!::db.isInitialized) db = Db(ctx.applicationContext)
        }
    }

    fun notifyChanged() = _version.update { it + 1 }

    private val r get() = db.readableDatabase
    private val w get() = db.writableDatabase

    private fun <T> list(sql: String, args: Array<String>?, map: (Cursor) -> T): List<T> =
        r.rawQuery(sql, args).use { c ->
            val out = ArrayList<T>()
            while (c.moveToNext()) out.add(map(c))
            out
        }

    private fun now() = System.currentTimeMillis()

    // ---------- Sujets ----------
    private fun sujetOf(c: Cursor) = Sujet(
        id = c.lng("id"),
        titre = c.str("titre"),
        description = c.str("description"),
        statut = runCatching { StatutSujet.valueOf(c.str("statut")) }.getOrDefault(StatutSujet.OUVERT),
        priorite = c.lng("priorite").toInt(),
        createdAt = c.lng("created_at"),
        updatedAt = c.lng("updated_at")
    )

    fun sujets(): List<Sujet> = list(
        "SELECT * FROM sujets ORDER BY CASE statut WHEN 'CLOS' THEN 1 ELSE 0 END, priorite DESC, updated_at DESC",
        null, ::sujetOf
    )

    fun sujet(id: Long): Sujet? =
        list("SELECT * FROM sujets WHERE id=?", arrayOf(id.toString()), ::sujetOf).firstOrNull()

    fun saveSujet(s: Sujet): Long {
        val cv = ContentValues().apply {
            put("titre", s.titre)
            put("description", s.description)
            put("statut", s.statut.name)
            put("priorite", s.priorite)
            put("updated_at", now())
        }
        val id = if (s.id == 0L) {
            cv.put("created_at", s.createdAt)
            w.insert("sujets", null, cv)
        } else {
            w.update("sujets", cv, "id=?", arrayOf(s.id.toString()))
            s.id
        }
        notifyChanged()
        return id
    }

    private fun toucherSujet(id: Long?) {
        if (id == null) return
        w.execSQL("UPDATE sujets SET updated_at=? WHERE id=?", arrayOf<Any>(now(), id))
    }

    fun deleteSujet(id: Long) {
        val a = arrayOf(id.toString())
        w.delete("sujets", "id=?", a)
        w.delete("notes", "sujet_id=?", a)
        w.execSQL("UPDATE taches SET sujet_id=NULL WHERE sujet_id=?", arrayOf<Any>(id))
        w.execSQL("UPDATE appels SET sujet_id=NULL WHERE sujet_id=?", arrayOf<Any>(id))
        notifyChanged()
    }

    fun tachesOuvertesParSujet(): Map<Long, Int> = list(
        "SELECT sujet_id, COUNT(*) AS n FROM taches WHERE colonne != 'TERMINE' AND sujet_id IS NOT NULL GROUP BY sujet_id",
        null
    ) { c -> c.lng("sujet_id") to c.lng("n").toInt() }.toMap()

    // ---------- Contacts ----------
    private fun contactOf(c: Cursor) = Contact(
        id = c.lng("id"),
        nom = c.str("nom"),
        societe = c.str("societe"),
        telephone = c.str("telephone"),
        email = c.str("email"),
        notes = c.str("notes")
    )

    fun contacts(): List<Contact> =
        list("SELECT * FROM contacts ORDER BY nom COLLATE NOCASE", null, ::contactOf)

    fun contact(id: Long): Contact? =
        list("SELECT * FROM contacts WHERE id=?", arrayOf(id.toString()), ::contactOf).firstOrNull()

    fun saveContact(ct: Contact, notifier: Boolean = true): Long {
        val cv = ContentValues().apply {
            put("nom", ct.nom)
            put("societe", ct.societe)
            put("telephone", ct.telephone)
            put("email", ct.email)
            put("notes", ct.notes)
        }
        val id = if (ct.id == 0L) w.insert("contacts", null, cv) else {
            w.update("contacts", cv, "id=?", arrayOf(ct.id.toString())); ct.id
        }
        if (notifier) notifyChanged()
        return id
    }

    fun deleteContact(id: Long) {
        w.delete("contacts", "id=?", arrayOf(id.toString()))
        w.execSQL("UPDATE taches SET contact_id=NULL WHERE contact_id=?", arrayOf<Any>(id))
        w.execSQL("UPDATE appels SET contact_id=NULL WHERE contact_id=?", arrayOf<Any>(id))
        notifyChanged()
    }

    fun findContactByPhone(numero: String): Contact? {
        val n = normaliserNumero(numero)
        if (n.length < 6) return null
        return contacts().firstOrNull { ct ->
            ct.telephone.split('/', ',', ';').any { normaliserNumero(it) == n }
        }
    }

    // ---------- Tâches ----------
    private fun tacheOf(c: Cursor) = Tache(
        id = c.lng("id"),
        titre = c.str("titre"),
        description = c.str("description"),
        colonne = runCatching { Colonne.valueOf(c.str("colonne")) }.getOrDefault(Colonne.A_FAIRE),
        priorite = c.lng("priorite").toInt(),
        echeance = c.lngN("echeance"),
        sujetId = c.lngN("sujet_id"),
        contactId = c.lngN("contact_id"),
        createdAt = c.lng("created_at")
    )

    private const val ORDRE_TACHES =
        " ORDER BY priorite DESC, CASE WHEN echeance IS NULL THEN 1 ELSE 0 END, echeance, created_at"

    fun taches(): List<Tache> = list("SELECT * FROM taches$ORDRE_TACHES", null, ::tacheOf)

    fun tachesSujet(id: Long): List<Tache> =
        list("SELECT * FROM taches WHERE sujet_id=?" +
            " ORDER BY CASE colonne WHEN 'TERMINE' THEN 1 ELSE 0 END, priorite DESC, echeance",
            arrayOf(id.toString()), ::tacheOf)

    fun tachesContact(id: Long): List<Tache> =
        list("SELECT * FROM taches WHERE contact_id=?" +
            " ORDER BY CASE colonne WHEN 'TERMINE' THEN 1 ELSE 0 END, priorite DESC, echeance",
            arrayOf(id.toString()), ::tacheOf)

    fun tachesEntre(debut: Long, fin: Long): List<Tache> =
        list("SELECT * FROM taches WHERE echeance >= ? AND echeance < ?$ORDRE_TACHES",
            arrayOf(debut.toString(), fin.toString()), ::tacheOf)

    fun saveTache(t: Tache): Long {
        val cv = ContentValues().apply {
            put("titre", t.titre)
            put("description", t.description)
            put("colonne", t.colonne.name)
            put("priorite", t.priorite)
            putN("echeance", t.echeance)
            putN("sujet_id", t.sujetId)
            putN("contact_id", t.contactId)
        }
        val id = if (t.id == 0L) {
            cv.put("created_at", t.createdAt)
            w.insert("taches", null, cv)
        } else {
            w.update("taches", cv, "id=?", arrayOf(t.id.toString())); t.id
        }
        toucherSujet(t.sujetId)
        notifyChanged()
        return id
    }

    fun moveTache(id: Long, colonne: Colonne) {
        w.execSQL("UPDATE taches SET colonne=? WHERE id=?", arrayOf<Any>(colonne.name, id))
        notifyChanged()
    }

    fun deleteTache(id: Long) {
        w.delete("taches", "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------- Appels ----------
    private fun appelOf(c: Cursor) = Appel(
        id = c.lng("id"),
        numero = c.str("numero"),
        contactId = c.lngN("contact_id"),
        sujetId = c.lngN("sujet_id"),
        type = runCatching { TypeAppel.valueOf(c.str("type")) }.getOrDefault(TypeAppel.AUTRE),
        date = c.lng("date"),
        dureeSec = c.lng("duree"),
        fichier = c.strN("fichier"),
        note = c.str("note")
    )

    fun appels(): List<Appel> = list("SELECT * FROM appels ORDER BY date DESC LIMIT 500", null, ::appelOf)

    fun appelsContact(id: Long): List<Appel> =
        list("SELECT * FROM appels WHERE contact_id=? ORDER BY date DESC", arrayOf(id.toString()), ::appelOf)

    fun appelsSujet(id: Long): List<Appel> =
        list("SELECT * FROM appels WHERE sujet_id=? ORDER BY date DESC", arrayOf(id.toString()), ::appelOf)

    fun appelsEntre(debut: Long, fin: Long): List<Appel> =
        list("SELECT * FROM appels WHERE date >= ? AND date < ? ORDER BY date",
            arrayOf(debut.toString(), fin.toString()), ::appelOf)

    fun appelExiste(date: Long): Boolean =
        r.rawQuery("SELECT COUNT(*) FROM appels WHERE date=?", arrayOf(date.toString())).use { c ->
            c.moveToFirst() && c.getLong(0) > 0
        }

    fun saveAppel(a: Appel, notifier: Boolean = true): Long {
        val cv = ContentValues().apply {
            put("numero", a.numero)
            putN("contact_id", a.contactId)
            putN("sujet_id", a.sujetId)
            put("type", a.type.name)
            put("date", a.date)
            put("duree", a.dureeSec)
            if (a.fichier == null) putNull("fichier") else put("fichier", a.fichier)
            put("note", a.note)
        }
        val id = if (a.id == 0L) w.insert("appels", null, cv) else {
            w.update("appels", cv, "id=?", arrayOf(a.id.toString())); a.id
        }
        toucherSujet(a.sujetId)
        if (notifier) notifyChanged()
        return id
    }

    fun deleteAppel(a: Appel) {
        a.fichier?.let { runCatching { File(it).delete() } }
        w.delete("appels", "id=?", arrayOf(a.id.toString()))
        notifyChanged()
    }

    // ---------- Notes de suivi ----------
    fun notes(sujetId: Long): List<NoteSujet> =
        list("SELECT * FROM notes WHERE sujet_id=? ORDER BY date DESC", arrayOf(sujetId.toString())) { c ->
            NoteSujet(c.lng("id"), c.lng("sujet_id"), c.str("texte"), c.lng("date"))
        }

    fun addNote(sujetId: Long, texte: String) {
        val cv = ContentValues().apply {
            put("sujet_id", sujetId)
            put("texte", texte)
            put("date", now())
        }
        w.insert("notes", null, cv)
        toucherSujet(sujetId)
        notifyChanged()
    }

    fun deleteNote(id: Long) {
        w.delete("notes", "id=?", arrayOf(id.toString()))
        notifyChanged()
    }
}
