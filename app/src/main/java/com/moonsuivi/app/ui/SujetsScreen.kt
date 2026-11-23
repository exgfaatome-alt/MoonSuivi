package com.moonsuivi.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.*

@Composable
fun SujetsScreen(ouvrir: (Ecran) -> Unit) {
    val sujets = rememberData { Repo.sujets() }
    val ouvertes = rememberData { Repo.tachesOuvertesParSujet() }
    var recherche by remember { mutableStateOf("") }
    var filtre by remember { mutableStateOf<StatutSujet?>(null) }
    var nouveau by remember { mutableStateOf(false) }
    val categories = rememberData { Repo.categories() }
    val parCategorie = remember(categories) { categories.associateBy { it.id } }
    var filtreCat by remember { mutableStateOf<Long?>(null) }
    var gererCategories by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Rechercher un sujet") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(Modifier.padding(horizontal = 12.dp)) {
                Astuce(
                    "sujets",
                    "Un sujet = une affaire, un client, un projet. Ouvrez-le pour voir ses tâches, son suivi et son 📂 dossier " +
                        "(photos, documents, pièces jointes, appels, e-mails). Classez-les par catégorie avec « Gérer… »."
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(selected = filtre == null, onClick = { filtre = null }, label = { Text("Tous") })
                }
                items(StatutSujet.values().toList()) { s ->
                    FilterChip(
                        selected = filtre == s,
                        onClick = { filtre = if (filtre == s) null else s },
                        label = { Text(s.label) }
                    )
                }
            }
            // Catégories : -1 = sans catégorie
            LazyRow(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(selected = filtreCat == null, onClick = { filtreCat = null }, label = { Text("Toutes catégories") })
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = filtreCat == c.id,
                        onClick = { filtreCat = if (filtreCat == c.id) null else c.id },
                        label = { Text(c.nom) },
                        leadingIcon = { Point(Color(c.couleur)) }
                    )
                }
                item {
                    FilterChip(
                        selected = filtreCat == -1L,
                        onClick = { filtreCat = if (filtreCat == -1L) null else -1L },
                        label = { Text("Sans catégorie") }
                    )
                }
                item {
                    TextButton(onClick = { gererCategories = true }) { Text("Gérer…") }
                }
            }
            val liste = sujets.filter {
                (filtreCat == null || (filtreCat == -1L && it.categorieId == null) || it.categorieId == filtreCat) &&
                (filtre == null || it.statut == filtre) &&
                    (recherche.isBlank() || it.titre.contains(recherche, true) || it.description.contains(recherche, true))
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (liste.isEmpty()) item { Vide("Aucun sujet. Appuyez sur + pour en créer un.") }
                items(liste, key = { it.id }) { s ->
                    SujetCarte(s, s.categorieId?.let { parCategorie[it] }, ouvertes[s.id] ?: 0) {
                        ouvrir(Ecran.SujetDetail(s.id))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nouveau = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Sujet")
        }
    }

    if (nouveau) {
        SujetDialog(Sujet(categorieId = filtreCat?.takeIf { it > 0 })) { nouveau = false }
    }
    if (gererCategories) CategoriesDialog { gererCategories = false }
}

