package com.example.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(val code: String, val displayName: String, val flagEmoji: String) {
    FRENCH("fr", "Français", "🇫🇷"),
    ENGLISH("en", "English", "🇬🇧")
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.FRENCH }

object Strings {
    fun get(key: String, lang: AppLanguage): String {
        val isEn = lang == AppLanguage.ENGLISH
        return when (key) {
            // Navigation
            "nav_home" -> if (isEn) "Home" else "Accueil"
            "nav_matches" -> if (isEn) "Matches" else "Matchs"
            "nav_standings" -> if (isEn) "Standings" else "Classements"
            "nav_favorites" -> if (isEn) "My Team" else "Mon Équipe"
            "nav_news" -> if (isEn) "News & Stars" else "Actus & Stars"
            "nav_predictions" -> if (isEn) "Predictions" else "Pronostics"

            // General UI
            "app_subtitle" -> if (isEn) "Live Football & AI Analysis" else "Football en Direct & IA"
            "search_placeholder" -> if (isEn) "Search team or league..." else "Rechercher équipe ou ligue..."
            "live_now" -> if (isEn) "LIVE NOW" else "EN DIRECT"
            "upcoming_matches" -> if (isEn) "Upcoming Matches" else "Matchs à Venir"
            "finished" -> if (isEn) "Finished" else "Terminé"
            "dark_mode" -> if (isEn) "Dark Mode" else "Mode Sombre"
            "light_mode" -> if (isEn) "Light Mode" else "Mode Clair"
            "language" -> if (isEn) "Language" else "Langue"

            // Chat & Reactions
            "public_chat" -> if (isEn) "Public Fan Chat" else "Chat Public Gratuit"
            "chat_placeholder" -> if (isEn) "Type your message or reaction..." else "Message public ou réaction..."
            "send" -> if (isEn) "Send" else "Envoyer"
            "reactions" -> if (isEn) "Reactions" else "Réactions"
            "quick_reactions" -> if (isEn) "Quick Emojis" else "Émojis Rapides"
            "pseudo" -> if (isEn) "Username" else "Pseudo"
            "my_team" -> if (isEn) "My Team" else "Mon Équipe"
            "supporters_online" -> if (isEn) "supporters online" else "supporters en ligne"

            // Predictions
            "predictions_title" -> if (isEn) "Predictions & Leaderboard 🏆" else "Pronostics & Classement 🏆"
            "predict_match" -> if (isEn) "Predict Result" else "Pronostiquer le Match"
            "home_win" -> if (isEn) "Home Win" else "Victoire Domicile"
            "draw" -> if (isEn) "Draw" else "Match Nul"
            "away_win" -> if (isEn) "Away Win" else "Victoire Extérieur"
            "leaderboard" -> if (isEn) "Top Predictors Leaderboard" else "Classement des Pronostiqueurs"
            "your_rank" -> if (isEn) "Your Rank" else "Votre Rang"
            "total_points" -> if (isEn) "Total Points" else "Points Totaux"
            "accuracy" -> if (isEn) "Accuracy" else "Précision"
            "streak" -> if (isEn) "Win Streak" else "Série"
            "my_predictions" -> if (isEn) "My Predictions" else "Mes Pronostics"
            "rules_bonus" -> if (isEn) "+50 pts for correct outcome, +100 pts for exact score!" else "+50 pts si bon résultat, +100 pts si score exact !"

            else -> key
        }
    }
}
