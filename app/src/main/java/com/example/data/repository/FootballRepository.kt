package com.example.data.repository

import android.content.Context
import com.example.data.ai.AiMatchSearchState
import com.example.data.ai.AiPostMatchVideoSummaryState
import com.example.data.ai.GeminiCommentatorRepository
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.network.FootballNewsApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class FootballRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val favoriteDao = db.favoriteDao()
    private val aiRepository = GeminiCommentatorRepository()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Live Matches State
    private val _matchesState = MutableStateFlow<List<Match>>(emptyList())
    val matchesState: StateFlow<List<Match>> = _matchesState.asStateFlow()

    private val _isRefreshingMatches = MutableStateFlow<Boolean>(false)
    val isRefreshingMatches: StateFlow<Boolean> = _isRefreshingMatches.asStateFlow()

    private val _matchesSyncSummary = MutableStateFlow<String>("Scores et prochains matchs actualisés en direct !")
    val matchesSyncSummary: StateFlow<String> = _matchesSyncSummary.asStateFlow()

    // Mercato Intelligence (Le Parisien, Foot Mercato, Le Monde)
    private val _mercatoNewsState = MutableStateFlow<List<MercatoNewsItem>>(emptyList())
    val mercatoNewsState: StateFlow<List<MercatoNewsItem>> = _mercatoNewsState.asStateFlow()

    private val _isMercatoLoading = MutableStateFlow<Boolean>(false)
    val isMercatoLoading: StateFlow<Boolean> = _isMercatoLoading.asStateFlow()

    private val _mercatoLastSync = MutableStateFlow<String>("Synchronisation en direct...")
    val mercatoLastSync: StateFlow<String> = _mercatoLastSync.asStateFlow()

    private val _geminiMercatoAnalysis = MutableStateFlow<String?>(null)
    val geminiMercatoAnalysis: StateFlow<String?> = _geminiMercatoAnalysis.asStateFlow()

    // Firebase AI Match Search & Filter State
    private val _aiMatchSearchState = MutableStateFlow<AiMatchSearchState>(AiMatchSearchState.Idle)
    val aiMatchSearchState: StateFlow<AiMatchSearchState> = _aiMatchSearchState.asStateFlow()
    private var aiSearchJob: Job? = null

    // Firebase AI Post-Match Video Highlights State
    private val _postMatchVideoSummaryState = MutableStateFlow<AiPostMatchVideoSummaryState>(AiPostMatchVideoSummaryState.Idle)
    val postMatchVideoSummaryState: StateFlow<AiPostMatchVideoSummaryState> = _postMatchVideoSummaryState.asStateFlow()
    private val postMatchCache = mutableMapOf<String, AiPostMatchVideoSummaryState.Success>()


    // Standings State
    private val _standingsState = MutableStateFlow<Map<String, List<LeagueStanding>>>(emptyMap())
    val standingsState: StateFlow<Map<String, List<LeagueStanding>>> = _standingsState.asStateFlow()

    // Players State
    private val _playersState = MutableStateFlow<List<PlayerProfile>>(emptyList())
    val playersState: StateFlow<List<PlayerProfile>> = _playersState.asStateFlow()

    // News State
    private val _newsState = MutableStateFlow<List<NewsArticle>>(emptyList())
    val newsState: StateFlow<List<NewsArticle>> = _newsState.asStateFlow()

    private val _isNewsLoading = MutableStateFlow<Boolean>(false)
    val isNewsLoading: StateFlow<Boolean> = _isNewsLoading.asStateFlow()

    private val _newsSource = MutableStateFlow<String>("En chargement...")
    val newsSource: StateFlow<String> = _newsSource.asStateFlow()

    // Local Preferences
    private val prefs = context.getSharedPreferences("footia_live_prefs", Context.MODE_PRIVATE)

    private val _isDarkTheme = MutableStateFlow<Boolean>(
        prefs.getBoolean("is_dark_theme", true) // Default to true (Mode Sombre Nocturne)
    )
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleDarkTheme() {
        val newTheme = !_isDarkTheme.value
        _isDarkTheme.value = newTheme
        prefs.edit().putBoolean("is_dark_theme", newTheme).apply()
    }

    // App Language Management (French / English)
    private val _appLanguage = MutableStateFlow<com.example.util.AppLanguage>(
        if (prefs.getString("app_language", "fr") == "en") com.example.util.AppLanguage.ENGLISH else com.example.util.AppLanguage.FRENCH
    )
    val appLanguage: StateFlow<com.example.util.AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(lang: com.example.util.AppLanguage) {
        _appLanguage.value = lang
        prefs.edit().putString("app_language", lang.code).apply()
    }

    // Predictions Management
    private val _predictionsState = MutableStateFlow<Map<String, UserMatchPrediction>>(emptyMap())
    val predictionsState: StateFlow<Map<String, UserMatchPrediction>> = _predictionsState.asStateFlow()

    fun savePrediction(matchId: String, choice: PredictionChoice, homeScore: Int = 1, awayScore: Int = 0) {
        val nowTs = System.currentTimeMillis()
        val currentMap = _predictionsState.value.toMutableMap()
        currentMap[matchId] = UserMatchPrediction(
            matchId = matchId,
            choice = choice,
            predictedHomeScore = homeScore,
            predictedAwayScore = awayScore,
            timestamp = nowTs
        )
        _predictionsState.value = currentMap

        prefs.edit()
            .putString("pred_choice_$matchId", choice.name)
            .putInt("pred_hs_$matchId", homeScore)
            .putInt("pred_as_$matchId", awayScore)
            .putLong("pred_ts_$matchId", nowTs)
            .apply()
    }

    private fun loadSavedPredictionsFromPrefs(): Map<String, UserMatchPrediction> {
        val restored = mutableMapOf<String, UserMatchPrediction>()
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (!key.startsWith("pred_choice_") || value !is String) continue
            val matchId = key.removePrefix("pred_choice_")
            if (matchId.isBlank()) continue
            val choice = runCatching { PredictionChoice.valueOf(value) }.getOrNull() ?: continue
            val hs = prefs.getInt("pred_hs_$matchId", if (choice == PredictionChoice.AWAY_WIN) 0 else 1)
            val asScore = prefs.getInt("pred_as_$matchId", if (choice == PredictionChoice.HOME_WIN) 0 else 1)
            val ts = prefs.getLong("pred_ts_$matchId", System.currentTimeMillis())
            restored[matchId] = UserMatchPrediction(
                matchId = matchId,
                choice = choice,
                predictedHomeScore = hs,
                predictedAwayScore = asScore,
                timestamp = ts
            )
        }
        return restored
    }


    // Local Notifications
    private val _notificationsState = MutableStateFlow<List<AppNotification>>(emptyList())
    val notificationsState: StateFlow<List<AppNotification>> = _notificationsState.asStateFlow()

    // API Key config for external football API integration
    private val _footballApiKey = MutableStateFlow<String>("")
    val footballApiKey: StateFlow<String> = _footballApiKey.asStateFlow()

    // Favorites
    val favoriteTeams: Flow<List<FavoriteTeamEntity>> = favoriteDao.getAllFavorites()

    init {
        loadInitialData()
        startLiveSimulation()
    }

    private fun loadInitialData() {
        // Restore persisted user predictions immediately
        _predictionsState.value = loadSavedPredictionsFromPrefs()

        // Never seed fake/invented matches: start empty in loading state and fetch real ESPN fixtures immediately
        _matchesState.value = emptyList()
        _isRefreshingMatches.value = true
        _matchesSyncSummary.value = "Synchronisation des vrais matchs officiels en direct..."
        _standingsState.value = generateSampleStandings()
        _playersState.value = generateSamplePlayers()
        _newsState.value = generateSampleNews()
        _newsSource.value = "Flux Directs & RSS"

        // Fetch real-time live & upcoming matches, headlines, and mercato from live feeds
        refreshRealMatchesFromApi()
        refreshNewsFromPublicApi()
        refreshMercatoNews()

        // Initial Notification
        val initialNotif = AppNotification(
            id = "notif_welcome",
            matchId = "espn_welcome",
            title = "⚽ Bienvenue sur FootIA Live !",
            message = "Chargement des vrais matchs en direct et recherche intelligente par IA Gemini (heure française).",
            timestamp = getCurrentTimeString(),
            type = "DEBRIEF_IA"
        )
        _notificationsState.value = listOf(initialNotif)
    }

    fun refreshRealMatchesFromApi() {
        repositoryScope.launch {
            _isRefreshingMatches.value = true
            try {
                val realMatches = FootballNewsApiService.fetchRealMatchesFromEspn()

                if (realMatches.isNotEmpty()) {
                    // Strictly real ESPN matches only — never inject or fall back to fake matches
                    val deduped = promoteKickoffLiveMatches(
                        realMatches.distinctBy { it.id },
                        FootballNewsApiService.currentParisNowMillis()
                    )
                    _matchesState.value = deduped

                    val upcomingCount = deduped.count { it.status == MatchStatus.UPCOMING }
                    val liveCount = deduped.count { it.status == MatchStatus.LIVE }
                    val leaguesCount = deduped.map { it.league }.distinct().size
                    _matchesSyncSummary.value = "$liveCount en direct • $upcomingCount prochains matchs ($leaguesCount ligues) • ${getCurrentTimeString()}"

                    val notif = AppNotification(
                        id = "notif_matches_updated_${System.currentTimeMillis()}",
                        matchId = deduped.firstOrNull()?.id ?: "",
                        title = "⚽ Matchs officiels actualisés !",
                        message = "$liveCount match(s) en direct et $upcomingCount prochains matchs synchronisés sur $leaguesCount compétitions.",
                        timestamp = getCurrentTimeString(),
                        type = "ACTU"
                    )
                    _notificationsState.value = listOf(notif) + _notificationsState.value
                } else {
                    _matchesSyncSummary.value = "Aucun match reçu — vérifiez la connexion et appuyez sur Actualiser (${getCurrentTimeString()})"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _matchesSyncSummary.value = "Erreur réseau lors du chargement des matchs réels"
            } finally {
                delay(250)
                _isRefreshingMatches.value = false
            }
        }
    }

    fun searchMatchesWithAI(query: String) {
        val trimmed = query.trim()
        aiSearchJob?.cancel()
        if (trimmed.isEmpty()) {
            _aiMatchSearchState.value = AiMatchSearchState.Idle
            return
        }
        aiSearchJob = repositoryScope.launch {
            _aiMatchSearchState.value = AiMatchSearchState.Loading(trimmed)
            val result = aiRepository.searchAndFilterMatchesWithAI(trimmed, _matchesState.value)
            _aiMatchSearchState.value = result
        }
    }

    fun clearAiMatchSearch() {
        aiSearchJob?.cancel()
        _aiMatchSearchState.value = AiMatchSearchState.Idle
    }

    suspend fun analyzeFinishedMatchHighlightsWithAI(match: Match, forceRefresh: Boolean = false): AiPostMatchVideoSummaryState {
        if (!forceRefresh) {
            postMatchCache[match.id]?.let { cached ->
                _postMatchVideoSummaryState.value = cached
                return cached
            }
        }
        _postMatchVideoSummaryState.value = AiPostMatchVideoSummaryState.Loading(match.id)
        val result = aiRepository.filterFinishedMatchHighlightsWithAI(match)
        if (result is AiPostMatchVideoSummaryState.Success) {
            postMatchCache[match.id] = result
        }
        _postMatchVideoSummaryState.value = result
        return result
    }

    fun refreshMercatoNews() {
        repositoryScope.launch {
            _isMercatoLoading.value = true
            try {
                val items = FootballNewsApiService.fetchMercatoNews()
                if (items.isNotEmpty()) {
                    _mercatoNewsState.value = items
                    val hasLive = items.any { !it.id.startsWith("mercato_offline") }
                    _mercatoLastSync.value = if (hasLive) {
                        "En direct • Mis à jour à ${getCurrentTimeString()}"
                    } else {
                        "Hors-ligne • Appuyez sur Actualiser"
                    }
                    // Automatically keep Gemini synthesis aligned with fresh headlines
                    val freshAnalysis = aiRepository.analyzeMercatoWithGemini(items, null)
                    _geminiMercatoAnalysis.value = freshAnalysis
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (_mercatoNewsState.value.isEmpty()) {
                    _mercatoNewsState.value = FootballNewsApiService.generateCuratedMercatoNews()
                }
                _mercatoLastSync.value = "Hors-ligne • Erreur de connexion"
            } finally {
                _isMercatoLoading.value = false
            }
        }
    }

    fun generateGeminiMercatoAnalysis(userQuery: String? = null) {
        repositoryScope.launch {
            _isMercatoLoading.value = true
            try {
                var currentItems = _mercatoNewsState.value
                if (currentItems.isEmpty() || currentItems.all { it.id.startsWith("mercato_offline") }) {
                    currentItems = FootballNewsApiService.fetchMercatoNews()
                    _mercatoNewsState.value = currentItems
                    _mercatoLastSync.value = "En direct • Mis à jour à ${getCurrentTimeString()}"
                }
                val analysis = aiRepository.analyzeMercatoWithGemini(currentItems, userQuery)
                _geminiMercatoAnalysis.value = analysis
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isMercatoLoading.value = false
            }
        }
    }


    fun refreshNewsFromPublicApi() {
        repositoryScope.launch {
            _isNewsLoading.value = true
            try {
                // Try fetching live headlines from ESPN Soccer API first
                val espnArticles = FootballNewsApiService.fetchEspnSoccerNews()
                val rmcArticles = FootballNewsApiService.fetchFrenchFootballNews()

                val combinedArticles = mutableListOf<NewsArticle>()
                combinedArticles.addAll(espnArticles)
                combinedArticles.addAll(rmcArticles)

                if (combinedArticles.isNotEmpty()) {
                    // Combine fetched articles with local curated articles
                    val allNews = combinedArticles + generateSampleNews()
                    _newsState.value = allNews.distinctBy { it.title }
                    _newsSource.value = "API Publique Direct (ESPN + RMC Sport)"
                } else {
                    _newsState.value = generateSampleNews()
                    _newsSource.value = "Flux d'Actualités Intégré"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (_newsState.value.isEmpty()) {
                    _newsState.value = generateSampleNews()
                }
                _newsSource.value = "Flux d'Actualités Intégré (Hors Ligne)"
            } finally {
                _isNewsLoading.value = false
            }
        }
    }

    private fun promoteKickoffLiveMatches(matches: List<Match>, nowMillis: Long): List<Match> {
        if (matches.isEmpty()) return matches
        var changed = false
        val updated = matches.map { m ->
            val ko = m.kickoffMillis
            if (ko > 0L && m.status == MatchStatus.UPCOMING && nowMillis in ko..(ko + 130L * 60_000L)) {
                changed = true
                val estMin = FootballNewsApiService.computeEstimatedLiveMinute(ko, nowMillis)
                m.copy(
                    status = MatchStatus.LIVE,
                    minute = estMin,
                    aiCommentaries = listOf(
                        AICommentary(
                            id = "c_live_${m.id}",
                            minute = estMin,
                            text = "⚡ COUP D'ENVOI (${estMin}') : ${m.homeTeam} ${m.homeScore} - ${m.awayScore} ${m.awayTeam} (${m.league}) est maintenant EN DIRECT !",
                            tone = "INTENSE"
                        )
                    )
                )
            } else if (ko > 0L && m.status == MatchStatus.LIVE) {
                val estMin = FootballNewsApiService.computeEstimatedLiveMinute(ko, nowMillis)
                if (estMin > m.minute && m.minute < 90) {
                    changed = true
                    m.copy(minute = estMin)
                } else m
            } else {
                m
            }
        }
        return if (changed) {
            updated.sortedWith(
                compareBy<Match>(
                    {
                        when (it.status) {
                            MatchStatus.LIVE -> 0
                            MatchStatus.UPCOMING -> 1
                            MatchStatus.FINISHED -> 2
                        }
                    },
                    { if (it.status == MatchStatus.FINISHED) -it.kickoffMillis else it.kickoffMillis }
                )
            )
        } else matches
    }

    private fun startLiveSimulation() {
        repositoryScope.launch {
            while (isActive) {
                val nowBeforeDelay = FootballNewsApiService.currentParisNowMillis()
                val promotedLocal = promoteKickoffLiveMatches(_matchesState.value, nowBeforeDelay)
                if (promotedLocal !== _matchesState.value) {
                    _matchesState.value = promotedLocal
                }

                val hasLiveOrImminent = _matchesState.value.any { m ->
                    m.status == MatchStatus.LIVE ||
                        (m.kickoffMillis > 0L && (m.kickoffMillis - nowBeforeDelay) in (-130L * 60_000L)..(15L * 60_000L))
                }
                delay(if (hasLiveOrImminent) 10_000L else 25_000L)

                val nowCheck = FootballNewsApiService.currentParisNowMillis()
                val localTicked = promoteKickoffLiveMatches(_matchesState.value, nowCheck)
                if (localTicked !== _matchesState.value) {
                    _matchesState.value = localTicked
                }

                if (!_isRefreshingMatches.value) {
                    try {
                        val fresh = FootballNewsApiService.fetchRealMatchesFromEspn()
                        if (fresh.isNotEmpty()) {
                            val deduped = promoteKickoffLiveMatches(
                                fresh.distinctBy { it.id },
                                FootballNewsApiService.currentParisNowMillis()
                            )
                            _matchesState.value = deduped
                            val liveCount = deduped.count { it.status == MatchStatus.LIVE }
                            val upcomingCount = deduped.count { it.status == MatchStatus.UPCOMING }
                            val leaguesCount = deduped.map { it.league }.distinct().size
                            _matchesSyncSummary.value = "$liveCount en direct • $upcomingCount prochains matchs ($leaguesCount ligues) • ${getCurrentTimeString()}"
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    suspend fun addManualEventToMatch(matchId: String, eventType: EventType, playerName: String, isHomeTeam: Boolean) {
        val matches = _matchesState.value.toMutableList()
        val index = matches.indexOfFirst { it.id == matchId }
        if (index != -1) {
            val m = matches[index]
            var hScore = m.homeScore
            var aScore = m.awayScore
            if (eventType == EventType.GOAL) {
                if (isHomeTeam) hScore++ else aScore++
            }

            val newEvent = MatchEvent(
                id = "manual_${UUID.randomUUID().toString().take(8)}",
                minute = m.minute,
                type = eventType,
                isHomeTeam = isHomeTeam,
                playerName = playerName,
                detail = "Action manuelle déclenchée"
            )

            val updatedEvents = listOf(newEvent) + m.events
            val commentaryText = when(eventType) {
                EventType.GOAL -> "💥 QUEL BUT INCROYABLE ! $playerName fait trembler les filets à la ${m.minute}e minute !"
                EventType.RED_CARD -> "🔴 CARTON ROUGE DIRECT ! $playerName est exclu sous les sifflets du public !"
                EventType.YELLOW_CARD -> "🟨 Carton jaune distribué à $playerName après un engagement trop virulent."
                else -> "⚡ Changement tactique ! Entrée de $playerName sous les applaudissements."
            }

            val newCommentary = AICommentary(
                id = "comm_${UUID.randomUUID().toString().take(8)}",
                minute = m.minute,
                text = commentaryText,
                tone = "INTENSE"
            )

            val updatedCommentaries = (listOf(newCommentary) + m.aiCommentaries).distinctBy { it.id }

            matches[index] = m.copy(
                homeScore = hScore,
                awayScore = aScore,
                events = updatedEvents,
                aiCommentaries = updatedCommentaries
            )
            _matchesState.value = matches

            // Add notification
            val notif = AppNotification(
                id = "notif_${System.currentTimeMillis()}",
                matchId = m.id,
                title = "⚡ Evénement Direct: ${if (isHomeTeam) m.homeTeam else m.awayTeam}",
                message = "$playerName - ${eventType.name} à la ${m.minute}' min",
                timestamp = getCurrentTimeString(),
                type = eventType.name
            )
            _notificationsState.value = listOf(notif) + _notificationsState.value
        }
    }

    suspend fun generateLiveCommentary(
        match: Match,
        minute: Int,
        actionPrompt: String? = null,
        languageCode: String = "fr"
    ): AICommentary {
        return aiRepository.generateLiveCommentary(match, minute, actionPrompt, languageCode)
    }

    suspend fun askMatchAI(matchId: String, question: String, languageCode: String = "fr"): String {
        val match = _matchesState.value.find { it.id == matchId } ?: return "Match non trouvé."
        return aiRepository.askCommentator(match, question, languageCode)
    }

    suspend fun generateFreshDebrief(matchId: String, languageCode: String = "fr"): String {
        val matches = _matchesState.value.toMutableList()
        val index = matches.indexOfFirst { it.id == matchId }
        if (index != -1) {
            val m = matches[index]
            val summary = aiRepository.generateMatchSummary(m, languageCode)
            matches[index] = m.copy(aiSummary = summary)
            _matchesState.value = matches
            return summary
        }
        return "Erreur lors de la génération."
    }

    suspend fun toggleFavoriteTeam(teamName: String, league: String, logoUrl: String) {
        val currentFavs = favoriteDao.getAllFavorites().first()
        val exists = currentFavs.any { it.teamName == teamName }
        if (exists) {
            favoriteDao.deleteFavorite(teamName)
        } else {
            favoriteDao.insertFavorite(FavoriteTeamEntity(teamName, league, logoUrl))
        }
    }

    fun isTeamFavorite(teamName: String): Flow<Boolean> {
        return favoriteDao.isFavorite(teamName)
    }

    fun setFootballApiKey(key: String) {
        _footballApiKey.value = key
    }

    fun markNotificationRead(id: String) {
        _notificationsState.value = _notificationsState.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun clearNotifications() {
        _notificationsState.value = emptyList()
    }

    private fun getCurrentTimeString(): String {
        return FootballNewsApiService.getCurrentParisTimeFormatted()
    }

    private fun generateSampleStandings(): Map<String, List<LeagueStanding>> {
        val ligueEuropa = listOf(
            LeagueStanding(1, "Lazio Rome", "https://media.api-sports.io/football/teams/487.png", 6, 5, 1, 0, 14, 4, +10, 16, listOf("W","W","W","D","W")),
            LeagueStanding(2, "Tottenham", "https://media.api-sports.io/football/teams/47.png", 6, 4, 1, 1, 13, 6, +7, 13, listOf("W","W","L","D","W")),
            LeagueStanding(3, "Olympique Lyonnais", "https://media.api-sports.io/football/teams/80.png", 6, 4, 1, 1, 12, 6, +6, 13, listOf("W","L","W","W","D")),
            LeagueStanding(4, "Manchester United", "https://media.api-sports.io/football/teams/33.png", 6, 3, 3, 0, 11, 7, +4, 12, listOf("D","D","W","W","D")),
            LeagueStanding(5, "Athletic Bilbao", "https://media.api-sports.io/football/teams/531.png", 6, 3, 2, 1, 9, 5, +4, 11, listOf("W","D","W","L","W")),
            LeagueStanding(6, "AS Roma", "https://media.api-sports.io/football/teams/497.png", 6, 3, 1, 2, 8, 6, +2, 10, listOf("L","W","W","D","L"))
        )

        val euro = listOf(
            LeagueStanding(1, "Espagne", "https://flagcdn.com/w160/es.png", 3, 3, 0, 0, 8, 1, +7, 9, listOf("W","W","W")),
            LeagueStanding(2, "France", "https://flagcdn.com/w160/fr.png", 3, 2, 1, 0, 5, 2, +3, 7, listOf("W","D","W")),
            LeagueStanding(3, "Allemagne", "https://flagcdn.com/w160/de.png", 3, 2, 1, 0, 8, 2, +6, 7, listOf("W","W","D")),
            LeagueStanding(4, "Angleterre", "https://flagcdn.com/w160/gb-eng.png", 3, 1, 2, 0, 2, 1, +1, 5, listOf("W","D","D")),
            LeagueStanding(5, "Portugal", "https://flagcdn.com/w160/pt.png", 3, 2, 0, 1, 5, 3, +2, 6, listOf("W","W","L")),
            LeagueStanding(6, "Italie", "https://flagcdn.com/w160/it.png", 3, 1, 1, 1, 3, 3, 0, 4, listOf("W","L","D"))
        )
        val ligue1 = listOf(
            LeagueStanding(1, "Paris SG", "https://media.api-sports.io/football/teams/85.png", 22, 17, 4, 1, 54, 18, +36, 55, listOf("W","W","D","W","W")),
            LeagueStanding(2, "AS Monaco", "https://media.api-sports.io/football/teams/91.png", 22, 14, 4, 4, 42, 22, +20, 46, listOf("W","L","W","W","D")),
            LeagueStanding(3, "Marseille", "https://media.api-sports.io/football/teams/81.png", 22, 13, 5, 4, 46, 25, +21, 44, listOf("L","W","W","D","L")),
            LeagueStanding(4, "Lille OSC", "https://media.api-sports.io/football/teams/79.png", 22, 12, 6, 4, 38, 21, +17, 42, listOf("W","D","W","L","W")),
            LeagueStanding(5, "RC Lens", "https://media.api-sports.io/football/teams/116.png", 22, 11, 6, 5, 33, 22, +11, 39, listOf("D","W","L","W","W")),
            LeagueStanding(6, "Olympique Lyonnais", "https://media.api-sports.io/football/teams/80.png", 22, 10, 5, 7, 35, 30, +5, 35, listOf("W","W","L","D","W"))
        )

        val premierLeague = listOf(
            LeagueStanding(1, "Liverpool FC", "https://media.api-sports.io/football/teams/40.png", 24, 17, 5, 2, 58, 21, +37, 56, listOf("W","W","W","D","W")),
            LeagueStanding(2, "Arsenal FC", "https://media.api-sports.io/football/teams/42.png", 24, 15, 6, 3, 49, 22, +27, 51, listOf("D","W","W","W","D")),
            LeagueStanding(3, "Manchester City", "https://media.api-sports.io/football/teams/50.png", 24, 14, 5, 5, 51, 28, +23, 47, listOf("W","L","D","W","W")),
            LeagueStanding(4, "Chelsea FC", "https://media.api-sports.io/football/teams/49.png", 24, 13, 5, 6, 45, 30, +15, 44, listOf("W","W","L","D","W"))
        )

        val laLiga = listOf(
            LeagueStanding(1, "Real Madrid", "https://media.api-sports.io/football/teams/541.png", 23, 17, 4, 2, 52, 19, +33, 55, listOf("W","W","W","W","W")),
            LeagueStanding(2, "FC Barcelone", "https://media.api-sports.io/football/teams/529.png", 23, 16, 3, 4, 60, 24, +36, 51, listOf("L","W","W","D","W")),
            LeagueStanding(3, "Atlético Madrid", "https://media.api-sports.io/football/teams/530.png", 23, 14, 6, 3, 40, 18, +22, 48, listOf("W","D","W","W","L"))
        )

        val bundesliga = listOf(
            LeagueStanding(1, "Bayern Munich", "https://media.api-sports.io/football/teams/157.png", 21, 16, 3, 2, 56, 19, +37, 51, listOf("W","W","D","W","W")),
            LeagueStanding(2, "Bayer Leverkusen", "https://media.api-sports.io/football/teams/168.png", 21, 14, 5, 2, 48, 24, +24, 47, listOf("W","D","W","W","D"))
        )

        val serieA = listOf(
            LeagueStanding(1, "Inter Milan", "https://media.api-sports.io/football/teams/505.png", 22, 16, 4, 2, 50, 16, +34, 52, listOf("W","W","W","D","W")),
            LeagueStanding(2, "Napoli", "https://media.api-sports.io/football/teams/492.png", 22, 15, 4, 3, 39, 17, +22, 49, listOf("W","D","W","W","W"))
        )

        val can = listOf(
            LeagueStanding(1, "Sénégal", "https://flagcdn.com/w160/sn.png", 3, 3, 0, 0, 8, 1, +7, 9, listOf("W","W","W")),
            LeagueStanding(2, "Côte d'Ivoire", "https://flagcdn.com/w160/ci.png", 3, 2, 0, 1, 6, 3, +3, 6, listOf("W","L","W")),
            LeagueStanding(3, "Algérie", "https://flagcdn.com/w160/dz.png", 3, 2, 1, 0, 5, 2, +3, 7, listOf("W","D","W")),
            LeagueStanding(4, "Maroc", "https://flagcdn.com/w160/ma.png", 3, 2, 1, 0, 7, 1, +6, 7, listOf("W","W","D"))
        )

        val rsl = listOf(
            LeagueStanding(1, "Al Hilal SFC", "https://media.api-sports.io/football/teams/345.png", 22, 19, 3, 0, 68, 18, +50, 60, listOf("W","W","W","W","D")),
            LeagueStanding(2, "Al Nassr FC (C. Ronaldo)", "https://media.api-sports.io/football/teams/347.png", 22, 17, 2, 3, 62, 23, +39, 53, listOf("W","W","W","L","W")),
            LeagueStanding(3, "Al Ahli Saudi (Mahrez)", "https://media.api-sports.io/football/teams/348.png", 22, 14, 4, 4, 48, 22, +26, 46, listOf("W","D","W","W","D")),
            LeagueStanding(4, "Al Ittihad (Benzema)", "https://media.api-sports.io/football/teams/346.png", 22, 13, 3, 6, 45, 27, +18, 42, listOf("L","W","W","W","L")),
            LeagueStanding(5, "Al Shabab FC", "https://media.api-sports.io/football/teams/349.png", 22, 10, 5, 7, 36, 29, +7, 35, listOf("D","W","L","W","W")),
            LeagueStanding(6, "Al Qadsiah FC", "https://media.api-sports.io/football/teams/350.png", 22, 10, 4, 8, 31, 28, +3, 34, listOf("W","L","D","W","L"))
        )

        val qualifCan = listOf(
            LeagueStanding(1, "Côte d'Ivoire (Gr. C)", "https://flagcdn.com/w160/ci.png", 2, 2, 0, 0, 5, 1, +4, 6, listOf("W","W")),
            LeagueStanding(2, "Gambie (Gr. C)", "https://flagcdn.com/w160/gm.png", 2, 2, 0, 0, 5, 2, +3, 6, listOf("W","W")),
            LeagueStanding(3, "Ghana (Gr. C)", "https://flagcdn.com/w160/gh.png", 2, 0, 0, 2, 1, 3, -2, 0, listOf("L","L")),
            LeagueStanding(4, "Somalie (Gr. C)", "https://flagcdn.com/w160/so.png", 2, 0, 0, 2, 0, 5, -5, 0, listOf("L","L")),
            LeagueStanding(5, "Maroc (Gr. B)", "https://flagcdn.com/w160/ma.png", 4, 4, 0, 0, 14, 1, +13, 12, listOf("W","W","W","W")),
            LeagueStanding(6, "Sénégal (Gr. L)", "https://flagcdn.com/w160/sn.png", 4, 3, 1, 0, 8, 1, +7, 10, listOf("W","W","D","W")),
            LeagueStanding(7, "Algérie (Gr. E)", "https://flagcdn.com/w160/dz.png", 4, 3, 1, 0, 9, 2, +7, 10, listOf("W","D","W","W")),
            LeagueStanding(8, "Cameroun (Gr. J)", "https://flagcdn.com/w160/cm.png", 4, 2, 2, 0, 5, 2, +3, 8, listOf("D","W","D","W"))
        )

        val matchAmicaux = listOf(
            LeagueStanding(1, "Brésil", "https://flagcdn.com/w160/br.png", 5, 4, 1, 0, 12, 4, +8, 13, listOf("W","W","D","W","W")),
            LeagueStanding(2, "Real Madrid", "https://media.api-sports.io/football/teams/541.png", 5, 4, 0, 1, 14, 5, +9, 12, listOf("W","W","W","L","W")),
            LeagueStanding(3, "Argentine", "https://flagcdn.com/w160/ar.png", 5, 3, 2, 0, 10, 5, +5, 11, listOf("W","D","D","W","W")),
            LeagueStanding(4, "AC Milan", "https://media.api-sports.io/football/teams/489.png", 5, 3, 0, 2, 9, 7, +2, 9, listOf("L","W","W","L","W"))
        )

        return mapOf(
            "Qualifications CAN 2027" to qualifCan,
            "RSL (Saudi Pro League)" to rsl,
            "Qualifications CAN" to qualifCan,
            "Matchs Amicaux" to matchAmicaux,
            "Ligue Europa" to ligueEuropa,
            "Ligue des Champions" to ligue1.take(4),
            "Ligue 1" to ligue1,
            "Premier League" to premierLeague,
            "LaLiga" to laLiga,
            "Bundesliga" to bundesliga,
            "Serie A" to serieA,
            "CAN" to can,
            "Euro" to euro,
            "Euro / Ligue des Nations" to euro
        )
    }

    private fun generateSamplePlayers(): List<PlayerProfile> {
        return listOf(
            PlayerProfile(
                id = "p1",
                name = "Kylian Mbappé",
                team = "Real Madrid",
                teamLogo = "https://media.api-sports.io/football/teams/541.png",
                position = "Attaquant",
                nationality = "France 🇫🇷",
                age = 27,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/95/Kylian_Mbappe_France_v_Senegal_16_June_2026-391_%28cropped%29.jpg/330px-Kylian_Mbappe_France_v_Senegal_16_June_2026-391_%28cropped%29.jpg",
                goals = 28,
                assists = 9,
                matches = 29,
                rating = 8.8f,
                marketValue = "180M €",
                bio = "Star incontestée du football mondial, Mbappé allie une vitesse supersonique et une finition clinique devant le but.",
                speed = 97, shooting = 92, passing = 83, dribbling = 93, defense = 38, physical = 79,
                expectedGoals = 26.4f,
                expectedAssists = 7.9f,
                keyPasses = 56,
                duelsWon = 132,
                duelsWonPct = 54,
                shotsOnTarget = 68,
                successfulDribbles = 84,
                recoveries = 34,
                minutesPlayed = 2480
            ),
            PlayerProfile(
                id = "p2",
                name = "Erling Haaland",
                team = "Manchester City",
                teamLogo = "https://media.api-sports.io/football/teams/50.png",
                position = "Avant-Centre",
                nationality = "Norvège 🇳🇴",
                age = 25,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/43/Erling_Haaland_Morocco_v_Norway_7_June_2026-51.jpg/330px-Erling_Haaland_Morocco_v_Norway_7_June_2026-51.jpg",
                goals = 31,
                assists = 4,
                matches = 27,
                rating = 8.7f,
                marketValue = "180M €",
                bio = "Cyborg des surfaces, puissance physique hors du commun et instinct de tueur dans la zone de vérité.",
                speed = 89, shooting = 95, passing = 68, dribbling = 80, defense = 45, physical = 91,
                expectedGoals = 29.8f,
                expectedAssists = 3.4f,
                keyPasses = 24,
                duelsWon = 118,
                duelsWonPct = 62,
                shotsOnTarget = 76,
                successfulDribbles = 26,
                recoveries = 29,
                minutesPlayed = 2320
            ),
            PlayerProfile(
                id = "p3",
                name = "Vinícius Júnior",
                team = "Real Madrid",
                teamLogo = "https://media.api-sports.io/football/teams/541.png",
                position = "Ailier Gauche",
                nationality = "Brésil 🇧🇷",
                age = 25,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/10/Vin%C3%ADcius_J%C3%BAnior_Brazil_V_Morocco_13_June_2026-207_%28cropped%29.jpg/330px-Vin%C3%ADcius_J%C3%BAnior_Brazil_V_Morocco_13_June_2026-207_%28cropped%29.jpg",
                goals = 21,
                assists = 14,
                matches = 30,
                rating = 8.6f,
                marketValue = "170M €",
                bio = "Dribbleur magique au style samba spectaculaire, capable d'éliminer plusieurs défenseurs sur un crochet intérieur.",
                speed = 95, shooting = 85, passing = 82, dribbling = 95, defense = 32, physical = 74,
                expectedGoals = 18.9f,
                expectedAssists = 12.6f,
                keyPasses = 66,
                duelsWon = 158,
                duelsWonPct = 52,
                shotsOnTarget = 51,
                successfulDribbles = 106,
                recoveries = 42,
                minutesPlayed = 2540
            ),
            PlayerProfile(
                id = "p4",
                name = "Lamine Yamal",
                team = "FC Barcelone",
                teamLogo = "https://media.api-sports.io/football/teams/529.png",
                position = "Ailier Droit",
                nationality = "Espagne 🇪🇸",
                age = 18,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/1/13/Lamine_Yamal_France_v_Spain_7.24.26-142.jpg/330px-Lamine_Yamal_France_v_Spain_7.24.26-142.jpg",
                goals = 14,
                assists = 18,
                matches = 32,
                rating = 8.5f,
                marketValue = "150M €",
                bio = "Prodige précoce doté d'un pied gauche merveilleux et d'une vision du jeu digne des plus grands maîtres du Camp Nou.",
                speed = 90, shooting = 82, passing = 88, dribbling = 94, defense = 40, physical = 68,
                expectedGoals = 12.3f,
                expectedAssists = 16.8f,
                keyPasses = 78,
                duelsWon = 152,
                duelsWonPct = 56,
                shotsOnTarget = 39,
                successfulDribbles = 101,
                recoveries = 54,
                minutesPlayed = 2620
            ),
            PlayerProfile(
                id = "p5",
                name = "Victor Osimhen",
                team = "Galatasaray / Nigeria",
                teamLogo = "https://media.api-sports.io/football/teams/19.png",
                position = "Attaquant",
                nationality = "Nigeria 🇳🇬",
                age = 27,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/34/Victor-osimhen-nigeria-2024-3-4.jpg/330px-Victor-osimhen-nigeria-2024-3-4.jpg",
                goals = 22,
                assists = 6,
                matches = 25,
                rating = 8.4f,
                marketValue = "100M €",
                bio = "Machine de guerre offensive, redoutable dans le jeu aérien et le pressing défensif.",
                speed = 91, shooting = 88, passing = 72, dribbling = 81, defense = 48, physical = 88,
                expectedGoals = 21.1f,
                expectedAssists = 4.7f,
                keyPasses = 30,
                duelsWon = 165,
                duelsWonPct = 65,
                shotsOnTarget = 54,
                successfulDribbles = 44,
                recoveries = 48,
                minutesPlayed = 2150
            ),
            PlayerProfile(
                id = "p6",
                name = "Jude Bellingham",
                team = "Real Madrid",
                teamLogo = "https://media.api-sports.io/football/teams/541.png",
                position = "Milieu Offensif",
                nationality = "Angleterre 🏴󠁧󠁢󠁥󠁮󠁧󠁿",
                age = 22,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/23/Jude_Bellingham_England_v_Ghana_23_June_2026-061_%28cropped%29.jpg/330px-Jude_Bellingham_England_v_Ghana_23_June_2026-061_%28cropped%29.jpg",
                goals = 19,
                assists = 12,
                matches = 31,
                rating = 8.7f,
                marketValue = "180M €",
                bio = "Milieu tout-terrain complet combinant projection tranchante dans la surface, récupération et leadership naturel.",
                speed = 84, shooting = 87, passing = 88, dribbling = 89, defense = 76, physical = 85,
                expectedGoals = 16.5f,
                expectedAssists = 11.2f,
                keyPasses = 72,
                duelsWon = 188,
                duelsWonPct = 67,
                shotsOnTarget = 44,
                successfulDribbles = 68,
                recoveries = 92,
                minutesPlayed = 2710
            ),
            PlayerProfile(
                id = "p7",
                name = "Kevin De Bruyne",
                team = "Manchester City",
                teamLogo = "https://media.api-sports.io/football/teams/50.png",
                position = "Meneur de Jeu",
                nationality = "Belgique 🇧🇪",
                age = 34,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/40/Kevin_De_Bruyne_USMNT_v_Belgium_Mar_28_2026-64_%28cropped%29.jpg/330px-Kevin_De_Bruyne_USMNT_v_Belgium_Mar_28_2026-64_%28cropped%29.jpg",
                goals = 10,
                assists = 22,
                matches = 26,
                rating = 8.6f,
                marketValue = "60M €",
                bio = "Maestro de la passe décisive, précision millimétrée des centres et transversales destructrices pour les défenses.",
                speed = 76, shooting = 86, passing = 96, dribbling = 87, defense = 64, physical = 75,
                expectedGoals = 7.4f,
                expectedAssists = 19.8f,
                keyPasses = 98,
                duelsWon = 92,
                duelsWonPct = 49,
                shotsOnTarget = 33,
                successfulDribbles = 38,
                recoveries = 65,
                minutesPlayed = 2140
            ),
            PlayerProfile(
                id = "p8",
                name = "Mohamed Salah",
                team = "Liverpool",
                teamLogo = "https://media.api-sports.io/football/teams/40.png",
                position = "Ailier Droit",
                nationality = "Égypte 🇪🇬",
                age = 33,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a6/Mohamed_Salah_Argentina_v_Egypt_7_July_2026-163_%28cropped%29.jpg/330px-Mohamed_Salah_Argentina_v_Egypt_7_July_2026-163_%28cropped%29.jpg",
                goals = 25,
                assists = 15,
                matches = 32,
                rating = 8.6f,
                marketValue = "80M €",
                bio = "Le Pharaon d'Anfield, tranchant de son pied gauche, constance phénoménale et créateur hors pair en zone haute.",
                speed = 91, shooting = 89, passing = 85, dribbling = 90, defense = 42, physical = 76,
                expectedGoals = 23.2f,
                expectedAssists = 14.1f,
                keyPasses = 75,
                duelsWon = 124,
                duelsWonPct = 50,
                shotsOnTarget = 63,
                successfulDribbles = 61,
                recoveries = 41,
                minutesPlayed = 2730
            ),
            PlayerProfile(
                id = "p9",
                name = "Achraf Hakimi",
                team = "Paris SG",
                teamLogo = "https://media.api-sports.io/football/teams/85.png",
                position = "Latéral Droit",
                nationality = "Maroc 🇲🇦",
                age = 27,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/36/Achraf_Hakimi_Morocco_v_Norway_7_June_2026-32.jpg/330px-Achraf_Hakimi_Morocco_v_Norway_7_June_2026-32.jpg",
                goals = 8,
                assists = 11,
                matches = 30,
                rating = 8.4f,
                marketValue = "75M €",
                bio = "TGV du couloir droit, apports offensifs incessants, replis défensifs à grande vitesse et centres dangereux.",
                speed = 95, shooting = 74, passing = 83, dribbling = 84, defense = 81, physical = 84,
                expectedGoals = 6.3f,
                expectedAssists = 10.2f,
                keyPasses = 58,
                duelsWon = 176,
                duelsWonPct = 63,
                shotsOnTarget = 25,
                successfulDribbles = 74,
                recoveries = 98,
                minutesPlayed = 2600
            ),
            PlayerProfile(
                id = "p10",
                name = "Rodri",
                team = "Manchester City",
                teamLogo = "https://media.api-sports.io/football/teams/50.png",
                position = "Milieu Défensif",
                nationality = "Espagne 🇪🇸",
                age = 29,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/7/7a/Rodri_Argentina_v_Spain_19_July_2026-187_%28cropped%29.jpg/330px-Rodri_Argentina_v_Spain_19_July_2026-187_%28cropped%29.jpg",
                goals = 8,
                assists = 9,
                matches = 33,
                rating = 8.8f,
                marketValue = "130M €",
                bio = "Ballon d'Or, régulateur absolu du tempo au milieu, intelligence tactique hors norme et invulnérable sous la pression.",
                speed = 68, shooting = 80, passing = 92, dribbling = 82, defense = 89, physical = 87,
                expectedGoals = 5.2f,
                expectedAssists = 7.5f,
                keyPasses = 53,
                duelsWon = 216,
                duelsWonPct = 72,
                shotsOnTarget = 22,
                successfulDribbles = 50,
                recoveries = 172,
                minutesPlayed = 2920
            ),
            PlayerProfile(
                id = "p11",
                name = "Cole Palmer",
                team = "Chelsea",
                teamLogo = "https://media.api-sports.io/football/teams/49.png",
                position = "Milieu Offensif",
                nationality = "Angleterre 🏴󠁧󠁢󠁥󠁮󠁧󠁿",
                age = 23,
                photoUrl = "https://upload.wikimedia.org/wikipedia/commons/f/fb/Cole_Palmer_2025_FIFA_Club_World_Cup_Final.jpg",
                goals = 22,
                assists = 13,
                matches = 31,
                rating = 8.5f,
                marketValue = "110M €",
                bio = "Cold Palmer : sang-froid légendaire sur penalty, vista exceptionnelle entre les lignes et frappe chirurgicale.",
                speed = 82, shooting = 88, passing = 89, dribbling = 88, defense = 52, physical = 70,
                expectedGoals = 19.8f,
                expectedAssists = 12.2f,
                keyPasses = 69,
                duelsWon = 126,
                duelsWonPct = 51,
                shotsOnTarget = 49,
                successfulDribbles = 65,
                recoveries = 46,
                minutesPlayed = 2640
            ),
            PlayerProfile(
                id = "p12",
                name = "Khvicha Kvaratskhelia",
                team = "Napoli / Paris SG",
                teamLogo = "https://media.api-sports.io/football/teams/492.png",
                position = "Ailier Gauche",
                nationality = "Géorgie 🇬🇪",
                age = 25,
                photoUrl = "https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4a/Kvaratskhelia_asse_psg_2425.png/330px-Kvaratskhelia_asse_psg_2425.png",
                goals = 16,
                assists = 12,
                matches = 28,
                rating = 8.5f,
                marketValue = "90M €",
                bio = "Kvaradona : dribbles imprévisibles, accélérations foudroyantes et grande créativité sur le flanc gauche.",
                speed = 89, shooting = 84, passing = 82, dribbling = 91, defense = 41, physical = 75,
                expectedGoals = 14.2f,
                expectedAssists = 10.5f,
                keyPasses = 64,
                duelsWon = 142,
                duelsWonPct = 53,
                shotsOnTarget = 42,
                successfulDribbles = 88,
                recoveries = 39,
                minutesPlayed = 2410
            )
        )
    }

    private fun generateSampleNews(): List<NewsArticle> {
        return listOf(
            NewsArticle(
                id = "n1",
                title = "Analyse IA : Le PSG prêt pour conquérir l'Europe ?",
                category = "IA Analyse",
                summary = "Grâce aux données statistiques récoltées par l'IA FootIA Live, découvrez les clés tactiques du Paris Saint-Germain cette saison.",
                content = "Le Paris Saint-Germain impressionne cette saison par son pressing haut et son jeu de transition ultra-rapide. L'algorithme de FootIA Live note une progression de 18% sur l'efficacité des duels gagnés au milieu de terrain...",
                author = "Rédaction FootIA",
                date = "Il y a 2 heures",
                readTime = "4 min",
                imageUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800"
            ),
            NewsArticle(
                id = "n2",
                title = "Mercato : Les rumeurs brûlantes de l'été 2026",
                category = "Transferts",
                summary = "De Madrid à Manchester, les plus grands clubs d'Europe s'activent pour préparer un marché des transferts historique.",
                content = "Les directeurs sportifs affûtent leurs offres. Plusieurs cadors européens négocient le transfert des jeunes pépites sud-américaines et africaines...",
                author = "Julien Laurens",
                date = "Il y a 5 heures",
                readTime = "3 min",
                imageUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=800"
            ),
            NewsArticle(
                id = "n3",
                title = "Prépa CAN : Le Sénégal et la Côte d'Ivoire montent en puissance",
                category = "CAN",
                summary = "Retour sur les derniers matchs de préparation des Lions de la Teranga et des Éléphants avant la phase finale.",
                content = "Des attaques prolifiques et des défenses de fer : le niveau s'annonce exceptionnel pour cette nouvelle édition de la Coupe d'Afrique des Nations.",
                author = "Amadou Diallo",
                date = "Hier",
                readTime = "5 min",
                imageUrl = "https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=800"
            )
        )
    }
}
