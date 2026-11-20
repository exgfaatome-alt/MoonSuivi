package com.moonsuivi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tableau de bord : l'essentiel de la journée en un coup d'œil. */
@Composable
fun AccueilScreen(ouvrir: (Ecran) -> Unit) {
    val colonnes = LocalColonnes.current
    val finies = remember(colonnes) { colonnes.filter { it.terminee }.map { it.id }.toSet() }
    val taches = rememberData { Repo.taches() }
    val sujets = rememberData { Repo.sujets() }
    val categories = rememberData { Repo.categories() }
    val contacts = rememberData { Repo.contacts() }
    val nomSujet = remember(sujets) { sujets.associate { it.id to it.titre } }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }
    val aujourdhui = remember { debutJour(System.currentTimeMillis()) }
    val debutSemaine = aujourdhui - 6 * UN_JOUR
    val appels = rememberData(debutSemaine) { Repo.appelsDepuis(debutSemaine) }
    val echanges = rememberData(debutSemaine) { Repo.echangesDepuis(debutSemaine) }
    val recents = rememberData { Repo.journalRecent(6) }
    var editTache by remember { mutableStateOf<Tache?>(null) }

    val ouvertes = taches.filter { it.colonneId !in finies }
    val enRetard = ouvertes.filter { val e = it.echeance; e != null && e < aujourdhui }.sortedBy { it.echeance }
    val pourAujourdhui = ouvertes.filter { val e = it.echeance; e != null && e >= aujourdhui && e < aujourdhui + UN_JOUR }
    val semaine = ouvertes.filter { val e = it.echeance; e != null && e >= aujourdhui + UN_JOUR && e < aujourdhui + 7 * UN_JOUR }
        .sortedBy { it.echeance }
    val sujetsActifs = sujets.filter { it.statut != StatutSujet.CLOS }
    val nbAppels = appels.size
    val nbMails = echanges.count { it.canal.estEmail }
    val nbSms = echanges.count { !it.canal.estEmail }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date()).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        enRetard.isNotEmpty() -> "${enRetard.size} tâche(s) en retard à rattraper."
                        pourAujourdhui.isNotEmpty() -> "${pourAujourdhui.size} tâche(s) prévue(s) aujourd'hui."
                        else -> "Rien d'urgent aujourd'hui 🎉"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tuile("Tâches ouvertes", ouvertes.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                        ouvrir(Ecran.Tableau)
                    }
                    Tuile(
                        "En retard", enRetard.size,
                        if (enRetard.isEmpty()) Color(0xFF2E7D32) else Color(0xFFD32F2F), Modifier.weight(1f)
                    ) { ouvrir(Ecran.Tableau) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tuile("Aujourd'hui", pourAujourdhui.size, Color(0xFFF57C00), Modifier.weight(1f)) {
                        ouvrir(Ecran.Agenda)
                    }
                    Tuile("Sujets actifs", sujetsActifs.size, Color(0xFF00897B), Modifier.weight(1f)) {
                        ouvrir(Ecran.Sujets)
                    }
                }
            }
        }

        if (enRetard.isNotEmpty()) {
            item { SectionTitre("⚠ En retard") }
            items(enRetard.take(5), key = { "r${it.id}" }) { t -> CarteTache(t, nomSujet, nomContact) { editTache = t } }
        }
        if (pourAujourdhui.isNotEmpty()) {
            item { SectionTitre("Aujourd'hui") }
            items(pourAujourdhui, key = { "j${it.id}" }) { t -> CarteTache(t, nomSujet, nomContact) { editTache = t } }
        }
        if (semaine.isNotEmpty()) {
            item { SectionTitre("Dans les 7 prochains jours") }
            items(semaine.take(5), key = { "s${it.id}" }) { t -> CarteTache(t, nomSujet, nomContact) { editTache = t } }
        }

        // Avancement du kanban
        item {
            BlocAccueil("Tableau des tâches") {
                val max = colonnes.maxOfOrNull { c -> taches.count { it.colonneId == c.id } }?.coerceAtLeast(1) ?: 1
                colonnes.forEach { c ->
                    Barre(c.nom, taches.count { it.colonneId == c.id }, max, Color(c.couleur))
                }
                if (taches.isNotEmpty()) {
                    val faites = taches.count { it.colonneId in finies }
                    Indication("Avancement global : ${faites * 100 / taches.size} % des tâches terminées")
                }
            }
        }

        // Sujets par catégorie
        item {
            BlocAccueil("Sujets actifs par catégorie") {
                val lignes = categories.map { c -> Triple(c.nom, sujetsActifs.count { it.categorieId == c.id }, Color(c.couleur)) } +
                    Triple("Sans catégorie", sujetsActifs.count { it.categorieId == null }, Color(0xFF9E9E9E))
                val max = lignes.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                lignes.filter { it.second > 0 }.forEach { (nom, n, couleur) -> Barre(nom, n, max, couleur) }
                if (sujetsActifs.isEmpty()) Indication("Aucun sujet actif.")
                val parStatut = StatutSujet.values().filter { it != StatutSujet.CLOS }
                    .joinToString(" · ") { s -> "${s.label} : ${sujets.count { it.statut == s }}" }
                Indication(parStatut)
            }
        }

        // Communications de la semaine
        item {
            BlocAccueil("Échanges des 7 derniers jours") {
                Text("📞 $nbAppels appel(s)   📧 $nbMails e-mail(s)   💬 $nbSms SMS")
                val parJour = (0..6).map { d ->
                    val debut = debutSemaine + d * UN_JOUR
                    val n = appels.count { it.date >= debut && it.date < debut + UN_JOUR } +
                        echanges.count { it.date >= debut && it.date < debut + UN_JOUR }
                    debut to n
                }
                val max = parJour.maxOf { it.second }.coerceAtLeast(1)
                val format = SimpleDateFormat("EEE", Locale.FRANCE)
                Row(
                    Modifier.fillMaxWidth().height(110.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    parJour.forEach { (debut, n) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (n > 0) Text("$n", style = MaterialTheme.typography.labelSmall)
                            Box(
                                Modifier
                                    .width(20.dp)
                                    .height((70f * n / max).coerceAtLeast(2f).dp)
                                    .background(
                                        if (debut == aujourdhui) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                            Text(
                                format.format(Date(debut)).take(3),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Activité récente
        item {
            SectionTitre("Activité récente") {
                TextButton(onClick = { ouvrir(Ecran.Journal) }) { Text("Voir le journal") }
            }
        }
        if (recents.isEmpty()) item { Vide("Aucune activité pour l'instant.") }
        items(recents, key = { "h${it.id}" }) { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(e.type.icone, modifier = Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.texte, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(jourHeure(e.date), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    editTache?.let { TacheDialog(it) { editTache = null } }
}

@Composable
private fun CarteTache(t: Tache, nomSujet: Map<Long, String>, nomContact: Map<Long, String>, onClick: () -> Unit) {
    TacheCarte(
        t = t,
        sujet = t.sujetId?.let { nomSujet[it] },
        contact = t.contactId?.let { nomContact[it] },
        onClick = onClick
    )
}

@Composable
private fun Tuile(titre: String, valeur: Int, couleur: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text("$valeur", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = couleur)
            Text(titre, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BlocAccueil(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            contenu()
        }
    }
}

@Composable
private fun Barre(libelle: String, valeur: Int, max: Int, couleur: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            libelle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(12.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(valeur.toFloat() / max)
                    .background(couleur, RoundedCornerShape(6.dp))
            )
        }
        Text(
            "$valeur",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
    }
}
