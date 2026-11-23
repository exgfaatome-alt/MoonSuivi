package com.moonsuivi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.moonsuivi.app.data.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** État du glisser-déposer (positions en coordonnées de l'écran). */
private class EtatGlisser {
    var tache by mutableStateOf<Tache?>(null)
    var pointeur by mutableStateOf(Offset.Zero)
    var prise = Offset.Zero
    var largeur by mutableStateOf(0)
    var cible by mutableStateOf<Pair<Long, Int>?>(null)
    val cartes = HashMap<Long, Rect>()
    val colonnes = HashMap<Long, Rect>()
    var listes: Map<Long, List<Tache>> = emptyMap()

    val actif: Boolean get() = tache != null

    fun calculerCible() {
        val t = tache ?: return
        val col = colonnes.entries.firstOrNull { it.value.contains(pointeur) }?.key
        if (col == null) {
            cible = null
            return
        }
        val liste = listes[col].orEmpty().filter { it.id != t.id }
        val index = liste.count { (cartes[it.id]?.center?.y ?: Float.MAX_VALUE) < pointeur.y }
        cible = col to index
    }

    fun annuler() {
        tache = null
        cible = null
    }
}

@Composable
fun KanbanScreen() {
    val colonnes = LocalColonnes.current
    val taches = rememberData { Repo.taches() }
    val sujets = rememberData { Repo.sujets() }
    val contacts = rememberData { Repo.contacts() }
    val nomSujet = remember(sujets) { sujets.associate { it.id to it.titre } }
    val nomContact = remember(contacts) { contacts.associate { it.id to it.nom } }
    var filtreSujet by remember { mutableStateOf<Long?>(null) }
    var prioriteMin by remember { mutableStateOf(PRIORITE_BASSE) }
    var trierParPriorite by remember { mutableStateOf(false) }
    var edition by remember { mutableStateOf<Tache?>(null) }
    var configurer by remember { mutableStateOf(false) }

    val drag = remember { EtatGlisser() }
    val scrollLigne = rememberScrollState()
    val scrollColonnes = remember { HashMap<Long, ScrollState>() }
    var origine by remember { mutableStateOf(Offset.Zero) }
    var zoneLigne by remember { mutableStateOf(Rect.Zero) }
    val densite = LocalDensity.current

    val idsColonnes = colonnes.map { it.id }.toSet()
    val premiere = colonnes.firstOrNull()?.id ?: 0L
    val visibles = taches.filter { (filtreSujet == null || it.sujetId == filtreSujet) && it.priorite >= prioriteMin }
    // Une tâche dont la colonne a disparu est affichée dans la première colonne
    val parColonne = colonnes.associate { c ->
        val liste = visibles.filter { (if (it.colonneId in idsColonnes) it.colonneId else premiere) == c.id }
        c.id to if (trierParPriorite) liste.sortedByDescending { it.priorite } else liste
    }
    drag.listes = parColonne

    fun deposer() {
        val t = drag.tache
        val c = drag.cible
        drag.annuler()
        if (t == null || c == null) return
        // Trié par priorité : l'ordre est imposé, on change seulement de colonne
        if (trierParPriorite) {
            if (t.colonneId != c.first) Repo.moveTache(t.id, c.first)
            return
        }
        // Traduit la position dans la liste filtrée en position dans la colonne complète
        val avant = parColonne[c.first].orEmpty().filter { it.id != t.id }.getOrNull(c.second)?.id
        val complete = taches.filter { it.colonneId == c.first && it.id != t.id }
        val index = if (avant == null) complete.size else complete.indexOfFirst { it.id == avant }.coerceAtLeast(0)
        Repo.deplacerTache(t.id, c.first, index)
    }

    // Défilement automatique quand on approche du bord pendant le glisser
    LaunchedEffect(drag.actif) {
        while (drag.actif) {
            val p = drag.pointeur
            val marge = with(densite) { 56.dp.toPx() }
            if (p.x > zoneLigne.right - marge) scrollLigne.scrollBy(28f)
            else if (p.x < zoneLigne.left + marge) scrollLigne.scrollBy(-28f)
            drag.cible?.first?.let { cid ->
                val r = drag.colonnes[cid]
                val s = scrollColonnes[cid]
                if (r != null && s != null) {
                    if (p.y > r.bottom - marge) s.scrollBy(22f)
                    else if (p.y < r.top + marge * 2) s.scrollBy(-22f)
                }
            }
            drag.calculerCible()
            delay(16)
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origine = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChoiceField(
                    "Filtrer par sujet",
                    listOf<Pair<Long?, String>>(null to "Tous les sujets") + sujets.map { it.id to it.titre },
                    filtreSujet,
                    { filtreSujet = it },
                    Modifier.weight(1f)
                )
                BoutonIcone(Icons.Default.Settings, "Configurer les colonnes", onClick = { configurer = true })
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    FilterChip(
                        selected = prioriteMin == PRIORITE_BASSE,
                        onClick = { prioriteMin = PRIORITE_BASSE },
                        label = { Text("Toutes") }
                    )
                }
                item {
                    FilterChip(
                        selected = prioriteMin == PRIORITE_HAUTE,
                        onClick = { prioriteMin = if (prioriteMin == PRIORITE_HAUTE) PRIORITE_BASSE else PRIORITE_HAUTE },
                        label = { Text("⬆ Haute et +") },
                        leadingIcon = { Point(couleurPriorite(PRIORITE_HAUTE)) }
                    )
                }
                item {
                    FilterChip(
                        selected = prioriteMin == PRIORITE_URGENTE,
                        onClick = { prioriteMin = if (prioriteMin == PRIORITE_URGENTE) PRIORITE_BASSE else PRIORITE_URGENTE },
                        label = { Text("🔥 Urgentes") },
                        leadingIcon = { Point(couleurPriorite(PRIORITE_URGENTE)) }
                    )
                }
                item {
                    Infobulle("Classer les cartes de chaque colonne de la plus urgente à la moins urgente") {
                        FilterChip(
                            selected = trierParPriorite,
                            onClick = { trierParPriorite = !trierParPriorite },
                            label = { Text("Trier par priorité") }
                        )
                    }
                }
            }
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Astuce(
                    "kanban",
                    "Appui long sur une carte puis glissez-la vers une autre colonne. ⚙ configure les colonnes. " +
                        "Les icônes 📞 ✈ ✉ d'une carte appellent ou écrivent au contact de la tâche."
                )
            }
            Text(
                "Appui long sur une carte puis glissez-la pour la déplacer",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { zoneLigne = it.boundsInRoot() }
                    .horizontalScroll(scrollLigne, enabled = !drag.actif)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colonnes.forEach { col ->
                    KanbanColonne(
                        col = col,
                        cartes = parColonne[col.id].orEmpty(),
                        drag = drag,
                        nomSujet = nomSujet,
                        nomContact = nomContact,
                        scrollColonnes = scrollColonnes,
                        onEdit = { edition = it },
                        onAdd = { edition = Tache(colonneId = col.id, sujetId = filtreSujet) },
                        onDeposer = { deposer() }
                    )
                }
                OutlinedButton(onClick = { configurer = true }, modifier = Modifier.padding(top = 4.dp, end = 8.dp)) {
                    Icon(Icons.Default.Add, null)
                    Text("Colonne")
                }
            }
        }

        // Carte qui suit le doigt
        drag.tache?.let { t ->
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (drag.pointeur.x - drag.prise.x - origine.x).roundToInt(),
                            (drag.pointeur.y - drag.prise.y - origine.y).roundToInt()
                        )
                    }
                    .width(with(densite) { drag.largeur.toDp() })
                    .graphicsLayer {
                        rotationZ = 2.5f
                        shadowElevation = 24f
                        alpha = 0.95f
                    }
            ) {
                TacheCarte(
                    t = t,
                    sujet = t.sujetId?.let { nomSujet[it] },
                    contact = t.contactId?.let { nomContact[it] },
                    onClick = {},
                    dansKanban = true
                )
            }
        }

        if (!drag.actif) {
            ExtendedFloatingActionButton(
                onClick = { edition = Tache(sujetId = filtreSujet) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Tâche")
        }
        }
    }

    edition?.let { TacheDialog(it) { edition = null } }
    if (configurer) ColonnesDialog { configurer = false }
}

