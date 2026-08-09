package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AICommentary
import com.example.data.model.EventType
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.components.AICommentaryCard
import com.example.ui.components.LiveBadge
import com.example.ui.components.LiveScoreAndCommentaryFeed
import com.example.ui.components.StatProgressBar
import com.example.ui.components.TeamLogo
import com.example.ui.theme.LiveGlowGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    match: Match,
    onBack: () -> Unit,
    onAskAI: suspend (question: String, languageCode: String) -> String,
    onGenerateDebrief: suspend (languageCode: String) -> String,
    onGenerateLiveCommentary: (suspend (match: Match, minute: Int, actionPrompt: String?, languageCode: String) -> AICommentary)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Direct (Stream Live)", "Statistiques", "Compositions", "Résumé IA")

    var selectedLanguageCode by remember { mutableStateOf("fr") }
    var currentMatchState by remember(match) { mutableStateOf(match) }
    var currentCommentaries by remember(match) { mutableStateOf(match.aiCommentaries) }

    var isGeneratingDebrief by remember { mutableStateOf(false) }
    var debriefText by remember { mutableStateOf(match.aiSummary ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "${currentMatchState.homeTeam} vs ${currentMatchState.awayTeam}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
            // Tabs Selector
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> { // Composant dédié au score en temps réel et au flux de commentaires IA
                    LiveScoreAndCommentaryFeed(
                        match = currentMatchState,
                        commentaries = currentCommentaries,
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
                                    currentCommentaries = listOf(langComm) + currentCommentaries
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
                                else -> {
                                    "Occasion brûlante dans la surface de réparation avec une superbe envolée du gardien de but !" to currentMatchState.copy(minute = nextMin)
                                }
                            }
                            currentMatchState = updatedMatch

                            coroutineScope.launch {
                                val newComm = if (onGenerateLiveCommentary != null) {
                                    onGenerateLiveCommentary(updatedMatch, nextMin, actionPrompt, selectedLanguageCode)
                                } else {
                                    AICommentary(
                                        id = "comm_${System.currentTimeMillis()}",
                                        minute = nextMin,
                                        text = "⚽ $nextMin' - $actionPrompt",
                                        tone = if (eventType.startsWith("GOAL")) "GOAL" else "INTENSE"
                                    )
                                }
                                currentCommentaries = listOf(newComm) + currentCommentaries
                            }
                        },
                        onAskCommentator = { question -> onAskAI(question, selectedLanguageCode) }
                    )
                }

                1 -> { // Match Statistics
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
                                    Text(text = "Statistiques de la rencontre", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    StatProgressBar("Possession", match.stats.possessionHome, match.stats.possessionAway, isPercentage = true)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Tirs Totaux", match.stats.shotsHome, match.stats.shotsAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Tirs Cadrés", match.stats.shotsOnTargetHome, match.stats.shotsOnTargetAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Passes", match.stats.passesHome, match.stats.passesAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Précision Passes", match.stats.passAccuracyHome, match.stats.passAccuracyAway, isPercentage = true)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Fautes", match.stats.foulsHome, match.stats.foulsAway)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    StatProgressBar("Cartons Jaunes", match.stats.yellowCardsHome, match.stats.yellowCardsAway)
                                }
                            }
                        }
                    }
                }

                2 -> { // Lineups / Compositions
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
                                        Text(text = "${match.homeTeam} (${match.lineup.homeFormation})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        match.lineup.homeStarting11.forEach { p ->
                                            Text(text = "${p.number}. ${p.name} (${p.position})", fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = "${match.awayTeam} (${match.lineup.awayFormation})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        match.lineup.awayStarting11.forEach { p ->
                                            Text(text = "${p.number}. ${p.name} (${p.position})", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> { // AI Match Summary / Debrief
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
