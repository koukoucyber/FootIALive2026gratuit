package com.example.data.network

import com.example.data.model.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FootballNewsApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

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
                                val published = item.optString("published", "Récemment")
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
                                    articles.add(
                                        NewsArticle(
                                            id = "espn_${i}_${System.currentTimeMillis()}",
                                            title = headline,
                                            category = category,
                                            summary = description.ifBlank { headline },
                                            content = "$description\n\nArticle complet publié par $byline via le flux API public ESPN Soccer.",
                                            author = byline.ifBlank { "ESPN Sports" },
                                            date = formatTimestamp(published),
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
     * Fetches public French football headlines via RSS2JSON (RMC Sport / L'Équipe RSS feeds).
     */
    suspend fun fetchFrenchFootballNews(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val articles = mutableListOf<NewsArticle>()
        try {
            val rssUrl = "https://rmcsport.bfmtv.com/rss/football/"
            val url = "https://api.rss2json.com/v1/api.json?rss_url=$rssUrl"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android FootIALive App)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        val items = json.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                val title = item.optString("title", "")
                                val pubDate = item.optString("pubDate", "Récemment")
                                val author = item.optString("author", "RMC Sport")
                                val description = cleanHtml(item.optString("description", ""))
                                val thumbnail = item.optString("thumbnail", "")
                                    .ifEmpty { "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=800" }

                                val category = when {
                                    title.contains("mercato", ignoreCase = true) || title.contains("transfert", ignoreCase = true) -> "Transferts"
                                    title.contains("ligue 1", ignoreCase = true) || title.contains("psg", ignoreCase = true) || title.contains("om", ignoreCase = true) -> "Ligue 1"
                                    title.contains("can", ignoreCase = true) || title.contains("afrique", ignoreCase = true) -> "CAN 2026"
                                    title.contains("champions", ignoreCase = true) -> "Champions League"
                                    else -> "Football Actu"
                                }

                                if (title.isNotBlank()) {
                                    articles.add(
                                        NewsArticle(
                                            id = "rmc_${i}_${System.currentTimeMillis()}",
                                            title = title,
                                            category = category,
                                            summary = description.take(180).ifEmpty { title },
                                            content = "$description\n\nRetrouvez tous les détails de cet article en direct du fil info football.",
                                            author = author.ifBlank { "RMC Sport" },
                                            date = formatTimestamp(pubDate),
                                            readTime = "2 min",
                                            imageUrl = thumbnail
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

    private fun cleanHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").replace("&quot;", "\"").trim()
    }

    private fun formatTimestamp(raw: String): String {
        return if (raw.contains("T") || raw.contains("-")) {
            "Récent"
        } else raw.take(16).ifBlank { "En direct" }
    }
}
