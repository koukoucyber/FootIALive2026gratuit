# Intégration du Lecteur Vidéo Réel de Match avec Statistiques en Superposition

Ce plan définit l'architecture et la conception pour remplacer les reconstitutions et simulations synthétiques par un **véritable lecteur vidéo de match (flux réels et temps forts officiels)**, doté d'une **superposition interactive des statistiques (HUD)** et d'un sélecteur de compétitions couvrant l'ensemble des grands tournois majeurs.

---

## Retours Utilisateur & Décisions Validées

> [!IMPORTANT]
> **Choix validés lors de la phase de clarification :**
> - **Type de contenu vidéo :** Véritables vidéos de match avec images en direct et temps forts réels (aucune reconstitution 2D ni simulation synthétique).
> - **Compétitions couvertes :** Tous les grands tournois majeurs (Premier League, LaLiga, Ligue 1, Bundesliga, Serie A, CAN, Euro / Ligue des Nations, Ligue des Champions).
> - **Format d'affichage :** Lecteur vidéo intégré avec tableau de bord et statistiques en superposition (HUD semi-transparent).

---

## 1. Vue d'Ensemble & Expérience Utilisateur

### Objectif
Offrir une expérience immersive de visionnage de match pour la soirée, permettant à l'utilisateur de regarder de véritables séquences vidéo (actions de buts, arrêts, temps forts réels de diffusion) avec le score en temps réel, le minuteur et les statistiques tactiques incrustés directement sur le flux vidéo.

### Cibles & Cas d'usage
- **Soirée Football :** L'utilisateur lance l'application le soir pour suivre les rencontres des grands championnats européens et africains.
- **Navigation Multi-Tournois :** Possibilité de zapper instantanément entre le choc de Ligue 1 (PSG - OM), le Classique de LaLiga (Real - Barça), le duel de Premier League (City - Arsenal), ou les rencontres de CAN et Ligue des Champions.
- **Accès Rapide aux Moments Clés :** Marqueurs interactifs sur la timeline vidéo (38' But, 67' But, 74' Arrêt) permettant de sauter directement aux vraies images des buts.

---

## 2. Expérience Utilisateur & Design Visuel (Jetpack Compose & M3)

### Parcours Utilisateur
1. **Accès au Lecteur Vidéo :**
   - Depuis l'onglet "Direct" de l'écran de détail d'un match ou via une nouvelle bannière "🔴 Visionner le Match de ce soir" en haut de l'écran d'accueil.
2. **Lecteur Vidéo Intégré Haute Définition :**
   - Lecteur vidéo plein format 16:9 intégré avec commandes tactiles (Lecture/Pause, Barre de progression, Rétablissement son, Plein écran).
   - Sélecteur de flux vidéo (Qualité 1080p / 720p, flux alternatif).
3. **Superposition Interactive (HUD Overlay) :**
   - **Scoreboard Flottant :** Logos des équipes, score actuel pulsant et chrono de jeu affichés avec effet de verre dépoli (*frosted glass*).
   - **Bouton Bascule Stats HUD :** Affiche/masque en transparence par-dessus la vidéo les jauges de possession, tirs cadrés et fautes sans masquer l'action.
   - **Timeline des Buts & Temps Forts (Highlight Chips) :** Barre de défilement horizontale en dessous ou en incrustation permettant de cliquer sur un but pour caler instantanément la vidéo sur l'action réelle correspondante.
4. **Zapping & Filtre par Compétition :**
   - Carrousel des tournois : `Tous`, `🏆 Ligue des Champions`, `🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier League`, `🇪🇸 LaLiga`, `🇫🇷 Ligue 1`, `🇩🇪 Bundesliga`, `🇮🇹 Serie A`, `🌍 CAN`, `🇪🇺 Euro/Nations`.

---

## 3. Choix Techniques & Décisions d'Architecture

- **Moteur Vidéo Réel :**
  - Utilisation d'un composant Android natif haute performance via `AndroidView` (`VideoView` / `ExoPlayer` / HTML5 Media Player sécurisé) capable de lire des flux MP4/HLS stables sans latence, avec gestion native du cycle de vie Compose (`DisposableEffect` pour libérer la mémoire lors de la fermeture de l'écran).
- **URLs de Vidéos Réelles de Football :**
  - Association de véritables séquences vidéo hébergées (actions de football réelles de buts, phases de jeu professionnelles, résumés officiels HD) pour chaque match et moment fort.
- **Cohérence des Données :**
  - Synchronisation du minuteur vidéo avec la timeline des événements du match (`MatchEvent`).
  - Prise en charge du thème sombre (Dark Mode) et des deux langues (Français et Anglais).

---

## 4. Architecture Technique & Schéma des Composants

```
┌─────────────────────────────────────────────────────────────┐
│                    MainActivity / AppNavHost                │
└──────────────────────────────┬──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
     ┌───────────────────┐           ┌───────────────────┐
     │    HomeScreen     │           │ MatchDetailScreen │
     │ (Live Banner &    │           │ (Onglet Direct &  │
     │  Zapping Tournois)│           │  Match Vidéo)     │
     └─────────┬─────────┘           └─────────┬─────────┘
               │                               │
               └───────────────┬───────────────┘
                               ▼
        ┌─────────────────────────────────────────────┐
        │          RealMatchVideoPlayer               │
        │  ┌───────────────────────────────────────┐  │
        │  │     AndroidView (Video Surface)       │  │
        │  │   Vraies vidéos / Flux HD MP4 & HLS   │  │
        │  └───────────────────────────────────────┘  │
        │  ┌───────────────────────────────────────┐  │
        │  │      HUD Overlay (Superposition)      │  │
        │  │  - Scoreboard dépoli (Équipes, Score) │  │
        │  │  - Badges Direct & Minuteur           │  │
        │  │  - Jauge possession & tirs en direct  │  │
        │  │  - Commandes & Plein écran            │  │
        │  └───────────────────────────────────────┘  │
        │  ┌───────────────────────────────────────┐  │
        │  │    HighlightsTimeline & Tournois      │  │
        │  │  [38' But] [67' But] [Zapping Matchs] │  │
        │  └───────────────────────────────────────┘  │
        └─────────────────────────────────────────────┘
                               ▲
                               │
        ┌─────────────────────────────────────────────┐
        │            FootballRepository               │
        │  - Matchs des 8 grands tournois             │
        │  - Liens vidéos réelles & timecodes         │
        └─────────────────────────────────────────────┘
```

### Prochaines étapes de réalisation (dès approbation) :
1. Mise à jour du modèle `Match` avec les attributs de flux vidéo réel (`videoStreamUrl`, `highlightsVideoUrl`, `videoTimestamps`).
2. Création du composant `RealMatchVideoPlayer` avec lecteur vidéo réel et HUD en superposition (score, chrono, statistiques en transparence, contrôles de lecture).
3. Intégration du composant dans l'écran de détail de match (`MatchDetailScreen`) et ajout d'un accès direct depuis la page d'accueil (`HomeScreen`).
4. Vérification et compilation avec `compile_applet`.
