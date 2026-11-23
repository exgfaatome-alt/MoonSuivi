package com.moonsuivi.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un élément du journal : entrée (auto ou manuelle), note de sujet, appel, e-mail / SMS. */
private sealed class ElementJournal {
    abstract val date: Long
    abstract val cle: String
    abstract val sujetId: Long?
    abstract val contactId: Long?

    class Entree(val e: EntreeJournal) : ElementJournal() {
        override val date get() = e.date
        override val cle get() = "j${e.id}"
        override val sujetId get() = e.sujetId
        override val contactId get() = e.contactId
    }

    class Note(val n: NoteSujet) : ElementJournal() {
        override val date get() = n.date
        override val cle get() = "n${n.id}"
        override val sujetId: Long? get() = n.sujetId
        override val contactId: Long? get() = null
    }

    class DAppel(val a: Appel) : ElementJournal() {
        override val date get() = a.date
        override val cle get() = "a${a.id}"
        override val sujetId get() = a.sujetId
        override val contactId get() = a.contactId
    }

    class DEchange(val x: Echange) : ElementJournal() {
        override val date get() = x.date
        override val cle get() = "e${x.id}"
        override val sujetId get() = x.sujetId
        override val contactId get() = x.contactId
    }
}

private enum class FiltreJournal(val label: String) {
    TOUT("Tout"), NOTES("Mes notes"), TACHES("Tâches"), SUJETS("Sujets"), COMMUNICATIONS("Appels et messages")
}

private val PERIODES = listOf(7 to "7 jours", 30 to "30 jours", 90 to "3 mois", 365 to "1 an")

