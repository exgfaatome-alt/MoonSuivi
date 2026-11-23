package com.moonsuivi.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.moonsuivi.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class FiltreDossier(val label: String) {
    TOUT("Tout"), PHOTOS("Photos"), DOCUMENTS("Documents"), APPELS("Appels"), EMAILS("E-mails"), TEXTOS("SMS")
}

fun iconeFichier(d: Document): String {
    val m = d.mime.lowercase()
    val n = d.nom.lowercase()
    return when {
        m.startsWith("image/") -> "🖼"
        m == "application/pdf" || n.endsWith(".pdf") -> "📕"
        m.startsWith("audio/") -> "🎵"
        m.startsWith("video/") -> "🎬"
        n.endsWith(".doc") || n.endsWith(".docx") || n.endsWith(".odt") || m.startsWith("text/") -> "📝"
        n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".ods") || n.endsWith(".csv") -> "📊"
        n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") -> "🗜"
        else -> "📄"
    }
}

/**
 * Dossier d'un sujet : toutes les pièces au même endroit
 * (photos, documents, pièces jointes des e-mails, appels avec enregistrement, e-mails, SMS).
 */
@Composable
fun DossierScreen(sujetId: Long, ouvrir: (Ecran) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sujet = rememberData(sujetId) { Repo.sujet(sujetId) }
    val documents = rememberData(sujetId) { Repo.documentsSujet(sujetId) }
    val appels = rememberData(sujetId) { Repo.appelsSujet(sujetId) }
    val echanges = rememberData(sujetId) { Repo.echangesSujet(sujetId) }
    val contacts = rememberData { Repo.contacts() }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }
    val objetEchange = remember(echanges) { echanges.associate { it.id to it.objet } }

    var filtre by remember { mutableStateOf(FiltreDossier.TOUT) }
    var apercu by remember { mutableStateOf<Document?>(null) }
    var actions by remember { mutableStateOf<Document?>(null) }
    var editAppel by remember { mutableStateOf<Appel?>(null) }
    var editEchange by remember { mutableStateOf<Echange?>(null) }
    var envoyer by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var importEnCours by remember { mutableStateOf(false) }
    var photoEnCours by rememberSaveable { mutableStateOf<String?>(null) }

    val prendrePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val chemin = photoEnCours
        photoEnCours = null
        if (chemin != null) {
            val f = File(chemin)
            if (ok && f.exists() && f.length() > 0) {
                Repo.saveDocument(
                    Document(
                        sujetId = sujetId, type = TypeDocument.PHOTO, nom = f.name,
                        chemin = chemin, mime = "image/jpeg", taille = f.length()
                    )
                )
                message = "📷 Photo ajoutée au dossier"
            } else {
                f.delete()
            }
        }
    }

    val choisirFichiers = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importEnCours = true
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { Fichiers.importer(ctx, uri, sujetId) }.getOrNull() }
                    .onEach { Repo.saveDocument(it, notifier = false) }
                    .size
            }
            Repo.notifyChanged()
            message = "📎 $n fichier(s) ajouté(s) au dossier"
            importEnCours = false
        }
    }

    if (sujet == null) {
        Vide("Ce sujet n'existe plus.")
        return
    }

    val photos = documents.filter { it.estImage }
    val fichiers = documents.filter { !it.estImage }
    val emails = echanges.filter { it.canal.estEmail }
    val sms = echanges.filter { !it.canal.estEmail }
    val voir = { f: FiltreDossier -> filtre == FiltreDossier.TOUT || filtre == f }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📂 ${sujet.titre}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${photos.size} photo(s) · ${fichiers.size} document(s) · ${appels.size} appel(s) · " +
                            "${emails.size} e-mail(s) · ${sms.size} SMS",
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Infobulle("Prendre une photo avec l'appareil et la ranger dans ce dossier") {
                            FilledTonalButton(onClick = {
                                val f = Fichiers.nouvellePhoto(ctx, sujetId)
                                photoEnCours = f.absolutePath
                                runCatching { prendrePhoto.launch(Fichiers.uriPour(ctx, f)) }
                                    .onFailure { message = "Impossible d'ouvrir l'appareil photo"; photoEnCours = null }
                            }) { Text("📷 Photo") }
                        }
                        Infobulle("Ajouter des fichiers du téléphone (PDF, images, documents…)") {
                            FilledTonalButton(onClick = { choisirFichiers.launch(arrayOf("*/*")) }) { Text("📎 Fichier") }
                        }
                        Infobulle("Envoyer des pièces de ce dossier par e-mail") {
                            OutlinedButton(onClick = { envoyer = true }, enabled = documents.isNotEmpty()) { Text("✉ Envoyer") }
                        }
                    }
                    if (importEnCours) LinearProgressIndicator(Modifier.fillMaxWidth())
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            Astuce(
                "dossier",
                "Le dossier regroupe tout ce qui concerne ce sujet. Les pièces jointes des e-mails rattachés au sujet " +
                    "y arrivent automatiquement, ainsi que les appels (avec leur enregistrement) et les SMS. " +
                    "Touchez une pièce pour l'ouvrir, ⋮ pour la partager, la renommer ou la déplacer."
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FiltreDossier.values().toList()) { f ->
                    FilterChip(selected = filtre == f, onClick = { filtre = f }, label = { Text(f.label) })
                }
            }
        }

        val rien = (!voir(FiltreDossier.PHOTOS) || photos.isEmpty()) && (!voir(FiltreDossier.DOCUMENTS) || fichiers.isEmpty()) &&
            (!voir(FiltreDossier.APPELS) || appels.isEmpty()) && (!voir(FiltreDossier.EMAILS) || emails.isEmpty()) &&
            (!voir(FiltreDossier.TEXTOS) || sms.isEmpty())
        if (rien) item { Vide("Rien ici pour l'instant. Ajoutez une photo ou un fichier avec les boutons ci-dessus.") }

        if (voir(FiltreDossier.PHOTOS) && photos.isNotEmpty()) {
            item { SectionTitre("Photos (${photos.size})") }
            items(photos.chunked(3), key = { ligne -> "p" + ligne.joinToString("-") { it.id.toString() } }) { ligne ->
                Row(Modifier.fillMaxWidth()) {
                    ligne.forEach { d ->
                        Miniature(d, Modifier.weight(1f).aspectRatio(1f)) { apercu = d }
                    }
                    repeat(3 - ligne.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (voir(FiltreDossier.DOCUMENTS) && fichiers.isNotEmpty()) {
            item { SectionTitre("Documents (${fichiers.size})") }
            items(fichiers, key = { "d${it.id}" }) { d ->
                DocumentLigne(
                    d = d,
                    origine = d.echangeId?.let { eid -> "📎 de l'e-mail « ${objetEchange[eid] ?: ""} »" },
                    onOuvrir = { Fichiers.ouvrir(ctx, d) },
                    onActions = { actions = d }
                )
            }
        }
        if (voir(FiltreDossier.APPELS) && appels.isNotEmpty()) {
            item { SectionTitre("Appels (${appels.size})") }
            items(appels, key = { "a${it.id}" }) { a ->
                AppelLigne(a, a.contactId?.let { nomContact[it] }) { editAppel = a }
            }
        }
        if (voir(FiltreDossier.EMAILS) && emails.isNotEmpty()) {
            item { SectionTitre("E-mails (${emails.size})") }
            items(emails, key = { "e${it.id}" }) { e ->
                EchangeLigne(e, e.contactId?.let { nomContact[it] }) { editEchange = e }
            }
        }
        if (voir(FiltreDossier.TEXTOS) && sms.isNotEmpty()) {
            item { SectionTitre("SMS (${sms.size})") }
            items(sms, key = { "s${it.id}" }) { e ->
                EchangeLigne(e, e.contactId?.let { nomContact[it] }) { editEchange = e }
            }
        }
    }

    apercu?.let { d ->
        ApercuPhoto(
            d = d,
            onDismiss = { apercu = null },
            onActions = { apercu = null; actions = d }
        )
    }
    actions?.let { d -> ActionsDocument(d) { actions = null } }
    editAppel?.let { AppelDialog(it) { editAppel = null } }
    editEchange?.let { EchangeDialog(it) { editEchange = null } }
    if (envoyer) {
        val contactPrincipal = (echanges.mapNotNull { it.contactId } + appels.mapNotNull { it.contactId }).firstOrNull()
        MessageDialog(
            type = TypeMessage.EMAIL,
            contactId = contactPrincipal,
            sujetId = sujetId,
            objet = sujet.titre,
            onDismiss = { envoyer = false }
        )
    }
}

@Composable
private fun Miniature(d: Document, modifier: Modifier, onClick: () -> Unit) {
    val image by produceState<ImageBitmap?>(null, d.chemin) {
        value = withContext(Dispatchers.IO) { runCatching { Fichiers.miniature(d.chemin, 300)?.asImageBitmap() }.getOrNull() }
    }
    Box(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = d.nom, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("🖼")
        }
        if (d.type == TypeDocument.PIECE_JOINTE) {
            Text("📎", modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

@Composable
private fun DocumentLigne(d: Document, origine: String?, onOuvrir: () -> Unit, onActions: () -> Unit) {
    Card(onClick = onOuvrir, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(iconeFichier(d), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text(d.nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Fichiers.tailleLisible(d.taille)} · ${jourHeure(d.date)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (origine != null) {
                    Text(origine, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            BoutonIcone(Icons.Default.MoreVert, "Partager, renommer, déplacer ou supprimer", onClick = onActions)
        }
    }
}

@Composable
private fun ApercuPhoto(d: Document, onDismiss: () -> Unit, onActions: () -> Unit) {
    val ctx = LocalContext.current
    val image by produceState<ImageBitmap?>(null, d.chemin) {
        value = withContext(Dispatchers.IO) { runCatching { Fichiers.miniature(d.chemin, 1400)?.asImageBitmap() }.getOrNull() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp), contentAlignment = Alignment.Center) {
                    val img = image
                    if (img != null) {
                        Image(img, contentDescription = d.nom, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                    } else {
                        CircularProgressIndicator()
                    }
                }
                Text(d.nom, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${jourHeure(d.date)} · ${Fichiers.tailleLisible(d.taille)}", style = MaterialTheme.typography.bodySmall)
                if (d.note.isNotBlank()) Text(d.note)
                Row {
                    TextButton(onClick = { Fichiers.ouvrir(ctx, d) }) { Text("Ouvrir") }
                    TextButton(onClick = { Fichiers.partager(ctx, listOf(d)) }) { Text("Partager") }
                    TextButton(onClick = onActions) { Text("Plus…") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
            }
        }
    }
}

/** Partager, renommer, annoter, déplacer vers un autre sujet ou supprimer une pièce. */
@Composable
private fun ActionsDocument(initial: Document, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sujets = rememberData { Repo.sujets() }
    var d by remember(initial) { mutableStateOf(initial) }
    FormDialog(
        titre = "${iconeFichier(initial)} Pièce du dossier",
        onDismiss = onDismiss,
        onSave = { Repo.saveDocument(d.copy(nom = d.nom.trim().ifBlank { initial.nom })); onDismiss() },
        onDelete = { Repo.deleteDocument(initial); onDismiss() }
    ) {
        Row {
            TextButton(onClick = { Fichiers.ouvrir(ctx, initial) }) { Text("Ouvrir") }
            TextButton(onClick = { Fichiers.partager(ctx, listOf(initial)) }) { Text("Partager") }
        }
        OutlinedTextField(
            value = d.nom, onValueChange = { d = d.copy(nom = it) },
            label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = d.note, onValueChange = { d = d.copy(note = it) },
            label = { Text("Note / description") }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        if (initial.echangeId == null) {
            ChoiceField(
                "Ranger dans le sujet",
                sujets.map<Sujet, Pair<Long?, String>> { it.id to it.titre },
                d.sujetId,
                { d = d.copy(sujetId = it) }
            )
        } else {
            Indication("Pièce jointe d'un e-mail : elle suit le sujet auquel l'e-mail est rattaché.")
        }
        Indication("${Fichiers.tailleLisible(initial.taille)} · ajouté le ${jourHeure(initial.date)}")
    }
}

/** Choix des pièces du dossier à joindre à un e-mail. */
@Composable
fun ChoixPiecesDialog(documents: List<Document>, selection: Set<Long>, onChange: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pièces jointes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (documents.isEmpty()) Indication("Le dossier de ce sujet est vide.")
                documents.forEach { d ->
                    val coche = d.id in selection
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(if (coche) selection - d.id else selection + d.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = coche, onCheckedChange = { onChange(if (it) selection + d.id else selection - d.id) })
                        Text(iconeFichier(d), modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.nom, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Fichiers.tailleLisible(d.taille), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                val total = documents.filter { it.id in selection }.sumOf { it.taille }
                if (total > 20L * 1024 * 1024) {
                    Text(
                        "⚠ ${Fichiers.tailleLisible(total)} : beaucoup de messageries refusent plus de 20 Mo.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Valider") } }
    )
}
