package com.moonsuivi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.moonsuivi.app.calls.Lecteur
import com.moonsuivi.app.data.Canal
import com.moonsuivi.app.data.Echange
import com.moonsuivi.app.data.Prefs
import com.moonsuivi.app.data.Repo
import com.moonsuivi.app.mail.MailWorker
import com.moonsuivi.app.service.ServiceActif
import kotlinx.coroutines.launch
import com.moonsuivi.app.update.InfoMaj
import com.moonsuivi.app.update.MiseAJour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.moonsuivi.app.ui.*

class MainActivity : ComponentActivity() {
    /** E-mail / texte partagé depuis une autre appli (bouton « Partager »). */
    private val partage = mutableStateOf<Echange?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        MailWorker.planifier(this)
        ServiceActif.demarrer(this)
        if (savedInstanceState == null) lirePartage(intent)
        setContent { MoonSuiviTheme { App(partage) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lirePartage(intent)
    }

    private fun lirePartage(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_SEND) return
        if (i.type?.startsWith("text/") != true) return
        val objet = i.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
        val texte = i.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val adresse = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+").find(texte)?.value ?: ""
        val contact = if (adresse.isNotBlank()) Repo.findContactByEmail(adresse) else null
        partage.value = Echange(
            canal = Canal.EMAIL_RECU,
            contactId = contact?.id,
            sujetId = contact?.let { Repo.sujetDuFil(it.id, objet) },
            adresse = adresse,
            objet = objet,
            contenu = texte.take(8000)
        )
    }

    override fun onStop() {
        super.onStop()
        Lecteur.stop()
    }
}

@Composable
fun MoonSuiviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF3F51B5),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDDE1FF),
            onPrimaryContainer = Color(0xFF001257),
            secondary = Color(0xFF00897B),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD0F0EA),
            tertiary = Color(0xFFF57C00),
            surfaceVariant = Color(0xFFECEEF6)
        ),
        content = content
    )
}

private data class Onglet(val ecran: Ecran, val label: String, val icone: ImageVector)

/** Barre du bas : les écrans les plus utilisés. */
private val ONGLETS = listOf(
    Onglet(Ecran.Accueil, "Accueil", Icons.Default.Home),
    Onglet(Ecran.Tableau, "Tâches", Icons.Default.List),
    Onglet(Ecran.Sujets, "Sujets", Icons.Default.Star),
    Onglet(Ecran.Contacts, "Contacts", Icons.Default.Person),
    Onglet(Ecran.Echanges, "Échanges", Icons.Default.Email)
)

/** Menu latéral : tous les écrans. */
private val MENU = listOf(
    Onglet(Ecran.Accueil, "Tableau de bord", Icons.Default.Home),
    Onglet(Ecran.Tableau, "Tâches (kanban)", Icons.Default.List),
    Onglet(Ecran.Agenda, "Agenda", Icons.Default.DateRange),
    Onglet(Ecran.Sujets, "Sujets", Icons.Default.Star),
    Onglet(Ecran.Contacts, "Contacts", Icons.Default.Person),
    Onglet(Ecran.Echanges, "Appels, e-mails, SMS", Icons.Default.Email),
    Onglet(Ecran.Journal, "Journal", Icons.Default.Edit),
    Onglet(Ecran.Reglages, "Réglages", Icons.Default.Settings),
    Onglet(Ecran.Aide, "Aide et guide", Icons.Default.Info)
)

