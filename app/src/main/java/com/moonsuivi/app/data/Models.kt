package com.moonsuivi.app.data

enum class Colonne(val label: String) {
    A_FAIRE("À faire"),
    EN_COURS("En cours"),
    EN_ATTENTE("En attente"),
    TERMINE("Terminé")
}

enum class StatutSujet(val label: String) {
    OUVERT("Ouvert"),
    EN_COURS("En cours"),
    EN_ATTENTE("En attente"),
    CLOS("Clos")
}

enum class TypeAppel(val label: String) {
    ENTRANT("Entrant"),
    SORTANT("Sortant"),
    MANQUE("Manqué"),
    AUTRE("Autre")
}

val PRIORITES: List<Pair<Int, String>> = listOf(0 to "Basse", 1 to "Normale", 2 to "Haute")

data class Sujet(
    val id: Long = 0,
    val titre: String = "",
    val description: String = "",
    val statut: StatutSujet = StatutSujet.OUVERT,
    val priorite: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Contact(
    val id: Long = 0,
    val nom: String = "",
    val societe: String = "",
    val telephone: String = "",
    val email: String = "",
    val notes: String = ""
)

data class Tache(
    val id: Long = 0,
    val titre: String = "",
    val description: String = "",
    val colonne: Colonne = Colonne.A_FAIRE,
    val priorite: Int = 1,
    val echeance: Long? = null,
    val sujetId: Long? = null,
    val contactId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Appel(
    val id: Long = 0,
    val numero: String = "",
    val contactId: Long? = null,
    val sujetId: Long? = null,
    val type: TypeAppel = TypeAppel.AUTRE,
    val date: Long = System.currentTimeMillis(),
    val dureeSec: Long = 0,
    val fichier: String? = null,
    val note: String = ""
)

data class NoteSujet(
    val id: Long = 0,
    val sujetId: Long,
    val texte: String,
    val date: Long = System.currentTimeMillis()
)

/** E-mails et SMS échangés avec un contact. */
enum class Canal(val label: String, val icone: String) {
    EMAIL_RECU("E-mail reçu", "📥"),
    EMAIL_ENVOYE("E-mail envoyé", "📤"),
    SMS_RECU("SMS reçu", "💬"),
    SMS_ENVOYE("SMS envoyé", "✉️");

    val estEmail: Boolean get() = this == EMAIL_RECU || this == EMAIL_ENVOYE
}

data class Echange(
    val id: Long = 0,
    val canal: Canal = Canal.EMAIL_RECU,
    val contactId: Long? = null,
    val sujetId: Long? = null,
    val adresse: String = "",
    val objet: String = "",
    val contenu: String = "",
    val date: Long = System.currentTimeMillis(),
    val messageId: String? = null
)
