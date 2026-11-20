package com.moonsuivi.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.Repo
import com.moonsuivi.app.data.StatutSujet
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Navigation ----------
sealed class Ecran {
    object Accueil : Ecran()
    object Journal : Ecran()
    object Tableau : Ecran()
    object Agenda : Ecran()
    object Sujets : Ecran()
    object Contacts : Ecran()
    object Echanges : Ecran()
    object Reglages : Ecran()
    data class SujetDetail(val id: Long) : Ecran()
    data class ContactDetail(val id: Long) : Ecran()
}

/** Colonnes du kanban, fournies à tous les écrans. */
val LocalColonnes = compositionLocalOf<List<com.moonsuivi.app.data.Colonne>> { emptyList() }

const val UN_JOUR: Long = 24L * 3600 * 1000

// ---------- Données réactives ----------
/** Relit les données à chaque modification de la base. */
@Composable
fun <T> rememberData(vararg keys: Any?, block: () -> T): T {
    val v by Repo.version.collectAsState()
    return remember(v, *keys) { block() }
}

// ---------- Formats ----------
fun jour(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(ms))
fun jourHeure(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ms))
fun duree(sec: Long): String = when {
    sec < 60 -> "${sec} s"
    sec < 3600 -> "${sec / 60} min ${sec % 60} s"
    else -> "${sec / 3600} h ${(sec % 3600) / 60} min"
}

fun debutJour(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun couleurPriorite(p: Int): Color = when (p) {
    2 -> Color(0xFFD32F2F)
    0 -> Color(0xFF9E9E9E)
    else -> Color(0xFF1976D2)
}

fun couleurStatut(s: StatutSujet): Color = when (s) {
    StatutSujet.OUVERT -> Color(0xFF1976D2)
    StatutSujet.EN_COURS -> Color(0xFF00897B)
    StatutSujet.EN_ATTENTE -> Color(0xFFF57C00)
    StatutSujet.CLOS -> Color(0xFF757575)
}

// ---------- Actions système ----------
/** Appelle directement (permission « Appels ») sinon ouvre le clavier avec le numéro. */
fun appeler(ctx: Context, numero: String) {
    val n = numero.split('/', ',', ';').first().trim()
    if (n.isBlank()) return
    val direct = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL
    runCatching { ctx.startActivity(Intent(action, Uri.parse("tel:" + Uri.encode(n)))) }
}

/** Ouvre l'appli e-mail du téléphone avec le message prérempli. */
fun ecrireEmail(ctx: Context, email: String, objet: String = "", texte: String = "") {
    if (email.isBlank()) return
    val uri = Uri.parse(
        "mailto:" + Uri.encode(email.trim()) +
            "?subject=" + Uri.encode(objet) + "&body=" + Uri.encode(texte)
    )
    val i = Intent(Intent.ACTION_SENDTO, uri)
        .putExtra(Intent.EXTRA_SUBJECT, objet)
        .putExtra(Intent.EXTRA_TEXT, texte)
    runCatching { ctx.startActivity(i) }
}

fun accessibiliteActive(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return s.split(':').any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
}

// ---------- Composants ----------
@Composable
fun <T> ChoiceField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var ouvert by remember { mutableStateOf(false) }
    val courant = options.firstOrNull { it.first == selected }?.second ?: "—"
    Box(modifier) {
        OutlinedButton(onClick = { ouvert = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(courant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            options.forEach { (valeur, libelle) ->
                DropdownMenuItem(
                    text = { Text(libelle) },
                    onClick = { onSelect(valeur); ouvert = false }
                )
            }
        }
    }
}

@Composable
fun DateField(label: String, valeur: Long?, onChange: (Long?) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                val cal = Calendar.getInstance()
                if (valeur != null) cal.timeInMillis = valeur
                DatePickerDialog(
                    ctx,
                    { _, y, m, d ->
                        val c = Calendar.getInstance()
                        c.clear()
                        c.set(y, m, d, 9, 0)
                        onChange(c.timeInMillis)
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (valeur == null) "$label : aucune" else "$label : ${jour(valeur)}")
        }
        if (valeur != null) {
            IconButton(onClick = { onChange(null) }) { Icon(Icons.Default.Clear, "Effacer la date") }
        }
    }
}

@Composable
fun FormDialog(
    titre: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    libelleOk: String = "Enregistrer",
    content: @Composable ColumnScope.() -> Unit
) {
    var confirmer by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titre) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = saveEnabled) { Text(libelleOk) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { if (confirmer) onDelete() else confirmer = true }) {
                        Text(
                            if (confirmer) "Confirmer ?" else "Supprimer",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}

@Composable
fun SectionTitre(texte: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            texte,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

@Composable
fun Vide(texte: String) {
    Text(
        texte,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
    )
}

@Composable
fun Pastille(texte: String, couleur: Color) {
    Text(
        texte,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(couleur, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun StatutBadge(s: StatutSujet) = Pastille(s.label, couleurStatut(s))

@Composable
fun Point(couleur: Color, taille: Int = 10) {
    Box(Modifier.size(taille.dp).background(couleur, CircleShape))
}

@Composable
fun EtatLigne(ok: Boolean, texte: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF2E7D32) else Color(0xFFF57C00)
        )
        Spacer(Modifier.width(8.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Petite ligne d'aide grise. */
@Composable
fun Indication(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Choix d'une couleur dans la palette. */
@Composable
fun SelecteurCouleur(couleur: Long, onChange: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.moonsuivi.app.data.PALETTE.forEach { c ->
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (c == couleur) 3.dp else 0.dp,
                        color = if (c == couleur) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onChange(c) },
                contentAlignment = Alignment.Center
            ) {
                if (c == couleur) Text("✓", color = Color.White)
            }
        }
    }
}
