package com.moonsuivi.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.Prefs

data class SectionAide(val icone: String, val titre: String, val texte: String)

private val AIDE_ACCUEIL = SectionAide(
    "🏠", "Tableau de bord",
    "L'écran d'accueil résume votre journée.\n" +
        "• Les tâches 🔥 urgentes sont listées en premier ; un graphique montre la répartition par priorité.\n" +
        "• Les 4 compteurs (tâches ouvertes, en retard, du jour, sujets actifs) sont cliquables.\n" +
        "• Les listes « En retard », « Aujourd'hui » et « Dans les 7 prochains jours » reprennent les tâches à échéance.\n" +
        "• Les graphiques montrent l'avancement du kanban, les sujets par catégorie et vos échanges de la semaine.\n" +
        "• « Activité récente » affiche les derniers événements du journal."
)

private val AIDE_TACHES = SectionAide(
    "☑️", "Tâches (kanban)",
    "• + (en bas à droite ou en haut d'une colonne) : nouvelle tâche.\n" +
        "• Touchez une carte pour la modifier : titre, colonne, priorité, échéance, sujet, contact.\n" +
        "• Glisser-déposer : appui long sur une carte, puis glissez-la vers une autre colonne ou une autre place. " +
        "Le tableau défile tout seul près des bords.\n" +
        "• 📞 ✈ ✉ sur une carte : appeler, envoyer un SMS ou un e-mail au contact de la tâche. " +
        "L'échange est rangé automatiquement dans le sujet de la tâche.\n" +
        "• ⚙ : configurer les colonnes (nom, couleur, ordre, nombre maximum de cartes, colonne « terminé » ✓).\n" +
        "• Priorité : 4 niveaux (point de couleur) — gris = basse, bleu = normale, orange ⬆ = haute, rouge 🔥 = urgente " +
        "(carte encadrée de rouge). Filtres « Haute et + » / « Urgentes » et « Trier par priorité » en haut du tableau."
)

private val AIDE_AGENDA = SectionAide(
    "📅", "Agenda",
    "• Les points sous un jour indiquent des tâches à échéance (vert) et des appels (orange).\n" +
        "• Touchez un jour pour voir son détail, puis + pour créer une tâche à cette date.\n" +
        "• « Aujourd'hui » revient au mois en cours."
)

private val AIDE_SUJETS = SectionAide(
    "📁", "Sujets",
    "Un sujet regroupe tout ce qui concerne une affaire, un client, un projet…\n" +
        "• Filtrez par statut (Ouvert, En cours, En attente, Clos) et par catégorie ; « Gérer… » modifie les catégories.\n" +
        "• La fiche d'un sujet affiche ses tâches, les contacts liés et le suivi (notes, appels, e-mails, SMS).\n" +
        "• Appeler / SMS / E-mail depuis la fiche : l'échange est rattaché au sujet.\n" +
        "• « Changer le statut » met à jour le sujet et l'inscrit au journal."
)

private val AIDE_DOSSIER = SectionAide(
    "📂", "Dossier d'un sujet",
    "Le dossier rassemble toutes les pièces d'un sujet :\n" +
        "• 📷 Photo : prenez une photo, elle est rangée directement dans le dossier.\n" +
        "• 📎 Fichier : ajoutez des PDF, images, documents… depuis le téléphone (plusieurs à la fois).\n" +
        "• Les pièces jointes des e-mails rattachés au sujet y arrivent automatiquement.\n" +
        "• Les appels (avec leur enregistrement), e-mails et SMS du sujet y sont aussi.\n" +
        "• Touchez une pièce pour l'ouvrir ; ⋮ pour la partager, la renommer, l'annoter, la déplacer ou la supprimer.\n" +
        "• ✉ Envoyer : joindre des pièces du dossier à un e-mail.\n" +
        "Les fichiers sont stockés dans Android/data/com.moonsuivi.app/files/Dossiers (supprimés si l'appli est désinstallée)."
)

private val AIDE_CONTACTS = SectionAide(
    "👤", "Contacts",
    "• « Importer » copie les contacts du téléphone (sans doublons).\n" +
        "• Plusieurs numéros ou adresses : séparez-les par « / ».\n" +
        "• La fiche permet d'appeler, d'envoyer un SMS ou un e-mail, et affiche tâches, sujets et échanges liés.\n" +
        "• Renseignez l'adresse e-mail d'un contact pour que ses e-mails soient suivis."
)

private val AIDE_ECHANGES = SectionAide(
    "✉️", "Appels, e-mails et SMS",
    "• Tous les appels sont enregistrés automatiquement (entrants, sortants, manqués), avec le bon contact.\n" +
        "• Les SMS reçus de vos contacts sont enregistrés automatiquement.\n" +
        "• Les e-mails de vos contacts sont relevés toutes les 30 minutes (messagerie à configurer dans Réglages).\n" +
        "• « Synchroniser » relève immédiatement le journal d'appels et la boîte e-mail.\n" +
        "• Touchez un échange pour le lire, le rattacher à un sujet, répondre ou rappeler.\n" +
        "• Une réponse à un e-mail est rangée automatiquement dans le même sujet (même objet).\n" +
        "• Depuis Gmail : Partager › MoonSuivi pour enregistrer un e-mail."
)

