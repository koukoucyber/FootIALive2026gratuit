package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.ai.AiMatchSearchState
import com.example.data.ai.GeminiCommentatorRepository
import com.example.data.model.*
import com.example.ui.screens.MatchesScreen
import com.example.ui.theme.FootIALiveTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class MatchAiSearchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun fixtureMatch(
        id: String,
        league: String,
        home: String,
        away: String,
        status: MatchStatus,
        date: String = "Aujourd'hui (14 nov)",
        time: String = "21:00 (FR)",
        homeScore: Int = 0,
        awayScore: Int = 0,
        minute: Int = 0,
        channel: String = "Canal+"
    ): Match {
        return Match(
            id = id,
            league = league,
            homeTeam = home,
            homeTeamLogo = "",
            awayTeam = away,
            awayTeamLogo = "",
            homeScore = homeScore,
            awayScore = awayScore,
            minute = minute,
            status = status,
            date = date,
            time = time,
            stadium = "Stade Officiel",
            referee = "Arbitre",
            stats = MatchStats(50, 50, 5, 5, 2, 2, 300, 300, 80, 80, 8, 8, 1, 1, 0, 0, 1, 1),
            events = emptyList(),
            lineup = MatchLineup("4-3-3", "4-3-3", emptyList(), emptyList(), emptyList(), emptyList()),
            aiCommentaries = emptyList(),
            broadcastChannel = channel
        )
    }

    @Test
    fun buildMatchCatalogForAi_includesRealMatchIdsAndFrenchTime() {
        val repo = GeminiCommentatorRepository()
        val matches = listOf(
            fixtureMatch("espn_real", "LaLiga", "Real Madrid", "Sevilla", MatchStatus.UPCOMING, time = "21:00 (FR)", channel = "beIN Sports 1"),
            fixtureMatch("espn_can", "CAN", "Burkina Faso", "Mauritanie", MatchStatus.LIVE, homeScore = 1, awayScore = 0, minute = 64, channel = "La 3")
        )

        val catalog = repo.buildMatchCatalogForAi(matches)
        assertTrue(catalog.contains("espn_real | LaLiga | Real Madrid vs Sevilla | UPCOMING"))
        assertTrue(catalog.contains("21:00 (FR)"))
        assertTrue(catalog.contains("beIN Sports 1"))
        assertTrue(catalog.contains("espn_can | CAN | Burkina Faso 1-0 Mauritanie | LIVE (64')"))
    }

    @Test
    fun parseAiMatchSearchJson_filtersHallucinatedIdsAndPreservesOrder() {
        val repo = GeminiCommentatorRepository()
        val validIds = setOf("espn_1", "espn_2", "espn_3")
        val json = """
            {
              "matchingIds": ["fake_id_999", "espn_3", "espn_1", "espn_3"],
              "suggestedLeague": "LaLiga",
              "suggestedStatus": "UPCOMING",
              "aiExplanation": "2 matchs trouvés correspondant à votre recherche."
            }
        """.trimIndent()

        val state = repo.parseAiMatchSearchJson("Real ce soir", json, validIds)
        assertTrue(state is AiMatchSearchState.Success)
        val success = state as AiMatchSearchState.Success
        assertEquals(listOf("espn_3", "espn_1"), success.matchingIds)
        assertEquals("LaLiga", success.suggestedLeague)
        assertEquals("UPCOMING", success.suggestedStatus)
        assertEquals("2 matchs trouvés correspondant à votre recherche.", success.explanation)
    }

    @Test
    fun parseAiMatchSearchJson_returnsErrorOnInvalidJson() {
        val repo = GeminiCommentatorRepository()
        val state = repo.parseAiMatchSearchJson("Real", "not-valid-json", setOf("espn_1"))
        assertTrue(state is AiMatchSearchState.Error)
    }

    @Test
    fun matchesScreen_rendersAiFilteringStatesAndNoFakeSimulation() {
        val matches = listOf(
            fixtureMatch("espn_real", "LaLiga", "Real Madrid", "Sevilla", MatchStatus.UPCOMING),
            fixtureMatch("espn_psg", "Ligue 1", "Paris SG", "Nice", MatchStatus.LIVE, homeScore = 2, awayScore = 1, minute = 71)
        )
        var capturedAiQuery = ""

        composeTestRule.setContent {
            FootIALiveTheme {
                MatchesScreen(
                    matches = matches,
                    onMatchClick = {},
                    aiMatchSearchState = AiMatchSearchState.Success(
                        query = "Real ce soir",
                        matchingIds = listOf("espn_real"),
                        explanation = "Match du Real Madrid prévu ce soir à 21:00 (FR)."
                    ),
                    externalSearchQuery = "Real ce soir",
                    onSearchMatchesWithAI = { capturedAiQuery = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Fake simulation button must never be shown
        composeTestRule.onNodeWithText("Simuler évènement IA").assertDoesNotExist()

        // AI result card is displayed and only the AI-matched fixture is shown
        composeTestRule.onNodeWithTag("ai_search_result_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Match du Real Madrid prévu ce soir à 21:00 (FR).").assertIsDisplayed()
        composeTestRule.onNodeWithText("Real Madrid").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paris SG").assertDoesNotExist()

        // Tapping quick AI chip triggers onSearchMatchesWithAI
        composeTestRule.onNodeWithText("🔴 Directs").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Matchs actuellement en direct", capturedAiQuery)
    }

    @Test
    fun matchesScreen_rendersAiLoadingAndErrorStates() {
        val matches = listOf(fixtureMatch("espn_1", "Ligue 1", "Lyon", "Lens", MatchStatus.LIVE, homeScore = 1, awayScore = 1, minute = 55))

        composeTestRule.setContent {
            FootIALiveTheme {
                MatchesScreen(
                    matches = matches,
                    onMatchClick = {},
                    aiMatchSearchState = AiMatchSearchState.Error(
                        query = "matchs ligue 1",
                        message = "Service Gemini indisponible"
                    )
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_search_error_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Service Gemini indisponible").assertIsDisplayed()
    }
}
