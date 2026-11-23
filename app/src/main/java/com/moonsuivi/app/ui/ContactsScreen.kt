package com.moonsuivi.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.*

@Composable
fun ContactsScreen(ouvrir: (Ecran) -> Unit) {
    val ctx = LocalContext.current
    val contacts = rememberData { Repo.contacts() }
    var recherche by remember { mutableStateOf("") }
    var nouveau by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        message = if (ok) "${ContactsImport.importer(ctx)} contact(s) importé(s)" else "Permission refusée"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Rechercher") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        message = "${ContactsImport.importer(ctx)} contact(s) importé(s)"
                    } else {
                        demande.launch(Manifest.permission.READ_CONTACTS)
                    }
                }) { Text("Importer") }
            }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Astuce("contacts", "« Importer » récupère les contacts du téléphone. Renseignez l'adresse e-mail d'un contact pour suivre ses e-mails automatiquement.")
            }
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            val liste = contacts.filter {
                recherche.isBlank() || it.nom.contains(recherche, true) ||
                    it.societe.contains(recherche, true) || it.telephone.contains(recherche)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (liste.isEmpty()) item {
                    Vide("Aucun contact. Ajoutez-en avec + ou importez ceux du téléphone.")
                }
                items(liste, key = { it.id }) { c ->
                    ContactCarte(c) { ouvrir(Ecran.ContactDetail(c.id)) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nouveau = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Contact")
        }
    }

    if (nouveau) ContactDialog(Contact()) { nouveau = false }
}

@Composable
private fun Avatar(nom: String) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            nom.trim().firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ContactCarte(c: Contact, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.nom)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sous = listOf(c.societe, c.telephone).filter { it.isNotBlank() }.joinToString(" · ")
                if (sous.isNotBlank()) {
                    Text(sous, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (c.telephone.isNotBlank()) {
                BoutonIcone(Icons.Default.Phone, "Appeler", onClick = { appeler(ctx, c.telephone) })
            }
        }
    }
}

@Composable
fun ContactDetailScreen(id: Long, ouvrir: (Ecran) -> Unit) {
    val ctx = LocalContext.current
    val contact = rememberData(id) { Repo.contact(id) }
    val taches = rememberData(id) { Repo.tachesContact(id) }
    val appels = rememberData(id) { Repo.appelsContact(id) }
    val echanges = rememberData(id) { Repo.echangesContact(id) }
    val historique = remember(appels, echanges) { fusionner(appels, echanges) }
    var redaction by remember { mutableStateOf<Pair<TypeMessage, String?>?>(null) }
    var editEchange by remember { mutableStateOf<Echange?>(null) }
    val sujets = rememberData { Repo.sujets() }
    val nomSujet = remember(sujets) { sujets.associate { it.id to it.titre } }
    var edit by remember { mutableStateOf(false) }
    var editTache by remember { mutableStateOf<Tache?>(null) }
    var editAppel by remember { mutableStateOf<Appel?>(null) }

    if (contact == null) {
        Vide("Ce contact n'existe plus.")
        return
    }

    val sujetsLies = remember(taches, appels, echanges) {
        (taches.mapNotNull { it.sujetId } + appels.mapNotNull { it.sujetId } + echanges.mapNotNull { it.sujetId }).distinct()
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contact.nom)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.nom, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (contact.societe.isNotBlank()) Text(contact.societe)
                        }
                        BoutonIcone(Icons.Default.Edit, "Modifier le contact", onClick = { edit = true })
                    }
                    contact.telephone.split('/', ',', ';').map { it.trim() }.filter { it.isNotBlank() }.forEach { num ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null)
                            Spacer(Modifier.width(8.dp))
                            Text(num, modifier = Modifier.weight(1f))
                            IconButton(onClick = { redaction = TypeMessage.SMS to num }) {
                                Icon(Icons.Default.Send, "Envoyer un SMS")
                            }
                            FilledTonalButton(onClick = { appeler(ctx, num) }) { Text("Appeler") }
                        }
                    }
                    emailsDe(contact).forEach { mail ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, null)
                            Spacer(Modifier.width(8.dp))
                            Text(mail, modifier = Modifier.weight(1f))
                            FilledTonalButton(onClick = { redaction = TypeMessage.EMAIL to mail }) { Text("E-mail") }
                        }
                    }
                    if (contact.notes.isNotBlank()) {
                        Text(contact.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (sujetsLies.isNotEmpty()) {
            item { SectionTitre("Sujets liés") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sujetsLies) { sid ->
                        AssistChip(
                            onClick = { ouvrir(Ecran.SujetDetail(sid)) },
                            label = { Text(nomSujet[sid] ?: "Sujet") },
                            leadingIcon = { Icon(Icons.Default.Star, null) }
                        )
                    }
                }
            }
        }

        item {
            SectionTitre("Tâches (${taches.size})") {
                BoutonIcone(Icons.Default.Add, "Ajouter une tâche", onClick = { editTache = Tache(contactId = id) })
            }
        }
        if (taches.isEmpty()) item { Vide("Aucune tâche avec ce contact.") }
        items(taches, key = { "t${it.id}" }) { t ->
            TacheCarte(
                t = t,
                sujet = t.sujetId?.let { nomSujet[it] },
                contact = null,
                onClick = { editTache = t },
                onMove = { c -> Repo.moveTache(t.id, c) }
            )
        }

        item {
            SectionTitre("Échanges (${historique.size})") {
                IconButton(onClick = { redaction = TypeMessage.EMAIL to null }) {
                    Icon(Icons.Default.Send, "Nouveau message")
                }
            }
        }
        if (historique.isEmpty()) item { Vide("Aucun appel, e-mail ou SMS avec ce contact.") }
        items(historique, key = { it.cle }) { l ->
            LigneHistorique(l, emptyMap(), { editAppel = it }, { editEchange = it })
        }
    }

    if (edit) ContactDialog(contact) { edit = false }
    editTache?.let { TacheDialog(it) { editTache = null } }
    editAppel?.let { AppelDialog(it) { editAppel = null } }
    editEchange?.let { EchangeDialog(it) { editEchange = null } }
    redaction?.let { (type, dest) ->
        MessageDialog(type = type, contactId = id, destination = dest, onDismiss = { redaction = null })
    }
}
