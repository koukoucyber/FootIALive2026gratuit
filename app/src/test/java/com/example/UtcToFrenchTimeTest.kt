package com.example

import com.example.data.network.FootballNewsApiService
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UtcToFrenchTimeTest {

    private lateinit var originalDefaultTz: TimeZone

    @Before
    fun saveDefaultTimeZone() {
        originalDefaultTz = TimeZone.getDefault()
        // Simulate an emulator/device set to US Pacific time (UTC-8) to ensure conversion never depends on device TZ
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
    }

    @After
    fun restoreDefaultTimeZone() {
        TimeZone.setDefault(originalDefaultTz)
    }

    @Test
    fun convertUtcToFrenchDateTime_handlesWinterTimeCetUtcPlus1() {
        val refNov14NoonUtc = FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T12:00:00Z")

        val eveningMatch = FootballNewsApiService.convertUtcToFrenchDateTime(
            utcDateTime = "2026-11-14T20:00Z",
            referenceNowMillis = refNov14NoonUtc
        )

        assertEquals("21:00 (FR)", eveningMatch.timeLabel)
        assertTrue(eveningMatch.dateLabel.startsWith("Aujourd'hui"))
        assertEquals("2026-11-14", eveningMatch.parisDateIso)
    }

    @Test
    fun convertUtcToFrenchDateTime_handlesSummerDstCestUtcPlus2() {
        val refJul14NoonUtc = FootballNewsApiService.parseUtcTimestampToMillis("2026-07-14T12:00:00Z")

        val eveningSummerMatch = FootballNewsApiService.convertUtcToFrenchDateTime(
            utcDateTime = "2026-07-14T19:00:00Z",
            referenceNowMillis = refJul14NoonUtc
        )

        assertEquals("21:00 (FR)", eveningSummerMatch.timeLabel)
        assertTrue(eveningSummerMatch.dateLabel.startsWith("Aujourd'hui"))
        assertEquals("2026-07-14", eveningSummerMatch.parisDateIso)
    }

    @Test
    fun convertUtcToFrenchDateTime_handlesMidnightUtcToParisDayRollover() {
        val refNov14AfternoonUtc = FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T15:00:00Z")

        // 23:30 UTC on Nov 14 is already 00:30 CET on Nov 15 ("Demain") in France
        val crossMidnightMatch = FootballNewsApiService.convertUtcToFrenchDateTime(
            utcDateTime = "2026-11-14T23:30:00Z",
            referenceNowMillis = refNov14AfternoonUtc
        )

        assertEquals("00:30 (FR)", crossMidnightMatch.timeLabel)
        assertTrue(crossMidnightMatch.dateLabel.startsWith("Demain"))
        assertEquals("2026-11-15", crossMidnightMatch.parisDateIso)
    }

    @Test
    fun convertUtcToFrenchDateTime_handlesOffsetsMicrosecondsAndUnzonedUtc() {
        val refNov14Utc = FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T12:00:00Z")

        val fromUsEastern = FootballNewsApiService.convertUtcToFrenchDateTime(
            utcDateTime = "2026-11-14T15:00:00-05:00",
            referenceNowMillis = refNov14Utc
        )
        assertEquals("21:00 (FR)", fromUsEastern.timeLabel)

        val fromMicroseconds = FootballNewsApiService.convertUtcToFrenchDateTime(
            utcDateTime = "2026-11-14T20:00:00.123456Z",
            referenceNowMillis = refNov14Utc
        )
        assertEquals("21:00 (FR)", fromMicroseconds.timeLabel)

        val fromNaiveUtc = FootballNewsApiService.convertUtcToFrenchDateTime(
            utcDateTime = "2026-11-14T20:00:00",
            referenceNowMillis = refNov14Utc
        )
        assertEquals("21:00 (FR)", fromNaiveUtc.timeLabel)
    }

    @Test
    fun parseEspnScoreboardEvents_convertsUtcMatchTimeToFrenchLocalTime() {
        val refNov14Utc = FootballNewsApiService.parseUtcTimestampToMillis("2026-11-14T14:00:00Z")
        val espnPayload = JSONObject(
            """
            {
              "events": [
                {
                  "id": "999001",
                  "date": "2026-11-14T20:00Z",
                  "season": { "slug": "spanish-laliga" },
                  "status": {
                    "clock": 0.0,
                    "displayClock": "0'",
                    "type": { "state": "pre" }
                  },
                  "competitions": [
                    {
                      "date": "2026-11-14T20:00Z",
                      "venue": { "fullName": "Estadio Santiago Bernabéu" },
                      "competitors": [
                        {
                          "homeAway": "home",
                          "score": "0",
                          "team": { "id": "86", "displayName": "Real Madrid", "logo": "" }
                        },
                        {
                          "homeAway": "away",
                          "score": "0",
                          "team": { "id": "83", "displayName": "Barcelona", "logo": "" }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val parsed = FootballNewsApiService.parseEspnScoreboardEvents(
            json = espnPayload,
            leagueName = "ALL",
            referenceNowMillis = refNov14Utc
        )

        assertEquals(1, parsed.size)
        val match = parsed[0].second
        assertEquals("21:00 (FR)", match.time)
        assertTrue(match.date.startsWith("Aujourd'hui"))
        assertEquals("LaLiga", match.league)
    }
}