@Composable
fun App(partage: MutableState<Echange?>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tiroir = rememberDrawerState(DrawerValue.Closed)
    var pile by remember { mutableStateOf(listOf<Ecran>(Ecran.Accueil)) }
    var maj by remember { mutableStateOf<InfoMaj?>(null) }
    var aide by remember { mutableStateOf(false) }
    val colonnes = rememberData { Repo.colonnes() }

    // Vérification automatique des mises à jour (au plus toutes les 6 h)
    LaunchedEffect(Unit) {
        if (Prefs.majAuto(ctx) && System.currentTimeMillis() - Prefs.majDerniereVerif(ctx) > 6L * 3600 * 1000) {
            val info = withContext(Dispatchers.IO) {
                runCatching { MiseAJour.verifier(Prefs.majUrl(ctx)) }.getOrNull()
            }
            Prefs.setMajDerniereVerif(ctx, System.currentTimeMillis())
            if (info != null) maj = info
        }
    }
    val courant = pile.last()
    val ouvrir: (Ecran) -> Unit = { pile = pile + it }
    val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) }
    val allerA: (Ecran) -> Unit = { e ->
        pile = listOf(e)
        scope.launch { tiroir.close() }
    }

    BackHandler(enabled = pile.size > 1 || tiroir.isOpen) {
        if (tiroir.isOpen) scope.launch { tiroir.close() } else retour()
    }

    val titre = when (courant) {
        Ecran.Accueil -> "Tableau de bord"
        Ecran.Tableau -> "Tâches"
        Ecran.Agenda -> "Agenda"
        Ecran.Sujets -> "Suivi des sujets"
        Ecran.Contacts -> "Contacts"
        Ecran.Echanges -> "Appels, e-mails et SMS"
        Ecran.Journal -> "Journal"
        Ecran.Reglages -> "Réglages"
        Ecran.Aide -> "Aide"
        is Ecran.Dossier -> "Dossier"
        is Ecran.SujetDetail -> "Sujet"
        is Ecran.ContactDetail -> "Contact"
    }

    CompositionLocalProvider(LocalColonnes provides colonnes) {
        ModalNavigationDrawer(
            drawerState = tiroir,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        "MoonSuivi",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp)
                    )
                    MENU.forEach { o ->
                        NavigationDrawerItem(
                            label = { Text(o.label) },
                            icon = { Icon(o.icone, null) },
                            selected = pile.first() == o.ecran,
                            onClick = { allerA(o.ecran) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(titre) },
                        actions = {
                            BoutonIcone(
                                Icons.Default.Info, "Aide sur cet écran",
                                teinte = MaterialTheme.colorScheme.onPrimary
                            ) { aide = true }
                            if (courant != Ecran.Reglages) {
                                BoutonIcone(
                                    Icons.Default.Settings, "Réglages",
                                    teinte = MaterialTheme.colorScheme.onPrimary
                                ) { pile = pile + Ecran.Reglages }
                            }
                        },
                        navigationIcon = {
                            if (pile.size > 1) {
                                BoutonIcone(Icons.Default.ArrowBack, "Retour", onClick = retour)
                            } else {
                                BoutonIcone(Icons.Default.Menu, "Menu : tous les écrans") {
                                    scope.launch { tiroir.open() }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                },
                bottomBar = {
                    NavigationBar {
                        ONGLETS.forEach { o ->
                            NavigationBarItem(
                                selected = pile.first() == o.ecran,
                                onClick = { pile = listOf(o.ecran) },
                                icon = { Icon(o.icone, null) },
                                label = { Text(o.label, maxLines = 1) }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (val e = courant) {
                        Ecran.Accueil -> AccueilScreen(ouvrir)
                        Ecran.Tableau -> KanbanScreen()
                        Ecran.Agenda -> AgendaScreen()
                        Ecran.Sujets -> SujetsScreen(ouvrir)
                        Ecran.Contacts -> ContactsScreen(ouvrir)
                        Ecran.Echanges -> EchangesScreen()
                        Ecran.Journal -> JournalScreen(ouvrir)
                        Ecran.Reglages -> ReglagesScreen { maj = it }
                        Ecran.Aide -> AideScreen()
                        is Ecran.Dossier -> key(e.sujetId) { DossierScreen(e.sujetId, ouvrir) }
                        is Ecran.SujetDetail -> key(e.id) { SujetDetailScreen(e.id, ouvrir) }
                        is Ecran.ContactDetail -> key(e.id) { ContactDetailScreen(e.id, ouvrir) }
                    }
                }
            }
        }

        maj?.let { MajDialog(it) { maj = null } }
        if (aide) {
            AideDialog(courant, onDismiss = { aide = false }) {
                aide = false
                pile = pile + Ecran.Aide
            }
        }
        partage.value?.let { EchangeDialog(it) { partage.value = null } }
    }
}
