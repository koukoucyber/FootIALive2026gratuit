package com.example.ui.components

import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AICommentary
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.LiveRed
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Composant UI style 'télévision' (TV Broadcast Chyron & Plateau TV)
 * positionné en haut de l'écran pour afficher le flux de commentaires IA en direct :
 * - Bandeau TV haute fidélité avec badge On-Air 🔴 DIRECT ANTENNE
 * - Visualiseur d'ondes audio / equalizer animé
 * - Synthèse vocale native Android (Text-to-Speech) pour écouter les commentaires du journaliste TV
 * - Navigation par carrousel / timecode des interventions
 * - Bandeau interactif pour interroger les consultants du plateau TV
 */
@Composable
fun TvBroadcastCommentaryBar(
    match: Match,
    commentaries: List<AICommentary>,
    modifier: Modifier = Modifier,
    onAskCommentator: (suspend (question: String) -> String)? = null,
    onSimulateEvent: ((type: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Commentator Mode: CLASSIC vs YA2NE (TCHIEEEEY !)
    var isYa2neMode by remember { mutableStateOf(true) } // Active by default for immediate fun

    // Local TextToSpeech engine
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var ya2neAnimatedTrigger by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        tts?.language = Locale.FRENCH
                        isTtsReady = true
                    } catch (_: Exception) {
                    }
                }
            }
            ttsEngine = tts
        } catch (_: Exception) {
            isTtsReady = false
        }
        onDispose {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    // Configure TTS voice depending on active mode (Ya2ne = high pitch, fast energetic rhythm)
    LaunchedEffect(isYa2neMode, isTtsReady) {
        ttsEngine?.let { tts ->
            if (isYa2neMode) {
                tts.setPitch(1.35f) // High energy / excited voice
                tts.setSpeechRate(1.22f) // Fast tempo shouting reaction
            } else {
                tts.setPitch(1.0f)
                tts.setSpeechRate(1.05f)
            }
        }
    }

    // Auto-clear Ya2ne animated trigger
    LaunchedEffect(ya2neAnimatedTrigger) {
        if (ya2neAnimatedTrigger != null) {
            kotlinx.coroutines.delay(4000L)
            ya2neAnimatedTrigger = null
        }
    }

    // Active commentary index (default to latest)
    var currentCommentaryIndex by remember { mutableIntStateOf(0) }
    val displayList = remember(commentaries, isYa2neMode) {
        val baseList = commentaries.ifEmpty {
            listOf(
                AICommentary(
                    id = "init_comm",
                    minute = match.minute,
                    text = "Bienvenue sur FootIA TV ! Les deux équipes sont prêtes, coup d'envoi imminent avec une intensité maximale.",
                    tone = "INTENSE"
                )
            )
        }
        if (isYa2neMode) {
            listOf(
                AICommentary(
                    id = "ya2ne_welcome",
                    minute = match.minute,
                    text = "🔥 TCHIEEEEY ! Mode Ya2ne Activé ! Préparez les brancards et les Ubers, dès qu'il y a un petit pont ou un cassage de reins, on va les éteindre !",
                    tone = "YA2NE"
                )
            ) + baseList
        } else {
            baseList
        }
    }

    val activeCommentary = displayList.getOrNull(currentCommentaryIndex) ?: displayList.first()

    // Expanded Full History Sheet Dialog
    var showFullTranscriptDialog by remember { mutableStateOf(false) }

    // Quick AI Question Input state
    var quickQuestionInput by remember { mutableStateOf("") }
    var isAskingAI by remember { mutableStateOf(false) }
    var aiAnswerText by remember { mutableStateOf<String?>(null) }

    // Auto-advance or keep up with new incoming live commentaries
    LaunchedEffect(commentaries.size) {
        if (commentaries.isNotEmpty()) {
            currentCommentaryIndex = 0
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tv_broadcast_commentary_bar")
    ) {
        // --- YA2NE HUD POPUP BADGE (When humiliating dribble happens) ---
        AnimatedVisibility(
            visible = ya2neAnimatedTrigger != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Surface(
                color = Color(0xFFFF4500), // Vibrant fiery orange-red
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD700)), // Gold rim
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "😱", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TCHIEEEEY ! CASSAGE DE REINS DÉTECTÉ !",
                            color = Color.Yellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = ya2neAnimatedTrigger ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // --- 1. TV BROADCAST TOP BANNER (Lower Third / Chyron Style) ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isYa2neMode) 2.dp else 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = if (isYa2neMode) listOf(
                            Color(0xFFFF0055),
                            Color(0xFFFF9900),
                            Color(0xFFFFEE00)
                        ) else listOf(
                            Color(0xFFE50914).copy(alpha = 0.8f),
                            Color(0xFFF59E0B).copy(alpha = 0.6f),
                            Color(0xFF3B82F6).copy(alpha = 0.7f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            color = if (isYa2neMode) Color(0xFF1A0A2A) else Color(0xFF0F172A), // Funky purple/dark for Ya2ne
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isYa2neMode) listOf(
                                Color(0xFF2D0B42).copy(alpha = 0.95f),
                                Color(0xFF13031E).copy(alpha = 0.98f)
                            ) else listOf(
                                Color(0xFF1E293B).copy(alpha = 0.95f),
                                Color(0xFF0B132B).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                // TV TOP ROW: Mode Selector, Channel Badge, Score Ticker, TTS Voice
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mode Toggle: TV Pro vs Mode Ya2ne
                    Surface(
                        color = if (isYa2neMode) Color(0xFFFF4500) else Color(0xFFE50914),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            isYa2neMode = !isYa2neMode
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isYa2neMode) "🔥 MODE YA2NE" else "🎙️ TV PRO",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = Color.Black.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isYa2neMode) "TCHIEEEEY" else "DIRECT",
                                    color = if (isYa2neMode) Color.Yellow else Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Center Scoreboard TV Chyron
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = match.homeTeam.take(3).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${match.homeScore} - ${match.awayScore}",
                                color = if (isYa2neMode) Color.Yellow else LiveGlowGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = match.awayTeam.take(3).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Equalizer & TTS Voice Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvEqualizerVisualizer(isSpeaking = isSpeaking)
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (isTtsReady) {
                                    if (isSpeaking) {
                                        ttsEngine?.stop()
                                        isSpeaking = false
                                    } else {
                                        val textToSpeak = if (isYa2neMode && activeCommentary.tone == "YA2NE") {
                                            activeCommentary.text.replace("🔥", "").replace("😱", "")
                                        } else {
                                            activeCommentary.text
                                        }
                                        ttsEngine?.speak(
                                            textToSpeak,
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            activeCommentary.id
                                        )
                                        isSpeaking = true
                                        coroutineScope.launch {
                                            // Auto reset speaker state after reading
                                            kotlinx.coroutines.delay((textToSpeak.length * 60L).coerceIn(3000L, 12000L))
                                            isSpeaking = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSpeaking) (if (isYa2neMode) Color(0xFFFF4500) else Color(0xFFE50914)) else Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Écouter la voix",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- 2. TV COMMENTARY TEXT CHYRON ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Persona Icon & Tone Pill
                    val toneColor = when (activeCommentary.tone) {
                        "YA2NE" -> Color(0xFFFF9900)
                        "GOAL", "CELEBRATION" -> LiveGlowGreen
                        "INTENSE", "EXCITED" -> Color(0xFFF59E0B)
                        "TACTIQUE", "ANALYTICAL" -> Color(0xFF38BDF8)
                        else -> Color.White
                    }

                    val toneLabel = when (activeCommentary.tone) {
                        "YA2NE" -> "🔥 CLASH TCHIEEEEY !"
                        "GOAL" -> "⚽ BUT !"
                        "CELEBRATION" -> "🎉 EXPLOSION"
                        "TACTIQUE", "ANALYTICAL" -> "🧠 TACTIQUE"
                        "EXCITED" -> "🔥 CHAUD"
                        else -> "🎙️ EN DIRECT"
                    }

                    Surface(
                        color = toneColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, toneColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = toneLabel,
                            color = toneColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isYa2neMode) "Micro Ya2ne (@ya2ne_) en direct :" else "Plateau TV • Margotton & Consultant IA :",
                        color = if (isYa2neMode) Color(0xFFFFCC00) else Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Animated Commentary Content Box
                AnimatedContent(
                    targetState = activeCommentary,
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    },
                    label = "tv_commentary_transition"
                ) { comm ->
                    Text(
                        text = comm.text,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- 3. BOTTOM CONTROLS & PAGINATION BAR ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pagination & Browsing Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (currentCommentaryIndex < displayList.size - 1) {
                                    currentCommentaryIndex++
                                    ttsEngine?.stop()
                                    isSpeaking = false
                                }
                            },
                            enabled = currentCommentaryIndex < displayList.size - 1,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Commentaire précédent",
                                tint = if (currentCommentaryIndex < displayList.size - 1) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "${currentCommentaryIndex + 1} / ${displayList.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                if (currentCommentaryIndex > 0) {
                                    currentCommentaryIndex--
                                    ttsEngine?.stop()
                                    isSpeaking = false
                                }
                            },
                            enabled = currentCommentaryIndex > 0,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Commentaire suivant",
                                tint = if (currentCommentaryIndex > 0) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Open Full TV Log button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFullTranscriptDialog = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Fil TV Complet >",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 4. QUICK INTERACTIVE TV CONSULTANT & YA2NE CLASH PROMPTS ---
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = if (isYa2neMode) "🔥 Ya2ne Clash :" else "🎤 Plateau TV :",
                        color = if (isYa2neMode) Color(0xFFFF9900) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isYa2neMode) {
                    item {
                        TvPromptChip(
                            text = "😱 Petit Pont Humiliant !",
                            onClick = {
                                val (clashText, spokenText) = com.example.data.util.Ya2neClashCommentator.getRandomClashReaction(match.homeTeam)
                                ya2neAnimatedTrigger = clashText
                                onSimulateEvent?.invoke("YA2NE_DRIBBLE")
                                if (isTtsReady) {
                                    ttsEngine?.stop()
                                    ttsEngine?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "ya2ne_clash_${System.currentTimeMillis()}")
                                    isSpeaking = true
                                }
                            }
                        )
                    }

                    item {
                        TvPromptChip(
                            text = "⚡ Cassage de Reins !",
                            onClick = {
                                val (clashText, spokenText) = com.example.data.util.Ya2neClashCommentator.getRandomClashReaction(match.awayTeam)
                                ya2neAnimatedTrigger = clashText
                                onSimulateEvent?.invoke("YA2NE_DRIBBLE")
                                if (isTtsReady) {
                                    ttsEngine?.stop()
                                    ttsEngine?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "ya2ne_clash_${System.currentTimeMillis()}")
                                    isSpeaking = true
                                }
                            }
                        )
                    }

                    item {
                        TvPromptChip(
                            text = "🚗 Envoyez un Uber !",
                            onClick = {
                                val spokenText = "Tchieeey ! Envoyez-lui un Uber de toute urgence ! Il est perdu sur la pelouse !"
                                ya2neAnimatedTrigger = "TCHIEEEEY ! Le défenseur a commandé son Uber direct au bord du terrain !"
                                onSimulateEvent?.invoke("YA2NE_DRIBBLE")
                                if (isTtsReady) {
                                    ttsEngine?.stop()
                                    ttsEngine?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, "ya2ne_uber_${System.currentTimeMillis()}")
                                    isSpeaking = true
                                }
                            }
                        )
                    }
                } else if (onAskCommentator != null) {
                    item {
                        TvPromptChip(
                            text = "Analyse du but ⚽",
                            onClick = {
                                coroutineScope.launch {
                                    isAskingAI = true
                                    aiAnswerText = onAskCommentator("Quelle est l'analyse tactique des commentateurs TV sur les buts de ce match ?")
                                    isAskingAI = false
                                }
                            }
                        )
                    }

                    item {
                        TvPromptChip(
                            text = "Qui domine ? 📊",
                            onClick = {
                                coroutineScope.launch {
                                    isAskingAI = true
                                    aiAnswerText = onAskCommentator("Qui domine le match et quelle équipe a l'ascendant tactique actuellement ?")
                                    isAskingAI = false
                                }
                            }
                        )
                    }

                    item {
                        TvPromptChip(
                            text = "Pronostic TV 🔮",
                            onClick = {
                                coroutineScope.launch {
                                    isAskingAI = true
                                    aiAnswerText = onAskCommentator("Quel est le pronostic du plateau télévisé pour l'issue de cette rencontre ?")
                                    isAskingAI = false
                                }
                            }
                        )
                    }
                }
            }
        }

        // Quick AI Answer Card Overlay
        if (isAskingAI || aiAnswerText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Régie TV • Intervention en Direct :",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (isAskingAI) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Le consultant TV analyse les images...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            Text(
                                text = aiAnswerText ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = { aiAnswerText = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    // --- 5. FULL TRANSCRIPT DIALOG (Plateau TV Complet) ---
    if (showFullTranscriptDialog) {
        AlertDialog(
            onDismissRequest = { showFullTranscriptDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color(0xFFE50914)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Plateau TV • Journal des Commentaires",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayList) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Minute ${item.minute}'",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = item.tone,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.text,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullTranscriptDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

/**
 * Animated Audio Equalizer Visualizer Bars simulating live TV broadcast speech.
 */
@Composable
fun TvEqualizerVisualizer(isSpeaking: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "tv_equalizer")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isSpeaking) 16f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = if (isSpeaking) 20f else 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = if (isSpeaking) 18f else 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = if (isSpeaking) 14f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .height(20.dp)
            .padding(horizontal = 4.dp)
    ) {
        val barColor = if (isSpeaking) LiveGlowGreen else Color.White.copy(alpha = 0.5f)
        Box(modifier = Modifier.width(3.dp).height(h1.dp).clip(RoundedCornerShape(1.dp)).background(barColor))
        Box(modifier = Modifier.width(3.dp).height(h2.dp).clip(RoundedCornerShape(1.dp)).background(barColor))
        Box(modifier = Modifier.width(3.dp).height(h3.dp).clip(RoundedCornerShape(1.dp)).background(barColor))
        Box(modifier = Modifier.width(3.dp).height(h4.dp).clip(RoundedCornerShape(1.dp)).background(barColor))
    }
}

@Composable
private fun TvPromptChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
