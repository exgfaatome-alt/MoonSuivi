package com.moonsuivi.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

/** Importe les contacts du téléphone (nom + numéros) sans créer de doublons. */
object ContactsImport {
    fun importer(ctx: Context): Int {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return 0
        Repo.init(ctx)
        val existants = Repo.contacts()
        val noms = existants.map { it.nom.lowercase() }.toMutableSet()
        val numeros = existants.flatMap { c -> c.telephone.split('/', ',', ';').map { normaliserNumero(it) } }
            .filter { it.length >= 6 }.toMutableSet()

        val parContact = LinkedHashMap<Long, Pair<String, MutableList<String>>>()
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val nom = c.getString(1) ?: continue
                val num = c.getString(2) ?: ""
                val entree = parContact.getOrPut(id) { nom to mutableListOf() }
                if (num.isNotBlank() && entree.second.none { normaliserNumero(it) == normaliserNumero(num) }) {
                    entree.second.add(num.trim())
                }
            }
        }

        var n = 0
        for ((nom, nums) in parContact.values) {
            if (nom.lowercase() in noms) continue
            if (nums.any { normaliserNumero(it) in numeros }) continue
            Repo.saveContact(Contact(nom = nom, telephone = nums.joinToString(" / ")), notifier = false)
            noms.add(nom.lowercase())
            nums.forEach { numeros.add(normaliserNumero(it)) }
            n++
        }
        if (n > 0) Repo.notifyChanged()
        return n
    }
}
