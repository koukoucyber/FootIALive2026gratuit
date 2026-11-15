package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import com.example.data.ai.AiPostMatchVideoSummaryState
import com.example.data.model.AICommentary
import com.example.data.model.EventType
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.components.AICommentaryCard
import com.example.ui.components.LiveBadge
import com.example.ui.components.LivePossessionPieChartCard
import com.example.ui.components.LiveScoreAndCommentaryFeed
import com.example.ui.components.PublicMatchChat
import com.example.ui.components.StatProgressBar
import com.example.ui.components.TeamLogo
import com.example.ui.theme.LiveGlowGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    match: Match,
    allMatches: List<Match> = emptyList(),
    onSelectMatch: ((Match) -> Unit)? = null,
    onBack: () -> Unit,
    onAskAI: suspend (question: String, languageCode: String) -> String,
    onGenerateDebrief: suspend (languageCode: String) -> String,
    onGenerateLiveCommentary: (suspend (match: Match, minute: Int, actionPrompt: String?, languageCode: String) -> AICommentary)? = null,
    postMatchSummaryState: AiPostMatchVideoSummaryState = AiPostMatchVideoSummaryState.Idle,
    onAnalyzeFinishedHighlights: (suspend (Match) -> AiPostMatchVideoSummaryState)? = null,
    isDarkTheme: Boolean = true,
    onToggleTheme: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    var selectedLanguageCode by remember { mutableStateOf("fr") }
    var currentMatchState by remember(match) { mutableStateOf(match) }
    var currentCommentaries by remember(match) { mutableStateOf(match.aiCommentaries) }

    val tabs = remember(currentMatchState.status) {
        listOf(
            if (currentMatchState.status == MatchStatus.FINISHED) "🎬 Résumé Vidéo IA" else "Direct (Vidéos Réelles)",
            "Chat Public",
            "Statistiques",
            "Compositions",
            "Résumé IA"
        )
    }

    LaunchedEffect(currentMatchState.id, currentMatchState.status) {
        if (currentMatchState.status == MatchStatus.FINISHED) {
            onAnalyzeFinishedHighlights?.invoke(currentMatchState)
        }
    }

    BackHandler(onBack = onBack)

    var isGeneratingDebrief by remember { mutableStateOf(false) }
    var debriefText by remember { mutableStateOf(match.aiSummary ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${currentMatchState.homeTeam} vs ${currentMatchState.awayTeam}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (onToggleTheme != null) {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("detail_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Thème Sombre Nuit"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs Selector (Scrollable so all 5 tab labels remain legible)
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 12.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> { // Composant dédié au score en temps réel et au flux de commentaires IA
                    LiveScoreAndCommentaryFeed(
                        match = currentMatchState,
                        commentaries = currentCommentaries,
                        allMatches = allMatches,
                        onSelectMatch = { selected ->
                            currentMatchState = selected
                            currentCommentaries = selected.aiCommentaries
                            onSelectMatch?.invoke(selected)
                        },
                        selectedLanguageCode = selectedLanguageCode,
                        onLanguageSelected = { lang ->
                            selectedLanguageCode = lang
                            coroutineScope.launch {
                                if (onGenerateLiveCommentary != null) {
                                    val langComm = onGenerateLiveCommentary(
                                        currentMatchState,
                                        currentMatchState.minute,
                                        "Bienvenue sur la transmission en direct !",
                                        lang
                                    )
                                    currentCommentaries = (listOf(langComm) + currentCommentaries).distinctBy { it.id }
                                }
                            }
                        },
                        onSimulateEvent = { eventType ->
                            val nextMin = currentMatchState.minute + 1
                            val (actionPrompt, updatedMatch) = when (eventType) {
                                "GOAL_HOME" -> {
                                    val newHomeScore = currentMatchState.homeScore + 1
                                    "BUT fabuleux inscrit par ${currentMatchState.homeTeam} ! Le score est de $newHomeScore à ${currentMatchState.awayScore}." to currentMatchState.copy(homeScore = newHomeScore, minute = nextMin)
                                }
                                "GOAL_AWAY" -> {
                                    val newAwayScore = currentMatchState.awayScore + 1
                                    "BUT fantastique de ${currentMatchState.awayTeam} ! Le score passe à ${currentMatchState.homeScore} à $newAwayScore." to currentMatchState.copy(awayScore = newAwayScore, minute = nextMin)
                                }
                                "YA2NE_DRIBBLE" -> {
                                    val (clash) = com.example.data.util.Ya2neClashCommentator.getRandomClashReaction(currentMatchState.homeTeam)
                                    clash to currentMatchState.copy(minute = nextMin)
                                }
                                else -> {
                                    "Occasion brûlante dans la surface de réparation avec une superbe envolée du gardien de but !" to currentMatchState.copy(minute = nextMin)
                                }
                            }
                            currentMatchState = updatedMatch

                            coroutineScope.launch {
                                val uniqueId = UUID.randomUUID().toString().take(8)
                                val newComm = if (eventType == "YA2NE_DRIBBLE") {
                                    AICommentary(
                                        id = "comm_ya2ne_$uniqueId",
                                        minute = nextMin,
                                        text = actionPrompt,
                                        tone = "YA2NE"
                                    )
                                } else if (onGenerateLiveCommentary != null) {
                                    onGenerateLiveCommentary(updatedMatch, nextMin, actionPrompt, selectedLanguageCode)
                                } else {
                                    AICommentary(
                                        id = "comm_$uniqueId",
                                        minute = nextMin,
                                        text = "⚽ $nextMin' - $actionPrompt",
                                        tone = if (eventType.startsWith("GOAL")) "GOAL" else "INTENSE"
                                    )
                                }
                                currentCommentaries = (listOf(newComm) + currentCommentaries).distinctBy { it.id }
                            }
                        },
                        onAskCommentator = { question -> onAskAI(question, selectedLanguageCode) },
                        postMatchSummaryState = postMatchSummaryState,
                        onRetryPostMatchAnalysis = if (onAnalyzeFinishedHighlights != null) {
                            {
                                coroutineScope.launch {
                                    onAnalyzeFinishedHighlights(currentMatchState)
                                }
                            }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                1 -> { // Chat Public Gratuit pour le match
                    PublicMatchChat(
                        match = currentMatchState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                2 -> { // Match Statistics
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Statistiques de la rencontre",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        item {
                            LivePossessionPieChartCard(match = currentMatchState)
                        }
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    StatProgressBar("Tirs Totaux", currentMatchState.stats.shotsHome, currentMatchState.stats.shotsAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Tirs Cadrés", currentMatchState.stats.shotsOnTargetHome, currentMatchState.stats.shotsOnTargetAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Passes", currentMatchState.stats.passesHome, currentMatchState.stats.passesAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Précision Passes", currentMatchState.stats.passAccuracyHome, currentMatchState.stats.passAccuracyAway, isPercentage = true)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Fautes", currentMatchState.stats.foulsHome, currentMatchState.stats.foulsAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Cartons Jaunes", currentMatchState.stats.yellowCardsHome, currentMatchState.stats.yellowCardsAway)
                                }
                            }
                        }
                    }
                }

                3 -> { // Lineups / Compositions
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = "${currentMatchState.homeTeam} (${currentMatchState.lineup.homeFormation})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        currentMatchState.lineup.homeStarting11.forEach { p ->
                                            Text(text = "${p.number}. ${p.name} (${p.position})", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = "${currentMatchState.awayTeam} (${currentMatchState.lineup.awayFormation})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        currentMatchState.lineup.awayStarting11.forEach { p ->
                                            Text(text = "${p.number}. ${p.name} (${p.position})", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> { // AI Match Summary / Debrief
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "Débrief Tactique IA Automatique", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                        Button(
                                            onClick = {
                                                isGeneratingDebrief = true
                                                coroutineScope.launch {
                                                    debriefText = onGenerateDebrief(selectedLanguageCode)
                                                    isGeneratingDebrief = false
                                                }
                                            },
                                            enabled = !isGeneratingDebrief,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            if (isGeneratingDebrief) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            else Text("Générer Débrief IA", fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    if (debriefText.isNotEmpty()) {
                                        Text(
                                            text = debriefText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            lineHeight = 22.sp
                                        )
                                    } else {
                                        Text(
                                            text = "Cliquez sur 'Générer Débrief IA' pour obtenir une analyse tactique complète en français de la rencontre.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
