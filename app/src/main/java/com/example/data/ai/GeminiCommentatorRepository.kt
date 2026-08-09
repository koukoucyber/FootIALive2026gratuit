package com.example.data.ai

import com.example.BuildConfig
import com.example.data.model.AICommentary
import com.example.data.model.Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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
            return values().find { it.code == code } ?: FRENCH
        }
    }
}

class GeminiCommentatorRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val apiKey: String = try {
        BuildConfig.GEMINI_API_KEY
    } catch (e: Exception) {
        ""
    }

    /**
     * Generates dynamic TV commentary for a live match action in the specified language (French, English, Arabic, Malinké).
     */
    suspend fun generateLiveCommentary(
        match: Match,
        minute: Int,
        actionPrompt: String? = null,
        languageCode: String = "fr"
    ): AICommentary = withContext(Dispatchers.IO) {
        val lang = CommentaryLanguage.values().find { it.code == languageCode } ?: CommentaryLanguage.FRENCH

        val prompt = if (actionPrompt != null) {
            "Tu es un commentateur télé de football professionnel et hyper passionné. Rédige ton commentaire ${lang.promptInstruction}. " +
                    "Commente cette action du match ${match.homeTeam} (${match.homeScore}) vs ${match.awayTeam} (${match.awayScore}) à la ${minute}e minute dans la ligue ${match.league}: '$actionPrompt'. " +
                    "Sois immersif, dynamique, utilise le vocabulaire et l'énergie d'un grand match en direct. Max 2-3 phrases."
        } else {
            "Tu es un commentateur télé de football en direct. Rédige ton commentaire ${lang.promptInstruction}. " +
                    "Génère un commentaire court (2 phrases max) à la ${minute}e minute pour le match ${match.homeTeam} (${match.homeScore}) vs ${match.awayTeam} (${match.awayScore}) (${match.league}). " +
                    "Décris une action réaliste (passe en profondeur, dribble spectaculaire, arrêt reflexe du gardien, tacle glissé ou feinte)."
        }

        val aiText = callGeminiApi(prompt) ?: getRandomFallbackCommentary(match, minute, actionPrompt, lang)

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
        val lang = CommentaryLanguage.values().find { it.code == languageCode } ?: CommentaryLanguage.FRENCH

        val prompt = "Tu es un consultant tactique de football chevronné pour 'FootIA Live'. " +
                "Rédige un résumé/débrief synthétique et captivant ${lang.promptInstruction} pour le match ${match.homeTeam} (${match.homeScore}) - (${match.awayTeam}) (${match.awayScore}) joué au ${match.stadium} (${match.league}). " +
                "Inclut les points clés (possession, efficacité devant le but, tournant du match, joueur clé). Structure en 3 paragraphes courts et percutants avec des émojis."

        callGeminiApi(prompt) ?: getFallbackSummary(match, lang)
    }

    /**
     * Interactive chat feature with AI Commentator in the selected language.
     */
    suspend fun askCommentator(match: Match, userQuestion: String, languageCode: String = "fr"): String = withContext(Dispatchers.IO) {
        val lang = CommentaryLanguage.values().find { it.code == languageCode } ?: CommentaryLanguage.FRENCH

        val prompt = "Tu es le commentateur IA vedette de 'FootIA Live' pour le match ${match.homeTeam} vs ${match.awayTeam} (${match.league}). " +
                "Score actuel: ${match.homeScore} - ${match.awayScore} (${match.minute}' min). " +
                "Statistiques: Possession ${match.stats.possessionHome}% - ${match.stats.possessionAway}%, Tirs ${match.stats.shotsHome} - ${match.stats.shotsAway}. " +
                "L'utilisateur te demande: '$userQuestion'. Réponds-lui comme un commentateur passionné et avisé ${lang.promptInstruction} en 2-3 phrases."

        callGeminiApi(prompt) ?: getFallbackAskResponse(match, lang)
    }

    private fun callGeminiApi(prompt: String): String? {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return null

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: return null
                    val jsonResponse = JSONObject(responseStr)
                    val candidates = jsonResponse.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            return parts.getJSONObject(0).optString("text")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
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
}