private val AIDE_JOURNAL = SectionAide(
    "📖", "Journal",
    "Le fil de toute votre activité, jour par jour.\n" +
        "• ✏ : écrire une note libre (compte rendu, décision, idée), éventuellement liée à un sujet ou un contact.\n" +
        "• Sont notés automatiquement : tâches créées, déplacées, terminées ; nouveaux sujets et changements de statut ; " +
        "pièces ajoutées aux dossiers.\n" +
        "• Les appels, e-mails, SMS et notes de sujets y apparaissent aussi.\n" +
        "• Recherche, filtres et période en haut de l'écran."
)

private val AIDE_REGLAGES = SectionAide(
    "⚙", "Réglages",
    "• Autorisations : téléphone, SMS, micro, contacts.\n" +
        "• Rester actif : garde MoonSuivi en marche en arrière-plan ; désactivez l'optimisation de batterie.\n" +
        "• Organisation : colonnes du kanban, catégories de sujets.\n" +
        "• Messagerie : adresse, mot de passe d'application (Gmail), serveurs IMAP/SMTP — « Enregistrer et tester ».\n" +
        "• Appels : enregistrement automatique et source audio.\n" +
        "• Mises à jour : vérification automatique au démarrage, ou « Vérifier maintenant »."
)

private val AIDE_APPELS = SectionAide(
    "🎙", "Enregistrement des appels",
    "• Activez le service d'accessibilité « MoonSuivi » (Réglages › Autorisations › Activer), puis " +
        "« Enregistrement automatique » dans Réglages › Appels.\n" +
        "• Sur Android 11, la qualité dépend du fabricant : si seule votre voix est enregistrée, utilisez le haut-parleur " +
        "ou changez la source audio.\n" +
        "• ▶ sur un appel pour écouter l'enregistrement.\n" +
        "• En France, prévenez votre interlocuteur avant d'enregistrer."
)

private val FAQ = SectionAide(
    "❓", "Questions fréquentes",
    "• Les e-mails n'arrivent pas : vérifiez Réglages › Messagerie (Gmail : mot de passe d'application) et que " +
        "l'adresse e-mail du contact est renseignée.\n" +
        "• MoonSuivi s'arrête tout seul : Réglages › Rester actif › Désactiver l'optimisation de batterie ; " +
        "sur Xiaomi/Huawei/Samsung autorisez aussi le démarrage automatique.\n" +
        "• La mise à jour ne s'installe pas : autorisez « Installer des applis inconnues » pour MoonSuivi.\n" +
        "• Où sont mes données ? Uniquement sur le téléphone. Pensez à faire des sauvegardes du téléphone.\n" +
        "• Appui long sur un bouton icône : une bulle explique à quoi il sert.\n" +
        "• Mode sombre : menu ☰ › 🌙 Mode sombre, ou Réglages › Apparence (clair, sombre ou comme le téléphone)."
)

val GUIDE: List<SectionAide> = listOf(
    AIDE_ACCUEIL, AIDE_TACHES, AIDE_AGENDA, AIDE_SUJETS, AIDE_DOSSIER, AIDE_CONTACTS,
    AIDE_ECHANGES, AIDE_APPELS, AIDE_JOURNAL, AIDE_REGLAGES, FAQ
)

/** Aide correspondant à l'écran affiché. */
fun aidePour(e: Ecran): SectionAide = when (e) {
    Ecran.Accueil -> AIDE_ACCUEIL
    Ecran.Tableau -> AIDE_TACHES
    Ecran.Agenda -> AIDE_AGENDA
    Ecran.Sujets, is Ecran.SujetDetail -> AIDE_SUJETS
    is Ecran.Dossier -> AIDE_DOSSIER
    Ecran.Contacts, is Ecran.ContactDetail -> AIDE_CONTACTS
    Ecran.Echanges -> AIDE_ECHANGES
    Ecran.Journal -> AIDE_JOURNAL
    Ecran.Reglages -> AIDE_REGLAGES
    Ecran.Aide -> FAQ
}

/** Fenêtre d'aide de l'écran courant (bouton ⓘ en haut). */
@Composable
fun AideDialog(e: Ecran, onDismiss: () -> Unit, onGuide: () -> Unit) {
    val a = aidePour(e)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${a.icone} ${a.titre}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(a.texte, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Compris") } },
        dismissButton = { TextButton(onClick = onGuide) { Text("Guide complet") } }
    )
}

/** Guide complet, rubrique par rubrique. */
@Composable
fun AideScreen() {
    val ctx = LocalContext.current
    var ouverte by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Bienvenue dans MoonSuivi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Pour bien démarrer :\n" +
                            "1. Réglages ⚙ › Autorisations › Autoriser.\n" +
                            "2. Contacts › Importer.\n" +
                            "3. Réglages › Messagerie pour suivre vos e-mails.\n" +
                            "4. Créez un sujet, puis ses tâches et son dossier.\n\n" +
                            "Partout dans l'appli : ⓘ en haut = aide de l'écran, appui long sur une icône = sa description, " +
                            "💡 = astuce (« Compris » pour la masquer).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        items(GUIDE, key = { it.titre }) { a ->
            val deplie = ouverte == a.titre
            Card(onClick = { ouverte = if (deplie) null else a.titre }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.icone, modifier = Modifier.width(32.dp))
                        Text(a.titre, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(
                            if (deplie) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            if (deplie) "Replier" else "Déplier"
                        )
                    }
                    if (deplie) {
                        Spacer(Modifier.height(8.dp))
                        Text(a.texte, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { Prefs.reafficherAstuces(ctx); message = "Les astuces 💡 s'afficheront à nouveau." },
                modifier = Modifier.fillMaxWidth()
            ) { Text("💡 Réafficher toutes les astuces") }
            message?.let { Indication(it) }
        }
    }
}
