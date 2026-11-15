package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.model.*
import com.example.ui.components.MercatoGeminiComponent
import com.example.ui.components.NewsFeedComponent
import com.example.ui.screens.*
import com.example.ui.theme.FootIALiveTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class FootIALiveScreensTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleMatch(
        id: String = "match_civ_gam",
        league: String = "Qualifications CAN 2027",
        home: String = "Côte d'Ivoire",
        away: String = "Gambie",
        status: MatchStatus = MatchStatus.LIVE,
        date: String = "Aujourd'hui"
    ): Match {
        return Match(
            id = id,
            league = league,
            homeTeam = home,
            homeTeamLogo = "",
            awayTeam = away,
            awayTeamLogo = "",
            homeScore = 2,
            awayScore = 1,
            minute = 67,
            status = status,
            date = date,
            time = "19:00",
            stadium = "Stade de la Paix, Bouaké",
            referee = "B. Gassama",
            stats = MatchStats(58, 42, 12, 6, 5, 2, 460, 320, 85, 76, 10, 14, 1, 2, 0, 0, 2, 1),
            events = listOf(
                MatchEvent("ev_1", 23, EventType.GOAL, true, "S. Haller", "Frappe croisée")
            ),
            lineup = MatchLineup(
                homeFormation = "4-3-3",
                awayFormation = "4-4-2",
                homeStarting11 = listOf(PlayerLineup(1, "Y. Fofana", "GK", false, 7.6f)),
                awayStarting11 = listOf(PlayerLineup(1, "B. Gaye", "GK", false, 7.1f)),
                homeSubstitutes = emptyList(),
                awaySubstitutes = emptyList()
            ),
            aiCommentaries = listOf(
                AICommentary("comm_1", 67, "Frappe puissante de S. Haller repoussée !", "INTENSE"),
                AICommentary("comm_2", 52, "TCHIEEEEY ! Quelle percée côté gauche !", "YA2NE")
            ),
            aiSummary = "Domination des Éléphants avec un pressing haut.",
            videoStreamUrl = null,
            videoHighlights = listOf(
                VideoHighlightMarker("vh_1", 23, "But de S. Haller", "GOAL", 5)
            ),
            broadcastChannel = "La 3 (RTI) & NCI"
        )
    }

    private fun samplePlayer() = PlayerProfile(
        id = "p_1",
        name = "S. Haller",
        team = "Côte d'Ivoire",
        teamLogo = "",
        position = "BU",
        nationality = "Côte d'Ivoire",
        age = 30,
        photoUrl = "",
        goals = 14,
        assists = 5,
        matches = 28,
        rating = 8.1f,
        marketValue = "18 M€",
        bio = "Avant-centre des Éléphants.",
        speed = 79,
        shooting = 85,
        passing = 74,
        dribbling = 77,
        defense = 42,
        physical = 88
    )

    private fun sampleNews() = listOf(
        NewsArticle(
            id = "news_1",
            title = "CAN 2027 : Les Éléphants prêts pour Bouaké",
            category = "CAN 2026",
            summary = "La Côte d'Ivoire prépare son choc du jour.",
            content = "Contenu détaillé de l'article sportif en Côte d'Ivoire.",
            author = "Rédaction FootIA",
            date = "Aujourd'hui",
            readTime = "3 min",
            imageUrl = ""
        ),
        NewsArticle(
            id = "news_2",
            title = "Mercato Ligue 1 : le point sur les négociations",
            category = "Transferts",
            summary = "Plusieurs clubs préparent l'hiver.",
            content = "Analyse complète des mouvements attendus.",
            author = "Mercato Desk",
            date = "Aujourd'hui",
            readTime = "4 min",
            imageUrl = ""
        )
    )

    @Test
    fun homeScreen_rendersAndOpensOverflowAndDialogs() {
        val matches = listOf(
            sampleMatch(),
            sampleMatch("m_psg", "Ligue 1", "Paris SG", "Olympique de Marseille", MatchStatus.UPCOMING, "Demain")
        )
        composeTestRule.setContent {
            FootIALiveTheme {
                HomeScreen(
                    matches = matches,
                    players = listOf(samplePlayer()),
                    news = sampleNews(),
                    selectedLeague = "Toutes",
                    onLeagueSelect = {},
                    onMatchClick = {},
                    onNavigateToMatches = {},
                    onNavigateToTeam = {},
                    onNavigateToNews = {},
                    onOpenDeploymentDialog = {},
                    onOpenNotifications = {},
                    unreadNotifCount = 2,
                    isDarkTheme = true,
                    onToggleTheme = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("FootIA Live").assertIsDisplayed()
        composeTestRule.onNodeWithTag("civ_match_special_card").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("civ_match_special_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_calendar_from_home_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("close_calendar_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_calendar_dialog").performClick()
        composeTestRule.waitForIdle()

        // Overflow menu contains Install Guide button cleanly without squashing TopAppBar
        composeTestRule.onNodeWithTag("home_more_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_install_guide_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Installation FootIA Live").assertIsDisplayed()
    }

    @Test
    fun matchDetailScreen_rendersFeedSwitchesTabsAndHandlesBack() {
        var backCalled = false
        val match = sampleMatch()
        composeTestRule.setContent {
            FootIALiveTheme {
                MatchDetailScreen(
                    match = match,
                    allMatches = listOf(match),
                    onBack = { backCalled = true },
                    onAskAI = { q, _ -> "Réponse IA pour: $q" },
                    onGenerateDebrief = { "Débrief généré par IA." }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Unified scrollable LazyColumn + Player render
        composeTestRule.onNodeWithTag("live_score_commentary_feed").assertIsDisplayed()

        // Switch through all 5 ScrollableTabRow tabs
        composeTestRule.onNodeWithText("Chat Public").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Statistiques").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Statistiques de la rencontre").assertIsDisplayed()

        composeTestRule.onNodeWithText("Compositions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1. Y. Fofana (GK)").assertIsDisplayed()

        composeTestRule.onNodeWithText("Résumé IA").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Débrief Tactique IA Automatique").assertIsDisplayed()

        // Back button & system BackHandler
        composeTestRule.onNodeWithTag("detail_back_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(backCalled)
    }

    @Test
    fun standingsScreen_scrollsToEuroAndDisplaysTeams() {
        val standings = mapOf(
            "Ligue 1" to listOf(LeagueStanding(1, "Paris SG", "", 10, 8, 2, 0, 26, 8, 18, 26, listOf("W", "W", "D"))),
            "Euro / Ligue des Nations" to listOf(LeagueStanding(1, "France", "", 4, 3, 1, 0, 9, 3, 6, 10, listOf("W", "W", "D")))
        )

        composeTestRule.setContent {
            FootIALiveTheme {
                StandingsScreen(standingsMap = standings)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Paris SG").assertIsDisplayed()

        // Scroll horizontal LazyRow to Euro / Ligue des Nations chip
        composeTestRule.onNodeWithTag("standings_league_row")
            .performScrollToNode(hasTestTag("standing_league_chip_Euro / Ligue des Nations"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("standing_league_chip_Euro / Ligue des Nations").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("France").assertIsDisplayed()
    }

    @Test
    fun matchesScreen_rendersCleanly() {
        val matches = listOf(
            sampleMatch(),
            sampleMatch("m_bfa_mrt", "Qualifications CAN 2027", "Burkina Faso", "Mauritanie", MatchStatus.LIVE, "Aujourd'hui"),
            sampleMatch("m_euro", "Euro / Ligue des Nations", "France", "Espagne", MatchStatus.UPCOMING, "15 Nov")
        )
        composeTestRule.setContent {
            FootIALiveTheme {
                MatchesScreen(
                    matches = matches,
                    onMatchClick = {},
                    onTriggerSimulatedAction = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Matchs & Scores en Direct").assertIsDisplayed()
        composeTestRule.onNodeWithTag("match_search_input").performTextInput("mauritanie")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Burkina Faso").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mauritanie").assertIsDisplayed()
    }

    @Test
    fun newsFeedComponent_rendersCleanly() {
        composeTestRule.setContent {
            FootIALiveTheme {
                NewsFeedComponent(newsList = sampleNews())
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("CAN 2027 : Les Éléphants prêts pour Bouaké").assertIsDisplayed()
    }

    @Test
    fun mercatoGeminiComponent_rendersCleanly() {
        val mercato = listOf(
            MercatoNewsItem(
                id = "mer_1",
                source = "Foot Mercato",
                title = "Offre imminente pour un international",
                playerOrClub = "Paris SG",
                transferType = "Négociations",
                estimatedFee = "45 M€",
                reliabilityIndex = 84,
                summary = "Discussions avancées entre les directions.",
                publishedAt = "Il y a 10 min"
            )
        )
        composeTestRule.setContent {
            FootIALiveTheme {
                MercatoGeminiComponent(
                    mercatoItems = mercato,
                    geminiAnalysis = "Tendance mercato stable.",
                    isLoading = false,
                    onRefresh = {},
                    onAnalyzeWithGemini = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gemini_mercato_hero_card").assertIsDisplayed()
    }

    @Test
    fun predictionsScreen_rendersCleanly() {
        val matches = listOf(sampleMatch())
        composeTestRule.setContent {
            FootIALiveTheme {
                PredictionsScreen(
                    matches = matches,
                    predictions = emptyMap(),
                    onMakePrediction = { _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Côte d'Ivoire").assertIsDisplayed()
        composeTestRule.onNodeWithText("N • Match Nul").assertIsDisplayed()
        composeTestRule.onNodeWithText("N (Mat)", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Côte 1.95", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Côte 3.20", substring = true).assertDoesNotExist()
    }

    @Test
    fun predictionsScreen_handlesScoreStepperAndCardClickNavigation() {
        val upcomingMatch = sampleMatch(
            id = "real_tonight",
            league = "LaLiga",
            home = "Real Madrid",
            away = "FC Barcelone",
            status = MatchStatus.UPCOMING
        )
        var clickedMatchId: String? = null
        var savedPrediction: Triple<PredictionChoice, Int, Int>? = null

        composeTestRule.setContent {
            FootIALiveTheme {
                PredictionsScreen(
                    matches = listOf(upcomingMatch),
                    predictions = emptyMap(),
                    onMakePrediction = { _, choice, hs, asScore ->
                        savedPrediction = Triple(choice, hs, asScore)
                    },
                    onMatchClick = { clickedMatchId = it.id }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap Draw button -> saves consistent 1-1 draw score instead of broken 2-1
        composeTestRule.onNodeWithTag("pred_btn_draw_real_tonight").performClick()
        composeTestRule.waitForIdle()
        assertTrue(savedPrediction == Triple(PredictionChoice.DRAW, 1, 1))

        // Tap home score +1 -> updates score to 2-1 and flips choice to HOME_WIN
        composeTestRule.onNodeWithTag("pred_home_plus_real_tonight").performClick()
        composeTestRule.waitForIdle()
        assertTrue(savedPrediction == Triple(PredictionChoice.HOME_WIN, 2, 1))

        // Tap the match header area -> opens match detail cleanly
        composeTestRule.onNodeWithTag("prediction_match_header_real_tonight").performClick()
        composeTestRule.waitForIdle()
        assertTrue(clickedMatchId == "real_tonight")
    }
}
