package com.example.data.ai

import android.util.Log
import com.example.data.model.AICommentary
import com.example.data.model.EventType
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.MercatoNewsItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.random.Random

private const val TAG = "GeminiFootIA"

sealed interface AiMatchSearchState {
    data object Idle : AiMatchSearchState
    data class Loading(val query: String) : AiMatchSearchState
    data class Success(
        val query: String,
        val matchingIds: List<String>,
        val explanation: String,
        val suggestedLeague: String? = null,
        val suggestedStatus: String = "ALL"
    ) : AiMatchSearchState

    data class Error(val query: String, val message: String) : AiMatchSearchState
}

data class AiVideoHighlightClip(
    val minute: Int,
    val category: String,
    val title: String,
    val reason: String,
    val timestampSeconds: Int
)

sealed interface AiPostMatchVideoSummaryState {
    data object Idle : AiPostMatchVideoSummaryState
    data class Loading(val matchId: String) : AiPostMatchVideoSummaryState
    data class Success(
        val matchId: String,
        val summaryHeadline: String,
        val clips: List<AiVideoHighlightClip>
    ) : AiPostMatchVideoSummaryState

    data class Error(val matchId: String, val message: String) : AiPostMatchVideoSummaryState
}

enum class CommentaryLanguage(val code: String, val displayName: String, val flagEmoji: String, val promptInstruction: String) {
    FRENCH("fr", "Français", "🇫🇷", "en français"),
    ENGLISH("en", "English", "🇬🇧", "in English"),
    ARABIC("ar", "العربية", "🇸🇦", "باللغة العربية مع مصطلحات كرة القدم الحماسية (Arabic)"),
    MALINKE("ml", "Malinké / Bambara / Dioula", "🇨🇮 🇲🇱", "en langue Malinké / Bambara / Dioula (Maninkan / Bamanankan d'Afrique de l'Ouest) avec les expressions authentiques du football africain (ex: 'Goolu kɛra!', 'Balla bɔra!', 'Sira bɛ kelen kan!', 'I ni ce!', 'Kuru fili')"),
    SPANISH("es", "Español", "🇪🇸", "en español apasionado con términos de fútbol"),
    PORTUGUESE("pt", "Português", "🇵🇹", "em português com sotaque e entusiasmo do futebol"),
    GERMAN("de", "Deutsch", "🇩🇪", "auf Deutsch mit Fußball-Kommentarstil"),
    ITALIAN("it", "Italiano", "🇮🇹", "in italiano con la passione del calcio"),
    DUTCH("nl", "Nederlands", "🇳🇱", "in het Nederlands"),
    RUSSIAN("ru", "Русский", "🇷🇺", "на русском языке с футбольными терминами"),
    CHINESE("zh", "中文", "🇨🇳", "用中文（普通话）解说足球比赛"),
    JAPANESE("ja", "日本語", "🇯🇵", "日本語で情熱的なサッカー実況"),
    KOREAN("ko", "한국어", "🇰🇷", "한국어로 열정적인 축구 중계"),
    TURKISH("tr", "Türkçe", "🇹🇷", "Türkçe coşkulu futbol spikeri tarzında"),
    SWAHILI("sw", "Kiswahili", "🇰🇪", "kwa Kiswahili cha michezo na soka"),
    WOLOF("wo", "Wolof", "🇸🇳", "en langue Wolof du Sénégal avec les expressions du football"),
    HINDI("hi", "हिन्दी", "🇮🇳", "हिन्दी में फुटबॉल कमेंट्री"),
    INDONESIAN("id", "Bahasa Indonesia", "🇮🇩", "dalam Bahasa Indonesia gaya komentator sepak bola"),
    POLISH("pl", "Polski", "🇵🇱", "po polsku w stylu komentatora sportowego"),
    VIETNAMESE("vi", "Tiếng Việt", "🇻🇳", "bằng Tiếng Việt phong cách bình luận viên bóng đá"),
    GREEK("el", "Ελληνικά", "🇬🇷", "στα ελληνικά με πάθος για το ποδόσφαιρο"),
    HEBREW("he", "עברית", "🇮🇱", "בעברית בסגנון שדרן כדורגל"),
    THAI("th", "ไทย", "🇹🇭", "เป็นภาษาไทย สไตล์ผู้บรรยายฟุตบอล"),
    UKRAINIAN("uk", "Українська", "🇺🇦", "українською мовою у стилі футбольного коментатора"),
    CZECH("cs", "Čeština", "🇨🇿", "v češtině v stylu fotbalového komentátora"),
    ROMANIAN("ro", "Română", "🇷🇴", "în limba română"),
    SWEDISH("sv", "Svenska", "🇸🇪", "på svenska i fotbollskommentatorsstil"),
    HUNGARIAN("hu", "Magyar", "🇭🇺", "magyarul futballkommentátor stílusban"),
    YORUBA("yo", "Yorùbá", "🇳🇬", "in Yoruba language with football commentary energy"),
    HAUSA("ha", "Hausa", "🇳🇬", "in Hausa language with sports commentary energy"),
    AMHARIC("am", "አማርኛ", "🇪🇹", "በአማርኛ የእግር ኳስ ሥርጭት ስል"),
    LINGALA("ln", "Lingála", "🇨🇩", "en Lingala avec la passion du football congolais");