@Composable
private fun SujetCarte(s: Sujet, categorie: Categorie?, tachesOuvertes: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Point(couleurPriorite(s.priorite))
                Spacer(Modifier.width(8.dp))
                Text(
                    s.titre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatutBadge(s.statut)
            }
            if (categorie != null) Pastille(categorie.nom, Color(categorie.couleur))
            if (s.description.isNotBlank()) {
                Text(
                    s.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "$tachesOuvertes tâche(s) ouverte(s) · mis à jour le ${jour(s.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private sealed class Evenement {
    abstract val date: Long
    data class N(val note: NoteSujet) : Evenement() {
        override val date: Long get() = note.date
    }
    data class A(val appel: Appel) : Evenement() {
        override val date: Long get() = appel.date
    }
    data class E(val echange: Echange) : Evenement() {
        override val date: Long get() = echange.date
    }
}

@Composable
fun SujetDetailScreen(id: Long, ouvrir: (Ecran) -> Unit) {
    val sujet = rememberData(id) { Repo.sujet(id) }
    val taches = rememberData(id) { Repo.tachesSujet(id) }
    val notes = rememberData(id) { Repo.notes(id) }
    val appels = rememberData(id) { Repo.appelsSujet(id) }
    val echanges = rememberData(id) { Repo.echangesSujet(id) }
    val categoriesDetail = rememberData { Repo.categories() }
    val nbPieces = rememberData(id) { Repo.documentsSujet(id).size }
    val finies = LocalColonnes.current.filter { it.terminee }.map { it.id }.toSet()
    var redaction by remember { mutableStateOf<TypeMessage?>(null) }
    var choixAppel by remember { mutableStateOf(false) }
    var editEchange by remember { mutableStateOf<Echange?>(null) }
    val contacts = rememberData { Repo.contacts() }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }
    var editSujet by remember { mutableStateOf(false) }
    var editTache by remember { mutableStateOf<Tache?>(null) }
    var editAppel by remember { mutableStateOf<Appel?>(null) }
    var note by remember { mutableStateOf("") }

    if (sujet == null) {
        Vide("Ce sujet n'existe plus.")
        return
    }

    val journal = remember(notes, appels, echanges) {
        (notes.map { Evenement.N(it) } + appels.map { Evenement.A(it) } + echanges.map { Evenement.E(it) })
            .sortedByDescending { it.date }
    }
    val contactsLies = remember(taches, appels, echanges) {
        (taches.mapNotNull { it.contactId } + appels.mapNotNull { it.contactId } + echanges.mapNotNull { it.contactId })
            .distinct()
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sujet.titre,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        BoutonIcone(Icons.Default.Edit, "Modifier le sujet (titre, catégorie, statut…)", onClick = { editSujet = true })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatutBadge(sujet.statut)
                        sujet.categorieId?.let { cid -> categoriesDetail.firstOrNull { it.id == cid } }?.let { c ->
                            Pastille(c.nom, Color(c.couleur))
                        }
                        Pastille(
                            "Priorité " + (PRIORITES.firstOrNull { it.first == sujet.priorite }?.second ?: "").lowercase(),
                            couleurPriorite(sujet.priorite)
                        )
                    }
                    if (sujet.description.isNotBlank()) Text(sujet.description)
                    ChoiceField(
                        "Changer le statut",
                        StatutSujet.values().map { it to it.label },
                        sujet.statut,
                        { Repo.saveSujet(sujet.copy(statut = it)) }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(onClick = { choixAppel = true }) {
                            Icon(Icons.Default.Phone, null); Spacer(Modifier.width(4.dp)); Text("Appeler")
                        }
                        FilledTonalButton(onClick = { redaction = TypeMessage.SMS }) { Text("SMS") }
                        FilledTonalButton(onClick = { redaction = TypeMessage.EMAIL }) { Text("E-mail") }
                    }
                    Text(
                        "Créé le ${jour(sujet.createdAt)} · mis à jour le ${jourHeure(sujet.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                onClick = { ouvrir(Ecran.Dossier(id)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📂", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dossier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "$nbPieces pièce(s) · ${appels.size} appel(s) · ${echanges.size} e-mail(s)/SMS",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Photos, documents, pièces jointes, enregistrements", style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, "Ouvrir le dossier")
                }
            }
        }

        if (contactsLies.isNotEmpty()) {
            item { SectionTitre("Contacts liés") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contactsLies) { cid ->
                        AssistChip(
                            onClick = { ouvrir(Ecran.ContactDetail(cid)) },
                            label = { Text(nomContact[cid] ?: "Contact") },
                            leadingIcon = { Icon(Icons.Default.Person, null) }
                        )
                    }
                }
            }
        }

        item {
            SectionTitre("Tâches (${taches.count { it.colonneId !in finies }} ouvertes / ${taches.size})") {
                BoutonIcone(Icons.Default.Add, "Ajouter une tâche", onClick = { editTache = Tache(sujetId = id) })
            }
        }
        if (taches.isEmpty()) item { Vide("Aucune tâche pour ce sujet.") }
        items(taches, key = { "t${it.id}" }) { t ->
            TacheCarte(
                t = t,
                sujet = null,
                contact = t.contactId?.let { nomContact[it] },
                onClick = { editTache = t },
                onMove = { c -> Repo.moveTache(t.id, c) }
            )
        }

        item { SectionTitre("Suivi") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Ajouter une note de suivi…") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { Repo.addNote(id, note.trim()); note = "" },
                    enabled = note.isNotBlank()
                ) { Icon(Icons.Default.Send, "Ajouter la note") }
            }
        }
        if (journal.isEmpty()) item { Vide("Aucune note, appel ou message rattaché.") }
        items(journal, key = { e ->
            when (e) {
                is Evenement.N -> "n${e.note.id}"
                is Evenement.A -> "a${e.appel.id}"
                is Evenement.E -> "e${e.echange.id}"
            }
        }) { e ->
            when (e) {
                is Evenement.N -> NoteLigne(e.note) { Repo.deleteNote(e.note.id) }
                is Evenement.A -> AppelLigne(e.appel, e.appel.contactId?.let { nomContact[it] }) { editAppel = e.appel }
                is Evenement.E -> EchangeLigne(e.echange, e.echange.contactId?.let { nomContact[it] }) { editEchange = e.echange }
            }
        }
    }

    if (editSujet) SujetDialog(sujet) { editSujet = false }
    editTache?.let { TacheDialog(it) { editTache = null } }
    editAppel?.let { AppelDialog(it) { editAppel = null } }
    editEchange?.let { EchangeDialog(it) { editEchange = null } }
    if (choixAppel) ChoixAppelDialog(contactsLies, id) { choixAppel = false }
    redaction?.let { t ->
        MessageDialog(
            type = t,
            contactId = contactsLies.firstOrNull(),
            sujetId = id,
            objet = if (t == TypeMessage.EMAIL) sujet.titre else "",
            onDismiss = { redaction = null }
        )
    }
}

@Composable
private fun NoteLigne(n: NoteSujet, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📝 ${jourHeure(n.date)}", style = MaterialTheme.typography.labelSmall)
                Text(n.texte)
            }
            BoutonIcone(Icons.Default.Delete, "Supprimer la note", onClick = onDelete)
        }
    }
}
