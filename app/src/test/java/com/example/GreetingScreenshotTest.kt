package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.*
import com.example.ui.components.MatchCard
import com.example.ui.theme.FootIALiveTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun match_card_screenshot() {
    val sampleMatch = Match(
      id = "m_preview",
      league = "Qualifications CAN 2027",
      homeTeam = "Côte d'Ivoire",
      homeTeamLogo = "",
      awayTeam = "Gambie",
      awayTeamLogo = "",
      homeScore = 2,
      awayScore = 1,
      minute = 68,
      status = MatchStatus.LIVE,
      date = "Aujourd'hui",
      time = "19:00",
      stadium = "Stade de la Paix, Bouaké",
      referee = "M. Ndala",
      stats = MatchStats(58, 42, 14, 7, 6, 3, 490, 310, 86, 78, 9, 12, 1, 2, 0, 0, 2, 1),
      events = emptyList(),
      lineup = MatchLineup("4-3-3", "4-2-3-1", emptyList(), emptyList(), emptyList(), emptyList()),
      aiCommentaries = emptyList(),
      broadcastChannel = "La 3 (RTI) & NCI"
    )

    composeTestRule.setContent {
      FootIALiveTheme(darkTheme = true) {
        MatchCard(match = sampleMatch, onClick = {})
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
