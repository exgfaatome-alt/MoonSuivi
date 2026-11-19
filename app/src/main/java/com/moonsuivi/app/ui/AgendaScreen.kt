package com.moonsuivi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AgendaScreen() {
    val zone = remember { ZoneId.systemDefault() }
    var mois by remember { mutableStateOf(YearMonth.now()) }
    var choisi by remember { mutableStateOf(LocalDate.now()) }
    var editTache by remember { mutableStateOf<Tache?>(null) }
    var editAppel by remember { mutableStateOf<Appel?>(null) }

    val debut = mois.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val fin = mois.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val taches = rememberData(debut) { Repo.tachesEntre(debut, fin) }
    val appels = rememberData(debut) { Repo.appelsEntre(debut, fin) }
    val sujets = rememberData { Repo.sujets() }
    val contacts = rememberData { Repo.contacts() }
    val nomSujet = remember(sujets) { sujets.associate { it.id to it.titre } }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }

    val tachesParJour = remember(taches) {
        taches.groupBy { Instant.ofEpochMilli(it.echeance ?: 0L).atZone(zone).toLocalDate() }
    }
    val appelsParJour = remember(appels) {
        appels.groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
    }
    val aujourdhui = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // En-tête du mois
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { mois = mois.minusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Mois précédent")
                }
                Text(
                    mois.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE))
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { mois = mois.plusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, "Mois suivant")
                }
                TextButton(onClick = { mois = YearMonth.now(); choisi = LocalDate.now() }) {
                    Text("Aujourd'hui")
                }
            }
            // Jours de la semaine
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            // Grille du mois
            val decalage = mois.atDay(1).dayOfWeek.value - 1
            val nbJours = mois.lengthOfMonth()
            val semaines = (decalage + nbJours + 6) / 7
            Column(Modifier.padding(horizontal = 8.dp)) {
                for (s in 0 until semaines) {
                    Row(Modifier.fillMaxWidth()) {
                        for (d in 0 until 7) {
                            val num = s * 7 + d - decalage + 1
                            if (num < 1 || num > nbJours) {
                                Spacer(Modifier.weight(1f).height(46.dp))
                            } else {
                                val date = mois.atDay(num)
                                CaseJour(
                                    date = date,
                                    selectionne = date == choisi,
                                    aujourdhui = date == aujourdhui,
                                    nbTaches = tachesParJour[date]?.size ?: 0,
                                    nbAppels = appelsParJour[date]?.size ?: 0,
                                    modifier = Modifier.weight(1f),
                                    onClick = { choisi = date }
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Point(MaterialTheme.colorScheme.secondary, 8); Text(" Tâches   ", style = MaterialTheme.typography.labelSmall)
                Point(MaterialTheme.colorScheme.tertiary, 8); Text(" Appels", style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()

            val tj = tachesParJour[choisi].orEmpty()
            val aj = appelsParJour[choisi].orEmpty()
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        choisi.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (tj.isEmpty() && aj.isEmpty()) item { Vide("Rien de prévu ce jour.") }
                items(tj, key = { "t${it.id}" }) { t ->
                    TacheCarte(
                        t = t,
                        sujet = t.sujetId?.let { nomSujet[it] },
                        contact = t.contactId?.let { nomContact[it] },
                        onClick = { editTache = t }
                    )
                }
                items(aj, key = { "a${it.id}" }) { a ->
                    AppelLigne(a, a.contactId?.let { nomContact[it] }) { editAppel = a }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editTache = Tache(echeance = choisi.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, "Nouvelle tâche ce jour") }
    }

    editTache?.let { TacheDialog(it) { editTache = null } }
    editAppel?.let { AppelDialog(it) { editAppel = null } }
}

@Composable
private fun CaseJour(
    date: LocalDate,
    selectionne: Boolean,
    aujourdhui: Boolean,
    nbTaches: Int,
    nbAppels: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fond = when {
        selectionne -> cs.primary
        aujourdhui -> cs.primaryContainer
        else -> Color.Transparent
    }
    val texte = if (selectionne) cs.onPrimary else cs.onSurface
    Column(
        modifier
            .height(46.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(fond)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "${date.dayOfMonth}",
            color = texte,
            fontWeight = if (aujourdhui) FontWeight.Bold else FontWeight.Normal
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (nbTaches > 0) Point(if (selectionne) cs.onPrimary else cs.secondary, 6)
            if (nbAppels > 0) Point(if (selectionne) cs.onPrimary else cs.tertiary, 6)
        }
    }
}
