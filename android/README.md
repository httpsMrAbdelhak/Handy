# Handy Mobile (Android)

Application Android native (Kotlin + Jetpack Compose), développée dans le dossier `android/` de ce dépôt. L'application de bureau Handy n'est pas modifiée.

**État : Phase 0.** Le projet est un squelette : un écran affiche la version, la version d'Android et l'appareil. Il n'y a ni dictée, ni whisper.cpp, ni bouton flottant, ni accessibilité pour l'instant.

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
