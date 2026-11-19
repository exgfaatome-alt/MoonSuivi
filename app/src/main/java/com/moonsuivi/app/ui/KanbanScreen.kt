package com.moonsuivi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.*

@Composable
fun KanbanScreen() {
    val taches = rememberData { Repo.taches() }
    val sujets = rememberData { Repo.sujets() }
    val contacts = rememberData { Repo.contacts() }
    val nomSujet = remember(sujets) { sujets.associate { it.id to it.titre } }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }
    var filtreSujet by remember { mutableStateOf<Long?>(null) }
    var edition by remember { mutableStateOf<Tache?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChoiceField(
                "Filtrer par sujet",
                listOf<Pair<Long?, String>>(null to "Tous les sujets") + sujets.map { it.id to it.titre },
                filtreSujet,
                { filtreSujet = it },
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            val visibles = taches.filter { filtreSujet == null || it.sujetId == filtreSujet }
            LazyRow(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Colonne.values().toList()) { col ->
                    KanbanColonne(
                        col = col,
                        cartes = visibles.filter { it.colonne == col },
                        nomSujet = nomSujet,
                        nomContact = nomContact,
                        onEdit = { edition = it },
                        onAdd = { edition = Tache(colonne = col, sujetId = filtreSujet) }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { edition = Tache(sujetId = filtreSujet) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, "Nouvelle tâche") }
    }

    edition?.let { TacheDialog(it) { edition = null } }
}

@Composable
private fun KanbanColonne(
    col: Colonne,
    cartes: List<Tache>,
    nomSujet: Map<Long, String>,
    nomContact: Map<Long, String>,
    onEdit: (Tache) -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier.width(290.dp).fillMaxHeight().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    col.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Pastille("${cartes.size}", MaterialTheme.colorScheme.primary)
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Ajouter dans ${col.label}") }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cartes, key = { it.id }) { t ->
                    TacheCarte(
                        t = t,
                        sujet = t.sujetId?.let { nomSujet[it] },
                        contact = t.contactId?.let { nomContact[it] },
                        onClick = { onEdit(t) },
                        onMove = { c -> Repo.moveTache(t.id, c) }
                    )
                }
            }
        }
    }
}

@Composable
fun TacheCarte(
    t: Tache,
    sujet: String?,
    contact: String?,
    onClick: () -> Unit,
    onMove: ((Colonne) -> Unit)? = null
) {
    val termine = t.colonne == Colonne.TERMINE
    val ech = t.echeance
    val enRetard = ech != null && ech < debutJour(System.currentTimeMillis()) && !termine
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Point(couleurPriorite(t.priorite))
                Spacer(Modifier.width(8.dp))
                Text(
                    t.titre,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (termine) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )
                if (onMove == null) Pastille(t.colonne.label, MaterialTheme.colorScheme.secondary)
            }
            if (t.description.isNotBlank()) {
                Text(
                    t.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sujet != null) Text("📁 $sujet", style = MaterialTheme.typography.labelMedium)
            if (contact != null) Text("👤 $contact", style = MaterialTheme.typography.labelMedium)
            if (ech != null) {
                Text(
                    (if (enRetard) "⚠ En retard — " else "📅 ") + jour(ech),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enRetard) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onMove != null) {
                val i = t.colonne.ordinal
                val cols = Colonne.values()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i > 0) {
                        IconButton(onClick = { onMove(cols[i - 1]) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowLeft, "Vers ${cols[i - 1].label}")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (i < cols.size - 1) {
                        IconButton(onClick = { onMove(cols[i + 1]) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowRight, "Vers ${cols[i + 1].label}")
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
