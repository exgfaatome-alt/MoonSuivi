package com.moonsuivi.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.moonsuivi.app.data.*

fun optionsSujets(sujets: List<Sujet>): List<Pair<Long?, String>> =
    listOf<Pair<Long?, String>>(null to "Aucun") + sujets.map { it.id to it.titre }

fun optionsContacts(contacts: List<Contact>): List<Pair<Long?, String>> =
    listOf<Pair<Long?, String>>(null to "Aucun") + contacts.map { it.id to it.nom }

@Composable
fun TacheDialog(initial: Tache, onDismiss: () -> Unit) {
    var t by remember(initial) { mutableStateOf(initial) }
    val sujets = rememberData { Repo.sujets() }
    val contacts = rememberData { Repo.contacts() }
    FormDialog(
        titre = if (initial.id == 0L) "Nouvelle tâche" else "Modifier la tâche",
        onDismiss = onDismiss,
        onSave = { Repo.saveTache(t.copy(titre = t.titre.trim())); onDismiss() },
        saveEnabled = t.titre.isNotBlank(),
        onDelete = if (initial.id != 0L) ({ Repo.deleteTache(initial.id); onDismiss() }) else null
    ) {
        OutlinedTextField(
            value = t.titre, onValueChange = { t = t.copy(titre = it) },
            label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = t.description, onValueChange = { t = t.copy(description = it) },
            label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        ChoiceField("Colonne", Colonne.values().map { it to it.label }, t.colonne, { t = t.copy(colonne = it) })
        ChoiceField("Priorité", PRIORITES, t.priorite, { t = t.copy(priorite = it) })
        DateField("Échéance", t.echeance) { t = t.copy(echeance = it) }
        ChoiceField("Sujet", optionsSujets(sujets), t.sujetId, { t = t.copy(sujetId = it) })
        ChoiceField("Contact", optionsContacts(contacts), t.contactId, { t = t.copy(contactId = it) })
    }
}

@Composable
fun SujetDialog(initial: Sujet, onDismiss: () -> Unit) {
    var s by remember(initial) { mutableStateOf(initial) }
    FormDialog(
        titre = if (initial.id == 0L) "Nouveau sujet" else "Modifier le sujet",
        onDismiss = onDismiss,
        onSave = { Repo.saveSujet(s.copy(titre = s.titre.trim())); onDismiss() },
        saveEnabled = s.titre.isNotBlank(),
        onDelete = if (initial.id != 0L) ({ Repo.deleteSujet(initial.id); onDismiss() }) else null
    ) {
        OutlinedTextField(
            value = s.titre, onValueChange = { s = s.copy(titre = it) },
            label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = s.description, onValueChange = { s = s.copy(description = it) },
            label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth()
        )
        ChoiceField("Statut", StatutSujet.values().map { it to it.label }, s.statut, { s = s.copy(statut = it) })
        ChoiceField("Priorité", PRIORITES, s.priorite, { s = s.copy(priorite = it) })
    }
}

@Composable
fun ContactDialog(initial: Contact, onDismiss: () -> Unit) {
    var c by remember(initial) { mutableStateOf(initial) }
    FormDialog(
        titre = if (initial.id == 0L) "Nouveau contact" else "Modifier le contact",
        onDismiss = onDismiss,
        onSave = { Repo.saveContact(c.copy(nom = c.nom.trim())); onDismiss() },
        saveEnabled = c.nom.isNotBlank(),
        onDelete = if (initial.id != 0L) ({ Repo.deleteContact(initial.id); onDismiss() }) else null
    ) {
        OutlinedTextField(
            value = c.nom, onValueChange = { c = c.copy(nom = it) },
            label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = c.societe, onValueChange = { c = c.copy(societe = it) },
            label = { Text("Société") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = c.telephone, onValueChange = { c = c.copy(telephone = it) },
            label = { Text("Téléphone(s) — séparés par /") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = c.email, onValueChange = { c = c.copy(email = it) },
            label = { Text("E-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = c.notes, onValueChange = { c = c.copy(notes = it) },
            label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AppelDialog(initial: Appel, onDismiss: () -> Unit) {
    var a by remember(initial) { mutableStateOf(initial) }
    val sujets = rememberData { Repo.sujets() }
    val contacts = rememberData { Repo.contacts() }
    FormDialog(
        titre = "Appel ${a.type.label.lowercase()}",
        onDismiss = onDismiss,
        onSave = { Repo.saveAppel(a); onDismiss() },
        onDelete = { Repo.deleteAppel(initial); onDismiss() }
    ) {
        Text("${a.numero.ifBlank { "Numéro masqué" }}\n${jourHeure(a.date)} · ${duree(a.dureeSec)}")
        if (a.fichier != null) Text("🎙 Enregistrement disponible", style = MaterialTheme.typography.bodySmall)
        ChoiceField("Contact", optionsContacts(contacts), a.contactId, { a = a.copy(contactId = it) })
        if (a.contactId == null && a.numero.isNotBlank()) {
            TextButton(onClick = {
                val id = Repo.saveContact(Contact(nom = a.numero, telephone = a.numero))
                a = a.copy(contactId = id)
            }) { Text("Créer un contact avec ce numéro") }
        }
        ChoiceField("Sujet", optionsSujets(sujets), a.sujetId, { a = a.copy(sujetId = it) })
        OutlinedTextField(
            value = a.note, onValueChange = { a = a.copy(note = it) },
            label = { Text("Compte rendu / note") }, minLines = 3, modifier = Modifier.fillMaxWidth()
        )
    }
}
