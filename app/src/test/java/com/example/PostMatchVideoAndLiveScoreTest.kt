package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.ai.AiPostMatchVideoSummaryState
import com.example.data.ai.AiVideoHighlightClip
import com.example.data.ai.GeminiCommentatorRepository
import com.example.data.model.*
import com.example.data.network.FootballNewsApiService
import com.example.ui.screens.MatchDetailScreen
import com.example.ui.theme.FootIALiveTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PostMatchVideoAndLiveScoreTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun finishedSampleMatch(id: String = "espn_fin_1"): Match {
        return Match(
            id = id,
            league = "LaLiga",
            homeTeam = "Real Madrid",
            homeTeamLogo = "",
            awayTeam = "FC Barcelone",
            awayTeamLogo = "",
            homeScore = 3,
            awayScore = 2,
            minute = 90,
            status = MatchStatus.FINISHED,
            date = "Hier (13 nov)",
            time = "21:00 (FR)",
            stadium = "Santiago Bernabéu",
            referee = "Arbitre",
            stats = MatchStats(52, 48, 16, 11, 7, 5, 510, 480, 88, 86, 9, 12, 2, 3, 0, 1, 2, 1),
            events = listOf(
                MatchEvent("e1", 18, EventType.GOAL, true, "Vinícius Jr", "Frappe enroulée"),
                MatchEvent("e2", 62, EventType.RED_CARD, false, "R. Araújo", "Expulsion directe"),
                MatchEvent("e3", 89, EventType.GOAL, true, "J. Bellingham", "But victorieux")
            ),
            lineup = MatchLineup("4-3-3", "4-3-3", emptyList(), emptyList(), emptyList(), emptyList()),
            aiCommentaries = listOf(AICommentary("c1", 90, "Score final 3-2", "INTENSE"))
        )
    }

    @Test
    fun parseEspnScoreboardEvents_parsesCompetitionStatusScoresLogosAndRealHighlights() {
        val refNow = FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T20:00:00Z")
        val json = JSONObject(
            """
            {
              "leagues": [{ "name": "Premier League" }],
              "events": [
                {
                  "id": "live_777",
                  "date": "2026-11-14T19:00Z",
                  "season": { "slug": "english-premier-league" },
                  "status": { "clock": 0.0, "displayClock": "0'", "type": { "state": "pre" } },
                  "competitions": [
                    {
                      "date": "2026-11-14T19:00Z",
                      "status": {
                        "clock": 2700.0,
                        "displayClock": "45'+2'",
                        "type": { "state": "in", "name": "STATUS_IN_PROGRESS", "shortDetail": "45'+2'" }
                      },
                      "venue": { "fullName": "Emirates Stadium" },
                      "competitors": [
                        {
                          "homeAway": "home",
                          "score": { "displayValue": "2" },
                          "team": {
                            "id": "359",
                            "displayName": "Arsenal",
                            "logo": "",
                            "logos": [{ "href": "https://a.espncdn.com/i/teamlogos/soccer/500/359.png" }]
                          }
                        },
                        {
                          "homeAway": "away",
                          "score": "1",
                          "team": {
                            "id": "99999",
                            "displayName": "Unknown Club FC",
                            "logo": ""
                          }
                        }
                      ],
                      "details": [
                        {
                          "scoringPlay": true,
                          "team": { "id": "359" },
                          "clock": { "displayValue": "33'" },
                          "athletesInvolved": [{ "displayName": "B. Saka" }],
                          "type": { "text": "But" }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val parsed = FootballNewsApiService.parseEspnScoreboardEvents(json, "ALL", refNow)
        assertEquals(1, parsed.size)
        val match = parsed[0].second

        assertEquals(MatchStatus.LIVE, match.status)
        assertEquals(2, match.homeScore)
        assertEquals(1, match.awayScore)
        assertEquals(47, match.minute)
        assertEquals("Premier League", match.league)
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/359.png", match.homeTeamLogo)
        // Must NEVER default to PSG (85.png) or OM (81.png) logos
        assertEquals("", match.awayTeamLogo)
        assertFalse(match.awayTeamLogo.contains("81.png"))
        assertFalse(match.homeTeamLogo.contains("85.png"))
        // Dynamic highlights must reflect real goal scorer instead of Barcola/Dembele
        assertEquals(1, match.videoHighlights.size)
        assertTrue(match.videoHighlights[0].title.contains("B. Saka"))
    }

    @Test
    fun parsePostMatchHighlightsJson_ordersClipsAndValidatesCategories() {
        val repo = GeminiCommentatorRepository()
        val match = finishedSampleMatch()
        val json = """
            {
              "summaryHeadline": "Le Real Madrid arrache la victoire 3-2 face au Barça grâce à Bellingham à la 89e.",
              "clips": [
                { "minute": 89, "category": "GOAL", "title": "But victorieux de Bellingham", "reason": "Frappe décisive en fin de match", "timestampSeconds": 12 },
                { "minute": 18, "category": "GOAL", "title": "Ouverture du score de Vinícius Jr", "reason": "Lance le match", "timestampSeconds": 3 },
                { "minute": 62, "category": "RED_CARD", "title": "Expulsion d'Araújo", "reason": "Tournant numérique du match", "timestampSeconds": 8 }
              ]
            }
        """.trimIndent()

        val result = repo.parsePostMatchHighlightsJson(match.id, json, match)
        assertTrue(result is AiPostMatchVideoSummaryState.Success)
        val success = result as AiPostMatchVideoSummaryState.Success
        assertEquals(listOf(18, 62, 89), success.clips.map { it.minute })
        assertEquals("GOAL", success.clips[0].category)
        assertEquals("RED_CARD", success.clips[1].category)

        val err = repo.parsePostMatchHighlightsJson(match.id, "invalid json", match)
        assertTrue(err is AiPostMatchVideoSummaryState.Error)
    }

    @Test
    fun matchDetailScreen_triggersGeminiSummaryAutomaticallyOnFinishedMatchTapAndFiltersClips() {
        val match = finishedSampleMatch()
        var triggeredMatchId: String? = null
        val readyState = AiPostMatchVideoSummaryState.Success(
            matchId = match.id,
            summaryHeadline = "Victoire renversante 3-2 du Real Madrid.",
            clips = listOf(
                AiVideoHighlightClip(18, "GOAL", "But de Vinícius Jr", "Ouverture du score", 3),
                AiVideoHighlightClip(62, "RED_CARD", "Carton rouge Araújo", "Faute de dernier défenseur", 8)
            )
        )

        composeTestRule.setContent {
            FootIALiveTheme {
                MatchDetailScreen(
                    match = match,
                    allMatches = listOf(match),
                    onBack = {},
                    onAskAI = { _, _ -> "" },
                    onGenerateDebrief = { "" },
                    postMatchSummaryState = readyState,
                    onAnalyzeFinishedHighlights = { m ->
                        triggeredMatchId = m.id
                        readyState
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(match.id, triggeredMatchId)
        composeTestRule.onNodeWithTag("post_match_ai_summary_card").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("But de Vinícius Jr").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carton rouge Araújo").assertIsDisplayed()

        // Filter to goals only
        composeTestRule.onNodeWithTag("post_match_filter_⚽ Buts").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("But de Vinícius Jr").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carton rouge Araújo").assertDoesNotExist()
    }

    @Test
    fun matchDetailScreen_displaysCircularPossessionChartAndInteractiveTooltip() {
        val liveMatch = finishedSampleMatch().copy(
            id = "espn_live_pos",
            status = MatchStatus.LIVE,
            minute = 74,
            stats = MatchStats(63, 37, 14, 7, 6, 3, 520, 310, 89, 78, 8, 11, 1, 2, 0, 0, 1, 2)
        )

        composeTestRule.setContent {
            FootIALiveTheme {
                MatchDetailScreen(
                    match = liveMatch,
                    allMatches = listOf(liveMatch),
                    onBack = {},
                    onAskAI = { _, _ -> "" },
                    onGenerateDebrief = { "" }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Statistiques tab
        composeTestRule.onNodeWithText("Statistiques").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("live_possession_pie_chart_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("possession_pie_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithText("63%").assertIsDisplayed()
        composeTestRule.onNodeWithText("37%").assertIsDisplayed()

        // Tap home legend to reveal tooltip with pass accuracy
        composeTestRule.onNodeWithTag("possession_legend_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("possession_tooltip_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Real Madrid • 63% de possession").assertIsDisplayed()
        composeTestRule.onNodeWithText("520 passes • 89% précision").assertIsDisplayed()
    }

    @Test
    fun parseEspnScoreboardEvents_promotesElapsedKickoffToLiveAndRespectsPostponed() {
        val refNow = FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T20:25:00Z")
        val payload = JSONObject(
            """
            {
              "events": [
                {
                  "id": "lagging_live_1",
                  "date": "2026-11-14T20:00:00Z",
                  "season": { "slug": "spanish-laliga" },
                  "competitions": [
                    {
                      "date": "2026-11-14T20:00:00Z",
                      "status": { "clock": 0.0, "displayClock": "0'", "type": { "state": "pre", "name": "STATUS_SCHEDULED" } },
                      "competitors": [
                        { "homeAway": "home", "score": "1", "team": { "id": "86", "displayName": "Real Madrid", "logo": "" } },
                        { "homeAway": "away", "score": "0", "team": { "id": "90", "displayName": "Real Betis", "logo": "" } }
                      ]
                    }
                  ]
                },
                {
                  "id": "postponed_1",
                  "date": "2026-11-14T20:00:00Z",
                  "season": { "slug": "french-ligue-1" },
                  "competitions": [
                    {
                      "date": "2026-11-14T20:00:00Z",
                      "status": { "clock": 0.0, "displayClock": "0'", "type": { "state": "pre", "name": "STATUS_POSTPONED" } },
                      "competitors": [
                        { "homeAway": "home", "score": "0", "team": { "id": "85", "displayName": "Paris Saint-Germain", "logo": "" } },
                        { "homeAway": "away", "score": "0", "team": { "id": "81", "displayName": "Marseille", "logo": "" } }
                      ]
                    }
                  ]
                },
                {
                  "id": "halftime_lag_1",
                  "date": "2026-11-14T19:33:00Z",
                  "season": { "slug": "english-premier-league" },
                  "competitions": [
                    {
                      "date": "2026-11-14T19:33:00Z",
                      "status": { "clock": 0.0, "displayClock": "", "type": { "state": "pre", "name": "STATUS_SCHEDULED" } },
                      "competitors": [
                        { "homeAway": "home", "score": "2", "team": { "id": "359", "displayName": "Arsenal", "logo": "" } },
                        { "homeAway": "away", "score": "1", "team": { "id": "363", "displayName": "Chelsea", "logo": "" } }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val parsed = FootballNewsApiService.parseEspnScoreboardEvents(payload, "ALL", refNow).map { it.second }
        assertEquals(3, parsed.size)

        val realMatch = parsed.first { it.id == "espn_lagging_live_1" }
        assertEquals(MatchStatus.LIVE, realMatch.status)
        assertEquals(25, realMatch.minute)
        assertTrue(realMatch.kickoffMillis > 0L)

        val postponedMatch = parsed.first { it.id == "espn_postponed_1" }
        assertEquals(MatchStatus.UPCOMING, postponedMatch.status)

        val htMatch = parsed.first { it.id == "espn_halftime_lag_1" }
        assertEquals(MatchStatus.LIVE, htMatch.status)
        assertEquals(45, htMatch.minute)
    }

    @Test
    fun parseEspnTeamScheduleOrNextEvents_keepsTonightRealMadridAndHydratesSummaryStats() {
        val refNow = FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T18:00:00Z")
        val scheduleJson = JSONObject(
            """
            {
              "events": [
                {
                  "id": "old_match_1",
                  "date": "2026-09-01T19:00:00Z",
                  "season": { "slug": "spanish-laliga" },
                  "competitions": [
                    {
                      "date": "2026-09-01T19:00:00Z",
                      "status": { "type": { "state": "post", "completed": true, "name": "STATUS_FINAL" } },
                      "competitors": [
                        { "homeAway": "home", "score": { "displayValue": "2" }, "team": { "id": "86", "displayName": "Real Madrid" } },
                        { "homeAway": "away", "score": { "displayValue": "0" }, "team": { "id": "88", "displayName": "Getafe" } }
                      ]
                    }
                  ]
                },
                {
                  "id": "real_tonight_86",
                  "date": "2026-11-14T20:00:00Z",
                  "season": { "slug": "spanish-laliga" },
                  "competitions": [
                    {
                      "date": "2026-11-14T20:00:00Z",
                      "status": { "type": { "state": "pre", "name": "STATUS_SCHEDULED" } },
                      "competitors": [
                        { "homeAway": "home", "score": { "displayValue": "0" }, "team": { "id": "86", "displayName": "Real Madrid" } },
                        { "homeAway": "away", "score": { "displayValue": "0" }, "team": { "id": "83", "displayName": "Barcelona" } }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val filtered = FootballNewsApiService.parseEspnTeamScheduleOrNextEvents(scheduleJson, "LaLiga", refNow)
        assertEquals(1, filtered.size)
        val tonightMatch = filtered[0].second
        assertEquals("espn_real_tonight_86", tonightMatch.id)
        assertEquals("Real Madrid", tonightMatch.homeTeam)
        assertEquals("21:00 (FR)", tonightMatch.time)

        val summaryJson = JSONObject(
            """
            {
              "header": {
                "competitions": [
                  {
                    "date": "2026-11-14T20:00:00Z",
                    "status": { "clock": 3840.0, "displayClock": "64'", "type": { "state": "in", "name": "STATUS_SECOND_HALF" } },
                    "competitors": [
                      { "homeAway": "home", "score": "2", "team": { "id": "86" } },
                      { "homeAway": "away", "score": "1", "team": { "id": "83" } }
                    ]
                  }
                ]
              },
              "boxscore": {
                "teams": [
                  { "homeAway": "home", "statistics": [{ "name": "possessionPct", "displayValue": "62%" }, { "name": "totalShots", "displayValue": "15" }] },
                  { "homeAway": "away", "statistics": [{ "name": "possessionPct", "displayValue": "38%" }, { "name": "totalShots", "displayValue": "8" }] }
                ]
              }
            }
            """.trimIndent()
        )

        val hydrated = FootballNewsApiService.hydrateMatchWithEspnSummary(
            summaryJson,
            tonightMatch,
            FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T21:19:00Z")
        )
        assertEquals(MatchStatus.LIVE, hydrated.status)
        assertEquals(64, hydrated.minute)
        assertEquals(2, hydrated.homeScore)
        assertEquals(1, hydrated.awayScore)
        assertEquals(62, hydrated.stats.possessionHome)
        assertEquals(38, hydrated.stats.possessionAway)
        assertEquals(15, hydrated.stats.shotsHome)
    }
}
