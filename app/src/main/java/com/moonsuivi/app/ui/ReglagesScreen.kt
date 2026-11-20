package com.moonsuivi.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.moonsuivi.app.data.ConfigMail
import com.moonsuivi.app.data.Prefs
import com.moonsuivi.app.mail.Courrier
import com.moonsuivi.app.mail.MailWorker
import com.moonsuivi.app.service.ServiceActif
import com.moonsuivi.app.update.InfoMaj
import com.moonsuivi.app.update.MiseAJour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PERMISSIONS = arrayOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.RECEIVE_SMS
)

private val SOURCES_AUDIO: List<Pair<Int, String>> = listOf(
    MediaRecorder.AudioSource.VOICE_RECOGNITION to "Reconnaissance vocale (recommandé)",
    MediaRecorder.AudioSource.MIC to "Micro",
    MediaRecorder.AudioSource.VOICE_COMMUNICATION to "Communication vocale",
    MediaRecorder.AudioSource.VOICE_CALL to "Ligne d'appel (téléphones rootés)"
)

@Composable
private fun Bloc(titre: String, contenu: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            contenu()
        }
    }
}

@Composable
private fun Aide(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun ReglagesScreen(onMaj: (InfoMaj) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Re-vérifie permissions / accessibilité au retour dans l'appli
    var verif by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) verif++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val permissionsOk = remember(verif) {
        PERMISSIONS.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    val accessOk = remember(verif) { accessibiliteActive(ctx) }
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { verif++ }

    // Fonctionnement permanent
    var resterActif by remember { mutableStateOf(Prefs.resterActif(ctx)) }
    val batterieOk = remember(verif) { ServiceActif.batterieLibre(ctx) }
    var colonnesOuvert by remember { mutableStateOf(false) }
    var categoriesOuvert by remember { mutableStateOf(false) }

    // Appels
    var auto by remember { mutableStateOf(Prefs.autoRecord(ctx)) }
    var source by remember { mutableStateOf(Prefs.audioSource(ctx)) }

    // Messagerie
    val initial = remember { Prefs.mail(ctx) }
    var fournisseur by remember { mutableStateOf("") }
    var adresse by remember { mutableStateOf(initial.utilisateur) }
    var motDePasse by remember { mutableStateOf(initial.motDePasse) }
    var imapHote by remember { mutableStateOf(initial.imapHote) }
    var imapPort by remember { mutableStateOf(initial.imapPort.toString()) }
    var smtpHote by remember { mutableStateOf(initial.smtpHote) }
    var smtpPort by remember { mutableStateOf(initial.smtpPort.toString()) }
    var releveAuto by remember { mutableStateOf(initial.auto) }
    var testEnCours by remember { mutableStateOf(false) }
    var resultatMail by remember { mutableStateOf<String?>(null) }

    // Mises à jour
    var urlMaj by remember { mutableStateOf(Prefs.majUrl(ctx)) }
    var majAuto by remember { mutableStateOf(Prefs.majAuto(ctx)) }
    var verifMaj by remember { mutableStateOf(false) }
    var resultatMaj by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Bloc("Autorisations") {
                EtatLigne(
                    ok = permissionsOk,
                    texte = if (permissionsOk) "Téléphone, SMS, micro et contacts autorisés"
                    else "Autorisations manquantes (appels, SMS, micro, contacts)",
                    action = "Autoriser"
                ) { demande.launch(PERMISSIONS) }
                EtatLigne(
                    ok = accessOk,
                    texte = if (accessOk) "Service d'accessibilité actif"
                    else "Activez le service d'accessibilité « MoonSuivi » (enregistrement audio des appels)",
                    action = "Activer"
                ) { runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
            }
        }

        item {
            Bloc("Rester actif en permanence") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Garder MoonSuivi actif en arrière-plan", modifier = Modifier.weight(1f))
                    Switch(checked = resterActif, onCheckedChange = {
                        resterActif = it
                        Prefs.setResterActif(ctx, it)
                        if (it) ServiceActif.demarrer(ctx) else ServiceActif.arreter(ctx)
                        MailWorker.planifier(ctx)
                    })
                }
                EtatLigne(
                    ok = batterieOk,
                    texte = if (batterieOk) "Optimisation de batterie désactivée pour MoonSuivi"
                    else "Android peut mettre MoonSuivi en veille pour économiser la batterie",
                    action = "Désactiver"
                ) { ServiceActif.demanderBatterieLibre(ctx) }
                Aide(
                    "Une notification discrète « MoonSuivi est actif » reste affichée : c'est elle qui empêche " +
                        "Android de fermer l'appli. MoonSuivi redémarre aussi tout seul après un redémarrage du téléphone. " +
                        "Sur Xiaomi, Huawei, Oppo ou Samsung, autorisez aussi le « Démarrage automatique » / " +
                        "« Activité en arrière-plan » dans les paramètres de l'appli."
                )
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                        )
                    }
                }) { Text("Ouvrir les paramètres de l'appli") }
            }
        }

        item {
            Bloc("Organisation") {
                OutlinedButton(onClick = { colonnesOuvert = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Colonnes du tableau des tâches")
                }
                OutlinedButton(onClick = { categoriesOuvert = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Catégories de sujets")
                }
            }
        }

        item {
            Bloc("Messagerie (suivi des e-mails)") {
                ChoiceField(
                    "Fournisseur",
                    listOf("" to "Choisir pour préremplir…") + Courrier.FOURNISSEURS.map { it.first to it.first },
                    fournisseur,
                    { nom ->
                        fournisseur = nom
                        Courrier.FOURNISSEURS.firstOrNull { it.first == nom }?.second?.let { p ->
                            imapHote = p.imapHote; imapPort = p.imapPort.toString()
                            smtpHote = p.smtpHote; smtpPort = p.smtpPort.toString()
                        }
                    }
                )
                OutlinedTextField(
                    value = adresse, onValueChange = { adresse = it },
                    label = { Text("Votre adresse e-mail") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = motDePasse, onValueChange = { motDePasse = it },
                    label = { Text("Mot de passe (d'application)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = imapHote, onValueChange = { imapHote = it },
                        label = { Text("Serveur IMAP") }, singleLine = true, modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = imapPort, onValueChange = { imapPort = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = smtpHote, onValueChange = { smtpHote = it },
                        label = { Text("Serveur SMTP") }, singleLine = true, modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = smtpPort, onValueChange = { smtpPort = it.filter { c -> c.isDigit() } },
                        label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Relève automatique (toutes les 30 min)", modifier = Modifier.weight(1f))
                    Switch(checked = releveAuto, onCheckedChange = { releveAuto = it })
                }
                Aide(
                    "Seuls les e-mails échangés avec vos contacts (adresse renseignée dans leur fiche) sont importés. " +
                        "Gmail : activez la validation en 2 étapes puis créez un « mot de passe d'application » " +
                        "sur myaccount.google.com/apppasswords et saisissez-le ici (pas votre mot de passe habituel)."
                )
                Button(
                    enabled = !testEnCours && adresse.isNotBlank() && motDePasse.isNotBlank() &&
                        imapHote.isNotBlank() && smtpHote.isNotBlank(),
                    onClick = {
                        val cfg = ConfigMail(
                            imapHote = imapHote.trim(),
                            imapPort = imapPort.toIntOrNull() ?: 993,
                            smtpHote = smtpHote.trim(),
                            smtpPort = smtpPort.toIntOrNull() ?: 465,
                            utilisateur = adresse.trim(),
                            motDePasse = motDePasse,
                            auto = releveAuto
                        )
                        Prefs.setMail(ctx, cfg)
                        MailWorker.planifier(ctx)
                        testEnCours = true
                        resultatMail = "Connexion en cours…"
                        scope.launch {
                            resultatMail = withContext(Dispatchers.IO) {
                                try {
                                    Courrier.tester(cfg)
                                    val n = Courrier.synchroniser(ctx)
                                    "✅ Connexion réussie — $n e-mail(s) importé(s)"
                                } catch (e: Exception) {
                                    "❌ Échec : ${e.message ?: e.javaClass.simpleName}"
                                }
                            }
                            testEnCours = false
                        }
                    }
                ) { Text("Enregistrer et tester") }
                if (testEnCours) LinearProgressIndicator(Modifier.fillMaxWidth())
                resultatMail?.let { Text(it) }
                val dernier = Prefs.mailDernierSync(ctx)
                if (dernier > 0) Aide("Dernière relève : ${jourHeure(dernier)}")
                Prefs.mailErreur(ctx)?.let { if (resultatMail == null) Aide("Dernière erreur : $it") }
            }
        }

        item {
            Bloc("Appels") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enregistrement automatique", modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it; Prefs.setAutoRecord(ctx, it) })
                }
                ChoiceField("Source audio", SOURCES_AUDIO, source, { source = it; Prefs.setAudioSource(ctx, it) })
                Aide(
                    "Sur Android 11, une appli non système ne peut pas capter directement la ligne téléphonique : " +
                        "la qualité dépend du fabricant. Si seule votre voix est enregistrée, activez le haut-parleur " +
                        "ou essayez une autre source audio. Prévenez votre interlocuteur avant d'enregistrer."
                )
            }
        }

        item {
            Bloc("Mises à jour") {
                Text("Version installée : ${MiseAJour.versionActuelle}")
                OutlinedTextField(
                    value = urlMaj, onValueChange = { urlMaj = it; Prefs.setMajUrl(ctx, it) },
                    label = { Text("Adresse du fichier version.json") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vérifier automatiquement au démarrage", modifier = Modifier.weight(1f))
                    Switch(checked = majAuto, onCheckedChange = { majAuto = it; Prefs.setMajAuto(ctx, it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !verifMaj,
                        onClick = {
                            verifMaj = true
                            resultatMaj = null
                            scope.launch {
                                try {
                                    val info = withContext(Dispatchers.IO) { MiseAJour.verifier(urlMaj.trim()) }
                                    Prefs.setMajDerniereVerif(ctx, System.currentTimeMillis())
                                    if (info == null) resultatMaj = "✅ Vous avez la dernière version." else onMaj(info)
                                } catch (e: Exception) {
                                    resultatMaj = "❌ ${e.message ?: e.javaClass.simpleName}"
                                }
                                verifMaj = false
                            }
                        }
                    ) { Text(if (verifMaj) "Vérification…" else "Vérifier maintenant") }
                    TextButton(onClick = {
                        urlMaj = Prefs.URL_MAJ_DEFAUT; Prefs.setMajUrl(ctx, Prefs.URL_MAJ_DEFAUT)
                    }) { Text("Adresse par défaut") }
                }
                resultatMaj?.let { Text(it) }
            }
        }
    }

    if (colonnesOuvert) ColonnesDialog { colonnesOuvert = false }
    if (categoriesOuvert) CategoriesDialog { categoriesOuvert = false }
}

/** Fenêtre « Mise à jour disponible » : téléchargement puis lancement de l'installateur Android. */
@Composable
fun MajDialog(info: InfoMaj, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progres by remember { mutableStateOf<Float?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (progres == null) onDismiss() },
        title = { Text("Mise à jour disponible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Version ${info.versionName} — vous avez la ${MiseAJour.versionActuelle}.")
                if (info.notes.isNotBlank()) Aide(info.notes)
                progres?.let { p ->
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Aide("Téléchargement… ${(p * 100).toInt()} %")
                }
                erreur?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = progres == null,
                onClick = {
                    if (!MiseAJour.installationAutorisee(ctx)) {
                        MiseAJour.ouvrirAutorisation(ctx)
                        erreur = "Autorisez « Installer des applis inconnues » pour MoonSuivi, revenez ici puis touchez à nouveau Installer."
                        return@TextButton
                    }
                    erreur = null
                    progres = 0f
                    scope.launch {
                        try {
                            val apk = withContext(Dispatchers.IO) {
                                MiseAJour.telecharger(ctx, info) { p -> progres = p }
                            }
                            MiseAJour.installer(ctx, apk)
                            onDismiss()
                        } catch (e: Exception) {
                            erreur = "Échec : ${e.message ?: e.javaClass.simpleName}"
                            progres = null
                        }
                    }
                }
            ) { Text("Installer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = progres == null) { Text("Plus tard") }
        }
    )
}