    companion object {
        fun fromCode(code: String): CommentaryLanguage {
            return entries.find { it.code == code } ?: FRENCH
        }
    }
}

class GeminiCommentatorRepository {

    private val matchSearchSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "matchingIds" to Schema.array(
                    items = Schema.string(),
                    description = "Ordered list of exact Match IDs matching the user's request"
                ),
                "suggestedLeague" to Schema.string(
                    description = "Detected league name from the matches or 'Toutes'",
                    nullable = true
                ),
                "suggestedStatus" to Schema.enumeration(
                    values = listOf("ALL", "LIVE", "UPCOMING", "FINISHED"),
                    description = "Detected match status filter"
                ),
                "aiExplanation" to Schema.string(
                    description = "Short 1-sentence French summary explaining which matches were found and why"
                )
            ),
            optionalProperties = listOf("suggestedLeague")
        )
    }

    private val textModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val postMatchHighlightsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "summaryHeadline" to Schema.string(
                    description = "Concise 1-sentence French recap of the finished match score and turning points"
                ),
                "clips" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "minute" to Schema.integer(description = "Match minute (1..120)"),
                            "category" to Schema.enumeration(
                                values = listOf("GOAL", "RED_CARD", "YELLOW_CARD", "KEY_ACTION"),
                                description = "Must be GOAL for goals or RED_CARD/YELLOW_CARD/KEY_ACTION for critical match actions"
                            ),
                            "title" to Schema.string(description = "Short French clip title (e.g. 'But de Kylian Mbappé')"),
                            "reason" to Schema.string(description = "Brief explanation why this moment was decisive in the match"),
                            "timestampSeconds" to Schema.integer(description = "Video reel seek offset in seconds (0..14)")
                        )
                    ),
                    description = "Filtered list of ONLY goals and key turning-point actions ordered chronologically"
                )
            )
        )
    }

    private val matchSearchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = matchSearchSchema
            }
        )
    }

    private val postMatchSummaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = postMatchHighlightsSchema
            }
        )
    }

    /**
     * Uses Firebase AI Logic (Gemini) with structured JSON output to intelligently search and filter
     * the real matches catalog by natural language (team, country, competition, French kickoff hour,
     * live status, derby, scoreline, broadcaster, or regional football intent).
     */
    suspend fun searchAndFilterMatchesWithAI(
        query: String,
        matches: List<Match>
    ): AiMatchSearchState = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext AiMatchSearchState.Idle
        if (matches.isEmpty()) {
            return@withContext AiMatchSearchState.Error(
                query = trimmed,
                message = "Aucun match réel n'est chargé pour le moment. Appuyez sur Actualiser puis relancez la recherche IA."
            )
        }

        val catalogText = buildMatchCatalogForAi(matches)
        val validIds = matches.map { it.id }.toSet()

        val prompt = """
            Tu es le moteur de recherche et de filtrage intelligent de FootIA Live.
            L'utilisateur recherche ou filtre des matchs réels avec la requête : "$trimmed".

            Analyse uniquement la liste réelle des matchs ci-dessous (tous les horaires sont déjà en heure locale française Europe/Paris) :
            $catalogText

            Règles strictes :
            - Retourne dans matchingIds UNIQUEMENT des identifiants exacts présents dans la liste ci-dessus, classés du plus pertinent au moins pertinent.
            - Comprends les surnoms, pays, régions et intentions en langage naturel : ex. "Real" -> Real Madrid, "Barça" -> Barcelona, "PSG" -> Paris SG, "Éléphants" -> Côte d'Ivoire, "Les Bleus" -> France, "ce soir" / "après 20h" -> matchs d'aujourd'hui avec heure >= 20:00 (FR), "en direct" -> LIVE, "Afrique" / "CAN" -> sélections africaines ou Qualifications CAN, "avec buts" -> total buts > 0, "gros matchs" -> affiches entre grands clubs/sélections ou matchs à buts.
            - Ne fabrique jamais de match ni d'identifiant absent de la liste. Si aucun match ne correspond vraiment, renvoie matchingIds = [] et explique clairement pourquoi en français dans aiExplanation.
        """.trimIndent()

        try {
            val response = withTimeout(45_000L) {
                matchSearchModel.generateContent(prompt)
            }
            val jsonString = response.text?.takeIf { it.isNotBlank() }
                ?: return@withContext AiMatchSearchState.Error(
                    query = trimmed,
                    message = "Le modèle IA a renvoyé une réponse vide."
                )

            parseAiMatchSearchJson(trimmed, jsonString, validIds)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI match search timed out", e)
            AiMatchSearchState.Error(
                query = trimmed,
                message = "Délai dépassé lors du filtrage IA Gemini. Veuillez réessayer."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI match search failed", e)
            AiMatchSearchState.Error(
                query = trimmed,
                message = e.localizedMessage ?: "Échec de la recherche IA Gemini."
            )
        }
    }

    internal fun buildMatchCatalogForAi(matches: List<Match>): String {
        return matches.take(120).joinToString("\n") { m ->
            val statusStr = when (m.status) {
                MatchStatus.LIVE -> "LIVE (${m.minute}')"
                MatchStatus.UPCOMING -> "UPCOMING"
                MatchStatus.FINISHED -> "FINISHED"
            }
            val scoreStr = if (m.status == MatchStatus.UPCOMING) "vs" else "${m.homeScore}-${m.awayScore}"
            val channel = m.broadcastChannel ?: "TV"
            "${m.id} | ${m.league} | ${m.homeTeam} $scoreStr ${m.awayTeam} | $statusStr | ${m.date} ${m.time} | ${m.stadium} | $channel"
        }
    }

    internal fun parseAiMatchSearchJson(
        query: String,
        jsonString: String,
        validIds: Set<String>
    ): AiMatchSearchState {
        return try {
            val json = JSONObject(jsonString)
            val idsArr = json.optJSONArray("matchingIds")
            val filteredIds = mutableListOf<String>()
            if (idsArr != null) {
                for (i in 0 until idsArr.length()) {
                    val id = idsArr.optString(i, "")
                    if (id in validIds && id !in filteredIds) {
                        filteredIds.add(id)
                    }
                }
            }
            val suggestedLeague = json.optString("suggestedLeague", "").takeIf { it.isNotBlank() && it != "null" }
            val suggestedStatus = json.optString("suggestedStatus", "ALL").ifBlank { "ALL" }
            val explanation = json.optString(
                "aiExplanation",
                "${filteredIds.size} match(s) sélectionné(s) par Gemini pour « $query »."
            )
            AiMatchSearchState.Success(
                query = query,
                matchingIds = filteredIds,
                explanation = explanation,
                suggestedLeague = suggestedLeague,
                suggestedStatus = suggestedStatus
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI match search JSON", e)
            AiMatchSearchState.Error(
                query = query,
                message = "Réponse JSON IA invalide : ${e.localizedMessage ?: "erreur de lecture"}"
            )
        }
    }

    /**
     * Automatically filters ONLY goals and critical turning-point actions for a finished match's
     * post-match video summary reel using Firebase AI Logic (Gemini).
     */
    suspend fun filterFinishedMatchHighlightsWithAI(match: Match): AiPostMatchVideoSummaryState = withContext(Dispatchers.IO) {
        val eventsText = if (match.events.isNotEmpty()) {
            match.events.joinToString("\n") { ev ->
                val side = if (ev.isHomeTeam) match.homeTeam else match.awayTeam
                "- ${ev.minute}' | ${ev.type.name} | $side | ${ev.playerName} | ${ev.detail}"
            }
        } else {
            "Score final : ${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam} (aucun détail d'évènement individuel transmis par l'API)."
        }

        val prompt = """
            Tu es le réalisateur vidéo IA de FootIA Live pour le résumé de fin de match.
            Match terminé : ${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam} (${match.league}, ${match.stadium}, ${match.date} ${match.time}).
            Statistiques : Possession ${match.stats.possessionHome}% - ${match.stats.possessionAway}%, Tirs ${match.stats.shotsHome} - ${match.stats.shotsAway}, Tirs cadrés ${match.stats.shotsOnTargetHome} - ${match.stats.shotsOnTargetAway}.

            Évènements réels officiels du match :
            $eventsText

            Ta mission :
            1. Filtre STRICTEMENT les buts (GOAL) et les actions importantes ayant fait basculer le match (RED_CARD, cartons jaunes décisifs YELLOW_CARD, tournant tactique KEY_ACTION).
            2. Ignore les faits mineurs sans impact.
            3. Attribue à chaque clip un timestampSeconds réparti entre 1 et 13 secondes (dans l'ordre chronologique des minutes) pour permettre la lecture directe dans le lecteur vidéo du résumé.
            4. Rédige summaryHeadline (1 phrase nette en français) et clips en français sans inventer de joueurs absents des évènements.
        """.trimIndent()

        try {
            val response = withTimeout(45_000L) {
                postMatchSummaryModel.generateContent(prompt)
            }
            val jsonString = response.text?.takeIf { it.isNotBlank() }
                ?: return@withContext AiPostMatchVideoSummaryState.Error(
                    matchId = match.id,
                    message = "Le modèle Gemini IA n'a renvoyé aucun résumé vidéo."
                )
            parsePostMatchHighlightsJson(match.id, jsonString, match)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI post-match highlight filter timed out", e)
            AiPostMatchVideoSummaryState.Error(
                matchId = match.id,
                message = "Délai dépassé lors de l'analyse vidéo Gemini. Appuyez sur Réessayer."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI post-match video summary failed", e)
            AiPostMatchVideoSummaryState.Error(
                matchId = match.id,
                message = e.localizedMessage ?: "Échec du filtrage vidéo Gemini."
            )
        }
    }

    internal fun parsePostMatchHighlightsJson(
        matchId: String,
        jsonString: String,
        match: Match
    ): AiPostMatchVideoSummaryState {
        return try {
            val json = JSONObject(jsonString)
            val headline = json.optString(
                "summaryHeadline",
                "Résumé vidéo : ${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam}"
            ).ifBlank {
                "Résumé vidéo : ${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam}"
            }

            val arr = json.optJSONArray("clips")
            val parsed = mutableListOf<AiVideoHighlightClip>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val minute = obj.optInt("minute", 1).coerceIn(1, 120)
                    val category = obj.optString("category", "KEY_ACTION").uppercase().let {
                        if (it in setOf("GOAL", "RED_CARD", "YELLOW_CARD", "KEY_ACTION")) it else "KEY_ACTION"
                    }
                    val title = obj.optString("title", "").ifBlank {
                        when (category) {
                            "GOAL" -> "⚽ But à la ${minute}'"
                            "RED_CARD" -> "🟥 Carton rouge (${minute}')"
                            "YELLOW_CARD" -> "🟨 Avertissement important (${minute}')"
                            else -> "🔥 Action décisive (${minute}')"
                        }
                    }
                    val reason = obj.optString("reason", "Action clé retenue par Gemini IA")
                    val ts = obj.optInt("timestampSeconds", ((minute * 13) / 90).coerceIn(1, 13)).coerceIn(0, 14)
                    parsed.add(
                        AiVideoHighlightClip(
                            minute = minute,
                            category = category,
                            title = title,
                            reason = reason,
                            timestampSeconds = ts
                        )
                    )
                }
            }

            AiPostMatchVideoSummaryState.Success(
                matchId = matchId,
                summaryHeadline = headline,
                clips = parsed.sortedBy { it.minute }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse post-match highlights JSON", e)
            AiPostMatchVideoSummaryState.Error(
                matchId = matchId,
                message = "Format du résumé vidéo IA invalide : ${e.localizedMessage ?: "erreur JSON"}"
            )
        }
    }

    /**
     * Generates dynamic TV commentary for a live match action in the specified language.
     */
    suspend fun generateLiveCommentary(
        match: Match,
        minute: Int,
        actionPrompt: String? = null,
        languageCode: String = "fr"
    ): AICommentary = withContext(Dispatchers.IO) {
        val lang = CommentaryLanguage.fromCode(languageCode)

        val prompt = if (actionPrompt != null) {
            "Tu es un commentateur télé de football professionnel et hyper passionné. Rédige ton commentaire ${lang.promptInstruction}. " +
                    "Commente cette action du match ${match.homeTeam} (${match.homeScore}) vs ${match.awayTeam} (${match.awayScore}) à la ${minute}e minute dans la ligue ${match.league}: '$actionPrompt'. " +
                    "Sois immersif, dynamique, utilise le vocabulaire et l'énergie d'un grand match en direct. Max 2-3 phrases."
        } else {
            "Tu es un commentateur télé de football en direct. Rédige ton commentaire ${lang.promptInstruction}. " +
                    "Génère un commentaire court (2 phrases max) à la ${minute}e minute pour le match ${match.homeTeam} (${match.homeScore}) vs ${match.awayTeam} (${match.awayScore}) (${match.league}). " +
                    "Décris une action réaliste (passe en profondeur, dribble spectaculaire, arrêt reflexe du gardien, tacle glissé ou feinte)."
        }

        val aiText = callFirebaseAiText(prompt) ?: getRandomFallbackCommentary(match, minute, actionPrompt, lang)

        AICommentary(
            id = "ai_${System.currentTimeMillis()}_${Random.nextInt(1000)}",
            minute = minute,
            text = aiText,
            tone = if (aiText.contains("BUT") || aiText.contains("GOAL") || aiText.contains("هدف") || aiText.contains("GOOLU")) "INTENSE" else "ANALYTICAL"
        )
    }

    /**
     * Generates a full match AI summary/debrief in the selected language.
     */
    suspend fun generateMatchSummary(match: Match, languageCode: String = "fr"): String = withContext(Dispatchers.IO) {
        val lang = CommentaryLanguage.fromCode(languageCode)

        val prompt = "Tu es un consultant tactique de football chevronné pour 'FootIA Live'. " +
                "Rédige un résumé/débrief synthétique et captivant ${lang.promptInstruction} pour le match ${match.homeTeam} (${match.homeScore}) - (${match.awayTeam}) (${match.awayScore}) joué au ${match.stadium} (${match.league}). " +
                "Inclut les points clés (possession, efficacité devant le but, tournant du match, joueur clé). Structure en 3 paragraphes courts et percutants avec des émojis."

        callFirebaseAiText(prompt) ?: getFallbackSummary(match, lang)
    }

    /**
     * Interactive chat feature with AI Commentator in the selected language.
     */
    suspend fun askCommentator(match: Match, userQuestion: String, languageCode: String = "fr"): String = withContext(Dispatchers.IO) {
        val lang = CommentaryLanguage.fromCode(languageCode)

        val prompt = "Tu es le commentateur IA vedette de 'FootIA Live' pour le match ${match.homeTeam} vs ${match.awayTeam} (${match.league}). " +
                "Score actuel: ${match.homeScore} - ${match.awayScore} (${match.minute}' min). " +
                "Statistiques: Possession ${match.stats.possessionHome}% - ${match.stats.possessionAway}%, Tirs ${match.stats.shotsHome} - ${match.stats.shotsAway}. " +
                "L'utilisateur te demande: '$userQuestion'. Réponds-lui comme un commentateur passionné et avisé ${lang.promptInstruction} en 2-3 phrases."

        callFirebaseAiText(prompt) ?: getFallbackAskResponse(match, lang)
    }

    private suspend fun callFirebaseAiText(prompt: String): String? {
        return try {
            val response = withTimeout(45_000L) {
                textModel.generateContent(prompt)
            }
            response.text?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI text generation failed", e)
            null
        }
    }

    private fun getRandomFallbackCommentary(
        match: Match,
        minute: Int,
        actionPrompt: String?,
        lang: CommentaryLanguage
    ): String {
        return when (lang) {
            CommentaryLanguage.ENGLISH -> {
                if (actionPrompt != null) "⚡ ${minute}' - Incredible moment! $actionPrompt. The crowd inside ${match.stadium} goes wild!"
                else listOf(
                    "⚽ ${minute}' - GOAL! What a masterpiece from ${match.homeTeam}! The stadium erupts in joy!",
                    "🔥 ${minute}' - Brilliant counter-attack! A razor-sharp pass slicing open the defense!",
                    "🛡️ ${minute}' - Astonishing reflex save on the goal line! World-class goalkeeping!",
                    "🎯 ${minute}' - Dazzling dribble on the wing! The fans are loving every second of this."
                )[Random.nextInt(4)]
            }
            CommentaryLanguage.ARABIC -> {
                if (actionPrompt != null) "⚡ ${minute}' - لقطة حماسية جداً! $actionPrompt. أجواء خيالية في ملعب ${match.stadium}!"
                else listOf(
                    "⚽ ${minute}' - هدف عالمي! تسديدة صاروخية تفجر حماس الجماهير في المدرجات!",
                    "🔥 ${minute}' - هجمة مرتدة سريعة وتمريرة ساحرة تكسر خط الدفاع!",
                    "🛡️ ${minute}' - تصدٍ إعجازي من حارس المرمى على حافة خط المرمى!",
                    "🎯 ${minute}' - مراوغة خيالية واختراق ممتاز في منطقة الجزاء!"
                )[Random.nextInt(4)]
            }
            CommentaryLanguage.MALINKE -> {
                if (actionPrompt != null) "⚡ ${minute}' - Balla bɔra tan! $actionPrompt. Nyɛfɛla bɛɛ bɛ fili la ${match.stadium} dugu kɔnɔ!"
                else listOf(
                    "⚽ ${minute}' - GOOLU KƐRA! ${match.homeTeam} ye balla gosi dugu ma! Stadium bɛɛ bɛ wulila!",
                    "🔥 ${minute}' - Bolonɔ cɛmanba! Dribble cɛman ka taa nyɛfɛ, defender-lu bɛɛ mabilen!",
                    "🛡️ ${minute}' - Gardien ye balla gosi ka bɔ sira kan! Intervention bonba tan!",
                    "🎯 ${minute}' - Tacle glissé cɛman, balla bɛ bɔ ka taa corner kan! I ni ce!"
                )[Random.nextInt(4)]
            }
            else -> {
                if (actionPrompt != null) "⚡ ${minute}' - Action (${lang.displayName}): $actionPrompt"
                else "⚽ ${minute}' - ${match.homeTeam} vs ${match.awayTeam} : Action intense à la ${minute}e minute !"
            }
        }
    }

    private fun getFallbackSummary(match: Match, lang: CommentaryLanguage): String {
        return when (lang) {
            CommentaryLanguage.ENGLISH -> """
                🏆 **FootIA Live AI Debrief**
                
                The clash between **${match.homeTeam}** and **${match.awayTeam}** (${match.league}) provided an intense tactical battle at ${match.stadium}. Final score: **${match.homeScore} - ${match.awayScore}**.
                
                ⚡ **Key Highlights & Tactical Analysis**
                Control and quick transitions proved crucial. ${match.homeTeam} registered ${match.stats.shotsHome} shots compared to ${match.stats.shotsAway} for ${match.awayTeam}.
            """.trimIndent()
            CommentaryLanguage.ARABIC -> """
                🏆 **ملخص المباراة - FootIA Live**
                
                مواجهة قوية ومثيرة جمعت بين **${match.homeTeam}** و **${match.awayTeam}** (${match.league}) على أرضية ملعب ${match.stadium}. انتهت المباراة بنتيجة **${match.homeScore} - ${match.awayScore}**.
                
                ⚡ **التحليل التكتيكي والأبرز**
                شهدت المباراة صراعاً تكتيكياً كبيراً في وسط الملعب مع هجمات مرتدة خطيرة من الجانبين.
            """.trimIndent()
            CommentaryLanguage.MALINKE -> """
                🏆 **FootIA Live AI Debrief (Malinké)**
                
                Nyalagbonba kɛra **${match.homeTeam}** ni **${match.awayTeam}** cɛ (${match.league}) ${match.stadium} dugu kɔnɔ. Score laban ye **${match.homeScore} - ${match.awayScore}** ye.
                
                ⚡ **Nyɛfɛla ni Balla fanga**
                Tulon cɛman fanga ye dugu mabilen. ${match.homeTeam} ye balla gosi ${match.stats.shotsHome} sen, ${match.awayTeam} ye balla gosi ${match.stats.shotsAway} sen.
            """.trimIndent()
            else -> """
                🏆 **Débrief IA de FootIA Live (${lang.displayName})**
                
                Le choc entre **${match.homeTeam}** et **${match.awayTeam}** (${match.league}) nous a offert une bataille tactique intense au ${match.stadium}. Score final: **${match.homeScore} - ${match.awayScore}**.
                
                ⚡ **Moments Clés & Analyse Tactique**
                La transition offensive s'est avérée décisive avec ${match.stats.shotsHome} tirs pour ${match.homeTeam} contre ${match.stats.shotsAway} tirs pour ${match.awayTeam}.
            """.trimIndent()
        }
    }

    private fun getFallbackAskResponse(match: Match, lang: CommentaryLanguage): String {
        return when (lang) {
            CommentaryLanguage.ENGLISH -> "Great question! With ${match.homeTeam}'s possession at ${match.stats.possessionHome}%, the key will be converting chances in the final third!"
            CommentaryLanguage.ARABIC -> "سؤال ممتاز! بالنظر إلى نسبة الاستحواذ (${match.stats.possessionHome}%)، فإن الحسم سيكون في السرعة والإنهاء أمام المرمى!"
            CommentaryLanguage.MALINKE -> "A niyi je! Balla mara ye ${match.stats.possessionHome}% ye ${match.homeTeam} fɛ, fɔ ka balla don goolu kɔnɔ sisan!"
            else -> "Excellente question ! Vu la possession de ${match.homeTeam} (${match.stats.possessionHome}%), le tournant du match se jouera sur l'efficacité dans les 30 derniers mètres !"
        }
    }

    /**
     * Gemini AI Analysis dedicated to processing Mercato and Transfer News
     * cross-referencing Le Parisien, Foot Mercato and Le Monde.
     */
    suspend fun analyzeMercatoWithGemini(
        items: List<MercatoNewsItem>,
        userQuery: String? = null
    ): String = withContext(Dispatchers.IO) {
        val titlesSummary = items.take(8).joinToString("\n") {
            "- [${it.source} - Fiabilité: ${it.reliabilityIndex}%] ${it.title} (Joueur/Club: ${it.playerOrClub}, Type: ${it.transferType}, Montant: ${it.estimatedFee})"
        }

        val prompt = if (!userQuery.isNullOrBlank()) {
            """
            Tu es l'analyste IA Mercato Football de 'FootIA Live', expert du marché des transferts européen et spécialiste des informations de Le Parisien, Foot Mercato et Le Monde.
            
            Voici les dernières informations et dépêches du marché des transferts :
            $titlesSummary
            
            L'utilisateur te pose une question spécifique sur le mercato :
            "$userQuery"
            
            Réponds de façon experte, documentée et captivante :
            1. Réponse directe avec le statut du dossier
            2. Confrontation des sources (Le Parisien pour le PSG/Ligue 1, Foot Mercato pour les rumeurs européennes, Le Monde pour les aspects financiers et réglementaires)
            3. Indice de faisabilité en %
            4. Conclusion percutante.
            """.trimIndent()
        } else {
            """
            Tu es l'analyste en chef IA Mercato de 'FootIA Live'.
            Voici la revue de presse mercato du jour croisée entre Le Parisien, Foot Mercato et Le Monde :
            $titlesSummary
            
            Génère une synthèse exclusive 'Flash Mercato Gemini' structurée avec des émojis :
            🔥 **Synthèse des Dossiers Brûlants** (Les 3 mouvements majeurs)
            📰 **Le Parisien & PSG** (Les coulisses exclusives parisiennes)
            ⚡ **Foot Mercato Radar** (Les rumeurs européennes chaudes)
            💼 **Le Monde & Économie** (L'angle financier et fair-play)
            🔮 **Le Pronostic Transfert Gemini** (Le prochain transfert officiel)
            
            Sois ultra précis, incisif et professionnel.
            """.trimIndent()
        }

        callFirebaseAiText(prompt) ?: getFallbackMercatoAnalysis(items, userQuery)
    }

    private fun getFallbackMercatoAnalysis(items: List<MercatoNewsItem>, userQuery: String?): String {
        val liveItems = items.filter { !it.id.startsWith("mercato_offline") }
        if (liveItems.isEmpty()) {
            return """
            ⚠️ **SYNTHÈSE MERCATO GEMINI**
            
            Aucune dépêche en direct n'a encore été chargée. Appuyez sur **Actualiser** pour synchroniser les flux RSS en direct de **Le Parisien**, **Foot Mercato** et **Le Monde**.
            """.trimIndent()
        }

        if (!userQuery.isNullOrBlank()) {
            val queryTokens = userQuery.lowercase()
                .split(Regex("[^a-zàâäéèêëïîôöùûüç0-9]+"))
                .filter { it.length >= 3 }

            val matchingItems = liveItems.filter { item ->
                val haystack = "${item.title} ${item.summary} ${item.playerOrClub} ${item.source} ${item.transferType}".lowercase()
                queryTokens.any { token -> haystack.contains(token) }
            }

            return if (matchingItems.isNotEmpty()) {
                val topMatches = matchingItems.take(5).joinToString("\n\n") { item ->
                    "• **[${item.source} • ${item.publishedAt}]** ${item.title}\n" +
                        "  ↳ Dossier : *${item.playerOrClub}* (${item.transferType} • ${item.estimatedFee} • Fiabilité ${item.reliabilityIndex}%)"
                }
                val avgReliability = matchingItems.map { it.reliabilityIndex }.average().toInt()
                """
                🔍 **Analyse Mercato Gemini : "$userQuery"**
                
                📌 **${matchingItems.size} dépêche(s) en direct trouvée(s) :**
                
                $topMatches
                
                💡 **Verdict IA :** Recoupement issu des flux temps réel (**indice moyen de fiabilité : $avgReliability%**). Consultez les fiches ci-dessous pour lire les articles complets.
                """.trimIndent()
            } else {
                val latestTopics = liveItems.take(4).joinToString("\n") {
                    "• [${it.source} • ${it.publishedAt}] ${it.title}"
                }
                """
                🔍 **Analyse Mercato Gemini : "$userQuery"**
                
                ℹ️ Aucun titre récent de **Le Parisien**, **Foot Mercato** ou **Le Monde** ne mentionne directement "$userQuery" dans les dernières dépêches synchronisées.
                
                📰 **Tendances actuelles en direct :**
                $latestTopics
                """.trimIndent()
            }
        }

        val parisien = liveItems.filter { it.source.contains("Parisien", ignoreCase = true) }.take(2)
        val footMercato = liveItems.filter { it.source.contains("Foot Mercato", ignoreCase = true) }.take(3)
        val leMonde = liveItems.filter { it.source.contains("Le Monde", ignoreCase = true) }.take(2)

        val sb = StringBuilder()
        sb.appendLine("🔥 **FLASH MERCATO EN DIRECT • LE PARISIEN, FOOT MERCATO & LE MONDE**")
        sb.appendLine()

        if (footMercato.isNotEmpty()) {
            sb.appendLine("⚡ **Radar Foot Mercato (Temps Réel)**")
            footMercato.forEach {
                sb.appendLine("• **${it.title}** (${it.publishedAt} • ${it.transferType})")
            }
            sb.appendLine()
        }

        if (parisien.isNotEmpty()) {
            sb.appendLine("📰 **Le Parisien • PSG & Ligue 1**")
            parisien.forEach {
                sb.appendLine("• **${it.title}** (${it.publishedAt} • Fiabilité ${it.reliabilityIndex}%)")
            }
            sb.appendLine()
        }

        if (leMonde.isNotEmpty()) {
            sb.appendLine("🗞️ **Le Monde Football • Analyses & Coulisses**")
            leMonde.forEach {
                sb.appendLine("• **${it.title}** (${it.publishedAt} • Fiabilité ${it.reliabilityIndex}%)")
            }
            sb.appendLine()
        }

        sb.append("🔮 **Synthèse IA :** ${liveItems.size} dépêches en direct analysées et triées par fraîcheur.")
        return sb.toString().trim()
    }
}
