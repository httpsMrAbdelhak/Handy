# Handy Mobile (Android)

Application Android native (Kotlin + Jetpack Compose), développée dans le dossier `android/` de ce dépôt. L'application de bureau Handy n'est pas modifiée.

**État : Phase 2.** Dictée hors ligne avec whisper.cpp (v1.9.5, CPU), modèles Whisper Tiny et Base q5_1 au choix, comparaison des modèles sur un même enregistrement, dictionnaire personnalisé, modes Darija + français / Auto / Français / Arabe classique / Anglais. Pas encore de bouton flottant ni d'accessibilité.

whisper.cpp n'est pas versionné ici : `android/scripts/fetch-whisper.sh` le récupère à une version épinglée (le workflow l'exécute).

**Darija :** Whisper n'a pas de modèle darija dédié. Le mode « Darija + français » utilise la langue arabe avec une phrase d'amorce en dialecte algérien mêlé de français, plus ton dictionnaire. C'est un biais de style, pas une garantie : la qualité réelle est à mesurer avec l'onglet Comparer.

## Contraintes du projet

- `minSdk` 26, `compileSdk` / `targetSdk` 34, Java 17.
- Android Gradle Plugin 8.5.2, Gradle 8.7, Kotlin 1.9.24, Compose compiler 1.5.14.
- Reconnaissance vocale prévue 100 % hors ligne (phases suivantes). Aucun serveur, aucun cloud.

## Télécharger l'APK depuis le téléphone

1. Ouvre le dépôt dans le navigateur du téléphone, onglet **Actions**.
2. Choisis le workflow **Android APK**, puis la dernière exécution avec une coche verte.
3. En bas de la page, section **Artifacts**, touche `handy-mobile-debug-apk`. GitHub télécharge un fichier `.zip` (connexion GitHub requise).
4. Ouvre le `.zip` avec l'application **Fichiers** et extrais `app-debug.apk`.

## Installer l'APK

1. Touche `app-debug.apk`.
2. Si Android le demande, autorise ton navigateur ou ton gestionnaire de fichiers à installer des applications inconnues (Paramètres > Applications et notifications > Accès spécial > Installer des applis inconnues).
3. Touche **Installer**. Si Play Protect avertit que l'application n'est pas connue, choisis **Installer quand même**.

## Mise à jour

Cet APK est signé avec une clé de débogage créée par GitHub à chaque compilation. Une version plus récente ne peut donc pas s'installer par-dessus l'ancienne : **désinstalle l'ancienne version d'abord**. Une signature permanente sera configurée dans une phase ultérieure.

## Compiler

Le workflow `.github/workflows/android-build.yml` compile l'APK à chaque push sur une branche `claude/**` qui touche `android/`, ou à la demande (**Run workflow**).