@Composable
fun JournalScreen(ouvrir: (Ecran) -> Unit) {
    var jours by remember { mutableStateOf(30) }
    val depuis = remember(jours) { debutJour(System.currentTimeMillis()) - (jours - 1) * UN_JOUR }
    val entrees = rememberData(depuis) { Repo.journal(depuis) }
    val notes = rememberData(depuis) { Repo.notesDepuis(depuis) }
    val appels = rememberData(depuis) { Repo.appelsDepuis(depuis) }
    val echanges = rememberData(depuis) { Repo.echangesDepuis(depuis) }
    val sujets = rememberData { Repo.sujets() }
    val contacts = rememberData { Repo.contacts() }
    val nomSujet = remember(sujets) { sujets.associate { it.id to it.titre } }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }

    var filtre by remember { mutableStateOf(FiltreJournal.TOUT) }
    var recherche by remember { mutableStateOf("") }
    var ecrire by remember { mutableStateOf<EntreeJournal?>(null) }
    var editAppel by remember { mutableStateOf<Appel?>(null) }
    var editEchange by remember { mutableStateOf<Echange?>(null) }

    val elements = remember(entrees, notes, appels, echanges, filtre, recherche) {
        val tous: List<ElementJournal> = when (filtre) {
            FiltreJournal.TOUT -> entrees.map { ElementJournal.Entree(it) } + notes.map { ElementJournal.Note(it) } +
                appels.map { ElementJournal.DAppel(it) } + echanges.map { ElementJournal.DEchange(it) }
            FiltreJournal.NOTES -> entrees.filter { it.type == TypeJournal.NOTE }.map { ElementJournal.Entree(it) } +
                notes.map { ElementJournal.Note(it) }
            FiltreJournal.TACHES -> entrees.filter { it.type == TypeJournal.TACHE }.map { ElementJournal.Entree(it) }
            FiltreJournal.SUJETS -> entrees.filter { it.type == TypeJournal.SUJET }.map { ElementJournal.Entree(it) }
            FiltreJournal.COMMUNICATIONS -> appels.map { ElementJournal.DAppel(it) } + echanges.map { ElementJournal.DEchange(it) }
        }
        val q = recherche.trim()
        tous.filter { q.isEmpty() || texteDe(it, nomContact).contains(q, ignoreCase = true) }
            .sortedByDescending { it.date }
    }
    val parJour = remember(elements) { elements.groupBy { debutJour(it.date) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = recherche,
                    onValueChange = { recherche = it },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Rechercher") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                ChoiceField("Période", PERIODES, jours, { jours = it }, Modifier.width(130.dp))
            }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Astuce("journal", "Le journal note automatiquement vos tâches, sujets et échanges. Touchez ✏ Écrire pour ajouter un compte rendu ou une décision.")
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FiltreJournal.values().toList()) { f ->
                    FilterChip(selected = filtre == f, onClick = { filtre = f }, label = { Text(f.label) })
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp)
            ) {
                if (elements.isEmpty()) item {
                    Vide("Rien dans le journal sur cette période. Touchez ✏ pour écrire une note.")
                }
                parJour.forEach { (jourMs, liste) ->
                    item(key = "jour$jourMs") {
                        Text(
                            enTeteJour(jourMs),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(liste, key = { it.cle }) { el ->
                        LigneJournal(
                            el = el,
                            nomSujet = nomSujet,
                            nomContact = nomContact,
                            onClick = {
                                when (el) {
                                    is ElementJournal.Entree ->
                                        if (el.e.type == TypeJournal.NOTE) ecrire = el.e
                                        else el.e.sujetId?.let { ouvrir(Ecran.SujetDetail(it)) }
                                    is ElementJournal.Note -> ouvrir(Ecran.SujetDetail(el.n.sujetId))
                                    is ElementJournal.DAppel -> editAppel = el.a
                                    is ElementJournal.DEchange -> editEchange = el.x
                                }
                            },
                            ouvrir = ouvrir
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { ecrire = EntreeJournal() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text("Écrire")
        }
    }

    ecrire?.let { JournalDialog(it) { ecrire = null } }
    editAppel?.let { AppelDialog(it) { editAppel = null } }
    editEchange?.let { EchangeDialog(it) { editEchange = null } }
}

private fun enTeteJour(ms: Long): String {
    val aujourdhui = debutJour(System.currentTimeMillis())
    return when (ms) {
        aujourdhui -> "Aujourd'hui"
        aujourdhui - UN_JOUR -> "Hier"
        else -> SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(Date(ms))
            .replaceFirstChar { it.uppercase() }
    }
}

private fun iconeDe(el: ElementJournal): String = when (el) {
    is ElementJournal.Entree -> el.e.type.icone
    is ElementJournal.Note -> "📝"
    is ElementJournal.DAppel -> when (el.a.type) {
        TypeAppel.ENTRANT -> "📞↙"
        TypeAppel.SORTANT -> "📞↗"
        TypeAppel.MANQUE -> "📵"
        TypeAppel.AUTRE -> "📞"
    }
    is ElementJournal.DEchange -> el.x.canal.icone
}

private fun texteDe(el: ElementJournal, nomContact: Map<Long, String>): String = when (el) {
    is ElementJournal.Entree -> el.e.texte
    is ElementJournal.Note -> el.n.texte
    is ElementJournal.DAppel -> {
        val qui = el.a.contactId?.let { nomContact[it] } ?: el.a.numero.ifBlank { "numéro masqué" }
        "Appel ${el.a.type.label.lowercase()} — $qui (${duree(el.a.dureeSec)})" +
            if (el.a.note.isNotBlank()) "\n${el.a.note}" else ""
    }
    is ElementJournal.DEchange -> {
        val qui = el.x.contactId?.let { nomContact[it] } ?: el.x.adresse
        "${el.x.canal.label} — $qui" +
            (if (el.x.objet.isNotBlank()) "\n${el.x.objet}" else "") +
            (if (el.x.objet.isBlank() && el.x.contenu.isNotBlank()) "\n${el.x.contenu}" else "")
    }
}

@Composable
private fun LigneJournal(
    el: ElementJournal,
    nomSujet: Map<Long, String>,
    nomContact: Map<Long, String>,
    onClick: () -> Unit,
    ouvrir: (Ecran) -> Unit
) {
    val heure = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(el.date))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            heure,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Text(iconeDe(el), modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(texteDe(el, nomContact), maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                el.sujetId?.let { sid ->
                    nomSujet[sid]?.let { nom ->
                        Text(
                            "📁 $nom",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { ouvrir(Ecran.SujetDetail(sid)) }
                        )
                    }
                }
                el.contactId?.let { cid ->
                    if (el !is ElementJournal.DAppel && el !is ElementJournal.DEchange) {
                        nomContact[cid]?.let { nom ->
                            Text(
                                "👤 $nom",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.clickable { ouvrir(Ecran.ContactDetail(cid)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
