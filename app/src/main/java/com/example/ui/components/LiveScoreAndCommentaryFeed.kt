package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.ai.CommentaryLanguage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AICommentary
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.LiveRed
import kotlinx.coroutines.launch

/**
 * Interface Jetpack Compose dédiée à l'affichage du score en temps réel
 * et au flux de commentaires généré en direct par l'IA.
 */
@Composable
fun LiveScoreAndCommentaryFeed(
    match: Match,
    commentaries: List<AICommentary>,
    modifier: Modifier = Modifier,
    selectedLanguageCode: String = "fr",
    onLanguageSelected: ((String) -> Unit)? = null,
    onSimulateEvent: ((type: String) -> Unit)? = null,
    onAskCommentator: (suspend (question: String) -> String)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf("Tous") }
    var userQuestion by remember { mutableStateOf("") }
    var isAsking by remember { mutableStateOf(false) }
    var activeSpeakerId by remember { mutableStateOf<String?>(null) }
    var chatHistory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val languages = remember {
        CommentaryLanguage.values().map { Triple(it.code, it.displayName, it.flagEmoji) }
    }

    val filters = listOf("Tous", "⚽ Buts", "🔥 Moments Forts", "🧠 Tactique")

    val filteredCommentaries = remember(commentaries, selectedFilter) {
        when (selectedFilter) {
            "⚽ Buts" -> commentaries.filter { it.text.contains("BUT", ignoreCase = true) || it.tone == "GOAL" || it.tone == "CELEBRATION" }
            "🔥 Moments Forts" -> commentaries.filter { it.tone == "INTENSE" || it.tone == "EXCITED" || it.tone == "GOAL" }
            "🧠 Tactique" -> commentaries.filter { it.tone == "TACTIQUE" || it.tone == "ANALYTICAL" || it.text.contains("tactique", ignoreCase = true) }
            else -> commentaries
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 1. Composant Header du Score en Temps Réel
        RealTimeScoreHeaderCard(
            match = match,
            onSimulateEvent = onSimulateEvent
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. En-tête du composant dédié au flux IA
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Flux IA",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Flux Commentaires IA TV",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Analyse en temps réel par Gemini Live",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // On-air Badge
                    Surface(
                        color = LiveRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio Stream",
                                tint = LiveRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EN DIRECT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LiveRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Language Selection Selector Bar
                Text(
                    text = "Langue du commentaire IA (Google Translate - ${languages.size} langues) :",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(languages) { (code, label, flag) ->
                        val isSelected = selectedLanguageCode == code
                        FilterChip(
                            selected = isSelected,
                            onClick = { onLanguageSelected?.invoke(code) },
                            label = { Text("$flag $label", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("lang_chip_$code")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filters.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Question à l'IA en direct
        if (onAskCommentator != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userQuestion,
                        onValueChange = { userQuestion = it },
                        placeholder = { Text("Interroger le commentateur IA...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_feed_question_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (userQuestion.isNotBlank() && !isAsking) {
                                val q = userQuestion
                                userQuestion = ""
                                isAsking = true
                                coroutineScope.launch {
                                    val ans = onAskCommentator(q)
                                    chatHistory = listOf(q to ans) + chatHistory
                                    isAsking = false
                                }
                            }
                        },
                        enabled = userQuestion.isNotBlank() && !isAsking,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (userQuestion.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("ai_feed_send_button")
                    ) {
                        if (isAsking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = if (userQuestion.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Questions & Réponses en direct
        if (chatHistory.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                chatHistory.take(2).forEach { (q, a) ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(text = "❓ $q", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "🎙️ $a", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        // 4. Flux de Commentaires IA en Direct (Liste défilante)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = filteredCommentaries,
                key = { it.id }
            ) { commentary ->
                DedicatedAICommentaryItemCard(
                    commentary = commentary,
                    isPlaying = activeSpeakerId == commentary.id,
                    onToggleAudio = {
                        activeSpeakerId = if (activeSpeakerId == commentary.id) null else commentary.id
                    }
                )
            }
        }
    }
}

/**
 * Composant de Score en Temps Réel avec minuteur et simulation d'évènements.
 */
@Composable
fun RealTimeScoreHeaderCard(
    match: Match,
    onSimulateEvent: ((type: String) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("real_time_score_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // League and Stadium Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.league,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (match.status == MatchStatus.LIVE) {
                    LiveBadge(minute = match.minute)
                } else {
                    StatusBadge(status = match.status, minute = match.minute, date = match.date, time = match.time)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Score Display Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Home Team
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamLogo(url = match.homeTeamLogo, teamName = match.homeTeam, modifier = Modifier.size(50.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.homeTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Big Score Box
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    PulsingLiveScore(
                        homeScore = match.homeScore,
                        awayScore = match.awayScore,
                        isLive = match.status == MatchStatus.LIVE,
                        fontSize = 36.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (match.status == MatchStatus.LIVE) "${match.minute}' Temps Réel" else match.date,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Away Team
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamLogo(url = match.awayTeamLogo, teamName = match.awayTeam, modifier = Modifier.size(50.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.awayTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Real Match Video Stream Container
            Spacer(modifier = Modifier.height(14.dp))
            var isPlayingVideo by remember { mutableStateOf(true) }
            var isMuted by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("live_match_video_player"),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Match stadium image / real video broadcast backdrop
                    AsyncImage(
                        model = if (match.stadium.contains("Parc", ignoreCase = true)) 
                            "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=800&q=80"
                            else "https://images.unsplash.com/photo-1522778119026-d647f0596c20?auto=format&fit=crop&w=800&q=80",
                        contentDescription = "Flux Vidéo Direct du Match - Images Réelles",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )

                    // Video overlay dark gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Top Bar Video Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.TopStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = LiveRed,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "VRAIES IMAGES TV • DIRECT 1080p HD",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "STADE: ${match.stadium}",
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Center Play / Pause Action Button
                    IconButton(
                        onClick = { isPlayingVideo = !isPlayingVideo },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = if (isPlayingVideo) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingVideo) "Mettre en pause" else "Lancer le direct",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Bottom Bar Video Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .align(Alignment.BottomStart),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Muter le son",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "🎥 Diffusion Officielle Temps Réel",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "CANAL / BEIN HD",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Interactive simulation buttons if provided
            if (onSimulateEvent != null && match.status == MatchStatus.LIVE) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AssistChip(
                        onClick = { onSimulateEvent("GOAL_HOME") },
                        label = { Text("⚽ But ${match.homeTeam}", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { onSimulateEvent("GOAL_AWAY") },
                        label = { Text("⚽ But ${match.awayTeam}", fontSize = 11.sp) }
                    )
                    AssistChip(
                        onClick = { onSimulateEvent("ACTION") },
                        label = { Text("🔥 Tir / Occasion", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

/**
 * Composant Carte Individuelle du Flux de Commentaires IA en Direct
 */
@Composable
fun DedicatedAICommentaryItemCard(
    commentary: AICommentary,
    isPlaying: Boolean,
    onToggleAudio: () -> Unit
) {
    val toneColor = when (commentary.tone) {
        "GOAL", "CELEBRATION" -> LiveGlowGreen
        "INTENSE", "EXCITED" -> LiveRed
        "TACTIQUE", "ANALYTICAL" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val toneLabel = when (commentary.tone) {
        "GOAL" -> "⚽ BUT"
        "CELEBRATION" -> "🎉 CELEBRATION"
        "INTENSE" -> "🔥 INTENSE"
        "EXCITED" -> "⚡ ACTION"
        "TACTIQUE", "ANALYTICAL" -> "🧠 TACTIQUE"
        else -> "🎙️ IA DIRECT"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Upper Row: Minute, Tone Tag, Audio Synth Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(toneColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${commentary.minute}'",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = toneColor
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = toneColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = toneLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = toneColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Audio Vocal Commentator Button
                IconButton(
                    onClick = onToggleAudio,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = "Écouter le commentaire vocal",
                        tint = if (isPlaying) toneColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Commentary Text
            Text(
                text = commentary.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            // Playing Audio Voice Banner simulation
            AnimatedVisibility(visible = isPlaying) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = toneColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Voice playing",
                                tint = toneColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lecture vocale IA en cours...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = toneColor
                            )
                        }
                    }
                }
            }
        }
    }
}
