package com.example.data.network

import android.os.SystemClock
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.xml.parsers.DocumentBuilderFactory

object FootballNewsApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
    private val parisTimeZone: TimeZone = TimeZone.getTimeZone("Europe/Paris")
    private val serverEpochAtSync = AtomicLong(0L)
    private val elapsedAtSync = AtomicLong(0L)

    data class FrenchLocalDateTime(
        val utcEpochMillis: Long,
        val dateLabel: String,
        val timeLabel: String,
        val parisDateIso: String
    )

    fun currentParisNowMillis(): Long {
        val synced = serverEpochAtSync.get()
        if (synced > 0L) {
            return try {
                synced + (SystemClock.elapsedRealtime() - elapsedAtSync.get()).coerceAtLeast(0L)
            } catch (_: Throwable) {
                synced
            }
        }
        return System.currentTimeMillis()
    }

    fun getCurrentParisTimeFormatted(): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.FRENCH).apply { timeZone = parisTimeZone }
        return "${fmt.format(Date(currentParisNowMillis()))} (FR)"
    }

    fun getCurrentParisCalendar(): Calendar {
        return Calendar.getInstance(parisTimeZone).apply { timeInMillis = currentParisNowMillis() }
    }

    private data class RssEntry(
        val title: String,
        val description: String,
        val link: String,
        val pubDateRaw: String,
        val epochMillis: Long,
        val formattedDate: String,
        val imageUrl: String
    )

    /**
     * Fetches real-time football headlines from ESPN's public soccer news API.
     */
    suspend fun fetchEspnSoccerNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val articles = mutableListOf<NewsArticle>()
        try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/all/news"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android FootIALive App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        val articlesArray = json.optJSONArray("articles")
                        if (articlesArray != null) {
                            for (i in 0 until articlesArray.length()) {
                                val item = articlesArray.getJSONObject(i)
                                val headline = item.optString("headline", "")
                                val description = item.optString("description", "")
                                val published = item.optString("published", "")
                                val byline = item.optString("byline", "ESPN Football")

                                val images = item.optJSONArray("images")
                                val imageUrl = if (images != null && images.length() > 0) {
                                    images.getJSONObject(0).optString("url", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800")
                                } else {
                                    "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800"
                                }

                                val category = when {
                                    headline.contains("transfer", ignoreCase = true) || headline.contains("deal", ignoreCase = true) -> "Transferts"
                                    headline.contains("league", ignoreCase = true) || headline.contains("champions", ignoreCase = true) -> "Champions League"
                                    headline.contains("psg", ignoreCase = true) || headline.contains("ligue", ignoreCase = true) -> "Ligue 1"
                                    else -> "International"
                                }

                                if (headline.isNotBlank()) {
                                    val (_, fmtDate) = parseFlexibleDate(published, "")
                                    articles.add(
                                        NewsArticle(
                                            id = "espn_${i}_${headline.hashCode()}",
                                            title = headline,
                                            category = category,
                                            summary = description.ifBlank { headline },
                                            content = "$description\n\nArticle complet publié par $byline via le flux API public ESPN Soccer.",
                                            author = byline.ifBlank { "ESPN Sports" },
                                            date = fmtDate,
                                            readTime = "3 min",
                                            imageUrl = imageUrl
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        articles
    }

    /**
     * Fetches public French football headlines directly from RMC Sport RSS XML feed.
     */
    suspend fun fetchFrenchFootballNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val articles = mutableListOf<NewsArticle>()
        try {
            val xml = fetchUrlString("https://rmcsport.bfmtv.com/rss/football/")
            if (!xml.isNullOrBlank()) {
                val entries = parseRssXml(xml, defaultImage = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=800")
                entries.forEachIndexed { i, entry ->
                    val category = when {
                        entry.title.contains("mercato", ignoreCase = true) || entry.title.contains("transfert", ignoreCase = true) -> "Transferts"
                        entry.title.contains("ligue 1", ignoreCase = true) || entry.title.contains("psg", ignoreCase = true) || entry.title.contains("om", ignoreCase = true) -> "Ligue 1"
                        entry.title.contains("can", ignoreCase = true) || entry.title.contains("afrique", ignoreCase = true) -> "CAN 2027"
                        entry.title.contains("champions", ignoreCase = true) -> "Champions League"
                        else -> "Football Actu"
                    }
                    articles.add(
                        NewsArticle(
                            id = "rmc_${i}_${entry.title.hashCode()}",
                            title = entry.title,
                            category = category,
                            summary = entry.description.take(180).ifEmpty { entry.title },
                            content = "${entry.description}\n\nRetrouvez tous les détails en direct sur RMC Sport.",
                            author = "RMC Sport",
                            date = entry.formattedDate,
                            readTime = "2 min",
                            imageUrl = entry.imageUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        articles
    }

    private fun fetchUrlString(url: String): String? {
        return try {
            val isLiveEndpoint = url.contains("espn.com") && (
                url.contains("scoreboard") ||
                    url.contains("summary") ||
                    url.contains("schedule") ||
                    url.contains("/teams/")
                )
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; FootIALive) AppleWebKit/537.36")
                .header("Accept", "application/rss+xml, application/xml, text/xml, application/json, */*")
                .header("Cache-Control", "no-cache, no-store, max-age=0")
                .header("Pragma", "no-cache")
            if (isLiveEndpoint) {
                requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
            }
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val dateHeader = response.header("Date")
                if (!dateHeader.isNullOrBlank()) {
                    try {
                        val rfcFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                            timeZone = utcTimeZone
                        }
                        val serverDate = rfcFmt.parse(dateHeader.trim())
                        if (serverDate != null) {
                            serverEpochAtSync.set(serverDate.time)
                            try {
                                elapsedAtSync.set(SystemClock.elapsedRealtime())
                            } catch (_: Throwable) {
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseRssXml(xml: String, defaultImage: String): List<RssEntry> {
        val list = mutableListOf<RssEntry>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml.trim())))

            val channelBuildDate = doc.getElementsByTagName("lastBuildDate").let { nodes ->
                if (nodes.length > 0) nodes.item(0).textContent?.trim() ?: "" else ""
            }

            val items = doc.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val elem = items.item(i) as? Element ?: continue
                val rawTitle = getChildText(elem, "title")
                val title = cleanHtml(rawTitle)
                    .removeSuffix(" - Le Parisien")
                    .removeSuffix(" - Le Monde.fr")
                    .removeSuffix(" - Foot Mercato")
                    .trim()
                if (title.isBlank()) continue

                val link = getChildText(elem, "link").trim()
                val rawDesc = getChildText(elem, "description")
                val description = cleanHtml(rawDesc)
                val pubDateRaw = getChildText(elem, "pubDate").ifBlank {
                    getChildText(elem, "updated").ifBlank { channelBuildDate }
                }

                val (epoch, formattedDate) = parseFlexibleDate(pubDateRaw, link)
                val imgFromTag = extractImageFromElement(elem, rawDesc) ?: defaultImage

                list.add(
                    RssEntry(
                        title = title,
                        description = description,
                        link = link,
                        pubDateRaw = pubDateRaw,
                        epochMillis = epoch,
                        formattedDate = formattedDate,
                        imageUrl = imgFromTag
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun getChildText(parent: Element, tagName: String): String {
        val nodes = parent.getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0).textContent ?: "" else ""
    }

    private fun extractImageFromElement(elem: Element, rawDesc: String): String? {
        val enc = elem.getElementsByTagName("enclosure")
        if (enc.length > 0) {
            val url = (enc.item(0) as? Element)?.getAttribute("url")
            if (!url.isNullOrBlank()) return url
        }
        val media = elem.getElementsByTagName("media:content")
        if (media.length > 0) {
            val url = (media.item(0) as? Element)?.getAttribute("url")
            if (!url.isNullOrBlank()) return url
        }
        val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val match = imgRegex.find(rawDesc)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.startsWith("http") }
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&amp;", "&")
            .replace("&euro;", "€")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseFlexibleDate(raw: String, linkUrl: String): Pair<Long, String> {
        val trimmed = raw.trim()
        var parsedMillis = parseUtcTimestampToMillis(trimmed).takeIf { it != Long.MAX_VALUE }

        if (parsedMillis == null && trimmed.isNotEmpty()) {
            val formats = listOf(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z"
            )
            for (pattern in formats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                        timeZone = utcTimeZone
                    }
                    val d = sdf.parse(trimmed)
                    if (d != null) {
                        parsedMillis = d.time
                        break
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Try extracting -DD-MM-YYYY- from Le Parisien URLs if pubDate was missing
        if (parsedMillis == null && linkUrl.isNotEmpty()) {
            val urlDateMatch = Regex("""-(\d{2})-(\d{2})-(\d{4})-""").find(linkUrl)
            if (urlDateMatch != null) {
                try {
                    val day = urlDateMatch.groupValues[1].toInt()
                    val month = urlDateMatch.groupValues[2].toInt() - 1
                    val year = urlDateMatch.groupValues[3].toInt()
                    val cal = Calendar.getInstance(parisTimeZone).apply {
                        set(year, month, day, 12, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    parsedMillis = cal.timeInMillis
                } catch (_: Exception) {
                }
            }
        }

        if (parsedMillis == null) {
            return currentParisNowMillis() to "En direct"
        }

        val parsedDate = Date(parsedMillis)
        val nowCal = getCurrentParisCalendar()
        val itemCal = Calendar.getInstance(parisTimeZone).apply { timeInMillis = parsedMillis }

        val timeFmt = SimpleDateFormat("HH'h'mm", Locale.FRENCH).apply { timeZone = parisTimeZone }
        val dayFmt = SimpleDateFormat("dd MMM", Locale.FRENCH).apply { timeZone = parisTimeZone }

        val isToday = nowCal.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                nowCal.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)

        val yesterdayCal = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterdayCal.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                yesterdayCal.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)

        val formatted = when {
            isToday -> "Aujourd'hui • ${timeFmt.format(parsedDate)}"
            isYesterday -> "Hier • ${timeFmt.format(parsedDate)}"
            else -> "${dayFmt.format(parsedDate)} • ${timeFmt.format(parsedDate)}"
        }

        return parsedMillis to formatted
    }

    /**
     * Parses an ISO-8601 UTC (or offset-aware) timestamp into exact UTC epoch milliseconds.
     * Unzoned timestamps default strictly to UTC rather than the device's local timezone.
     */
    fun parseUtcTimestampToMillis(utcDateTime: String): Long {
        val trimmed = utcDateTime.trim()
        if (trimmed.isBlank()) return Long.MAX_VALUE

        val isoRegex = Regex(
            """^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d+))?)?(Z|[+-]\d{2}:?\d{2})?$"""
        )
        val match = isoRegex.matchEntire(trimmed)
        if (match != null) {
            return try {
                val year = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val day = match.groupValues[3].toInt()
                val hourStr = match.groupValues[4]
                val hasTime = hourStr.isNotEmpty()
                val hour = if (hasTime) hourStr.toInt() else 12
                val minute = if (hasTime) match.groupValues[5].toInt() else 0
                val second = match.groupValues[6].toIntOrNull() ?: 0
                val fracRaw = match.groupValues[7]
                val millis = if (fracRaw.isNotEmpty()) {
                    fracRaw.take(3).padEnd(3, '0').toInt()
                } else 0

                val tzGroup = match.groupValues[8]
                val offsetMillis = when {
                    tzGroup.isEmpty() || tzGroup == "Z" -> 0L
                    else -> {
                        val sign = if (tzGroup.startsWith("-")) -1L else 1L
                        val digits = tzGroup.substring(1).replace(":", "")
                        val offHours = digits.substring(0, 2).toLong()
                        val offMins = digits.substring(2, 4).toLong()
                        sign * ((offHours * 60L) + offMins) * 60_000L
                    }
                }

                val cal = Calendar.getInstance(utcTimeZone).apply {
                    isLenient = false
                    clear()
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, second)
                    set(Calendar.MILLISECOND, millis)
                }
                cal.timeInMillis - offsetMillis
            } catch (_: Exception) {
                Long.MAX_VALUE
            }
        }

        val fallbackPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ"
        )
        for (p in fallbackPatterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.ENGLISH).apply {
                    timeZone = utcTimeZone
                }
                val d = sdf.parse(trimmed)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return Long.MAX_VALUE
    }

    /**
     * Converts any UTC (or offset) match timestamp into French local time (Europe/Paris),
     * automatically handling winter time (CET, UTC+1) and summer DST (CEST, UTC+2)
     * as well as midnight date rollovers between UTC and France.
     */
    fun convertUtcToFrenchDateTime(
        utcDateTime: String,
        referenceNowMillis: Long = currentParisNowMillis()
    ): FrenchLocalDateTime {
        val utcMillis = parseUtcTimestampToMillis(utcDateTime)
        if (utcMillis == Long.MAX_VALUE) {
            val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = parisTimeZone
            }.format(Date(referenceNowMillis))
            return FrenchLocalDateTime(
                utcEpochMillis = Long.MAX_VALUE,
                dateLabel = "Aujourd'hui",
                timeLabel = "À conf. (FR)",
                parisDateIso = todayIso
            )
        }

        val matchParisCal = Calendar.getInstance(parisTimeZone).apply { timeInMillis = utcMillis }
        val todayParisCal = Calendar.getInstance(parisTimeZone).apply { timeInMillis = referenceNowMillis }
        val yesterdayParisCal = (todayParisCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val tomorrowParisCal = (todayParisCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

        val shortDateFmt = SimpleDateFormat("dd MMM", Locale.FRENCH).apply { timeZone = parisTimeZone }
        val shortDayMonth = shortDateFmt.format(Date(utcMillis)).replace(".", "").trim()

        val dateLabel = when {
            matchParisCal.get(Calendar.YEAR) == todayParisCal.get(Calendar.YEAR) &&
                    matchParisCal.get(Calendar.DAY_OF_YEAR) == todayParisCal.get(Calendar.DAY_OF_YEAR) -> "Aujourd'hui ($shortDayMonth)"
            matchParisCal.get(Calendar.YEAR) == tomorrowParisCal.get(Calendar.YEAR) &&
                    matchParisCal.get(Calendar.DAY_OF_YEAR) == tomorrowParisCal.get(Calendar.DAY_OF_YEAR) -> "Demain ($shortDayMonth)"
            matchParisCal.get(Calendar.YEAR) == yesterdayParisCal.get(Calendar.YEAR) &&
                    matchParisCal.get(Calendar.DAY_OF_YEAR) == yesterdayParisCal.get(Calendar.DAY_OF_YEAR) -> "Hier ($shortDayMonth)"
            else -> {
                val fmt = SimpleDateFormat("EEE dd MMM", Locale.FRENCH).apply { timeZone = parisTimeZone }
                fmt.format(Date(utcMillis))
                    .replace(".", "")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
            }
        }

        val timeFmt = SimpleDateFormat("HH:mm", Locale.FRENCH).apply { timeZone = parisTimeZone }
        val timeLabel = "${timeFmt.format(Date(utcMillis))} (FR)"
        val parisDateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = parisTimeZone
        }.format(Date(utcMillis))

        return FrenchLocalDateTime(
            utcEpochMillis = utcMillis,
            dateLabel = dateLabel,
            timeLabel = timeLabel,
            parisDateIso = parisDateIso
        )
    }

    private fun parseIsoDateToMillis(isoDate: String): Long = parseUtcTimestampToMillis(isoDate)

    private fun formatMatchDate(isoDate: String): String = convertUtcToFrenchDateTime(isoDate).dateLabel

    private fun formatMatchTime(isoDate: String): String = convertUtcToFrenchDateTime(isoDate).timeLabel

    /**
     * Fetches ALL live, today, and upcoming matches across ALL supported leagues
     * from ESPN's open soccer scoreboard API (including next matchdays via calendar discovery
     * and international breaks).
     */
    suspend fun fetchRealMatchesFromEspn(): List<Match> = withContext(Dispatchers.IO) {
        val clubLeagues = listOf(
            "fra.1" to "Ligue 1",
            "eng.1" to "Premier League",
            "esp.1" to "LaLiga",
            "ger.1" to "Bundesliga",
            "ita.1" to "Serie A",
            "ksa.1" to "RSL (Saudi Pro League)",
            "esp.copa_del_rey" to "Copa del Rey",
            "esp.super_cup" to "Supercoupe d'Espagne",
            "esp.w.1" to "Liga F (F)",
            "fra.coupe_de_france" to "Coupe de France",
            "eng.fa" to "FA Cup"
        )
        val tournamentLeagues = listOf(
            "uefa.champions" to "Ligue des Champions",
            "uefa.europa" to "Ligue Europa",
            "uefa.conference" to "Conference League",
            "uefa.wchampions" to "Ligue des Champions (F)",
            "caf.nations_qual" to "Qualifications CAN 2027",
            "uefa.nations" to "Euro / Ligue des Nations",
            "fifa.worldq.uefa" to "Qualifs Coupe du Monde",
            "fifa.worldq.caf" to "Qualifs Coupe du Monde (CAF)",
            "fifa.worldq.conmebol" to "Qualifs Coupe du Monde (CONMEBOL)",
            "fifa.worldq.concacaf" to "Qualifs Coupe du Monde (CONCACAF)",
            "fifa.worldq.afc" to "Qualifs Coupe du Monde (AFC)",
            "club.friendly" to "Matchs Amicaux",
            "fifa.friendly" to "Matchs Amicaux"
        )

        val semaphore = Semaphore(10)
        val collected = mutableMapOf<String, Pair<Long, Match>>() // id -> (isoMillis, Match)

        coroutineScope {
            // 1. Fetch base scoreboards for all leagues in parallel & collect raw UTC calendar entries
            val baseJobs = (clubLeagues + tournamentLeagues).map { (code, leagueName) ->
                async {
                    semaphore.withPermit {
                        val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$code/scoreboard"
                        val body = fetchUrlString(url) ?: return@withPermit emptyList<String>()
                        val json = try {
                            JSONObject(body)
                        } catch (_: Exception) {
                            return@withPermit emptyList<String>()
                        }

                        val baseMatches = parseEspnScoreboardEvents(json, leagueName)
                        synchronized(collected) {
                            baseMatches.forEach { (ms, m) -> mergePreferredMatch(collected, ms, m) }
                        }

                        val rawCalendarEntries = mutableListOf<String>()
                        val leaguesArr = json.optJSONArray("leagues")
                        if (leaguesArr != null && leaguesArr.length() > 0) {
                            val calArr = leaguesArr.getJSONObject(0).optJSONArray("calendar")
                            if (calArr != null) {
                                for (i in 0 until calArr.length()) {
                                    val entry = calArr.opt(i)
                                    if (entry is String && entry.isNotBlank()) {
                                        rawCalendarEntries.add(entry)
                                    }
                                }
                            }
                        }
                        rawCalendarEntries
                    }
                }
            }

            // 1b. Fetch Real Madrid official team schedules & nextEvent directly so no Real Madrid match (LaLiga, UCL, Copa, Amical, Liga F) is ever missed
            val realMadridEndpoints = listOf(
                "https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/86/schedule" to "ALL",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/esp.1/teams/86/schedule" to "LaLiga",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/uefa.champions/teams/86/schedule" to "Ligue des Champions",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/86" to "ALL",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/esp.w.1/teams/20892/schedule" to "Liga F (F)"
            )
            val realMadridJobs = realMadridEndpoints.map { (url, fallbackLeague) ->
                async {
                    semaphore.withPermit {
                        val body = fetchUrlString(url) ?: return@withPermit
                        try {
                            val json = JSONObject(body)
                            val nowRef = currentParisNowMillis()
                            val scheduleMatches = parseEspnTeamScheduleOrNextEvents(json, fallbackLeague, nowRef)
                            synchronized(collected) {
                                scheduleMatches.forEach { (ms, m) -> mergePreferredMatch(collected, ms, m) }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            val allLeagues = clubLeagues + tournamentLeagues
            val rawCalendarsPerLeague = baseJobs.awaitAll()
            realMadridJobs.awaitAll()

            // Re-format any base matches using the freshly synced server clock (in case the first load started with device drift)
            val nowParisSynced = currentParisNowMillis()
            val ymdFmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = parisTimeZone }
            val espnQueryTz = TimeZone.getTimeZone("America/New_York")
            val espnYmdFmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = espnQueryTz }

            val calProbe = getCurrentParisCalendar()
            val todayYmd = ymdFmt.format(calProbe.time)
            val yesterdayYmd = (calProbe.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.let { ymdFmt.format(it.time) }
            val tomorrowYmd = (calProbe.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.let { ymdFmt.format(it.time) }
            val estTodayYmd = espnYmdFmt.format(calProbe.time)

            // 2. Convert each league's UTC calendar entries to French local time (Europe/Paris)
            val dateQueriesSet = linkedSetOf<Triple<String, String, String>>() // (code, leagueName, yyyymmdd)
            val discoveredParisCountPerLeague = IntArray(allLeagues.size)

            allLeagues.forEachIndexed { idx, (code, leagueName) ->
                val rawEntries = rawCalendarsPerLeague.getOrNull(idx).orEmpty()
                val parisQuerySet = sortedSetOf<String>()
                for (utcEntry in rawEntries) {
                    val utcMs = parseUtcTimestampToMillis(utcEntry)
                    val parisYmd = if (utcMs != Long.MAX_VALUE) {
                        ymdFmt.format(Date(utcMs))
                    } else if (utcEntry.length >= 10) {
                        utcEntry.substring(0, 10).replace("-", "")
                    } else ""

                    if (parisYmd.length == 8 && parisYmd >= yesterdayYmd) {
                        parisQuerySet.add(parisYmd)
                        if (utcMs != Long.MAX_VALUE) {
                            val estYmd = espnYmdFmt.format(Date(utcMs))
                            if (estYmd.length == 8 && estYmd >= yesterdayYmd) {
                                parisQuerySet.add(estYmd)
                            }
                        }
                    }
                }
                val selectedDates = parisQuerySet.take(7)
                discoveredParisCountPerLeague[idx] = selectedDates.size
                selectedDates.forEach { ymd ->
                    dateQueriesSet.add(Triple(code, leagueName, ymd))
                }
                dateQueriesSet.add(Triple(code, leagueName, yesterdayYmd))
                dateQueriesSet.add(Triple(code, leagueName, todayYmd))
                dateQueriesSet.add(Triple(code, leagueName, tomorrowYmd))
            }

            val next14Days = (1..14).map { offset ->
                val c = (calProbe.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                ymdFmt.format(c.time)
            }
            tournamentLeagues.forEachIndexed { tIdx, (code, leagueName) ->
                if (discoveredParisCountPerLeague[clubLeagues.size + tIdx] < 3) {
                    val probeDays = if (code.startsWith("uefa.champions") || code.startsWith("uefa.europa") || code.startsWith("uefa.conference")) {
                        next14Days.drop(2)
                    } else {
                        next14Days.take(8)
                    }
                    for (ymd in probeDays) {
                        dateQueriesSet.add(Triple(code, leagueName, ymd))
                    }
                }
            }

            val dateQueries = dateQueriesSet.toList()

            // 3. Execute all upcoming date queries concurrently
            val extraJobs = dateQueries.map { (code, leagueName, ymd) ->
                async {
                    semaphore.withPermit {
                        val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$code/scoreboard?dates=$ymd"
                        val body = fetchUrlString(url) ?: return@withPermit
                        try {
                            val json = JSONObject(body)
                            val matches = parseEspnScoreboardEvents(json, leagueName, nowParisSynced)
                            synchronized(collected) {
                                matches.forEach { (ms, m) -> mergePreferredMatch(collected, ms, m) }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            extraJobs.awaitAll()

            // 4. Fetch global ESPN 'all/scoreboard' across Paris & US-Eastern windows
            val globalUrls = listOf(
                "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=$yesterdayYmd",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=$estTodayYmd",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=$todayYmd",
                "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=$tomorrowYmd"
            ).distinct()
            val globalJobs = globalUrls.map { gUrl ->
                async {
                    semaphore.withPermit {
                        val allBody = fetchUrlString(gUrl) ?: return@withPermit
                        try {
                            val json = JSONObject(allBody)
                            val globalMatches = parseEspnScoreboardEvents(json, "ALL", nowParisSynced)
                            synchronized(collected) {
                                globalMatches.forEach { (ms, m) ->
                                    mergePreferredMatch(collected, ms, m)
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            globalJobs.awaitAll()

            // 5. Hydrate LIVE & kickoff-window matches from ESPN summary endpoint for real-time score, clock, stats & events
            val liveWindowCandidates = synchronized(collected) {
                collected.values
                    .filter { (epochMs, match) ->
                        val isInKickoffWindow = epochMs != Long.MAX_VALUE &&
                            (nowParisSynced - epochMs) in (-10L * 60_000L)..(140L * 60_000L)
                        match.status == MatchStatus.LIVE || isInKickoffWindow
                    }
                    .sortedByDescending { (_, m) ->
                        val isReal = m.homeTeam.contains("Real Madrid", ignoreCase = true) ||
                            m.awayTeam.contains("Real Madrid", ignoreCase = true)
                        (if (m.status == MatchStatus.LIVE) 100 else 0) + (if (isReal) 50 else 0)
                    }
                    .take(16)
            }

            val summaryJobs = liveWindowCandidates.map { (epochMs, baseMatch) ->
                async {
                    semaphore.withPermit {
                        val rawEventId = baseMatch.id.removePrefix("espn_")
                        if (rawEventId.isBlank() || !rawEventId.all { it.isDigit() }) return@withPermit
                        val summaryUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer/all/summary?event=$rawEventId"
                        val summaryBody = fetchUrlString(summaryUrl) ?: return@withPermit
                        try {
                            val summaryJson = JSONObject(summaryBody)
                            val hydrated = hydrateMatchWithEspnSummary(summaryJson, baseMatch, nowParisSynced)
                            synchronized(collected) {
                                mergePreferredMatch(collected, epochMs, hydrated)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            summaryJobs.awaitAll()
        }

        // Sort: LIVE first, then UPCOMING chronologically, then FINISHED most recent first
        collected.values
            .sortedWith(
                compareBy<Pair<Long, Match>>(
                    {
                        when (it.second.status) {
                            MatchStatus.LIVE -> 0
                            MatchStatus.UPCOMING -> 1
                            MatchStatus.FINISHED -> 2
                        }
                    },
                    { if (it.second.status == MatchStatus.FINISHED) -it.first else it.first }
                )
            )
            .map { it.second }
    }

    private fun statusRank(status: MatchStatus): Int = when (status) {
        MatchStatus.UPCOMING -> 0
        MatchStatus.FINISHED -> 1
        MatchStatus.LIVE -> 2
    }

    private fun mergePreferredMatch(
        collected: MutableMap<String, Pair<Long, Match>>,
        epochMillis: Long,
        incoming: Match
    ) {
        val current = collected[incoming.id]
        if (current == null) {
            collected[incoming.id] = epochMillis to incoming
            return
        }
        val existingMatch = current.second
        val existingRank = statusRank(existingMatch.status)
        val incomingRank = statusRank(incoming.status)

        val shouldReplace = when {
            incomingRank > existingRank -> true
            incomingRank < existingRank -> false
            else -> {
                val incomingGoals = incoming.homeScore + incoming.awayScore
                val existingGoals = existingMatch.homeScore + existingMatch.awayScore
                when {
                    incomingGoals > existingGoals -> true
                    incomingGoals < existingGoals -> false
                    incoming.minute > existingMatch.minute -> true
                    incoming.minute < existingMatch.minute -> false
                    incoming.events.size > existingMatch.events.size -> true
                    incoming.stats.shotsHome + incoming.stats.shotsAway >
                        existingMatch.stats.shotsHome + existingMatch.stats.shotsAway -> true
                    else -> false
                }
            }
        }

        val winner = if (shouldReplace) incoming else existingMatch
        val loser = if (shouldReplace) existingMatch else incoming
        val resolvedWinner = winner.copy(
            homeTeamLogo = winner.homeTeamLogo.ifBlank { loser.homeTeamLogo },
            awayTeamLogo = winner.awayTeamLogo.ifBlank { loser.awayTeamLogo },
            league = if (winner.league == "Football International" && loser.league != "Football International") loser.league else winner.league,
            kickoffMillis = if (winner.kickoffMillis > 0L) winner.kickoffMillis else loser.kickoffMillis
        )
        val resolvedEpoch = if (shouldReplace) epochMillis else current.first
        collected[incoming.id] = resolvedEpoch to resolvedWinner
    }

    private fun resolveLeagueFromSeasonSlug(
        slug: String,
        fallback: String,
        rawLeagueName: String = ""
    ): String {
        val s = "${slug.lowercase()} ${rawLeagueName.lowercase()}"
        if (s.isNotBlank()) {
            when {
                s.contains("copa-del-rey") || s.contains("copa del rey") -> return "Copa del Rey"
                s.contains("supercopa") || s.contains("super_cup") || s.contains("super cup") -> return "Supercoupe d'Espagne"
                s.contains("spanish-liga-f") || s.contains("liga f") -> return "Liga F (F)"
                s.contains("spanish-laliga") || s.contains("laliga") -> return "LaLiga"
                s.contains("coupe-de-france") || s.contains("coupe de france") -> return "Coupe de France"
                s.contains("french-ligue-1") || s.contains("ligue 1") -> return "Ligue 1"
                s.contains("fa-cup") || s.contains("fa cup") -> return "FA Cup"
                s.contains("english-premier") || s.contains("premier league") || s.contains("carabao") -> return "Premier League"
                s.contains("german-bundesliga") || s.contains("bundesliga") -> return "Bundesliga"
                s.contains("italian-serie-a") || s.contains("serie a") || s.contains("coppa italia") -> return "Serie A"
                s.contains("saudi") -> return "RSL (Saudi Pro League)"
                s.contains("women's champions") || s.contains("uefa-wchampions") -> return "Ligue des Champions (F)"
                s.contains("champions-league") || s.contains("champions league") -> return "Ligue des Champions"
                s.contains("europa-league") || s.contains("europa league") -> return "Ligue Europa"
                s.contains("conference-league") || s.contains("conference league") -> return "Conference League"
                s.contains("caf") || s.contains("africa") || s.contains("can ") -> return "Qualifications CAN 2027"
                s.contains("uefa-nations") || s.contains("nations league") || s.contains("euro") -> return "Euro / Ligue des Nations"
                s.contains("world-cup") || s.contains("world cup") || s.contains("worldq") -> return "Qualifs Coupe du Monde"
                s.contains("portug") || s.contains("primeira") -> return "Liga Portugal"
                s.contains("dutch") || s.contains("eredivisie") -> return "Eredivisie"
                s.contains("turk") || s.contains("super lig") -> return "Süper Lig"
                s.contains("mls") -> return "MLS"
                s.contains("brasileir") || s.contains("brazil") -> return "Brasileirão"
                s.contains("friendly") || s.contains("amicaux") -> return "Matchs Amicaux"
            }
        }
        if (fallback != "ALL") return fallback
        if (rawLeagueName.isNotBlank()) return rawLeagueName.trim()
        return "Football International"
    }

    private fun extractTeamLogo(teamObj: JSONObject?, translatedTeamName: String): String {
        val directLogo = teamObj?.optString("logo", "")?.trim().orEmpty()
        if (directLogo.startsWith("http")) return directLogo

        val logosArr = teamObj?.optJSONArray("logos")
        if (logosArr != null) {
            for (i in 0 until logosArr.length()) {
                val href = logosArr.optJSONObject(i)?.optString("href", "")?.trim().orEmpty()
                if (href.startsWith("http")) return href
            }
        }

        val iso = nationalFlagIsoCode(translatedTeamName)
        if (iso != null) {
            return "https://flagcdn.com/w160/$iso.png"
        }
        return ""
    }

    private fun nationalFlagIsoCode(teamName: String): String? {
        val key = teamName.lowercase(Locale.FRENCH).trim()
        return when {
            key == "france" -> "fr"
            key == "espagne" || key == "spain" -> "es"
            key == "allemagne" || key == "germany" -> "de"
            key == "italie" || key == "italy" -> "it"
            key == "angleterre" || key == "england" -> "gb-eng"
            key == "portugal" -> "pt"
            key == "pays-bas" || key == "netherlands" -> "nl"
            key == "belgique" || key == "belgium" -> "be"
            key == "suisse" || key == "switzerland" -> "ch"
            key == "croatie" || key == "croatia" -> "hr"
            key == "maroc" || key == "morocco" -> "ma"
            key == "sénégal" || key == "senegal" -> "sn"
            key == "côte d'ivoire" || key == "ivory coast" -> "ci"
            key == "algérie" || key == "algeria" -> "dz"
            key == "tunisie" || key == "tunisia" -> "tn"
            key == "égypte" || key == "egypt" -> "eg"
            key == "cameroun" || key == "cameroon" -> "cm"
            key == "nigeria" -> "ng"
            key == "ghana" -> "gh"
            key == "mauritanie" || key == "mauritania" -> "mr"
            key == "burkina faso" -> "bf"
            key == "mali" -> "ml"
            key == "guinée" || key == "guinea" -> "gn"
            key == "gambie" || key == "gambia" -> "gm"
            key == "cap-vert" || key == "cape verde" -> "cv"
            key == "comores" || key == "comoros" -> "km"
            key == "rd congo" || key == "dr congo" || key == "congo dr" -> "cd"
            key == "brésil" || key == "brazil" -> "br"
            key == "argentine" || key == "argentina" -> "ar"
            key == "états-unis" || key == "united states" -> "us"
            key == "chine" || key == "china" || key == "china pr" -> "cn"
            key == "malaisie" || key == "malaysia" -> "my"
            key == "japon" || key == "japan" -> "jp"
            key == "corée du sud" || key == "south korea" -> "kr"
            key == "arabie saoudite" || key == "saudi arabia" -> "sa"
            key == "turquie" || key == "turkey" || key == "türkiye" -> "tr"
            key == "pologne" || key == "poland" -> "pl"
            key == "danemark" || key == "denmark" -> "dk"
            key == "suède" || key == "sweden" -> "se"
            key == "norvège" || key == "norway" -> "no"
            key == "écosse" || key == "scotland" -> "gb-sct"
            else -> null
        }
    }

    private fun parseCompetitorScore(comp: JSONObject): Int {
        val raw = comp.opt("score") ?: return 0
        return when (raw) {
            is Number -> raw.toInt().coerceAtLeast(0)
            is JSONObject -> {
                val dv = raw.optString("displayValue", "").trim().toIntOrNull()
                dv ?: raw.optDouble("value", 0.0).toInt().coerceAtLeast(0)
            }
            is String -> raw.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
            else -> 0
        }
    }

    private fun parseLiveClockMinute(
        displayClock: String,
        shortDetail: String,
        statusName: String,
        rawClockSec: Double
    ): Int {
        val upperDetail = shortDetail.trim().uppercase(Locale.US)
        val upperClock = displayClock.trim().uppercase(Locale.US)
        val upperStatus = statusName.trim().uppercase(Locale.US)
        if (upperStatus.contains("HALFTIME") || upperStatus.contains("END_OF_FIRST_HALF") ||
            upperDetail == "HT" || upperClock == "HT" || upperDetail.contains("HALF")) {
            return 45
        }
        val stoppageRegex = Regex("""(\d+)\s*'?\s*\+\s*(\d+)""")
        val clockToParse = displayClock.ifBlank { shortDetail }
        val stoppageMatch = stoppageRegex.find(clockToParse)
        if (stoppageMatch != null) {
            val base = stoppageMatch.groupValues[1].toIntOrNull() ?: 45
            val extra = stoppageMatch.groupValues[2].toIntOrNull() ?: 0
            return (base + extra).coerceIn(1, 130)
        }
        val singleMin = Regex("""(\d+)""").find(clockToParse)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (singleMin != null && singleMin > 0) {
            return singleMin.coerceIn(1, 130)
        }
        val fromClock = if (rawClockSec > 180.0) (rawClockSec / 60.0).toInt() else rawClockSec.toInt()
        return if (fromClock > 0) fromClock.coerceIn(1, 130) else 0
    }

    internal fun computeEstimatedLiveMinute(kickoffMillis: Long, referenceNowMillis: Long): Int {
        if (kickoffMillis <= 0L || kickoffMillis == Long.MAX_VALUE) return 1
        val elapsedMinutes = ((referenceNowMillis - kickoffMillis) / 60_000L).toInt().coerceAtLeast(1)
        return when {
            elapsedMinutes <= 45 -> elapsedMinutes
            elapsedMinutes in 46..60 -> 45
            elapsedMinutes in 61..105 -> (elapsedMinutes - 15).coerceIn(46, 90)
            else -> (90 + (elapsedMinutes - 105)).coerceIn(91, 120)
        }
    }

    internal fun parseEspnTeamScheduleOrNextEvents(
        json: JSONObject,
        leagueName: String,
        referenceNowMillis: Long = currentParisNowMillis()
    ): List<Pair<Long, Match>> {
        val windowStart = referenceNowMillis - (36L * 3_600_000L)
        val windowEnd = referenceNowMillis + (21L * 24L * 3_600_000L)

        // If /teams/86 response has team.nextEvent rather than root events[], wrap it into an events JSONObject
        val effectiveJson = if (json.has("events")) {
            json
        } else {
            val nextEvents = json.optJSONObject("team")?.optJSONArray("nextEvent")
            if (nextEvents != null && nextEvents.length() > 0) {
                JSONObject().apply { put("events", nextEvents) }
            } else {
                return emptyList()
            }
        }

        val parsed = parseEspnScoreboardEvents(effectiveJson, leagueName, referenceNowMillis)
        return parsed.filter { (epochMs, match) ->
            match.status == MatchStatus.LIVE ||
                epochMs == Long.MAX_VALUE ||
                epochMs in windowStart..windowEnd
        }
    }

    internal fun hydrateMatchWithEspnSummary(
        summaryJson: JSONObject,
        baseMatch: Match,
        referenceNowMillis: Long = currentParisNowMillis()
    ): Match {
        val header = summaryJson.optJSONObject("header")
        val headerComp = header?.optJSONArray("competitions")?.optJSONObject(0)
        var updated = baseMatch

        if (headerComp != null) {
            val competitors = headerComp.optJSONArray("competitors")
            var homeScore = baseMatch.homeScore
            var awayScore = baseMatch.awayScore
            var homeTeamId = ""
            if (competitors != null) {
                for (i in 0 until competitors.length()) {
                    val c = competitors.optJSONObject(i) ?: continue
                    val ha = c.optString("homeAway", "")
                    val scoreVal = parseCompetitorScore(c)
                    if (ha == "home") {
                        homeScore = scoreVal
                        homeTeamId = c.optJSONObject("team")?.optString("id", "") ?: ""
                    } else if (ha == "away") {
                        awayScore = scoreVal
                    }
                }
            }

            val statusObj = headerComp.optJSONObject("status")
            val statusType = statusObj?.optJSONObject("type")
            val rawDate = headerComp.optString("date", "")
            val kickoffMs = if (rawDate.isNotBlank()) parseUtcTimestampToMillis(rawDate) else baseMatch.kickoffMillis
            val (resolvedStatus, resolvedMinute) = resolveEspnMatchStatusAndMinute(
                statusObj = statusObj,
                statusType = statusType,
                dateMillis = if (kickoffMs == Long.MAX_VALUE) baseMatch.kickoffMillis else kickoffMs,
                referenceNowMillis = referenceNowMillis
            )

            val parsedEvents = mutableListOf<MatchEvent>()
            val detailsArray = headerComp.optJSONArray("details") ?: summaryJson.optJSONArray("keyEvents")
            if (detailsArray != null && detailsArray.length() > 0) {
                for (dIdx in 0 until detailsArray.length()) {
                    val dObj = detailsArray.optJSONObject(dIdx) ?: continue
                    val typeObj = dObj.optJSONObject("type")
                    val typeText = typeObj?.optString("text", "") ?: ""
                    val isScoring = dObj.optBoolean("scoringPlay", false) ||
                        typeText.contains("Goal", ignoreCase = true) ||
                        typeText.contains("Penalty - Scored", ignoreCase = true)
                    val isRed = dObj.optBoolean("redCard", false) || typeText.contains("Red Card", ignoreCase = true)
                    val isYellow = dObj.optBoolean("yellowCard", false) || typeText.contains("Yellow Card", ignoreCase = true)
                    if (!isScoring && !isRed && !isYellow) continue

                    val evType = when {
                        isScoring -> EventType.GOAL
                        isRed -> EventType.RED_CARD
                        else -> EventType.YELLOW_CARD
                    }
                    val evTeamId = dObj.optJSONObject("team")?.optString("id", "") ?: ""
                    val isHomeEv = if (homeTeamId.isNotEmpty() && evTeamId.isNotEmpty()) evTeamId == homeTeamId else true
                    val clockObj = dObj.optJSONObject("clock")
                    val clockDisp = clockObj?.optString("displayValue", "") ?: ""
                    val evMin = Regex("""(\d+)""").find(clockDisp)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: ((clockObj?.optDouble("value", 0.0) ?: 0.0) / 60.0).toInt().coerceAtLeast(1)
                    val athletes = dObj.optJSONArray("athletesInvolved") ?: dObj.optJSONArray("participants")
                    val athleteName = if (athletes != null && athletes.length() > 0) {
                        val firstAth = athletes.optJSONObject(0)
                        firstAth?.optString("displayName", "")
                            ?.ifBlank { firstAth.optJSONObject("athlete")?.optString("displayName", "") ?: "" }
                            .orEmpty()
                    } else ""
                    parsedEvents.add(
                        MatchEvent(
                            id = "sum_ev_${baseMatch.id}_$dIdx",
                            minute = evMin.coerceIn(1, 125),
                            type = evType,
                            isHomeTeam = isHomeEv,
                            playerName = athleteName.ifBlank { if (isHomeEv) baseMatch.homeTeam else baseMatch.awayTeam },
                            detail = typeText.ifBlank { clockDisp }
                        )
                    )
                }
            }

            val finalEvents = if (parsedEvents.isNotEmpty()) parsedEvents.sortedBy { it.minute } else baseMatch.events
            val finalStatus = if (statusRank(resolvedStatus) >= statusRank(baseMatch.status)) resolvedStatus else baseMatch.status
            val finalMinute = if (finalStatus == MatchStatus.LIVE) maxOf(resolvedMinute, baseMatch.minute) else resolvedMinute

            updated = updated.copy(
                homeScore = maxOf(homeScore, baseMatch.homeScore),
                awayScore = maxOf(awayScore, baseMatch.awayScore),
                status = finalStatus,
                minute = finalMinute,
                events = finalEvents
            )
        }

        // Hydrate real boxscore stats (possession %, shots, passes, cards) when available
        val boxTeams = summaryJson.optJSONObject("boxscore")?.optJSONArray("teams")
        if (boxTeams != null && boxTeams.length() >= 2) {
            val teamStatsMap = mutableMapOf<String, Map<String, Int>>()
            for (tIdx in 0 until boxTeams.length()) {
                val tObj = boxTeams.optJSONObject(tIdx) ?: continue
                val ha = tObj.optString("homeAway", if (tIdx == 0) "home" else "away")
                val statsArr = tObj.optJSONArray("statistics") ?: continue
                val parsedMap = mutableMapOf<String, Int>()
                for (sIdx in 0 until statsArr.length()) {
                    val sObj = statsArr.optJSONObject(sIdx) ?: continue
                    val name = sObj.optString("name", "").lowercase(Locale.US)
                    val dv = sObj.optString("displayValue", "").replace("%", "").trim()
                    val numVal = dv.toDoubleOrNull()?.toInt()
                    if (name.isNotBlank() && numVal != null) {
                        parsedMap[name] = numVal
                    }
                }
                teamStatsMap[ha] = parsedMap
            }
            val homeMap = teamStatsMap["home"].orEmpty()
            val awayMap = teamStatsMap["away"].orEmpty()
            if (homeMap.isNotEmpty() || awayMap.isNotEmpty()) {
                val possH = homeMap["possessionpct"] ?: baseMatch.stats.possessionHome
                val possA = awayMap["possessionpct"] ?: (100 - possH).coerceIn(0, 100)
                updated = updated.copy(
                    stats = updated.stats.copy(
                        possessionHome = possH.coerceIn(0, 100),
                        possessionAway = possA.coerceIn(0, 100),
                        shotsHome = homeMap["totalshots"] ?: updated.stats.shotsHome,
                        shotsAway = awayMap["totalshots"] ?: updated.stats.shotsAway,
                        shotsOnTargetHome = homeMap["shotsontarget"] ?: updated.stats.shotsOnTargetHome,
                        shotsOnTargetAway = awayMap["shotsontarget"] ?: updated.stats.shotsOnTargetAway,
                        passesHome = homeMap["accuratepasses"] ?: homeMap["totalpasses"] ?: updated.stats.passesHome,
                        passesAway = awayMap["accuratepasses"] ?: awayMap["totalpasses"] ?: updated.stats.passesAway,
                        passAccuracyHome = homeMap["passpct"] ?: updated.stats.passAccuracyHome,
                        passAccuracyAway = awayMap["passpct"] ?: updated.stats.passAccuracyAway,
                        foulsHome = homeMap["foulscommitted"] ?: updated.stats.foulsHome,
                        foulsAway = awayMap["foulscommitted"] ?: updated.stats.foulsAway,
                        yellowCardsHome = homeMap["yellowcards"] ?: updated.stats.yellowCardsHome,
                        yellowCardsAway = awayMap["yellowcards"] ?: updated.stats.yellowCardsAway,
                        redCardsHome = homeMap["redcards"] ?: updated.stats.redCardsHome,
                        redCardsAway = awayMap["redcards"] ?: updated.stats.redCardsAway,
                        offsidesHome = homeMap["offsides"] ?: updated.stats.offsidesHome,
                        offsidesAway = awayMap["offsides"] ?: updated.stats.offsidesAway
                    )
                )
            }
        }

        return updated
    }

    private fun resolveEspnMatchStatusAndMinute(
        statusObj: JSONObject?,
        statusType: JSONObject?,
        dateMillis: Long,
        referenceNowMillis: Long
    ): Pair<MatchStatus, Int> {
        val state = statusType?.optString("state", "pre") ?: "pre"
        val statusName = statusType?.optString("name", "") ?: ""
        val shortDetail = statusType?.optString("shortDetail", "") ?: ""
        val displayClock = statusObj?.optString("displayClock", "") ?: ""
        val rawClockSec = statusObj?.optDouble("clock", 0.0) ?: 0.0
        val parsedClockMin = parseLiveClockMinute(displayClock, shortDetail, statusName, rawClockSec)

        val normalizedStatus = statusName.trim().uppercase(Locale.US)
        val normalizedState = state.trim().lowercase(Locale.US)
        val normalizedDetail = shortDetail.trim().uppercase(Locale.US)

        val isCompletedFlag = (statusType?.optBoolean("completed", false) == true) ||
            normalizedState == "post" ||
            normalizedStatus.contains("FINAL") ||
            normalizedStatus.contains("FULL_TIME") ||
            normalizedStatus.contains("AFTER_EXTRA_TIME") ||
            normalizedStatus.contains("AFTER_PENALTIES") ||
            normalizedDetail == "FT" || normalizedDetail == "AET" || normalizedDetail == "PEN"

        val isAbortedOrDelayed = normalizedStatus.contains("POSTPONED") ||
            normalizedStatus.contains("CANCELED") ||
            normalizedStatus.contains("CANCELLED") ||
            normalizedStatus.contains("ABANDONED") ||
            normalizedStatus.contains("SUSPENDED") ||
            normalizedStatus.contains("DELAYED")

        val isExplicitLive = normalizedState == "in" ||
            normalizedStatus.contains("IN_PROGRESS") ||
            normalizedStatus.contains("FIRST_HALF") ||
            normalizedStatus.contains("SECOND_HALF") ||
            normalizedStatus.contains("HALFTIME") ||
            normalizedStatus.contains("END_OF_FIRST_HALF") ||
            normalizedStatus.contains("EXTRA_TIME") ||
            normalizedStatus.contains("PENALTIES") ||
            normalizedStatus.contains("SHOOTOUT") ||
            normalizedStatus.contains("LIVE") ||
            normalizedDetail == "HT" || normalizedDetail == "LIVE" ||
            (displayClock.trim().let { dc ->
                dc.isNotBlank() && dc != "0'" && dc != "0" &&
                    Regex("""^\d+\s*(\+\s*\d+)?'$""").matches(dc)
            })

        val validKickoff = dateMillis > 0L && dateMillis != Long.MAX_VALUE
        val elapsedSinceKickoffMs = if (validKickoff) referenceNowMillis - dateMillis else -1L
        val isKickoffElapsedWindow = validKickoff &&
            !isCompletedFlag &&
            !isAbortedOrDelayed &&
            elapsedSinceKickoffMs in 0L..(130L * 60_000L)

        val matchStatus = when {
            isExplicitLive -> MatchStatus.LIVE
            isCompletedFlag -> MatchStatus.FINISHED
            isKickoffElapsedWindow -> MatchStatus.LIVE
            else -> MatchStatus.UPCOMING
        }

        val minute = when (matchStatus) {
            MatchStatus.FINISHED -> if (parsedClockMin >= 90) parsedClockMin else 90
            MatchStatus.LIVE -> when {
                parsedClockMin > 0 -> parsedClockMin
                isKickoffElapsedWindow -> computeEstimatedLiveMinute(dateMillis, referenceNowMillis)
                else -> 1
            }
            MatchStatus.UPCOMING -> 0
        }

        return matchStatus to minute
    }

    internal fun parseEspnScoreboardEvents(
        json: JSONObject,
        leagueName: String,
        referenceNowMillis: Long = currentParisNowMillis()
    ): List<Pair<Long, Match>> {
        val result = mutableListOf<Pair<Long, Match>>()
        val events = json.optJSONArray("events") ?: return result
        val rootLeagueName = json.optJSONArray("leagues")?.optJSONObject(0)?.optString("name", "") ?: ""

        for (i in 0 until events.length()) {
            val ev = events.getJSONObject(i)
            val evId = ev.optString("id", "${leagueName.hashCode()}_$i")
            val seasonSlug = ev.optJSONObject("season")?.optString("slug", "") ?: ""
            val evLeagueName = ev.optJSONObject("league")?.optString("name", "")?.ifBlank { rootLeagueName } ?: rootLeagueName
            val resolvedLeague = resolveLeagueFromSeasonSlug(seasonSlug, leagueName, evLeagueName)
            val competitions = ev.optJSONArray("competitions") ?: continue
            if (competitions.length() == 0) continue
            val comp = competitions.getJSONObject(0)
            val competitors = comp.optJSONArray("competitors") ?: continue
            if (competitors.length() < 2) continue

            var homeComp = competitors.getJSONObject(0)
            var awayComp = competitors.getJSONObject(1)
            for (j in 0 until competitors.length()) {
                val c = competitors.getJSONObject(j)
                if (c.optString("homeAway") == "home") homeComp = c
                if (c.optString("homeAway") == "away") awayComp = c
            }

            val homeTeamObj = homeComp.optJSONObject("team")
            val awayTeamObj = awayComp.optJSONObject("team")
            val homeRawName = homeTeamObj?.optString("displayName", "")
                ?.ifBlank { homeTeamObj.optString("shortDisplayName", "") }
                ?.ifBlank { homeTeamObj.optString("name", "Équipe Domicile") }
                ?: "Équipe Domicile"
            val awayRawName = awayTeamObj?.optString("displayName", "")
                ?.ifBlank { awayTeamObj.optString("shortDisplayName", "") }
                ?.ifBlank { awayTeamObj.optString("name", "Équipe Extérieure") }
                ?: "Équipe Extérieure"

            val homeTeamName = translateTeamName(homeRawName)
            val awayTeamName = translateTeamName(awayRawName)
            val homeLogo = extractTeamLogo(homeTeamObj, homeTeamName)
            val awayLogo = extractTeamLogo(awayTeamObj, awayTeamName)

            val homeScore = parseCompetitorScore(homeComp)
            val awayScore = parseCompetitorScore(awayComp)

            val rawDate = comp.optString("date", "").ifBlank { ev.optString("date", "") }
            val frSchedule = convertUtcToFrenchDateTime(rawDate, referenceNowMillis)
            val dateMillis = frSchedule.utcEpochMillis
            val dateStr = frSchedule.dateLabel
            val timeStr = frSchedule.timeLabel
            val venue = comp.optJSONObject("venue")?.optString("fullName", "Stade officiel") ?: "Stade officiel"

            val statusObj = comp.optJSONObject("status") ?: ev.optJSONObject("status")
            val statusType = statusObj?.optJSONObject("type")
            val (matchStatus, minute) = resolveEspnMatchStatusAndMinute(
                statusObj = statusObj,
                statusType = statusType,
                dateMillis = dateMillis,
                referenceNowMillis = referenceNowMillis
            )

            val stats = MatchStats(
                possessionHome = if (homeScore > awayScore) 56 else if (homeScore < awayScore) 44 else 50,
                possessionAway = if (homeScore > awayScore) 44 else if (homeScore < awayScore) 56 else 50,
                shotsHome = if (matchStatus == MatchStatus.UPCOMING) 0 else (homeScore * 3) + 7,
                shotsAway = if (matchStatus == MatchStatus.UPCOMING) 0 else (awayScore * 3) + 5,
                shotsOnTargetHome = if (matchStatus == MatchStatus.UPCOMING) 0 else (homeScore * 2) + 3,
                shotsOnTargetAway = if (matchStatus == MatchStatus.UPCOMING) 0 else (awayScore * 2) + 2,
                passesHome = if (matchStatus == MatchStatus.UPCOMING) 0 else 460,
                passesAway = if (matchStatus == MatchStatus.UPCOMING) 0 else 430,
                passAccuracyHome = 84,
                passAccuracyAway = 81,
                foulsHome = if (matchStatus == MatchStatus.UPCOMING) 0 else 10,
                foulsAway = if (matchStatus == MatchStatus.UPCOMING) 0 else 11,
                yellowCardsHome = if (matchStatus == MatchStatus.UPCOMING) 0 else 1,
                yellowCardsAway = if (matchStatus == MatchStatus.UPCOMING) 0 else 2,
                redCardsHome = 0,
                redCardsAway = 0,
                offsidesHome = if (matchStatus == MatchStatus.UPCOMING) 0 else 2,
                offsidesAway = if (matchStatus == MatchStatus.UPCOMING) 0 else 1
            )

            val homeTeamId = homeComp.optJSONObject("team")?.optString("id", "") ?: ""
            val eventsList = mutableListOf<MatchEvent>()
            val detailsArray = comp.optJSONArray("details")
            if (detailsArray != null && detailsArray.length() > 0) {
                for (dIdx in 0 until detailsArray.length()) {
                    val dObj = detailsArray.optJSONObject(dIdx) ?: continue
                    val isScoring = dObj.optBoolean("scoringPlay", false)
                    val isRed = dObj.optBoolean("redCard", false)
                    val isYellow = dObj.optBoolean("yellowCard", false)
                    if (!isScoring && !isRed && !isYellow) continue

                    val evType = when {
                        isScoring -> EventType.GOAL
                        isRed -> EventType.RED_CARD
                        else -> EventType.YELLOW_CARD
                    }
                    val evTeamId = dObj.optJSONObject("team")?.optString("id", "") ?: ""
                    val isHomeEv = evTeamId.isNotEmpty() && evTeamId == homeTeamId
                    val clockObj = dObj.optJSONObject("clock")
                    val clockDisp = clockObj?.optString("displayValue", "") ?: ""
                    val evMin = Regex("""(\d+)""").find(clockDisp)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: ((clockObj?.optDouble("value", 0.0) ?: 0.0) / 60.0).toInt().coerceAtLeast(1)
                    val athletes = dObj.optJSONArray("athletesInvolved")
                    val athleteName = if (athletes != null && athletes.length() > 0) {
                        athletes.optJSONObject(0)?.optString("displayName", "") ?: ""
                    } else ""
                    val playerLabel = athleteName.ifBlank { if (isHomeEv) homeTeamName else awayTeamName }
                    val detailText = dObj.optJSONObject("type")?.optString("text", "") ?: ""
                    eventsList.add(
                        MatchEvent(
                            id = "ev_${evId}_$dIdx",
                            minute = evMin.coerceIn(1, 120),
                            type = evType,
                            isHomeTeam = isHomeEv,
                            playerName = playerLabel,
                            detail = detailText.ifBlank { if (isScoring) "But ($clockDisp)" else clockDisp }
                        )
                    )
                }
            }
            if (eventsList.isEmpty() && matchStatus != MatchStatus.UPCOMING) {
                for (h in 1..homeScore) {
                    eventsList.add(
                        MatchEvent(
                            id = "ev_${evId}_h$h",
                            minute = (15 * h + 10).coerceAtMost(89),
                            type = EventType.GOAL,
                            isHomeTeam = true,
                            playerName = "$homeTeamName (Buteur)",
                            detail = "But marqué"
                        )
                    )
                }
                for (a in 1..awayScore) {
                    eventsList.add(
                        MatchEvent(
                            id = "ev_${evId}_a$a",
                            minute = (18 * a + 12).coerceAtMost(89),
                            type = EventType.GOAL,
                            isHomeTeam = false,
                            playerName = "$awayTeamName (Buteur)",
                            detail = "But marqué"
                        )
                    )
                }
            }

            val isIvoryCoast = homeTeamName.contains("Côte d'Ivoire", ignoreCase = true) ||
                    awayTeamName.contains("Côte d'Ivoire", ignoreCase = true) ||
                    homeTeamName.contains("Ivory Coast", ignoreCase = true) ||
                    awayTeamName.contains("Ivory Coast", ignoreCase = true)

            val broadcast = when {
                isIvoryCoast -> "La 3 (RTI) • NCI • Canal+ Sport"
                homeTeamName.contains("Real Madrid", ignoreCase = true) || awayTeamName.contains("Real Madrid", ignoreCase = true) -> "beIN Sports 1 • DAZN • Real Madrid TV"
                resolvedLeague == "Ligue 1" -> "DAZN 1 • beIN Sports 1"
                resolvedLeague == "Premier League" -> "Canal+ Foot"
                resolvedLeague == "LaLiga" || resolvedLeague == "Bundesliga" || resolvedLeague == "Serie A" -> "beIN Sports 1"
                resolvedLeague == "Ligue des Champions" -> "Canal+ • Canal+ Foot"
                resolvedLeague == "Ligue Europa" -> "Canal+ Foot"
                resolvedLeague.contains("CAN") -> "beIN Sports • Canal+ Afrique"
                resolvedLeague.contains("RSL") -> "Canal+ Sport"
                else -> "L'Équipe Live • beIN Sports"
            }

            val commentaryText = when (matchStatus) {
                MatchStatus.LIVE -> "⚡ DIRECT (${minute}') : $homeTeamName $homeScore - $awayScore $awayTeamName ($resolvedLeague). Match intense à $venue !"
                MatchStatus.FINISHED -> "🏁 SCORE FINAL : $homeTeamName $homeScore - $awayScore $awayTeamName. Rencontre terminée à $venue."
                MatchStatus.UPCOMING -> "📅 PROGRAMMÉ ($dateStr à $timeStr) : $homeTeamName vs $awayTeamName à $venue ($resolvedLeague) • 📺 $broadcast"
            }

            val sortedEvents = eventsList.sortedBy { it.minute }
            val dynamicVideoHighlights = sortedEvents
                .filter { it.type == EventType.GOAL || it.type == EventType.RED_CARD || it.type == EventType.YELLOW_CARD }
                .mapIndexed { idx, evItem ->
                    val hlType = when (evItem.type) {
                        EventType.GOAL -> "GOAL"
                        EventType.RED_CARD, EventType.YELLOW_CARD -> "FOUL"
                        else -> "ACTION"
                    }
                    val prefix = when (evItem.type) {
                        EventType.GOAL -> "⚽ But"
                        EventType.RED_CARD -> "🟥 Rouge"
                        EventType.YELLOW_CARD -> "🟨 Jaune"
                        else -> "🔥 Action"
                    }
                    val seekSec = ((evItem.minute * 13) / 90).coerceIn(1, 13)
                    VideoHighlightMarker(
                        id = "hl_${evId}_$idx",
                        minute = evItem.minute,
                        title = "$prefix • ${evItem.playerName}",
                        type = hlType,
                        timestampSeconds = seekSec
                    )
                }

            result.add(
                dateMillis to Match(
                    id = "espn_$evId",
                    league = resolvedLeague,
                    homeTeam = homeTeamName,
                    homeTeamLogo = homeLogo,
                    awayTeam = awayTeamName,
                    awayTeamLogo = awayLogo,
                    homeScore = homeScore,
                    awayScore = awayScore,
                    minute = minute,
                    status = matchStatus,
                    date = dateStr,
                    time = timeStr,
                    stadium = venue,
                    referee = "Arbitre Officiel",
                    stats = stats,
                    events = sortedEvents,
                    lineup = MatchLineup(
                        homeFormation = "4-3-3",
                        awayFormation = "4-2-3-1",
                        homeStarting11 = emptyList(),
                        awayStarting11 = emptyList(),
                        homeSubstitutes = emptyList(),
                        awaySubstitutes = emptyList()
                    ),
                    aiCommentaries = listOf(
                        AICommentary("c_${evId}", minute, commentaryText, "INTENSE")
                    ),
                    videoStreamUrl = "android.resource://com.aistudio.footialive.app/raw/match_live_stream",
                    videoThumbnailUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&auto=format&fit=crop",
                    videoHighlights = dynamicVideoHighlights,
                    broadcastChannel = broadcast,
                    kickoffMillis = if (dateMillis == Long.MAX_VALUE) 0L else dateMillis
                )
            )
        }
        return result
    }

    private fun translateTeamName(name: String): String {
        return when (name) {
            "Ivory Coast" -> "Côte d'Ivoire"
            "Gambia" -> "Gambie"
            "Mauritania" -> "Mauritanie"
            "Burkina Faso" -> "Burkina Faso"
            "Cape Verde" -> "Cap-Vert"
            "Comoros" -> "Comores"
            "Ethiopia" -> "Éthiopie"
            "South Africa" -> "Afrique du Sud"
            "Libya" -> "Libye"
            "Tanzania" -> "Tanzanie"
            "Equatorial Guinea" -> "Guinée Équatoriale"
            "Guinea" -> "Guinée"
            "Guinea-Bissau" -> "Guinée-Bissau"
            "Sudan" -> "Soudan"
            "South Sudan" -> "Soudan du Sud"
            "Uganda" -> "Ouganda"
            "Zambia" -> "Zambie"
            "Namibia" -> "Namibie"
            "Somalia" -> "Somalie"
            "Congo DR", "DR Congo" -> "RD Congo"
            "Central African Republic" -> "Centrafrique"
            "France" -> "France"
            "Germany" -> "Allemagne"
            "Spain" -> "Espagne"
            "Brazil" -> "Brésil"
            "Morocco" -> "Maroc"
            "Senegal" -> "Sénégal"
            "Algeria" -> "Algérie"
            "Cameroon" -> "Cameroun"
            "Egypt" -> "Égypte"
            "Tunisia" -> "Tunisie"
            "Nigeria" -> "Nigeria"
            "Ghana" -> "Ghana"
            "Belgium" -> "Belgique"
            "Netherlands" -> "Pays-Bas"
            "Portugal" -> "Portugal"
            "England" -> "Angleterre"
            "Italy" -> "Italie"
            "Switzerland" -> "Suisse"
            "Croatia" -> "Croatie"
            "Poland" -> "Pologne"
            "Denmark" -> "Danemark"
            "Sweden" -> "Suède"
            "Norway" -> "Norvège"
            "Scotland" -> "Écosse"
            "Turkey", "Türkiye" -> "Turquie"
            "United States" -> "États-Unis"
            "China", "China PR" -> "Chine"
            "Malaysia" -> "Malaisie"
            "Chinese Taipei" -> "Taïpei Chinois"
            "Kyrgyz Republic" -> "Kirghizistan"
            "Indonesia" -> "Indonésie"
            "Thailand" -> "Thaïlande"
            "Vietnam" -> "Viêt Nam"
            "Australia" -> "Australie"
            "Japan" -> "Japon"
            "South Korea" -> "Corée du Sud"
            "Saudi Arabia" -> "Arabie Saoudite"
            "Paris Saint-Germain" -> "Paris SG"
            "Marseille" -> "Olympique de Marseille"
            "Lyon" -> "Olympique Lyonnais"
            else -> name
        }
    }

    /**
     * Fetches and aggregates REAL-TIME Mercato & Football news directly from XML RSS feeds:
     * - Foot Mercato (https://www.footmercato.net/flux-rss)
     * - Le Parisien Football (https://feeds.leparisien.fr/leparisien/rss/sports/football + Google News Le Parisien Mercato)
     * - Le Monde Football (https://www.lemonde.fr/football/rss_full.xml + Google News Le Monde Football/Mercato)
     */
    suspend fun fetchMercatoNews(): List<MercatoNewsItem> = withContext(Dispatchers.IO) {
        val rawCollected = mutableListOf<Pair<Long, MercatoNewsItem>>()

        val feedConfigs = listOf(
            Triple("Foot Mercato", "https://www.footmercato.net/flux-rss", 82),
            Triple("Le Parisien", "https://feeds.leparisien.fr/leparisien/rss/sports/football", 92),
            Triple("Le Parisien", "https://news.google.com/rss/search?q=mercato+OR+transfert+OR+psg+site:leparisien.fr+when:14d&hl=fr&gl=FR&ceid=FR:fr", 92),
            Triple("Le Monde", "https://www.lemonde.fr/football/rss_full.xml", 96),
            Triple("Le Monde", "https://news.google.com/rss/search?q=football+OR+mercato+site:lemonde.fr+when:14d&hl=fr&gl=FR&ceid=FR:fr", 96)
        )

        coroutineScope {
            val jobs = feedConfigs.map { (sourceName, feedUrl, reliability) ->
                async {
                    val xml = fetchUrlString(feedUrl) ?: return@async emptyList<Pair<Long, MercatoNewsItem>>()
                    val entries = parseRssXml(
                        xml = xml,
                        defaultImage = when (sourceName) {
                            "Le Parisien" -> "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800"
                            "Le Monde" -> "https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=800"
                            else -> "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=800"
                        }
                    )

                    val mapped = mutableListOf<Pair<Long, MercatoNewsItem>>()
                    entries.take(15).forEachIndexed { idx, entry ->
                        val titleLower = entry.title.lowercase()
                        val descLower = entry.description.lowercase()
                        val combined = "$titleLower $descLower"

                        val isPureMercatoKeyword = combined.contains("mercato") ||
                                combined.contains("transfert") ||
                                combined.contains("prolongation") ||
                                combined.contains("recrue") ||
                                combined.contains("entraîneur") ||
                                combined.contains("sélectionneur") ||
                                combined.contains("offre") ||
                                combined.contains("contrat") ||
                                combined.contains("licenci") ||
                                combined.contains("piste") ||
                                combined.contains("cibl") ||
                                combined.contains("avenir") ||
                                combined.contains("prêt") ||
                                combined.contains("clause") ||
                                combined.contains("signe") ||
                                combined.contains("rumeur")

                        // For Foot Mercato include all top items + mercato stories; for Le Parisien/Le Monde prioritize mercato stories then latest football stories
                        val includeItem = when (sourceName) {
                            "Foot Mercato" -> isPureMercatoKeyword || idx < 10
                            "Le Parisien" -> isPureMercatoKeyword || idx < 6
                            else -> isPureMercatoKeyword || idx < 5
                        }

                        if (includeItem) {
                            val transferType = when {
                                combined.contains("officiel") || combined.contains("validé") || combined.contains("nommé") -> "Officiel"
                                combined.contains("prolong") -> "Prolongation"
                                combined.contains("accord") || combined.contains("négoci") || combined.contains("renseigne") || combined.contains("coché") || combined.contains("pressenti") -> "Négociations"
                                isPureMercatoKeyword -> "Mercato"
                                else -> "Actu Foot"
                            }

                            // Boost score slightly if it's strictly Mercato-keyworded so mercato rumors appear right at the top
                            val sortPriorityEpoch = if (isPureMercatoKeyword) entry.epochMillis + 1_800_000L else entry.epochMillis

                            mapped.add(
                                sortPriorityEpoch to MercatoNewsItem(
                                    id = "live_${sourceName.lowercase().replace(" ", "_")}_${entry.title.hashCode()}",
                                    source = sourceName,
                                    title = entry.title,
                                    playerOrClub = extractTargetPlayerOrClub(entry.title),
                                    transferType = transferType,
                                    estimatedFee = extractEstimatedFee(entry.title + " " + entry.description),
                                    reliabilityIndex = reliability,
                                    summary = entry.description.take(220).ifEmpty { entry.title },
                                    publishedAt = entry.formattedDate,
                                    url = entry.link,
                                    imageUrl = entry.imageUrl,
                                    geminiInsight = buildItemGeminiInsight(sourceName, entry.title, transferType, reliability)
                                )
                            )
                        }
                    }
                    mapped
                }
            }
            jobs.awaitAll().forEach { rawCollected.addAll(it) }
        }

        val sortedUnique = rawCollected
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { it.title.lowercase().take(45) }

        // Only fall back to static items if offline / zero live items retrieved
        sortedUnique.ifEmpty { generateCuratedMercatoNews() }
    }

    private fun buildItemGeminiInsight(source: String, title: String, transferType: String, reliability: Int): String {
        return when (source) {
            "Le Parisien" -> "Source francilienne ($reliability%) : Dossier $transferType suivi de près par la rédaction sport du Parisien."
            "Le Monde" -> "Analyse de référence ($reliability%) : Décryptage institutionnel et économique par Le Monde."
            else -> "Radar Foot Mercato ($reliability%) : Tendance $transferType détectée en direct sur le marché."
        }
    }

    private fun extractTargetPlayerOrClub(title: String): String {
        val keywords = listOf(
            "PSG" to "Paris Saint-Germain",
            "Paris" to "Paris Saint-Germain",
            "OM" to "Olympique de Marseille",
            "Marseille" to "Olympique de Marseille",
            "Lens" to "RC Lens",
            "Lyon" to "Olympique Lyonnais",
            "Bordeaux" to "Girondins de Bordeaux",
            "Real Madrid" to "Real Madrid",
            "Barça" to "FC Barcelone",
            "Barcelone" to "FC Barcelone",
            "Arsenal" to "Arsenal FC",
            "Manchester United" to "Manchester United",
            "Tottenham" to "Tottenham",
            "Liverpool" to "Liverpool FC",
            "Zidane" to "Z. Zidane (Équipe de France)",
            "Regragui" to "Walid Regragui",
            "Pierre Sage" to "Pierre Sage",
            "Ibrahim Maza" to "Ibrahim Maza (Leverkusen)",
            "Gonçalo Ramos" to "Gonçalo Ramos (PSG)",
            "Mbappé" to "K. Mbappé",
            "Barcola" to "B. Barcola (PSG)",
            "Hakimi" to "A. Hakimi (PSG)",
            "Désiré Doué" to "Désiré Doué (PSG)",
            "Côte d'Ivoire" to "Éléphants (Côte d'Ivoire)",
            "CAN" to "Qualifications CAN 2027",
            "Égypte" to "Sélection d'Égypte",
            "Bleus" to "Équipe de France"
        )
        for ((key, label) in keywords) {
            if (title.contains(key, ignoreCase = true)) return label
        }
        return "Mercato & Football"
    }

    private fun extractEstimatedFee(text: String): String {
        val regex = Regex("""(\d+[\.,]?\d*)\s*(M€|millions|milliards|k€)""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        return match?.value ?: "Info en direct"
    }

    fun generateCuratedMercatoNews(): List<MercatoNewsItem> {
        return listOf(
            MercatoNewsItem(
                id = "mercato_offline_1",
                source = "Foot Mercato",
                title = "Mode Hors-Ligne : Appuyez sur Actualiser pour charger les derniers transferts en direct",
                playerOrClub = "Mercato International",
                transferType = "Rumeur",
                estimatedFee = "Hors-ligne",
                reliabilityIndex = 82,
                summary = "Vérifiez votre connexion Internet puis appuyez sur Actualiser pour synchroniser les dépêches en direct de Foot Mercato, Le Parisien et Le Monde.",
                publishedAt = "Hors-ligne",
                imageUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=800",
                geminiInsight = "Synchronisation requise pour analyser les dépêches en temps réel."
            )
        )
    }
}


