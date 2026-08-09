package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MatchStatus {
    LIVE, UPCOMING, FINISHED
}

enum class EventType {
    GOAL, YELLOW_CARD, RED_CARD, SUB, VAR, PENALTY, HALF_TIME, FULL_TIME
}

data class MatchEvent(
    val id: String,
    val minute: Int,
    val type: EventType,
    val isHomeTeam: Boolean,
    val playerName: String,
    val detail: String? = null
)

data class MatchStats(
    val possessionHome: Int,
    val possessionAway: Int,
    val shotsHome: Int,
    val shotsAway: Int,
    val shotsOnTargetHome: Int,
    val shotsOnTargetAway: Int,
    val passesHome: Int,
    val passesAway: Int,
    val passAccuracyHome: Int,
    val passAccuracyAway: Int,
    val foulsHome: Int,
    val foulsAway: Int,
    val yellowCardsHome: Int,
    val yellowCardsAway: Int,
    val redCardsHome: Int,
    val redCardsAway: Int,
    val offsidesHome: Int,
    val offsidesAway: Int
)

data class PlayerLineup(
    val number: Int,
    val name: String,
    val position: String,
    val isCaptain: Boolean = false,
    val rating: Float = 7.5f
)

data class MatchLineup(
    val homeFormation: String,
    val awayFormation: String,
    val homeStarting11: List<PlayerLineup>,
    val awayStarting11: List<PlayerLineup>,
    val homeSubstitutes: List<PlayerLineup>,
    val awaySubstitutes: List<PlayerLineup>
)

data class AICommentary(
    val id: String,
    val minute: Int,
    val text: String,
    val tone: String = "INTENSE", // INTENSE, ANALYTICAL, CELEBRATION
    val timestamp: Long = System.currentTimeMillis()
)

data class Match(
    val id: String,
    val league: String, // Ligue 1, Premier League, LaLiga, Bundesliga, Serie A, Ligue des Champions, CAN
    val homeTeam: String,
    val homeTeamLogo: String,
    val awayTeam: String,
    val awayTeamLogo: String,
    val homeScore: Int,
    val awayScore: Int,
    val minute: Int,
    val status: MatchStatus,
    val date: String,
    val time: String,
    val stadium: String,
    val referee: String,
    val stats: MatchStats,
    val events: List<MatchEvent>,
    val lineup: MatchLineup,
    val aiCommentaries: List<AICommentary>,
    val aiSummary: String? = null
)

data class LeagueStanding(
    val rank: Int,
    val teamName: String,
    val teamLogo: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int,
    val form: List<String> // W, D, L
)

data class PlayerProfile(
    val id: String,
    val name: String,
    val team: String,
    val teamLogo: String,
    val position: String,
    val nationality: String,
    val age: Int,
    val photoUrl: String,
    val goals: Int,
    val assists: Int,
    val matches: Int,
    val rating: Float,
    val marketValue: String,
    val bio: String,
    val speed: Int,
    val shooting: Int,
    val passing: Int,
    val dribbling: Int,
    val defense: Int,
    val physical: Int
)

data class NewsArticle(
    val id: String,
    val title: String,
    val category: String, // Transferts, CAN 2026, Ligue 1, IA Analyse
    val summary: String,
    val content: String,
    val author: String,
    val date: String,
    val readTime: String,
    val imageUrl: String
)

data class AppNotification(
    val id: String,
    val matchId: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false,
    val type: String // BUT, CARTON, FIN_MATCH, DEBRIEF_IA
)

@Entity(tableName = "favorite_teams")
data class FavoriteTeamEntity(
    @PrimaryKey val teamName: String,
    val league: String,
    val logoUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)