@Composable
private fun KanbanColonne(
    col: Colonne,
    cartes: List<Tache>,
    drag: EtatGlisser,
    nomSujet: Map<Long, String>,
    nomContact: Map<Long, String>,
    scrollColonnes: MutableMap<Long, ScrollState>,
    onEdit: (Tache) -> Unit,
    onAdd: () -> Unit,
    onDeposer: () -> Unit
) {
    val scroll = rememberScrollState()
    DisposableEffect(col.id) {
        scrollColonnes[col.id] = scroll
        onDispose { scrollColonnes.remove(col.id) }
    }
    val couleur = Color(col.couleur)
    val cible = drag.cible
    val estCible = drag.actif && cible?.first == col.id
    val depasse = col.limite > 0 && cartes.size > col.limite

    Surface(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .padding(bottom = 8.dp)
            .onGloballyPositioned { drag.colonnes[col.id] = it.boundsInRoot() },
        shape = RoundedCornerShape(12.dp),
        color = if (estCible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (estCible) BorderStroke(2.dp, couleur) else null
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(couleur))
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    col.nom + if (col.terminee) " ✓" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Pastille(
                    if (col.limite > 0) "${cartes.size}/${col.limite}" else "${cartes.size}",
                    if (depasse) Color(0xFFD32F2F) else couleur
                )
                BoutonIcone(Icons.Default.Add, "Ajouter dans ${col.nom}", onClick = onAdd)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll, enabled = !drag.actif)
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var k = 0
                cartes.forEach { t ->
                    val enMain = drag.tache?.id == t.id
                    if (!enMain) {
                        if (estCible && cible?.second == k) BarreInsertion(couleur)
                        k++
                    }
                    CarteGlissable(
                        t = t,
                        drag = drag,
                        sujet = t.sujetId?.let { nomSujet[it] },
                        contact = t.contactId?.let { nomContact[it] },
                        fantome = enMain,
                        onClick = { onEdit(t) },
                        onDeposer = onDeposer
                    )
                }
                if (estCible && (cible?.second ?: 0) >= k) BarreInsertion(couleur)
                if (cartes.isEmpty() && !drag.actif) {
                    Text(
                        "Aucune tâche",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarreInsertion(couleur: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(couleur, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun CarteGlissable(
    t: Tache,
    drag: EtatGlisser,
    sujet: String?,
    contact: String?,
    fantome: Boolean,
    onClick: () -> Unit,
    onDeposer: () -> Unit
) {
    val tache by rememberUpdatedState(t)
    val deposer by rememberUpdatedState(onDeposer)
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { drag.cartes[t.id] = it.boundsInRoot() }
            .graphicsLayer { alpha = if (fantome) 0.25f else 1f }
            .pointerInput(t.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        val r = drag.cartes[tache.id] ?: Rect.Zero
                        drag.prise = pos
                        drag.pointeur = r.topLeft + pos
                        drag.largeur = r.width.roundToInt()
                        drag.tache = tache
                        drag.calculerCible()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        // Position recalculée depuis la carte : reste juste même pendant le défilement automatique
                        val r = drag.cartes[tache.id]
                        if (r != null) drag.pointeur = r.topLeft + change.position
                        drag.calculerCible()
                    },
                    onDragEnd = { deposer() },
                    onDragCancel = { drag.annuler() }
                )
            }
    ) {
        TacheCarte(t = t, sujet = sujet, contact = contact, onClick = onClick, dansKanban = true)
    }
}

@Composable
fun TacheCarte(
    t: Tache,
    sujet: String?,
    contact: String?,
    onClick: () -> Unit,
    onMove: ((Long) -> Unit)? = null,
    dansKanban: Boolean = false
) {
    val colonnes = LocalColonnes.current
    val col = colonnes.firstOrNull { it.id == t.colonneId } ?: colonnes.firstOrNull()
    val termine = col?.terminee == true
    val ech = t.echeance
    val enRetard = ech != null && ech < debutJour(System.currentTimeMillis()) && !termine
    val urgente = t.priorite >= PRIORITE_URGENTE && !termine
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (urgente) BorderStroke(2.dp, couleurPriorite(PRIORITE_URGENTE)) else null
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = if (onMove != null) 2.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Point(couleurPriorite(t.priorite))
                Spacer(Modifier.width(8.dp))
                Text(
                    t.titre,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (termine) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )
                if (!dansKanban && col != null) Pastille(col.nom, Color(col.couleur))
                Spacer(Modifier.width(8.dp))
            }
            if (t.priorite >= PRIORITE_HAUTE && !termine) {
                Box(Modifier.padding(vertical = 2.dp)) {
                    Pastille("${iconePriorite(t.priorite)} ${libellePriorite(t.priorite)}", couleurPriorite(t.priorite))
                }
            }
            if (t.description.isNotBlank()) {
                Text(
                    t.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sujet != null) Text("📁 $sujet", style = MaterialTheme.typography.labelMedium)
            if (contact != null) Text("👤 $contact", style = MaterialTheme.typography.labelMedium)
            if (ech != null) {
                Text(
                    (if (enRetard) "⚠ En retard — " else "📅 ") + jour(ech),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enRetard) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (t.contactId != null) {
                ActionsContact(t.contactId, t.sujetId, t.titre, compact = true)
            }
            if (onMove != null && col != null) {
                val i = colonnes.indexOfFirst { it.id == col.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i > 0) {
                        IconButton(onClick = { onMove(colonnes[i - 1].id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowLeft, "Vers ${colonnes[i - 1].nom}")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (i >= 0 && i < colonnes.size - 1) {
                        IconButton(onClick = { onMove(colonnes[i + 1].id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowRight, "Vers ${colonnes[i + 1].nom}")
                        }
                    }
                }
            }
        }
    }
}

// ---------- Configuration des colonnes ----------

@Composable
fun ColonnesDialog(onDismiss: () -> Unit) {
    val colonnes = rememberData { Repo.colonnes() }
    val taches = rememberData { Repo.taches() }
    var edition by remember { mutableStateOf<Colonne?>(null) }

    fun echanger(i: Int, j: Int) {
        val ids = colonnes.map { it.id }.toMutableList()
        val tmp = ids[i]
        ids[i] = ids[j]
        ids[j] = tmp
        Repo.ordonnerColonnes(ids)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colonnes du tableau") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                colonnes.forEachIndexed { i, c ->
                    val n = taches.count { it.colonneId == c.id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Point(Color(c.couleur), 14)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nom + if (c.terminee) " ✓" else "", fontWeight = FontWeight.SemiBold)
                            Text(
                                "$n tâche(s)" + if (c.limite > 0) " · max ${c.limite}" else "",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = { echanger(i, i - 1) }, enabled = i > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.KeyboardArrowUp, "Monter")
                        }
                        IconButton(
                            onClick = { echanger(i, i + 1) },
                            enabled = i < colonnes.size - 1,
                            modifier = Modifier.size(36.dp)
                        ) { Icon(Icons.Default.KeyboardArrowDown, "Descendre") }
                        IconButton(onClick = { edition = c }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, "Modifier")
                        }
                    }
                }
                TextButton(onClick = { edition = Colonne(couleur = PALETTE[colonnes.size % PALETTE.size]) }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Ajouter une colonne")
                }
                Indication("Les tâches d'une colonne marquée ✓ sont considérées comme terminées (tableau de bord, retards).")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )

    edition?.let { ColonneDialog(it, colonnes) { edition = null } }
}

