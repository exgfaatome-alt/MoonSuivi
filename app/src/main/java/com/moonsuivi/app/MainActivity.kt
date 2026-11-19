package com.moonsuivi.app

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.moonsuivi.app.calls.Lecteur
import com.moonsuivi.app.data.Repo
import com.moonsuivi.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent { MoonSuiviTheme { App() } }
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

private val ONGLETS = listOf(
    Onglet(Ecran.Tableau, "Tableau", Icons.Default.List),
    Onglet(Ecran.Agenda, "Agenda", Icons.Default.DateRange),
    Onglet(Ecran.Sujets, "Sujets", Icons.Default.Star),
    Onglet(Ecran.Contacts, "Contacts", Icons.Default.Person),
    Onglet(Ecran.Appels, "Appels", Icons.Default.Call)
)

@Composable
fun App() {
    var pile by remember { mutableStateOf(listOf<Ecran>(Ecran.Tableau)) }
    val courant = pile.last()
    val ouvrir: (Ecran) -> Unit = { pile = pile + it }
    val retour: () -> Unit = { if (pile.size > 1) pile = pile.dropLast(1) }

    BackHandler(enabled = pile.size > 1) { retour() }

    val titre = when (courant) {
        Ecran.Tableau -> "Tableau des tâches"
        Ecran.Agenda -> "Agenda"
        Ecran.Sujets -> "Suivi des sujets"
        Ecran.Contacts -> "Contacts"
        Ecran.Appels -> "Appels"
        is Ecran.SujetDetail -> "Sujet"
        is Ecran.ContactDetail -> "Contact"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titre) },
                navigationIcon = {
                    if (pile.size > 1) {
                        IconButton(onClick = retour) { Icon(Icons.Default.ArrowBack, "Retour") }
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
                        label = { Text(o.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val e = courant) {
                Ecran.Tableau -> KanbanScreen()
                Ecran.Agenda -> AgendaScreen()
                Ecran.Sujets -> SujetsScreen(ouvrir)
                Ecran.Contacts -> ContactsScreen(ouvrir)
                Ecran.Appels -> AppelsScreen()
                is Ecran.SujetDetail -> key(e.id) { SujetDetailScreen(e.id, ouvrir) }
                is Ecran.ContactDetail -> key(e.id) { ContactDetailScreen(e.id, ouvrir) }
            }
        }
    }
}
