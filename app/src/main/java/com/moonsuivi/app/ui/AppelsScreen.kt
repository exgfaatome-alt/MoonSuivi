package com.moonsuivi.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.moonsuivi.app.calls.CallLogger
import com.moonsuivi.app.calls.Lecteur
import com.moonsuivi.app.data.*

private val PERMISSIONS_APPELS = arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_CONTACTS
)

private val SOURCES_AUDIO: List<Pair<Int, String>> = listOf(
    MediaRecorder.AudioSource.VOICE_RECOGNITION to "Reconnaissance vocale (recommandé)",
    MediaRecorder.AudioSource.MIC to "Micro",
    MediaRecorder.AudioSource.VOICE_COMMUNICATION to "Communication vocale",
    MediaRecorder.AudioSource.VOICE_CALL to "Ligne d'appel (téléphones rootés)"
)

@Composable
fun AppelsScreen() {
    val ctx = LocalContext.current
    val appels = rememberData { Repo.appels() }
    val contacts = rememberData { Repo.contacts() }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }
    var auto by remember { mutableStateOf(Prefs.autoRecord(ctx)) }
    var source by remember { mutableStateOf(Prefs.audioSource(ctx)) }
    var verif by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var editAppel by remember { mutableStateOf<Appel?>(null) }

    // Re-vérifie permissions / accessibilité au retour dans l'appli
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) verif++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val permissionsOk = remember(verif) {
        PERMISSIONS_APPELS.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    val accessOk = remember(verif) { accessibiliteActive(ctx) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        verif++
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enregistrement des appels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enregistrement automatique", modifier = Modifier.weight(1f))
                        Switch(checked = auto, onCheckedChange = { auto = it; Prefs.setAutoRecord(ctx, it) })
                    }
                    ChoiceField("Source audio", SOURCES_AUDIO, source, { source = it; Prefs.setAudioSource(ctx, it) })
                    EtatLigne(
                        ok = permissionsOk,
                        texte = if (permissionsOk) "Autorisations accordées" else "Autorisations téléphone / micro manquantes",
                        action = "Autoriser"
                    ) { demande.launch(PERMISSIONS_APPELS) }
                    EtatLigne(
                        ok = accessOk,
                        texte = if (accessOk) "Service d'accessibilité actif" else "Activez le service d'accessibilité « MoonSuivi » (indispensable pour capter l'audio pendant un appel)",
                        action = "Activer"
                    ) { runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
                    Text(
                        "Sur Android 11, une appli non système ne peut pas capter directement la ligne téléphonique : " +
                            "la qualité dépend du fabricant. Si seule votre voix est enregistrée, activez le haut-parleur " +
                            "ou essayez une autre source audio. Prévenez votre interlocuteur avant d'enregistrer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val n = CallLogger.sync(ctx, System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
                            message = "$n appel(s) importé(s) depuis le journal"
                        },
                        enabled = permissionsOk
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Importer le journal (30 derniers jours)")
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        item { SectionTitre("Historique (${appels.size})") }
        if (appels.isEmpty()) item {
            Vide("Aucun appel pour l'instant. Les appels entrants et sortants apparaîtront ici automatiquement.")
        }
        items(appels, key = { it.id }) { a ->
            AppelLigne(a, a.contactId?.let { nomContact[it] }) { editAppel = a }
        }
    }

    editAppel?.let { AppelDialog(it) { editAppel = null } }
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
