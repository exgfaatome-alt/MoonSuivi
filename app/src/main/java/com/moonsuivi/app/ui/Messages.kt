package com.moonsuivi.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonsuivi.app.data.*
import com.moonsuivi.app.mail.Courrier
import com.moonsuivi.app.messages.Sms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TypeMessage { EMAIL, SMS }

private fun destinationDe(contactId: Long?, type: TypeMessage): String {
    val c = contactId?.let { Repo.contact(it) } ?: return ""
    return if (type == TypeMessage.EMAIL) emailsDe(c).firstOrNull() ?: "" else telephonesDe(c).firstOrNull() ?: ""
}

/**
 * Rédaction d'un e-mail ou d'un SMS.
 * - E-mail : envoi direct si la messagerie est configurée (Réglages), sinon ouverture de l'appli e-mail.
 * - SMS : envoi direct depuis l'appli.
 * Le message est enregistré dans l'historique du contact (et du sujet choisi).
 */
@Composable
fun MessageDialog(
    type: TypeMessage,
    contactId: Long? = null,
    sujetId: Long? = null,
    objet: String = "",
    destination: String? = null,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts = rememberData { Repo.contacts() }
    val sujets = rememberData { Repo.sujets() }
    val config = remember { Prefs.mail(ctx) }
    var mode by remember { mutableStateOf(type) }
    var cid by remember { mutableStateOf(contactId) }
    var sid by remember { mutableStateOf(sujetId) }
    var dest by remember { mutableStateOf(destination ?: destinationDe(contactId, type)) }
    var obj by remember { mutableStateOf(objet) }
    var texte by remember { mutableStateOf("") }
    var envoi by remember { mutableStateOf(false) }
    var erreur by remember { mutableStateOf<String?>(null) }

    fun journaliser(canal: Canal, messageId: String?) {
        val adresse = dest.trim()
        val contact = cid
            ?: (if (mode == TypeMessage.EMAIL) Repo.findContactByEmail(adresse) else Repo.findContactByPhone(adresse))?.id
        Repo.saveEchange(
            Echange(
                canal = canal,
                contactId = contact,
                sujetId = sid,
                adresse = adresse,
                objet = if (mode == TypeMessage.EMAIL) obj else "",
                contenu = texte,
                messageId = messageId
            )
        )
    }

    fun envoyerSms() {
        try {
            Sms.envoyer(ctx, dest.trim(), texte)
            journaliser(Canal.SMS_ENVOYE, null)
            onDismiss()
        } catch (e: Exception) {
            erreur = "Échec de l'envoi : ${e.message}"
        }
    }

    val permissionSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            envoyerSms()
        } else {
            Sms.ouvrirAppli(ctx, dest.trim(), texte)
            journaliser(Canal.SMS_ENVOYE, null)
            onDismiss()
        }
    }

    fun envoyer() {
        erreur = null
        when {
            mode == TypeMessage.SMS -> {
                if (Sms.peutEnvoyer(ctx)) envoyerSms() else permissionSms.launch(Manifest.permission.SEND_SMS)
            }
            config.configuree -> {
                envoi = true
                scope.launch {
                    try {
                        val mid = withContext(Dispatchers.IO) { Courrier.envoyer(config, dest.trim(), obj, texte) }
                        journaliser(Canal.EMAIL_ENVOYE, mid)
                        onDismiss()
                    } catch (e: Exception) {
                        erreur = "Échec de l'envoi : ${e.message}"
                        envoi = false
                    }
                }
            }
            else -> {
                ecrireEmail(ctx, dest, obj, texte)
                journaliser(Canal.EMAIL_ENVOYE, null)
                onDismiss()
            }
        }
    }

    FormDialog(
        titre = if (mode == TypeMessage.EMAIL) "Nouvel e-mail" else "Nouveau SMS",
        onDismiss = { if (!envoi) onDismiss() },
        onSave = { envoyer() },
        saveEnabled = !envoi && dest.isNotBlank() && texte.isNotBlank(),
        libelleOk = if (envoi) "Envoi…" else "Envoyer"
    ) {
        Row {
            FilterChip(
                selected = mode == TypeMessage.EMAIL,
                onClick = { mode = TypeMessage.EMAIL; dest = destinationDe(cid, TypeMessage.EMAIL) },
                label = { Text("E-mail") },
                leadingIcon = { Icon(Icons.Default.Email, null) }
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = mode == TypeMessage.SMS,
                onClick = { mode = TypeMessage.SMS; dest = destinationDe(cid, TypeMessage.SMS) },
                label = { Text("SMS") },
                leadingIcon = { Icon(Icons.Default.Send, null) }
            )
        }
        ChoiceField("Contact", optionsContacts(contacts), cid, { cid = it; dest = destinationDe(it, mode) })
        OutlinedTextField(
            value = dest,
            onValueChange = { dest = it },
            label = { Text(if (mode == TypeMessage.EMAIL) "À (adresse e-mail)" else "À (numéro)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (mode == TypeMessage.EMAIL) KeyboardType.Email else KeyboardType.Phone
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (mode == TypeMessage.EMAIL) {
            OutlinedTextField(
                value = obj, onValueChange = { obj = it },
                label = { Text("Objet") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = texte, onValueChange = { texte = it },
            label = { Text("Message") }, minLines = 5, modifier = Modifier.fillMaxWidth()
        )
        ChoiceField("Rattacher au sujet", optionsSujets(sujets), sid, { sid = it })
        Text(
            when {
                mode == TypeMessage.SMS -> "Le SMS est envoyé directement depuis votre forfait."
                config.configuree -> "Envoyé depuis ${config.utilisateur}."
                else -> "Messagerie non configurée : l'e-mail s'ouvrira dans votre appli de messagerie. " +
                    "Configurez-la dans Réglages ⚙ pour envoyer directement."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (envoi) LinearProgressIndicator(Modifier.fillMaxWidth())
        erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun EchangeLigne(e: Echange, contact: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(e.canal.icone, fontSize = 20.sp, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    contact ?: e.adresse.ifBlank { "Inconnu" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("${e.canal.label} · ${jourHeure(e.date)}", style = MaterialTheme.typography.bodySmall)
                if (e.objet.isNotBlank()) {
                    Text(e.objet, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (e.contenu.isNotBlank()) {
                    Text(
                        e.contenu,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Détail d'un e-mail / SMS (ou saisie d'un échange partagé depuis une autre appli). */
@Composable
fun EchangeDialog(initial: Echange, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val contacts = rememberData { Repo.contacts() }
    val sujets = rememberData { Repo.sujets() }
    var e by remember(initial) { mutableStateOf(initial) }
    var repondre by remember { mutableStateOf<TypeMessage?>(null) }
    val nouveau = initial.id == 0L

    FormDialog(
        titre = if (nouveau) "Enregistrer un échange" else "${e.canal.icone} ${e.canal.label}",
        onDismiss = onDismiss,
        onSave = {
            val adresse = e.adresse.ifBlank {
                val c = e.contactId?.let { Repo.contact(it) }
                if (c == null) "" else if (e.canal.estEmail) emailsDe(c).firstOrNull() ?: "" else telephonesDe(c).firstOrNull() ?: ""
            }
            Repo.saveEchange(e.copy(adresse = adresse))
            onDismiss()
        },
        saveEnabled = e.contenu.isNotBlank() || e.objet.isNotBlank(),
        onDelete = if (!nouveau) ({ Repo.deleteEchange(initial.id); onDismiss() }) else null
    ) {
        if (nouveau) {
            ChoiceField("Type", Canal.values().map { it to "${it.icone} ${it.label}" }, e.canal, { e = e.copy(canal = it) })
            OutlinedTextField(
                value = e.objet, onValueChange = { e = e.copy(objet = it) },
                label = { Text("Objet") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = e.contenu, onValueChange = { e = e.copy(contenu = it) },
                label = { Text("Contenu") }, minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text("${jourHeure(e.date)} · ${e.adresse}", style = MaterialTheme.typography.bodySmall)
            if (e.objet.isNotBlank()) Text(e.objet, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(e.contenu.ifBlank { "(message vide)" }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ChoiceField("Contact", optionsContacts(contacts), e.contactId, { e = e.copy(contactId = it) })
        ChoiceField("Sujet", optionsSujets(sujets), e.sujetId, { e = e.copy(sujetId = it) })
        if (!nouveau && e.contactId != null) {
            Row {
                TextButton(onClick = {
                    repondre = if (e.canal.estEmail) TypeMessage.EMAIL else TypeMessage.SMS
                }) { Text("Répondre") }
                TextButton(onClick = {
                    e.contactId?.let { Repo.contact(it) }?.let { appeler(ctx, it.telephone) }
                }) { Text("Appeler") }
            }
        }
    }

    repondre?.let { t ->
        val objetReponse = when {
            t != TypeMessage.EMAIL || e.objet.isBlank() -> ""
            normaliserObjet(e.objet) != e.objet.trim().lowercase() -> e.objet
            else -> "RE: " + e.objet
        }
        MessageDialog(
            type = t,
            contactId = e.contactId,
            sujetId = e.sujetId,
            objet = objetReponse,
            destination = e.adresse.ifBlank { null },
            onDismiss = { repondre = null; onDismiss() }
        )
    }
}

/** Choisir qui appeler (depuis un sujet). */
@Composable
fun ChoixAppelDialog(contactIds: List<Long>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val tous = rememberData { Repo.contacts() }
    val liste = remember(tous, contactIds) {
        val lies = tous.filter { it.id in contactIds && telephonesDe(it).isNotEmpty() }
        lies.ifEmpty { tous.filter { telephonesDe(it).isNotEmpty() } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appeler") },
        text = {
            if (liste.isEmpty()) {
                Text("Aucun contact avec un numéro de téléphone.")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(liste, key = { it.id }) { c ->
                        telephonesDe(c).forEach { num ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { appeler(ctx, num); onDismiss() }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Phone, null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(c.nom, fontWeight = FontWeight.SemiBold)
                                    Text(num, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
