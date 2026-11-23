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

private class Db(ctx: Context) : SQLiteOpenHelper(ctx, "moonsuivi.db", null, 4) {
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
        migrer(db, 1)
    }

    private fun migrer(db: SQLiteDatabase, depuis: Int) {
        if (depuis < 2) creerEchanges(db)
        if (depuis < 3) versionTrois(db)
        if (depuis < 4) versionQuatre(db)
    }

    /** v3 : colonnes configurables, ordre manuel des tâches, catégories, journal. */
    private fun versionTrois(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS colonnes(id INTEGER PRIMARY KEY AUTOINCREMENT, nom TEXT NOT NULL, " +
                "ordre INTEGER NOT NULL, couleur INTEGER NOT NULL, terminee INTEGER NOT NULL DEFAULT 0, " +
                "limite INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "INSERT INTO colonnes(id, nom, ordre, couleur, terminee) VALUES " +
                "(1, 'À faire', 0, ${0xFF1976D2}, 0), (2, 'En cours', 1, ${0xFFF57C00}, 0), " +
                "(3, 'En attente', 2, ${0xFF8E24AA}, 0), (4, 'Terminé', 3, ${0xFF2E7D32}, 1)"
        )
        db.execSQL("ALTER TABLE taches ADD COLUMN colonne_id INTEGER")
        db.execSQL("ALTER TABLE taches ADD COLUMN position REAL NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE taches SET colonne_id = CASE colonne WHEN 'EN_COURS' THEN 2 WHEN 'EN_ATTENTE' THEN 3 " +
                "WHEN 'TERMINE' THEN 4 ELSE 1 END, position = id"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_taches_colonne ON taches(colonne_id)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS categories(id INTEGER PRIMARY KEY AUTOINCREMENT, nom TEXT NOT NULL, " +
                "couleur INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO categories(nom, couleur) VALUES ('Client', ${0xFF1976D2}), " +
                "('Projet', ${0xFF00897B}), ('Administratif', ${0xFF546E7A})"
        )
        db.execSQL("ALTER TABLE sujets ADD COLUMN categorie_id INTEGER")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS journal(id INTEGER PRIMARY KEY AUTOINCREMENT, date INTEGER NOT NULL, " +
                "type TEXT NOT NULL, texte TEXT NOT NULL, sujet_id INTEGER, contact_id INTEGER)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_date ON journal(date)")
    }

    /** v4 : pièces du dossier (photos, documents, pièces jointes d'e-mails). */
    private fun versionQuatre(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS documents(id INTEGER PRIMARY KEY AUTOINCREMENT, sujet_id INTEGER, " +
                "echange_id INTEGER, tache_id INTEGER, type TEXT NOT NULL, nom TEXT NOT NULL, chemin TEXT NOT NULL, " +
                "mime TEXT NOT NULL DEFAULT '', taille INTEGER NOT NULL DEFAULT 0, date INTEGER NOT NULL, " +
                "note TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_sujet ON documents(sujet_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_documents_echange ON documents(echange_id)")
    }

    private fun creerEchanges(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS echanges(id INTEGER PRIMARY KEY AUTOINCREMENT, canal TEXT NOT NULL, " +
                "contact_id INTEGER, sujet_id INTEGER, adresse TEXT NOT NULL DEFAULT '', objet TEXT NOT NULL DEFAULT '', " +
                "contenu TEXT NOT NULL DEFAULT '', date INTEGER NOT NULL, message_id TEXT)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_echanges_mid ON echanges(message_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_echanges_date ON echanges(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_echanges_contact ON echanges(contact_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        migrer(db, oldVersion)
    }
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

fun emailsDe(c: Contact): List<String> =
    c.email.split(',', ';', ' ').map { it.trim() }.filter { it.contains('@') }

fun telephonesDe(c: Contact): List<String> =
    c.telephone.split('/', ',', ';').map { it.trim() }.filter { it.isNotBlank() }

/** « RE: TR: Devis » -> « devis » */
fun normaliserObjet(objet: String): String {
    var o = objet.trim()
    val prefixe = Regex("^(re|tr|fw|fwd|réf|ref|aw|wg)\\s*(\\[\\d+\\])?\\s*:\\s*", RegexOption.IGNORE_CASE)
    while (true) {
        val m = prefixe.find(o) ?: break
        o = o.substring(m.range.last + 1).trim()
    }
    return o.lowercase()
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
        categorieId = c.lngN("categorie_id"),
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
        val avant = if (s.id == 0L) null else sujet(s.id)
        val cv = ContentValues().apply {
            put("titre", s.titre)
            put("description", s.description)
            put("statut", s.statut.name)
            put("priorite", s.priorite)
            putN("categorie_id", s.categorieId)
            put("updated_at", now())
        }
        val id = if (s.id == 0L) {
            cv.put("created_at", s.createdAt)
            w.insert("sujets", null, cv)
        } else {
            w.update("sujets", cv, "id=?", arrayOf(s.id.toString()))
            s.id
        }
        if (avant == null) {
            journaliser(TypeJournal.SUJET, "Nouveau sujet : ${s.titre}", sujetId = id)
        } else if (avant.statut != s.statut) {
            journaliser(TypeJournal.SUJET, "« ${s.titre} » : ${avant.statut.label} → ${s.statut.label}", sujetId = id)
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
        w.execSQL("UPDATE echanges SET sujet_id=NULL WHERE sujet_id=?", arrayOf<Any>(id))
        w.execSQL("UPDATE journal SET sujet_id=NULL WHERE sujet_id=?", arrayOf<Any>(id))
        // Les photos et documents ajoutés au dossier sont supprimés avec lui
        list("SELECT * FROM documents WHERE sujet_id=? AND echange_id IS NULL", a, ::documentOf).forEach {
            runCatching { File(it.chemin).delete() }
        }
        w.delete("documents", "sujet_id=? AND echange_id IS NULL", a)
        notifyChanged()
    }

    fun tachesOuvertesParSujet(): Map<Long, Int> = list(
        "SELECT sujet_id, COUNT(*) AS n FROM taches WHERE $OUVERTE AND sujet_id IS NOT NULL GROUP BY sujet_id",
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
        w.execSQL("UPDATE echanges SET contact_id=NULL WHERE contact_id=?", arrayOf<Any>(id))
        w.execSQL("UPDATE journal SET contact_id=NULL WHERE contact_id=?", arrayOf<Any>(id))
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
        colonneId = c.lngN("colonne_id") ?: 0L,
        position = c.getDouble(c.getColumnIndexOrThrow("position")),
        priorite = c.lng("priorite").toInt(),
        echeance = c.lngN("echeance"),
        sujetId = c.lngN("sujet_id"),
        contactId = c.lngN("contact_id"),
        createdAt = c.lng("created_at")
    )

    private const val ORDRE_TACHES =
        " ORDER BY priorite DESC, CASE WHEN echeance IS NULL THEN 1 ELSE 0 END, echeance, created_at"
    private const val OUVERTE = "IFNULL(colonne_id, 0) NOT IN (SELECT id FROM colonnes WHERE terminee = 1)"
    private const val ORDRE_FAITES_EN_DERNIER =
        " ORDER BY CASE WHEN $OUVERTE THEN 0 ELSE 1 END, priorite DESC, echeance"

    /** Toutes les tâches, dans l'ordre manuel du kanban. */
    fun taches(): List<Tache> = list("SELECT * FROM taches ORDER BY position, created_at", null, ::tacheOf)

    fun tachesOuvertes(): List<Tache> = list("SELECT * FROM taches WHERE $OUVERTE$ORDRE_TACHES", null, ::tacheOf)

    fun tachesSujet(id: Long): List<Tache> =
        list("SELECT * FROM taches WHERE sujet_id=?" +
            ORDRE_FAITES_EN_DERNIER,
            arrayOf(id.toString()), ::tacheOf)

    fun tachesContact(id: Long): List<Tache> =
        list("SELECT * FROM taches WHERE contact_id=?" +
            ORDRE_FAITES_EN_DERNIER,
            arrayOf(id.toString()), ::tacheOf)

    fun tachesEntre(debut: Long, fin: Long): List<Tache> =
        list("SELECT * FROM taches WHERE echeance >= ? AND echeance < ?$ORDRE_TACHES",
            arrayOf(debut.toString(), fin.toString()), ::tacheOf)

    fun tache(id: Long): Tache? =
        list("SELECT * FROM taches WHERE id=?", arrayOf(id.toString()), ::tacheOf).firstOrNull()

    private fun positionFin(colonneId: Long): Double =
        r.rawQuery("SELECT IFNULL(MAX(position), 0) FROM taches WHERE colonne_id=?", arrayOf(colonneId.toString()))
            .use { c -> if (c.moveToFirst()) c.getDouble(0) + 1 else 1.0 }

    fun saveTache(t: Tache): Long {
        val avant = if (t.id == 0L) null else tache(t.id)
        val colonne = if (t.colonneId == 0L) premiereColonneId() else t.colonneId
        val cv = ContentValues().apply {
            put("titre", t.titre)
            put("description", t.description)
            put("colonne", "")
            put("colonne_id", colonne)
            put("priorite", t.priorite)
            putN("echeance", t.echeance)
            putN("sujet_id", t.sujetId)
            putN("contact_id", t.contactId)
        }
        val id = if (t.id == 0L) {
            cv.put("created_at", t.createdAt)
            cv.put("position", positionFin(colonne))
            w.insert("taches", null, cv)
        } else {
            if (avant != null && avant.colonneId != colonne) cv.put("position", positionFin(colonne))
            w.update("taches", cv, "id=?", arrayOf(t.id.toString())); t.id
        }
        if (avant == null) {
            journaliser(TypeJournal.TACHE, "Nouvelle tâche : ${t.titre}", t.sujetId, t.contactId)
        } else if (avant.colonneId != colonne) {
            journaliserDeplacement(t.titre, avant.colonneId, colonne, t.sujetId, t.contactId)
        }
        if (avant != null && avant.priorite != t.priorite) {
            journaliser(
                TypeJournal.TACHE,
                "« ${t.titre} » : priorité ${libellePriorite(avant.priorite).lowercase()} → ${libellePriorite(t.priorite).lowercase()}",
                t.sujetId, t.contactId
            )
        }
        toucherSujet(t.sujetId)
        notifyChanged()
        return id
    }

    /** Déplace une tâche dans une colonne, à la place [index] (glisser-déposer). */
    fun deplacerTache(id: Long, colonneId: Long, index: Int) {
        val t = tache(id) ?: return
        val autres = list(
            "SELECT * FROM taches WHERE colonne_id=? AND id != ? ORDER BY position, created_at",
            arrayOf(colonneId.toString(), id.toString()), ::tacheOf
        ).map { it.id }.toMutableList()
        autres.add(index.coerceIn(0, autres.size), id)
        val db = w
        db.beginTransaction()
        try {
            autres.forEachIndexed { i, tid ->
                db.execSQL("UPDATE taches SET position=?, colonne_id=? WHERE id=?", arrayOf<Any>(i.toDouble(), colonneId, tid))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (t.colonneId != colonneId) {
            journaliserDeplacement(t.titre, t.colonneId, colonneId, t.sujetId, t.contactId)
            toucherSujet(t.sujetId)
        }
        notifyChanged()
    }

    /** Déplace une tâche à la fin d'une colonne. */
    fun moveTache(id: Long, colonneId: Long) = deplacerTache(id, colonneId, Int.MAX_VALUE)

    private fun journaliserDeplacement(titre: String, de: Long, vers: Long, sujetId: Long?, contactId: Long?) {
        val cols = colonnes().associateBy { it.id }
        val cible = cols[vers] ?: return
        val texte = if (cible.terminee) "Tâche terminée : $titre"
        else "« $titre » : ${cols[de]?.nom ?: "?"} → ${cible.nom}"
        journaliser(TypeJournal.TACHE, texte, sujetId, contactId)
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

    fun findContactByEmail(email: String): Contact? {
        val e = email.trim()
        if (e.isEmpty()) return null
        return contacts().firstOrNull { ct -> emailsDe(ct).any { it.equals(e, ignoreCase = true) } }
    }

    // ---------- Échanges (e-mails / SMS) ----------
    private fun echangeOf(c: Cursor) = Echange(
        id = c.lng("id"),
        canal = runCatching { Canal.valueOf(c.str("canal")) }.getOrDefault(Canal.EMAIL_RECU),
        contactId = c.lngN("contact_id"),
        sujetId = c.lngN("sujet_id"),
        adresse = c.str("adresse"),
        objet = c.str("objet"),
        contenu = c.str("contenu"),
        date = c.lng("date"),
        messageId = c.strN("message_id")
    )

    fun echanges(): List<Echange> =
        list("SELECT * FROM echanges ORDER BY date DESC LIMIT 500", null, ::echangeOf)

    fun echangesContact(id: Long): List<Echange> =
        list("SELECT * FROM echanges WHERE contact_id=? ORDER BY date DESC", arrayOf(id.toString()), ::echangeOf)

    fun echangesSujet(id: Long): List<Echange> =
        list("SELECT * FROM echanges WHERE sujet_id=? ORDER BY date DESC", arrayOf(id.toString()), ::echangeOf)

    fun echangeExiste(messageId: String): Boolean =
        r.rawQuery("SELECT COUNT(*) FROM echanges WHERE message_id=?", arrayOf(messageId)).use { c ->
            c.moveToFirst() && c.getLong(0) > 0
        }

    /** Retrouve le sujet d'un fil de discussion : même contact + même objet (sans Re:, TR:...). */
    fun sujetDuFil(contactId: Long, objet: String): Long? {
        val o = normaliserObjet(objet)
        if (o.isEmpty()) return null
        return list(
            "SELECT * FROM echanges WHERE contact_id=? AND sujet_id IS NOT NULL ORDER BY date DESC LIMIT 200",
            arrayOf(contactId.toString()), ::echangeOf
        ).firstOrNull { normaliserObjet(it.objet) == o }?.sujetId
    }

    fun saveEchange(e: Echange, notifier: Boolean = true): Long {
        val cv = ContentValues().apply {
            put("canal", e.canal.name)
            putN("contact_id", e.contactId)
            putN("sujet_id", e.sujetId)
            put("adresse", e.adresse)
            put("objet", e.objet)
            put("contenu", e.contenu)
            put("date", e.date)
            if (e.messageId == null) putNull("message_id") else put("message_id", e.messageId)
        }
        val id = if (e.id == 0L) w.insertWithOnConflict("echanges", null, cv, SQLiteDatabase.CONFLICT_IGNORE) else {
            w.update("echanges", cv, "id=?", arrayOf(e.id.toString())); e.id
        }
        toucherSujet(e.sujetId)
        if (notifier) notifyChanged()
        return id
    }

    fun deleteEchange(id: Long) {
        documentsEchange(id).forEach { runCatching { File(it.chemin).delete() } }
        w.delete("documents", "echange_id=?", arrayOf(id.toString()))
        w.delete("echanges", "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------- Colonnes du kanban ----------
    private fun colonneOf(c: Cursor) = Colonne(
        id = c.lng("id"),
        nom = c.str("nom"),
        ordre = c.lng("ordre").toInt(),
        couleur = c.lng("couleur"),
        terminee = c.lng("terminee") != 0L,
        limite = c.lng("limite").toInt()
    )

    fun colonnes(): List<Colonne> = list("SELECT * FROM colonnes ORDER BY ordre, id", null, ::colonneOf)

    fun premiereColonneId(): Long = colonnes().firstOrNull()?.id ?: run {
        saveColonne(Colonne(nom = "À faire"))
    }

    fun saveColonne(c: Colonne): Long {
        val cv = ContentValues().apply {
            put("nom", c.nom)
            put("couleur", c.couleur)
            put("terminee", if (c.terminee) 1 else 0)
            put("limite", c.limite)
        }
        val id = if (c.id == 0L) {
            val ordre = r.rawQuery("SELECT IFNULL(MAX(ordre), -1) + 1 FROM colonnes", null)
                .use { k -> if (k.moveToFirst()) k.getInt(0) else 0 }
            cv.put("ordre", ordre)
            w.insert("colonnes", null, cv)
        } else {
            w.update("colonnes", cv, "id=?", arrayOf(c.id.toString())); c.id
        }
        notifyChanged()
        return id
    }

    /** Enregistre le nouvel ordre des colonnes. */
    fun ordonnerColonnes(ids: List<Long>) {
        val db = w
        db.beginTransaction()
        try {
            ids.forEachIndexed { i, id -> db.execSQL("UPDATE colonnes SET ordre=? WHERE id=?", arrayOf<Any>(i, id)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        notifyChanged()
    }

    /** Supprime une colonne ; ses tâches sont déplacées dans [versId]. */
    fun deleteColonne(id: Long, versId: Long) {
        if (id == versId) return
        val fin = positionFin(versId)
        w.execSQL("UPDATE taches SET colonne_id=?, position=position+? WHERE colonne_id=?", arrayOf<Any>(versId, fin, id))
        w.delete("colonnes", "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------- Catégories de sujets ----------
    private fun categorieOf(c: Cursor) = Categorie(c.lng("id"), c.str("nom"), c.lng("couleur"))

    fun categories(): List<Categorie> =
        list("SELECT * FROM categories ORDER BY nom COLLATE NOCASE", null, ::categorieOf)

    fun saveCategorie(c: Categorie): Long {
        val cv = ContentValues().apply {
            put("nom", c.nom)
            put("couleur", c.couleur)
        }
        val id = if (c.id == 0L) w.insert("categories", null, cv) else {
            w.update("categories", cv, "id=?", arrayOf(c.id.toString())); c.id
        }
        notifyChanged()
        return id
    }

    fun deleteCategorie(id: Long) {
        w.delete("categories", "id=?", arrayOf(id.toString()))
        w.execSQL("UPDATE sujets SET categorie_id=NULL WHERE categorie_id=?", arrayOf<Any>(id))
        notifyChanged()
    }

    // ---------- Journal ----------
    private fun journalOf(c: Cursor) = EntreeJournal(
        id = c.lng("id"),
        date = c.lng("date"),
        type = runCatching { TypeJournal.valueOf(c.str("type")) }.getOrDefault(TypeJournal.NOTE),
        texte = c.str("texte"),
        sujetId = c.lngN("sujet_id"),
        contactId = c.lngN("contact_id")
    )

    fun journal(depuis: Long): List<EntreeJournal> =
        list("SELECT * FROM journal WHERE date >= ? ORDER BY date DESC", arrayOf(depuis.toString()), ::journalOf)

    fun journalRecent(n: Int): List<EntreeJournal> =
        list("SELECT * FROM journal ORDER BY date DESC LIMIT $n", null, ::journalOf)

    private fun journaliser(type: TypeJournal, texte: String, sujetId: Long? = null, contactId: Long? = null) {
        val cv = ContentValues().apply {
            put("date", now())
            put("type", type.name)
            put("texte", texte)
            putN("sujet_id", sujetId)
            putN("contact_id", contactId)
        }
        w.insert("journal", null, cv)
    }

    fun saveJournal(e: EntreeJournal): Long {
        val cv = ContentValues().apply {
            put("date", e.date)
            put("type", e.type.name)
            put("texte", e.texte)
            putN("sujet_id", e.sujetId)
            putN("contact_id", e.contactId)
        }
        val id = if (e.id == 0L) w.insert("journal", null, cv) else {
            w.update("journal", cv, "id=?", arrayOf(e.id.toString())); e.id
        }
        notifyChanged()
        return id
    }

    fun deleteJournal(id: Long) {
        w.delete("journal", "id=?", arrayOf(id.toString()))
        notifyChanged()
    }

    // ---------- Pour le journal et le tableau de bord ----------
    fun notesDepuis(depuis: Long): List<NoteSujet> =
        list("SELECT * FROM notes WHERE date >= ? ORDER BY date DESC", arrayOf(depuis.toString())) { c ->
            NoteSujet(c.lng("id"), c.lng("sujet_id"), c.str("texte"), c.lng("date"))
        }

    fun appelsDepuis(depuis: Long): List<Appel> =
        list("SELECT * FROM appels WHERE date >= ? ORDER BY date DESC", arrayOf(depuis.toString()), ::appelOf)

    fun echangesDepuis(depuis: Long): List<Echange> =
        list("SELECT * FROM echanges WHERE date >= ? ORDER BY date DESC", arrayOf(depuis.toString()), ::echangeOf)

    // ---------- Dossier : photos, documents, pièces jointes ----------
    private fun documentOf(c: Cursor) = Document(
        id = c.lng("id"),
        sujetId = c.lngN("sujet_id"),
        echangeId = c.lngN("echange_id"),
        tacheId = c.lngN("tache_id"),
        type = runCatching { TypeDocument.valueOf(c.str("type")) }.getOrDefault(TypeDocument.FICHIER),
        nom = c.str("nom"),
        chemin = c.str("chemin"),
        mime = c.str("mime"),
        taille = c.lng("taille"),
        date = c.lng("date"),
        note = c.str("note")
    )

    /** Pièces du dossier : ajoutées directement, ou jointes à un e-mail rattaché au sujet. */
    fun documentsSujet(sujetId: Long): List<Document> = list(
        "SELECT * FROM documents WHERE sujet_id=? OR echange_id IN (SELECT id FROM echanges WHERE sujet_id=?) " +
            "ORDER BY date DESC",
        arrayOf(sujetId.toString(), sujetId.toString()), ::documentOf
    )

    fun documentsEchange(echangeId: Long): List<Document> =
        list("SELECT * FROM documents WHERE echange_id=? ORDER BY nom", arrayOf(echangeId.toString()), ::documentOf)

    fun nbDocumentsParSujet(): Map<Long, Int> = list(
        "SELECT s, COUNT(*) AS n FROM (SELECT IFNULL(d.sujet_id, e.sujet_id) AS s FROM documents d " +
            "LEFT JOIN echanges e ON e.id = d.echange_id) WHERE s IS NOT NULL GROUP BY s",
        null
    ) { c -> c.lng("s") to c.lng("n").toInt() }.toMap()

    fun saveDocument(d: Document, notifier: Boolean = true): Long {
        val cv = ContentValues().apply {
            putN("sujet_id", d.sujetId)
            putN("echange_id", d.echangeId)
            putN("tache_id", d.tacheId)
            put("type", d.type.name)
            put("nom", d.nom)
            put("chemin", d.chemin)
            put("mime", d.mime)
            put("taille", d.taille)
            put("date", d.date)
            put("note", d.note)
        }
        val id = if (d.id == 0L) w.insert("documents", null, cv) else {
            w.update("documents", cv, "id=?", arrayOf(d.id.toString())); d.id
        }
        if (d.id == 0L && d.echangeId == null && d.sujetId != null) {
            journaliser(TypeJournal.SUJET, "${d.type.icone} Ajouté au dossier : ${d.nom}", sujetId = d.sujetId)
            toucherSujet(d.sujetId)
        }
        if (notifier) notifyChanged()
        return id
    }

    fun deleteDocument(d: Document) {
        runCatching { File(d.chemin).delete() }
        w.delete("documents", "id=?", arrayOf(d.id.toString()))
        notifyChanged()
    }
}
