package com.example.data.repository

import android.content.Context
import com.example.data.ai.GeminiCommentatorRepository
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.network.FootballNewsApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class FootballRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val favoriteDao = db.favoriteDao()
    private val aiRepository = GeminiCommentatorRepository()

    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Live Matches State
    private val _matchesState = MutableStateFlow<List<Match>>(emptyList())
    val matchesState: StateFlow<List<Match>> = _matchesState.asStateFlow()

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
        val currentMap = _predictionsState.value.toMutableMap()
        currentMap[matchId] = UserMatchPrediction(
            matchId = matchId,
            choice = choice,
            predictedHomeScore = homeScore,
            predictedAwayScore = awayScore
        )
        _predictionsState.value = currentMap

        prefs.edit().putString("pred_choice_$matchId", choice.name).apply()
        prefs.edit().putInt("pred_hs_$matchId", homeScore).apply()
        prefs.edit().putInt("pred_as_$matchId", awayScore).apply()
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
        _matchesState.value = generateSampleMatches()
        _standingsState.value = generateSampleStandings()
        _playersState.value = generateSamplePlayers()
        _newsState.value = generateSampleNews()
        _newsSource.value = "Édition Spéciale FootIA"
        
        // Fetch real-time live headlines from public APIs
        refreshNewsFromPublicApi()
        
        // Initial Notification
        val initialNotif = AppNotification(
            id = "notif_welcome",
            matchId = "m1",
            title = "⚽ Bienvenue sur FootIA Live !",
            message = "Le commentateur IA TV est prêt à analyser vos matchs préférés d'Euro, Ligue 1, Premier League, LaLiga, CAN et Champions League.",
            timestamp = getCurrentTimeString(),
            type = "DEBRIEF_IA"
        )
        _notificationsState.value = listOf(initialNotif)
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

    private fun startLiveSimulation() {
        repositoryScope.launch {
            while (isActive) {
                delay(12000L) // Simulate live tick every 12 seconds
                updateLiveMatchesTick()
            }
        }
    }

    private suspend fun updateLiveMatchesTick() {
        val currentMatches = _matchesState.value.toMutableList()
        var updated = false
        val newNotifs = mutableListOf<AppNotification>()

        for (i in currentMatches.indices) {
            val m = currentMatches[i]
            if (m.status == MatchStatus.LIVE) {
                updated = true
                val nextMinute = if (m.minute >= 90) 90 else m.minute + 1
                var newHomeScore = m.homeScore
                var newAwayScore = m.awayScore
                val updatedEvents = m.events.toMutableList()
                val updatedCommentaries = m.aiCommentaries.toMutableList()

                // Random event trigger
                val chance = Random.nextInt(100)
                if (chance < 15 && nextMinute < 90) { // Goal event
                    val isHome = Random.nextBoolean()
                    val scorer = if (isHome) "K. Mbappé" else "E. Haaland"
                    if (isHome) newHomeScore++ else newAwayScore++

                    val goalEvent = MatchEvent(
                        id = "ev_${System.currentTimeMillis()}",
                        minute = nextMinute,
                        type = EventType.GOAL,
                        isHomeTeam = isHome,
                        playerName = scorer,
                        detail = "But spectaculaire !"
                    )
                    updatedEvents.add(0, goalEvent)

                    // Generate live AI commentary
                    val goalCommentary = aiRepository.generateLiveCommentary(
                        m.copy(homeScore = newHomeScore, awayScore = newAwayScore),
                        nextMinute,
                        "BUT magistral inscrit par $scorer pour ${if (isHome) m.homeTeam else m.awayTeam} !"
                    )
                    updatedCommentaries.add(0, goalCommentary)

                    // Add Notification
                    newNotifs.add(
                        AppNotification(
                            id = "notif_${System.currentTimeMillis()}",
                            matchId = m.id,
                            title = "⚽ GOAL ! ${m.homeTeam} $newHomeScore - $newAwayScore ${m.awayTeam}",
                            message = "$scorer marque à la ${nextMinute}e minute dans le match ${m.league} !",
                            timestamp = getCurrentTimeString(),
                            type = "BUT"
                        )
                    )
                } else if (chance in 15..25 && nextMinute < 90) { // Yellow Card
                    val isHome = Random.nextBoolean()
                    val player = if (isHome) "A. Hakimi" else "R. Dias"
                    val cardEvent = MatchEvent(
                        id = "ev_${System.currentTimeMillis()}",
                        minute = nextMinute,
                        type = EventType.YELLOW_CARD,
                        isHomeTeam = isHome,
                        playerName = player,
                        detail = "Anti-jeu / Tacle en retard"
                    )
                    updatedEvents.add(0, cardEvent)

                    val cardCommentary = aiRepository.generateLiveCommentary(
                        m,
                        nextMinute,
                        "Carton jaune distribué à $player par l'arbitre !"
                    )
                    updatedCommentaries.add(0, cardCommentary)
                } else if (nextMinute % 15 == 0) { // Periodic AI Commentary
                    val periodicCommentary = aiRepository.generateLiveCommentary(m, nextMinute)
                    updatedCommentaries.add(0, periodicCommentary)
                }

                val isFullTime = nextMinute >= 90
                val newStatus = if (isFullTime) MatchStatus.FINISHED else MatchStatus.LIVE

                var aiSummary = m.aiSummary
                if (isFullTime && aiSummary == null) {
                    aiSummary = aiRepository.generateMatchSummary(m.copy(homeScore = newHomeScore, awayScore = newAwayScore, status = MatchStatus.FINISHED))
                    newNotifs.add(
                        AppNotification(
                            id = "notif_ft_${System.currentTimeMillis()}",
                            matchId = m.id,
                            title = "🏁 Fin du Match: ${m.homeTeam} $newHomeScore - $newAwayScore ${m.awayTeam}",
                            message = "Le débrief IA complet est maintenant disponible !",
                            timestamp = getCurrentTimeString(),
                            type = "FIN_MATCH"
                        )
                    )
                }

                currentMatches[i] = m.copy(
                    minute = nextMinute,
                    homeScore = newHomeScore,
                    awayScore = newAwayScore,
                    status = newStatus,
                    events = updatedEvents,
                    aiCommentaries = updatedCommentaries,
                    aiSummary = aiSummary
                )
            }
        }

        if (updated) {
            _matchesState.value = currentMatches
            if (newNotifs.isNotEmpty()) {
                _notificationsState.value = newNotifs + _notificationsState.value
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
                id = "manual_${System.currentTimeMillis()}",
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
                id = "comm_${System.currentTimeMillis()}",
                minute = m.minute,
                text = commentaryText,
                tone = "INTENSE"
            )

            val updatedCommentaries = listOf(newCommentary) + m.aiCommentaries

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
        val sdf = SimpleDateFormat("HH:mm", Locale.FRANCE)
        return sdf.format(Date())
    }

    // --- Mock Data Generators ---
    private fun generateSampleMatches(): List<Match> {
        val stats1 = MatchStats(
            possessionHome = 62, possessionAway = 38,
            shotsHome = 14, shotsAway = 7,
            shotsOnTargetHome = 6, shotsOnTargetAway = 2,
            passesHome = 540, passesAway = 310,
            passAccuracyHome = 89, passAccuracyAway = 78,
            foulsHome = 9, foulsAway = 14,
            yellowCardsHome = 1, yellowCardsAway = 3,
            redCardsHome = 0, redCardsAway = 0,
            offsidesHome = 2, offsidesAway = 1
        )

        val lineup1 = MatchLineup(
            homeFormation = "4-3-3",
            awayFormation = "4-2-3-1",
            homeStarting11 = listOf(
                PlayerLineup(1, "Donnarumma", "GK"),
                PlayerLineup(2, "A. Hakimi", "RB", rating = 8.1f),
                PlayerLineup(5, "Marquinhos", "CB", isCaptain = true),
                PlayerLineup(51, "W. Pacho", "CB"),
                PlayerLineup(25, "N. Mendes", "LB"),
                PlayerLineup(17, "Vitinha", "CM", rating = 8.4f),
                PlayerLineup(87, "J. Neves", "CM"),
                PlayerLineup(8, "F. Ruiz", "CM"),
                PlayerLineup(10, "O. Dembélé", "RW", rating = 7.9f),
                PlayerLineup(29, "B. Barcola", "LW", rating = 8.7f),
                PlayerLineup(9, "G. Ramos", "ST")
            ),
            awayStarting11 = listOf(
                PlayerLineup(1, "Rulli", "GK"),
                PlayerLineup(62, "Murillo", "RB"),
                PlayerLineup(5, "Balerdi", "CB", isCaptain = true),
                PlayerLineup(4, "Gigot", "CB"),
                PlayerLineup(3, "Merlin", "LB"),
                PlayerLineup(23, "Højbjerg", "CDM"),
                PlayerLineup(19, "Kondogbia", "CDM"),
                PlayerLineup(10, "Greenwood", "RM", rating = 7.6f),
                PlayerLineup(11, "Harit", "CAM"),
                PlayerLineup(44, "Henrique", "LM"),
                PlayerLineup(9, "Wahi", "ST")
            ),
            homeSubstitutes = listOf(PlayerLineup(19, "Kang-in Lee", "CAM"), PlayerLineup(23, "R. Kolo Muani", "ST")),
            awaySubstitutes = listOf(PlayerLineup(8, "Ounahi", "CM"), PlayerLineup(14, "Moumbagna", "ST"))
        )

        val events1 = listOf(
            MatchEvent("e1", 38, EventType.GOAL, true, "B. Barcola", "Passe décisive de Vitinha"),
            MatchEvent("e2", 54, EventType.YELLOW_CARD, false, "L. Balerdi", "Tacle appuyé"),
            MatchEvent("e3", 67, EventType.GOAL, true, "O. Dembélé", "Frappe croisée du droit")
        )

        val commentaries1 = listOf(
            AICommentary("c1", 67, "🔥 67' - BUT EXCEPTIONNEL D'OUSMANE DEMBÉLÉ ! Crochet dévastateur et frappe en pleine lucarne ! Le Parc des Princes chavire !", "INTENSE"),
            AICommentary("c2", 58, "📊 58' - Le PSG accentue sa possession (64%). L'OM peine à sortir de son propre demi-terrain.", "ANALYTICAL"),
            AICommentary("c3", 38, "⚽ 38' - BUT DE BRADLEY BARCOLA ! Un modèle de contre-attaque conclu avec sang-froid !", "INTENSE")
        )

        val m1 = Match(
            id = "m1",
            league = "Ligue 1",
            homeTeam = "Paris SG",
            homeTeamLogo = "https://media.api-sports.io/football/teams/85.png",
            awayTeam = "Olympique de Marseille",
            awayTeamLogo = "https://media.api-sports.io/football/teams/81.png",
            homeScore = 2,
            awayScore = 0,
            minute = 72,
            status = MatchStatus.LIVE,
            date = "Aujourd'hui",
            time = "20:45",
            stadium = "Parc des Princes",
            referee = "Clément Turpin",
            stats = stats1,
            events = events1,
            lineup = lineup1,
            aiCommentaries = commentaries1,
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl1_1", 38, "⚽ But Barcola", "GOAL", 3),
                VideoHighlightMarker("hl1_2", 54, "🟨 Tacle Balerdi", "FOUL", 7),
                VideoHighlightMarker("hl1_3", 67, "⚽ But Dembélé", "GOAL", 11),
                VideoHighlightMarker("hl1_4", 71, "🧤 Parade Donnarumma", "SAVE", 13)
            )
        )

        val m2 = Match(
            id = "m2",
            league = "Premier League",
            homeTeam = "Arsenal",
            homeTeamLogo = "https://media.api-sports.io/football/teams/42.png",
            awayTeam = "Manchester City",
            awayTeamLogo = "https://media.api-sports.io/football/teams/50.png",
            homeScore = 1,
            awayScore = 1,
            minute = 88,
            status = MatchStatus.LIVE,
            date = "Aujourd'hui",
            time = "17:30",
            stadium = "Emirates Stadium",
            referee = "Michael Oliver",
            stats = stats1.copy(possessionHome = 48, possessionAway = 52, shotsHome = 11, shotsAway = 13),
            events = listOf(
                MatchEvent("e10", 22, EventType.GOAL, false, "E. Haaland", "Tête puissante"),
                MatchEvent("e11", 76, EventType.GOAL, true, "B. Saka", "Penalty transformé")
            ),
            lineup = lineup1,
            aiCommentaries = listOf(
                AICommentary("c10", 88, "⚡ 88' - Fin de match irrespirable à l'Emirates Stadium ! Les deux géants se rendent coup pour coup !", "INTENSE")
            ),
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl2_1", 22, "⚽ But Haaland", "GOAL", 2),
                VideoHighlightMarker("hl2_2", 65, "🔥 Tir Foden", "CHANCE", 6),
                VideoHighlightMarker("hl2_3", 76, "⚽ Penalty Saka", "GOAL", 10)
            )
        )

        val m3 = Match(
            id = "m3",
            league = "LaLiga",
            homeTeam = "Real Madrid",
            homeTeamLogo = "https://media.api-sports.io/football/teams/541.png",
            awayTeam = "FC Barcelone",
            awayTeamLogo = "https://media.api-sports.io/football/teams/529.png",
            homeScore = 3,
            awayScore = 2,
            minute = 90,
            status = MatchStatus.FINISHED,
            date = "Hier",
            time = "21:00",
            stadium = "Santiago Bernabéu",
            referee = "Gil Manzano",
            stats = stats1.copy(possessionHome = 51, possessionAway = 49),
            events = listOf(
                MatchEvent("e20", 12, EventType.GOAL, false, "L. Yamal", "Coup franc magistral"),
                MatchEvent("e21", 34, EventType.GOAL, true, "Vini Jr.", "Raid solitaire"),
                MatchEvent("e22", 61, EventType.GOAL, false, "R. Lewandowski", "Reprise de la tête"),
                MatchEvent("e23", 85, EventType.GOAL, true, "J. Bellingham", "Frappe du plat du pied"),
                MatchEvent("e24", 90, EventType.GOAL, true, "K. Mbappé", "Penalty de la victoire")
            ),
            lineup = lineup1,
            aiCommentaries = listOf(
                AICommentary("c20", 90, "🎉 90' - FIN DU MATCH ! Victoire épique du Real Madrid dans un Clasico d'antologie !", "INTENSE")
            ),
            aiSummary = "Un Clasico d'exception au Santiago Bernabéu ! Le Real Madrid s'impose 3-2 au terme d'un scénario à rebondissements avec des réalisations signées Vinícius Jr, Bellingham et le but vainqueur sur penalty de Mbappé.",
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1522778119026-d647f0596c20?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl3_1", 12, "⚽ Coup franc Yamal", "GOAL", 2),
                VideoHighlightMarker("hl3_2", 34, "⚽ Slalom Vini Jr", "GOAL", 5),
                VideoHighlightMarker("hl3_3", 85, "⚽ But Bellingham", "GOAL", 8),
                VideoHighlightMarker("hl3_4", 90, "⚽ Penalty Mbappé", "GOAL", 12)
            )
        )

        val m4 = Match(
            id = "m4",
            league = "CAN",
            homeTeam = "Sénégal",
            homeTeamLogo = "https://flagcdn.com/w160/sn.png",
            awayTeam = "Côte d'Ivoire",
            awayTeamLogo = "https://flagcdn.com/w160/ci.png",
            homeScore = 1,
            awayScore = 0,
            minute = 45,
            status = MatchStatus.LIVE,
            date = "Aujourd'hui",
            time = "21:00",
            stadium = "Stade Léopold Sédar Senghor",
            referee = "Mustapha Ghorbal",
            stats = stats1.copy(possessionHome = 55, possessionAway = 45),
            events = listOf(
                MatchEvent("e30", 28, EventType.GOAL, true, "Sadio Mané", "Reprise de volée")
            ),
            lineup = lineup1,
            aiCommentaries = listOf(
                AICommentary("c30", 45, "🌍 45' - Mi-temps ! Le Sénégal mène 1-0 grâce à une bicyclette sensationnelle de Sadio Mané !", "INTENSE")
            ),
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl4_1", 28, "⚽ Volée Sadio Mané", "GOAL", 3),
                VideoHighlightMarker("hl4_2", 41, "🔥 Tête Haller", "CHANCE", 8)
            )
        )

        val m5 = Match(
            id = "m5",
            league = "Ligue des Champions",
            homeTeam = "Bayern Munich",
            homeTeamLogo = "https://media.api-sports.io/football/teams/157.png",
            awayTeam = "Inter Milan",
            awayTeamLogo = "https://media.api-sports.io/football/teams/505.png",
            homeScore = 2,
            awayScore = 1,
            minute = 64,
            status = MatchStatus.LIVE,
            date = "Aujourd'hui",
            time = "21:00",
            stadium = "Allianz Arena",
            referee = "Szymon Marciniak",
            stats = stats1.copy(possessionHome = 60, possessionAway = 40, shotsHome = 15, shotsAway = 8),
            events = listOf(
                MatchEvent("e50", 17, EventType.GOAL, true, "H. Kane", "Pique par dessus le gardien"),
                MatchEvent("e51", 49, EventType.GOAL, false, "Lautaro Martínez", "Tir puissant petit filet"),
                MatchEvent("e52", 61, EventType.GOAL, true, "J. Musiala", "Festival dans la surface")
            ),
            lineup = lineup1,
            aiCommentaries = listOf(
                AICommentary("c50", 64, "⭐ 64' - Choc européen grandiose ! Le Bayern reprend l'avantage grâce à un exploit de Musiala !", "INTENSE")
            ),
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1511886929837-354d827aae26?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl5_1", 17, "⚽ Piqué Kane", "GOAL", 2),
                VideoHighlightMarker("hl5_2", 49, "⚽ Égalisation Lautaro", "GOAL", 6),
                VideoHighlightMarker("hl5_3", 61, "⚽ Festival Musiala", "GOAL", 10)
            )
        )

        val m6 = Match(
            id = "m6",
            league = "Euro / Ligue des Nations",
            homeTeam = "France",
            homeTeamLogo = "https://flagcdn.com/w160/fr.png",
            awayTeam = "Espagne",
            awayTeamLogo = "https://flagcdn.com/w160/es.png",
            homeScore = 2,
            awayScore = 1,
            minute = 81,
            status = MatchStatus.LIVE,
            date = "Aujourd'hui",
            time = "21:00",
            stadium = "Allianz Arena, Munich",
            referee = "Clément Turpin",
            stats = stats1.copy(possessionHome = 52, possessionAway = 48, shotsHome = 12, shotsAway = 10),
            events = listOf(
                MatchEvent("e60", 24, EventType.GOAL, false, "Lamine Yamal", "Superbe enroulé du gauche"),
                MatchEvent("e61", 52, EventType.GOAL, true, "K. Mbappé", "Penalty en force"),
                MatchEvent("e62", 78, EventType.GOAL, true, "A. Griezmann", "Reprise de volée")
            ),
            lineup = lineup1,
            aiCommentaries = listOf(
                AICommentary("c60", 78, "🇪🇺 78' - BUT FABULEUX POUR LA FRANCE ! Antoine Griezmann reprend de volée un centre millimétré ! 2-1 pour les Bleus !", "INTENSE")
            ),
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1551958219-acbc608c6377?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl6_1", 24, "⚽ Enroulé Yamal", "GOAL", 2),
                VideoHighlightMarker("hl6_2", 52, "⚽ Penalty Mbappé", "GOAL", 7),
                VideoHighlightMarker("hl6_3", 78, "⚽ Volée Griezmann", "GOAL", 12)
            )
        )

        val m7 = Match(
            id = "m7",
            league = "Bundesliga",
            homeTeam = "Borussia Dortmund",
            homeTeamLogo = "https://media.api-sports.io/football/teams/165.png",
            awayTeam = "Bayer Leverkusen",
            awayTeamLogo = "https://media.api-sports.io/football/teams/168.png",
            homeScore = 2,
            awayScore = 2,
            minute = 75,
            status = MatchStatus.LIVE,
            date = "Aujourd'hui",
            time = "18:30",
            stadium = "Signal Iduna Park",
            referee = "Felix Brych",
            stats = stats1.copy(possessionHome = 47, possessionAway = 53, shotsHome = 14, shotsAway = 16),
            events = listOf(
                MatchEvent("e70", 14, EventType.GOAL, true, "J. Brandt", "Frappe tendue"),
                MatchEvent("e71", 38, EventType.GOAL, false, "F. Wirtz", "Plein axe lucarne"),
                MatchEvent("e72", 59, EventType.GOAL, false, "V. Boniface", "Renard des surfaces"),
                MatchEvent("e73", 72, EventType.GOAL, true, "S. Guirassy", "Tête puissante")
            ),
            lineup = lineup1,
            aiCommentaries = listOf(
                AICommentary("c70", 75, "🇩🇪 75' - Ambiance de feu au Signal Iduna Park ! Égalisation de Guirassy face au champion Leverkusen !", "INTENSE")
            ),
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl7_1", 14, "⚽ But Brandt", "GOAL", 2),
                VideoHighlightMarker("hl7_2", 38, "⚽ Bijou Wirtz", "GOAL", 5),
                VideoHighlightMarker("hl7_3", 72, "⚽ Tête Guirassy", "GOAL", 10)
            )
        )

        val m8 = Match(
            id = "m8",
            league = "Serie A",
            homeTeam = "Inter Milan",
            homeTeamLogo = "https://media.api-sports.io/football/teams/505.png",
            awayTeam = "AC Milan",
            awayTeamLogo = "https://media.api-sports.io/football/teams/489.png",
            homeScore = 2,
            awayScore = 1,
            minute = 83,
            status = MatchStatus.LIVE,
            date = "Aujourd'hui",
            time = "20:45",
            stadium = "San Siro, Milan",
            referee = "Daniele Orsato",
            stats = stats1.copy(possessionHome = 53, possessionAway = 47, shotsHome = 13, shotsAway = 11),
            events = listOf(
                MatchEvent("e80", 22, EventType.GOAL, true, "M. Thuram", "Frappe lourde"),
                MatchEvent("e81", 44, EventType.GOAL, false, "R. Leão", "Accélération foudroyante"),
                MatchEvent("e82", 79, EventType.GOAL, true, "N. Barella", "Demi-volée magique")
            ),
            lineup = lineup1,
            aiCommentaries = listOf(
                AICommentary("c80", 83, "🇮🇹 83' - Derby della Madonnina incandescent ! Barella redonne l'avantage aux Nerazzurri !", "INTENSE")
            ),
            videoStreamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            videoThumbnailUrl = "https://images.unsplash.com/photo-1489944440615-453fc2b6a9a9?w=800&auto=format&fit=crop",
            videoHighlights = listOf(
                VideoHighlightMarker("hl8_1", 22, "⚽ Frappe Thuram", "GOAL", 3),
                VideoHighlightMarker("hl8_2", 44, "⚽ Raid Leão", "GOAL", 7),
                VideoHighlightMarker("hl8_3", 79, "⚽ Volée Barella", "GOAL", 11)
            )
        )

        return listOf(m1, m2, m3, m4, m5, m6, m7, m8)
    }

    private fun generateSampleStandings(): Map<String, List<LeagueStanding>> {
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

        val matchAmicaux = listOf(
            LeagueStanding(1, "Brésil", "https://flagcdn.com/w160/br.png", 5, 4, 1, 0, 12, 4, +8, 13, listOf("W","W","D","W","W")),
            LeagueStanding(2, "Real Madrid", "https://media.api-sports.io/football/teams/541.png", 5, 4, 0, 1, 14, 5, +9, 12, listOf("W","W","W","L","W")),
            LeagueStanding(3, "Argentine", "https://flagcdn.com/w160/ar.png", 5, 3, 2, 0, 10, 5, +5, 11, listOf("W","D","D","W","W")),
            LeagueStanding(4, "AC Milan", "https://media.api-sports.io/football/teams/489.png", 5, 3, 0, 2, 9, 7, +2, 9, listOf("L","W","W","L","W"))
        )

        return mapOf(
            "Matchs Amicaux" to matchAmicaux,
            "Euro" to euro,
            "Ligue 1" to ligue1,
            "Premier League" to premierLeague,
            "LaLiga" to laLiga,
            "Bundesliga" to bundesliga,
            "Serie A" to serieA,
            "CAN" to can,
            "Ligue des Champions" to ligue1.take(4)
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
                photoUrl = "https://media.api-sports.io/football/players/278.png",
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
                photoUrl = "https://media.api-sports.io/football/players/1100.png",
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
                photoUrl = "https://media.api-sports.io/football/players/645.png",
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
                photoUrl = "https://media.api-sports.io/football/players/358315.png",
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
                photoUrl = "https://media.api-sports.io/football/players/48.png",
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
                photoUrl = "https://media.api-sports.io/football/players/152982.png",
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
                photoUrl = "https://media.api-sports.io/football/players/629.png",
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
                photoUrl = "https://media.api-sports.io/football/players/306.png",
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
                photoUrl = "https://media.api-sports.io/football/players/85.png",
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
                photoUrl = "https://media.api-sports.io/football/players/643.png",
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
                photoUrl = "https://media.api-sports.io/football/players/162685.png",
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