@Composable
private fun ColonneDialog(initial: Colonne, toutes: List<Colonne>, onDismiss: () -> Unit) {
    var c by remember(initial) { mutableStateOf(initial) }
    var limite by remember(initial) { mutableStateOf(if (initial.limite > 0) initial.limite.toString() else "") }
    val autre = toutes.firstOrNull { it.id != initial.id }
    FormDialog(
        titre = if (initial.id == 0L) "Nouvelle colonne" else "Modifier la colonne",
        onDismiss = onDismiss,
        onSave = {
            Repo.saveColonne(c.copy(nom = c.nom.trim(), limite = limite.toIntOrNull() ?: 0))
            onDismiss()
        },
        saveEnabled = c.nom.isNotBlank(),
        onDelete = if (initial.id != 0L && autre != null) ({
            Repo.deleteColonne(initial.id, autre.id)
            onDismiss()
        }) else null
    ) {
        OutlinedTextField(
            value = c.nom, onValueChange = { c = c.copy(nom = it) },
            label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("Couleur", style = MaterialTheme.typography.labelMedium)
        SelecteurCouleur(c.couleur) { c = c.copy(couleur = it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Colonne « terminé » ✓", modifier = Modifier.weight(1f))
            Switch(checked = c.terminee, onCheckedChange = { c = c.copy(terminee = it) })
        }
        OutlinedTextField(
            value = limite, onValueChange = { limite = it.filter { ch -> ch.isDigit() } },
            label = { Text("Nombre maximum de tâches (vide = illimité)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (initial.id != 0L && autre != null) {
            Indication("Si vous supprimez cette colonne, ses tâches iront dans « ${autre.nom} ».")
        }
    }
}
