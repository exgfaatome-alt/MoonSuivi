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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonsuivi.app.calls.CallLogger
import com.moonsuivi.app.calls.Lecteur
import com.moonsuivi.app.data.*
import com.moonsuivi.app.mail.Courrier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un élément de l'historique : appel, ou e-mail / SMS. */
sealed class Ligne {
    abstract val date: Long
    abstract val cle: String

    class DeAppel(val appel: Appel) : Ligne() {
        override val date: Long get() = appel.date
        override val cle: String get() = "a${appel.id}"
    }

    class DeEchange(val echange: Echange) : Ligne() {
        override val date: Long get() = echange.date
        override val cle: String get() = "e${echange.id}"
    }
}

fun fusionner(appels: List<Appel>, echanges: List<Echange>): List<Ligne> =
    (appels.map { Ligne.DeAppel(it) } + echanges.map { Ligne.DeEchange(it) }).sortedByDescending { it.date }

@Composable
fun LigneHistorique(
    l: Ligne,
    nomContact: Map<Long, String>,
    onAppel: (Appel) -> Unit,
    onEchange: (Echange) -> Unit
) {
    when (l) {
        is Ligne.DeAppel -> AppelLigne(l.appel, l.appel.contactId?.let { nomContact[it] }) { onAppel(l.appel) }
        is Ligne.DeEchange -> EchangeLigne(l.echange, l.echange.contactId?.let { nomContact[it] }) { onEchange(l.echange) }
    }
}

private enum class FiltreEchange(val label: String) {
    TOUS("Tous"), APPELS("Appels"), EMAILS("E-mails"), TEXTOS("SMS")
}

@Composable
fun EchangesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val appels = rememberData { Repo.appels() }
    val echanges = rememberData { Repo.echanges() }
    val contacts = rememberData { Repo.contacts() }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }
    var filtre by remember { mutableStateOf(FiltreEchange.TOUS) }
    var editAppel by remember { mutableStateOf<Appel?>(null) }
    var editEchange by remember { mutableStateOf<Echange?>(null) }
    var nouveau by remember { mutableStateOf(false) }
    var synchro by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val lignes = remember(appels, echanges, filtre) {
        when (filtre) {
            FiltreEchange.TOUS -> fusionner(appels, echanges)
            FiltreEchange.APPELS -> fusionner(appels, emptyList())
            FiltreEchange.EMAILS -> fusionner(emptyList(), echanges.filter { it.canal.estEmail })
            FiltreEchange.TEXTOS -> fusionner(emptyList(), echanges.filter { !it.canal.estEmail })
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FiltreEchange.values().toList()) { f ->
                    FilterChip(selected = filtre == f, onClick = { filtre = f }, label = { Text(f.label) })
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = !synchro,
                    onClick = {
                        synchro = true
                        message = null
                        scope.launch {
                            message = withContext(Dispatchers.IO) {
                                val nA = runCatching {
                                    CallLogger.sync(ctx, System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
                                }.getOrDefault(0)
                                if (Prefs.mail(ctx).configuree) {
                                    try {
                                        val nM = Courrier.synchroniser(ctx)
                                        "$nA appel(s) et $nM e-mail(s) importé(s)"
                                    } catch (e: Exception) {
                                        "$nA appel(s) importé(s) — e-mails : échec (${e.message})"
                                    }
                                } else {
                                    "$nA appel(s) importé(s). Configurez votre messagerie dans Réglages ⚙ pour suivre les e-mails."
                                }
                            }
                            synchro = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (synchro) "Synchronisation…" else "Synchroniser")
                }
            }
            if (synchro) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lignes.isEmpty()) item {
                    Vide("Aucun échange. Les appels, SMS et e-mails avec vos contacts apparaîtront ici.")
                }
                items(lignes, key = { it.cle }) { l ->
                    LigneHistorique(l, nomContact, { editAppel = it }, { editEchange = it })
                }
            }
        }
        FloatingActionButton(
            onClick = { nouveau = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Send, "Nouveau message") }
    }

    editAppel?.let { AppelDialog(it) { editAppel = null } }
    editEchange?.let { EchangeDialog(it) { editEchange = null } }
    if (nouveau) MessageDialog(TypeMessage.EMAIL, onDismiss = { nouveau = false })
}

@Composable
fun AppelLigne(a: Appel, contact: String?, onClick: () -> Unit) {
    val lecture by Lecteur.enCours
    val (symbole, couleur) = when (a.type) {
        TypeAppel.ENTRANT -> "↙" to Color(0xFF2E7D32)
        TypeAppel.SORTANT -> "↗" to Color(0xFF1565C0)
        TypeAppel.MANQUE -> "✕" to Color(0xFFC62828)
        TypeAppel.AUTRE -> "•" to Color.Gray
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbole, color = couleur, fontSize = 22.sp, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                Text(contact ?: a.numero.ifBlank { "Numéro masqué" }, fontWeight = FontWeight.SemiBold)
                Text(
                    "${a.type.label} · ${jourHeure(a.date)} · ${duree(a.dureeSec)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (a.note.isNotBlank()) {
                    Text("📝 ${a.note}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            a.fichier?.let { f ->
                IconButton(onClick = { Lecteur.basculer(f) }) {
                    Icon(
                        if (lecture == f) Icons.Default.Close else Icons.Default.PlayArrow,
                        if (lecture == f) "Arrêter" else "Écouter l'enregistrement"
                    )
                }
            }
        }
    }
}
