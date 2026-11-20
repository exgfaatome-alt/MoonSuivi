# MoonSuivi — application Android 11

Application de suivi d'activité : **sujets**, **contacts**, **tâches en kanban**, **agenda** et **enregistrement automatique des appels**. Toutes les données restent sur le téléphone (base SQLite locale).

## Fonctions

| Onglet | Ce qu'il fait |
|---|---|
| **Tableau** | Kanban 4 colonnes (À faire / En cours / En attente / Terminé). Flèches ← → pour déplacer une carte, filtre par sujet, priorité, échéance (en rouge si en retard), lien sujet + contact. |
| **Agenda** | Calendrier mensuel : points pour les tâches à échéance et les appels du jour. Toucher un jour affiche le détail ; « + » crée une tâche à cette date. |
| **Sujets** | Liste filtrable par statut (Ouvert, En cours, En attente, Clos) + recherche. La fiche d'un sujet regroupe ses tâches, les contacts liés et un **journal de suivi** (notes datées + appels rattachés). |
| **Contacts** | Fiches (nom, société, téléphones, e-mail, notes), import des contacts du téléphone, bouton Appeler / Écrire. La fiche affiche les tâches, appels et sujets liés. |
| **Échanges** | Historique unifié : appels (avec enregistrement audio), e-mails et SMS échangés avec vos contacts. Filtres, synchronisation, bouton ✈ pour écrire un e-mail ou un SMS. |
| **Réglages ⚙** | Autorisations, messagerie (IMAP/SMTP), enregistrement des appels, mises à jour. |

### Communiquer depuis l'appli
- **Téléphoner** : appel direct depuis une fiche contact, un sujet ou un échange.
- **SMS** : envoyé directement depuis l'appli ; les SMS reçus de vos contacts sont enregistrés automatiquement.
- **E-mail** : envoyé directement si la messagerie est configurée (Réglages ⚙), sinon via votre appli e-mail.
- **Suivi des e-mails** : MoonSuivi relève votre boîte toutes les 30 min et importe les e-mails reçus de / envoyés à vos contacts. Une réponse est rattachée automatiquement au même sujet que le message d'origine (même objet).
- Depuis Gmail/Outlook, **Partager › MoonSuivi** enregistre un e-mail dans un sujet.

### Mises à jour automatiques
À chaque modification envoyée sur GitHub, le workflow publie une « Release » (APK + `version.json`). Au démarrage, l'appli vérifie cette adresse et propose d'installer la nouvelle version. Conditions : le secret `MOONSUIVI_KEYSTORE` est configuré et le dépôt est public (ou bien changez l'adresse dans Réglages pour un autre site qui publie un `version.json`).

## Compiler l'APK

### Option A — Android Studio (le plus simple)
1. Dézipper, puis **File › Open** sur le dossier `MoonSuivi`.
2. Laisser Gradle se synchroniser (Android Studio télécharge le SDK 34 si besoin).
3. Brancher le téléphone (débogage USB activé) et cliquer **Run ▶**, ou **Build › Build APK(s)**.
   L'APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

### Option B — GitHub (sans rien installer)
1. Créer un dépôt GitHub et y pousser le dossier.
2. Le workflow `.github/workflows/build.yml` compile automatiquement.
3. Onglet **Actions** › dernier build › télécharger l'artefact **MoonSuivi-apk**.

### Option C — ligne de commande
```bash
./gradlew assembleDebug        # nécessite JDK 17 et ANDROID_HOME
```

## Premier lancement sur le téléphone

1. Installer l'APK (autoriser les « sources inconnues »).
2. **Réglages ⚙** › **Autoriser** : téléphone, journal d'appels, micro, contacts, SMS.
3. Toujours dans **Réglages**, bouton **Activer** › *Accessibilité* › *MoonSuivi – enregistrement des appels* › activer.
4. Activer l'interrupteur **Enregistrement automatique**.
5. Désactiver l'optimisation de batterie pour MoonSuivi (Paramètres › Applications › MoonSuivi › Batterie › Non optimisée), sinon certains fabricants coupent le service.

## ⚠️ À savoir sur l'enregistrement des appels

- Depuis Android 10, Google interdit aux applis non système de capter la ligne téléphonique. La seule méthode possible sans root est celle utilisée ici (service d'accessibilité + micro), comme les applis du Play Store de type « Cube ACR ».
- **Le résultat dépend du fabricant** : sur certains téléphones les deux voix sont enregistrées, sur d'autres seulement la vôtre. Dans ce cas, mettez le **haut-parleur** ou changez la **source audio** dans Réglages.
- Le **journal des appels** (qui, quand, durée, contact) fonctionne lui sur tous les téléphones.
- Les fichiers sont dans `Android/data/com.moonsuivi.app/files/Enregistrements/` (supprimés si l'appli est désinstallée).
- **Légalité** : en France, enregistrer une conversation à l'insu de son interlocuteur est interdit. Prévenez-le au début de l'appel.

## Structure du code

```
app/src/main/java/com/moonsuivi/app/
├── MainActivity.kt            navigation, thème, barre d'onglets
├── data/
│   ├── Models.kt              Sujet, Contact, Tache, Appel, NoteSujet
│   ├── Repo.kt                base SQLite + requêtes
│   ├── Prefs.kt               réglages
│   └── ContactsImport.kt      import des contacts du téléphone
├── mail/                      Courrier.kt (IMAP/SMTP), MailWorker.kt (relève automatique)
├── messages/                  Sms.kt, SmsReceiver.kt
├── update/                    MiseAJour.kt (vérification, téléchargement, installation)
├── calls/
│   ├── CallReceiver.kt        détection début / fin d'appel + journalisation
│   ├── CallAccessibilityService.kt  enregistrement pendant l'appel
│   ├── RecordingService.kt    enregistrement de secours (sans accessibilité)
│   ├── Recorder.kt            MediaRecorder (.m4a)
│   ├── CallLogger.kt          copie du journal Android + rattachement audio
│   └── Lecteur.kt             lecture des enregistrements
└── ui/                        écrans Jetpack Compose (Kanban, Agenda, Sujets, Contacts, Échanges, Réglages)
```

Technos : Kotlin 1.9, Jetpack Compose (Material 3), SQLite, minSdk / targetSdk 30 (Android 11).
