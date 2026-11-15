package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.PredictionChoice
import com.example.data.model.PredictorLeaderboardUser
import com.example.data.model.UserMatchPrediction
import com.example.ui.components.LiveBadge
import com.example.ui.components.PulsingLiveScore
import com.example.ui.components.TeamLogo
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.PrimaryGold
import com.example.util.AppLanguage
import com.example.util.Strings

private enum class PredictionFilter {
    OPEN, LIVE, REAL_MADRID, FINISHED, ALL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionsScreen(
    matches: List<Match>,
    predictions: Map<String, UserMatchPrediction>,
    onMakePrediction: (matchId: String, choice: PredictionChoice, homeScore: Int, awayScore: Int) -> Unit,
    appLanguage: AppLanguage = AppLanguage.FRENCH,
    onToggleLanguage: () -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleTheme: (() -> Unit)? = null,
    onMatchClick: ((Match) -> Unit)? = null,
    onRefreshMatches: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Strings.get("nav_predictions", appLanguage),
        Strings.get("leaderboard", appLanguage)
    )

    val matchById = remember(matches) { matches.associateBy { it.id } }

    // Evaluate real points & accuracy from predictions matched against finished fixtures
    val evalStats = remember(predictions, matchById) {
        var finishedPredicted = 0
        var correctOutcomes = 0
        var exactScores = 0
        var earnedPoints = 0
        var currentStreak = 0

        val sortedFinishedPreds = predictions.values
            .mapNotNull { pred -> matchById[pred.matchId]?.takeIf { it.status == MatchStatus.FINISHED }?.let { pred to it } }
            .sortedByDescending { it.second.kickoffMillis }

        sortedFinishedPreds.forEachIndexed { idx, (pred, m) ->
            finishedPredicted++
            val actualChoice = when {
                m.homeScore > m.awayScore -> PredictionChoice.HOME_WIN
                m.awayScore > m.homeScore -> PredictionChoice.AWAY_WIN
                else -> PredictionChoice.DRAW
            }
            val isCorrectOutcome = pred.choice == actualChoice
            val isExactScore = pred.predictedHomeScore == m.homeScore && pred.predictedAwayScore == m.awayScore
            if (isCorrectOutcome) {
                correctOutcomes++
                earnedPoints += 50
                if (idx == currentStreak) currentStreak++
            }
            if (isExactScore) {
                exactScores++
                earnedPoints += 100
            }
        }

        val pendingCount = (predictions.size - finishedPredicted).coerceAtLeast(0)
        val totalPts = earnedPoints + (pendingCount * 10)
        val accuracyStr = if (finishedPredicted > 0) "${(correctOutcomes * 100) / finishedPredicted}%" else "—"
        QuadrupleStats(
            totalPoints = totalPts,
            finishedPredicted = finishedPredicted,
            correctOutcomes = correctOutcomes,
            accuracyLabel = accuracyStr,
            streak = currentStreak
        )
    }

    val userPredictionCount = predictions.size

    val leaderboardUsers = remember(evalStats.totalPoints, evalStats.correctOutcomes, userPredictionCount, evalStats.streak) {
        listOf(
            PredictorLeaderboardUser(1, "Alex_Prono 🏆", "⚽", "🇫🇷", 450, 9, 11, 6),
            PredictorLeaderboardUser(2, "Mamadou_Goal", "🔥", "🇨🇮", 390, 8, 10, 4),
            PredictorLeaderboardUser(
                rank = 3,
                username = "Vous (Supporter)",
                avatarEmoji = "🎯",
                countryFlag = "🇫🇷",
                points = evalStats.totalPoints,
                correctCount = evalStats.correctOutcomes,
                totalPredictions = userPredictionCount,
                streak = evalStats.streak,
                isUser = true
            ),
            PredictorLeaderboardUser(4, "Sofiane_10", "⭐", "🇩🇿", 280, 6, 9, 2),
            PredictorLeaderboardUser(5, "Sarah_Foot", "🚀", "🇸🇳", 230, 5, 8, 1),
            PredictorLeaderboardUser(6, "Carlos_Madrid", "👑", "🇪🇸", 195, 4, 7, 0),
            PredictorLeaderboardUser(7, "Marco_Milan", "⚡", "🇮🇹", 160, 3, 6, 2)
        ).sortedByDescending { it.points }.mapIndexed { index, user -> user.copy(rank = index + 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = PrimaryGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Strings.get("predictions_title", appLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (onRefreshMatches != null) {
                        IconButton(
                            onClick = onRefreshMatches,
                            modifier = Modifier.testTag("predictions_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser les matchs"
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier.testTag("predictions_lang_toggle")
                    ) {
                        Text(
                            text = if (appLanguage == AppLanguage.FRENCH) "🇬🇧 EN" else "🇫🇷 FR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (onToggleTheme != null) {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("predictions_theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Thème"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            UserPointsHeader(
                userPoints = evalStats.totalPoints,
                userRank = leaderboardUsers.find { it.isUser }?.rank ?: 1,
                predictionsCount = userPredictionCount,
                accuracyLabel = evalStats.accuracyLabel,
                streak = evalStats.streak,
                appLanguage = appLanguage
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> MatchPredictionsList(
                    matches = matches,
                    predictions = predictions,
                    onMakePrediction = onMakePrediction,
                    appLanguage = appLanguage,
                    onMatchClick = onMatchClick,
                    onRefreshMatches = onRefreshMatches
                )
                1 -> LeaderboardView(
                    users = leaderboardUsers
                )
            }
        }
    }
}

private data class QuadrupleStats(
    val totalPoints: Int,
    val finishedPredicted: Int,
    val correctOutcomes: Int,
    val accuracyLabel: String,
    val streak: Int
)

@Composable
private fun UserPointsHeader(
    userPoints: Int,
    userRank: Int,
    predictionsCount: Int,
    accuracyLabel: String,
    streak: Int,
    appLanguage: AppLanguage
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            PrimaryGold.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Strings.get("rules_bonus", appLanguage),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$userPoints PTS",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryGold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = PrimaryGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PrimaryGold)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏆 Rang #$userRank",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    HeaderStatItem(
                        title = Strings.get("my_predictions", appLanguage),
                        value = "$predictionsCount",
                        icon = Icons.Default.CheckCircle
                    )
                    HeaderStatItem(
                        title = Strings.get("accuracy", appLanguage),
                        value = accuracyLabel,
                        icon = Icons.Default.PieChart
                    )
                    HeaderStatItem(
                        title = Strings.get("streak", appLanguage),
                        value = "🔥 $streak",
                        icon = Icons.Default.LocalFireDepartment
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderStatItem(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MatchPredictionsList(
    matches: List<Match>,
    predictions: Map<String, UserMatchPrediction>,
    onMakePrediction: (matchId: String, choice: PredictionChoice, homeScore: Int, awayScore: Int) -> Unit,
    appLanguage: AppLanguage,
    onMatchClick: ((Match) -> Unit)?,
    onRefreshMatches: (() -> Unit)?
) {
    var selectedFilter by remember { mutableStateOf(PredictionFilter.OPEN) }

    val openMatches = remember(matches) {
        matches.filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.UPCOMING }
    }
    val liveMatches = remember(matches) { matches.filter { it.status == MatchStatus.LIVE } }
    val realMatches = remember(matches) {
        matches.filter {
            it.homeTeam.contains("Real Madrid", ignoreCase = true) ||
                it.awayTeam.contains("Real Madrid", ignoreCase = true)
        }
    }
    val finishedMatches = remember(matches) { matches.filter { it.status == MatchStatus.FINISHED } }

    val effectiveFilter = remember(selectedFilter, openMatches.size, matches.size) {
        if (selectedFilter == PredictionFilter.OPEN && openMatches.isEmpty() && matches.isNotEmpty()) {
            PredictionFilter.ALL
        } else selectedFilter
    }

    val displayedMatches = remember(effectiveFilter, matches, openMatches, liveMatches, realMatches, finishedMatches) {
        when (effectiveFilter) {
            PredictionFilter.OPEN -> openMatches
            PredictionFilter.LIVE -> liveMatches
            PredictionFilter.REAL_MADRID -> realMatches
            PredictionFilter.FINISHED -> finishedMatches
            PredictionFilter.ALL -> matches
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = effectiveFilter == PredictionFilter.OPEN,
                    onClick = { selectedFilter = PredictionFilter.OPEN },
                    label = { Text("Ouverts (${openMatches.size})", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = effectiveFilter == PredictionFilter.LIVE,
                    onClick = { selectedFilter = PredictionFilter.LIVE },
                    label = { Text("🔴 En direct (${liveMatches.size})", fontSize = 12.sp) }
                )
            }
            if (realMatches.isNotEmpty()) {
                item {
                    FilterChip(
                        selected = effectiveFilter == PredictionFilter.REAL_MADRID,
                        onClick = { selectedFilter = PredictionFilter.REAL_MADRID },
                        label = { Text("👑 Real Madrid (${realMatches.size})", fontSize = 12.sp) }
                    )
                }
            }
            item {
                FilterChip(
                    selected = effectiveFilter == PredictionFilter.FINISHED,
                    onClick = { selectedFilter = PredictionFilter.FINISHED },
                    label = { Text("Terminés (${finishedMatches.size})", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = effectiveFilter == PredictionFilter.ALL,
                    onClick = { selectedFilter = PredictionFilter.ALL },
                    label = { Text("Tous (${matches.size})", fontSize = 12.sp) }
                )
            }
        }

        if (displayedMatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (matches.isEmpty()) {
                                "Synchronisation des vrais matchs en cours..."
                            } else {
                                "Aucun match dans cette catégorie pour le moment."
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (onRefreshMatches != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onRefreshMatches) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actualiser")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(displayedMatches, key = { it.id }) { match ->
                    PredictionCard(
                        match = match,
                        currentPrediction = predictions[match.id],
                        onMakePrediction = { choice, homeScore, awayScore ->
                            onMakePrediction(match.id, choice, homeScore, awayScore)
                        },
                        onMatchClick = onMatchClick?.let { cb -> { cb(match) } },
                        appLanguage = appLanguage
                    )
                }
            }
        }
    }
}

@Composable
private fun PredictionCard(
    match: Match,
    currentPrediction: UserMatchPrediction?,
    onMakePrediction: (choice: PredictionChoice, homeScore: Int, awayScore: Int) -> Unit,
    onMatchClick: (() -> Unit)?,
    appLanguage: AppLanguage
) {
    var homeScoreInput by remember(match.id, currentPrediction) {
        mutableIntStateOf(currentPrediction?.predictedHomeScore ?: 1)
    }
    var awayScoreInput by remember(match.id, currentPrediction) {
        mutableIntStateOf(currentPrediction?.predictedAwayScore ?: 0)
    }

    val isFinished = match.status == MatchStatus.FINISHED
    val isLive = match.status == MatchStatus.LIVE

    fun choiceFromScore(hs: Int, asScore: Int): PredictionChoice = when {
        hs > asScore -> PredictionChoice.HOME_WIN
        asScore > hs -> PredictionChoice.AWAY_WIN
        else -> PredictionChoice.DRAW
    }

    val cardModifier = Modifier
        .fillMaxWidth()
        .testTag("prediction_card_${match.id}")
        .let { base -> if (onMatchClick != null) base.clickable(onClick = onMatchClick) else base }

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .let { base ->
                        if (onMatchClick != null) {
                            base
                                .testTag("prediction_match_header_${match.id}")
                                .clickable(onClick = onMatchClick)
                        } else base
                    }
                    .padding(bottom = 4.dp)
            ) {
            // Match Header info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = match.league,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        isLive -> LiveBadge(minute = match.minute)
                        isFinished -> Text(
                            text = "${Strings.get("finished", appLanguage)} • ${match.date}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            text = "${match.date} • ${match.time}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (onMatchClick != null) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Ouvrir le match",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teams & Score / VS display
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamLogo(url = match.homeTeamLogo, teamName = match.homeTeam, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.homeTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    if (match.status != MatchStatus.UPCOMING) {
                        PulsingLiveScore(
                            homeScore = match.homeScore,
                            awayScore = match.awayScore,
                            isLive = isLive,
                            fontSize = 22.sp
                        )
                    } else {
                        Text(
                            text = "VS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamLogo(url = match.awayTeamLogo, teamName = match.awayTeam, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = match.awayTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            if (isFinished) {
                FinishedPredictionSummary(
                    match = match,
                    currentPrediction = currentPrediction,
                    appLanguage = appLanguage,
                    onOpenMatch = onMatchClick
                )
            } else {
                Text(
                    text = Strings.get("predict_match", appLanguage),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Outcome 1 / N / 2 buttons with clear full labels (no broken .take(3) or "Côte" typo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PredictionOptionButton(
                        badge = "1",
                        label = match.homeTeam,
                        pointsHint = "+50 pts",
                        isSelected = currentPrediction?.choice == PredictionChoice.HOME_WIN,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pred_btn_home_${match.id}"),
                        onClick = {
                            val adjustedHome = if (homeScoreInput <= awayScoreInput) awayScoreInput + 1 else homeScoreInput
                            homeScoreInput = adjustedHome
                            onMakePrediction(PredictionChoice.HOME_WIN, adjustedHome, awayScoreInput)
                        }
                    )

                    PredictionOptionButton(
                        badge = "N",
                        label = Strings.get("draw", appLanguage),
                        pointsHint = "+50 pts",
                        isSelected = currentPrediction?.choice == PredictionChoice.DRAW,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pred_btn_draw_${match.id}"),
                        onClick = {
                            val drawScore = maxOf(1, minOf(homeScoreInput, awayScoreInput))
                            homeScoreInput = drawScore
                            awayScoreInput = drawScore
                            onMakePrediction(PredictionChoice.DRAW, drawScore, drawScore)
                        }
                    )

                    PredictionOptionButton(
                        badge = "2",
                        label = match.awayTeam,
                        pointsHint = "+50 pts",
                        isSelected = currentPrediction?.choice == PredictionChoice.AWAY_WIN,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pred_btn_away_${match.id}"),
                        onClick = {
                            val adjustedAway = if (awayScoreInput <= homeScoreInput) homeScoreInput + 1 else awayScoreInput
                            awayScoreInput = adjustedAway
                            onMakePrediction(PredictionChoice.AWAY_WIN, homeScoreInput, adjustedAway)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Exact Score Stepper (+100 pts bonus)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (appLanguage == AppLanguage.ENGLISH) "Exact Score" else "Score exact",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bonus +100 pts",
                                fontSize = 10.sp,
                                color = PrimaryGold,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ScoreStepperControl(
                                score = homeScoreInput,
                                decrementTag = "pred_home_minus_${match.id}",
                                incrementTag = "pred_home_plus_${match.id}",
                                onDecrement = {
                                    if (homeScoreInput > 0) {
                                        val nh = homeScoreInput - 1
                                        homeScoreInput = nh
                                        onMakePrediction(choiceFromScore(nh, awayScoreInput), nh, awayScoreInput)
                                    }
                                },
                                onIncrement = {
                                    if (homeScoreInput < 15) {
                                        val nh = homeScoreInput + 1
                                        homeScoreInput = nh
                                        onMakePrediction(choiceFromScore(nh, awayScoreInput), nh, awayScoreInput)
                                    }
                                }
                            )

                            Text("-", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)

                            ScoreStepperControl(
                                score = awayScoreInput,
                                decrementTag = "pred_away_minus_${match.id}",
                                incrementTag = "pred_away_plus_${match.id}",
                                onDecrement = {
                                    if (awayScoreInput > 0) {
                                        val na = awayScoreInput - 1
                                        awayScoreInput = na
                                        onMakePrediction(choiceFromScore(homeScoreInput, na), homeScoreInput, na)
                                    }
                                },
                                onIncrement = {
                                    if (awayScoreInput < 15) {
                                        val na = awayScoreInput + 1
                                        awayScoreInput = na
                                        onMakePrediction(choiceFromScore(homeScoreInput, na), homeScoreInput, na)
                                    }
                                }
                            )
                        }
                    }
                }

                if (currentPrediction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val choiceLabel = when (currentPrediction.choice) {
                        PredictionChoice.HOME_WIN -> match.homeTeam
                        PredictionChoice.DRAW -> Strings.get("draw", appLanguage)
                        PredictionChoice.AWAY_WIN -> match.awayTeam
                    }
                    Surface(
                        color = LiveGlowGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prediction_saved_banner_${match.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = LiveGlowGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pronostic enregistré : $choiceLabel (${currentPrediction.predictedHomeScore}-${currentPrediction.predictedAwayScore})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LiveGlowGreen,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (onMatchClick != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onMatchClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("pred_open_match_${match.id}")
                        ) {
                            Icon(
                                imageVector = if (isLive) Icons.Default.PlayCircle else Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isLive) "Regarder le direct >" else "Fiche du match >",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreStepperControl(
    score: Int,
    decrementTag: String,
    incrementTag: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onDecrement,
            modifier = Modifier
                .size(32.dp)
                .testTag(decrementTag)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Moins", modifier = Modifier.size(14.dp))
        }
        Text(
            text = "$score",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        FilledTonalIconButton(
            onClick = onIncrement,
            modifier = Modifier
                .size(32.dp)
                .testTag(incrementTag)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun FinishedPredictionSummary(
    match: Match,
    currentPrediction: UserMatchPrediction?,
    appLanguage: AppLanguage,
    onOpenMatch: (() -> Unit)?
) {
    val actualChoice = when {
        match.homeScore > match.awayScore -> PredictionChoice.HOME_WIN
        match.awayScore > match.homeScore -> PredictionChoice.AWAY_WIN
        else -> PredictionChoice.DRAW
    }

    if (currentPrediction != null) {
        val isExact = currentPrediction.predictedHomeScore == match.homeScore &&
            currentPrediction.predictedAwayScore == match.awayScore
        val isCorrectOutcome = currentPrediction.choice == actualChoice

        val (bgColor, textColor, summaryText) = when {
            isExact -> Triple(
                PrimaryGold.copy(alpha = 0.2f),
                PrimaryGold,
                "🏆 Score exact ! +150 pts (${currentPrediction.predictedHomeScore}-${currentPrediction.predictedAwayScore})"
            )
            isCorrectOutcome -> Triple(
                LiveGlowGreen.copy(alpha = 0.16f),
                LiveGlowGreen,
                "✅ Bon pronostic ! +50 pts (${currentPrediction.predictedHomeScore}-${currentPrediction.predictedAwayScore})"
            )
            else -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Pronostic ${currentPrediction.predictedHomeScore}-${currentPrediction.predictedAwayScore} • Score final ${match.homeScore}-${match.awayScore}"
            )
        }

        Surface(
            color = bgColor,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = summaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (appLanguage == AppLanguage.ENGLISH) {
                    "Match finished • Predictions closed"
                } else {
                    "Match terminé • Pronostics clos"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }

    if (onOpenMatch != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onOpenMatch) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("🎬 Voir Résumé Vidéo IA >", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PredictionOptionButton(
    badge: String,
    label: String,
    pointsHint: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = if (isSelected) BorderStroke(2.dp, PrimaryGold) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$badge • $label",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pointsHint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LeaderboardView(
    users: List<PredictorLeaderboardUser>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(users, key = { it.username }) { user ->
            LeaderboardUserRow(user = user)
        }
    }
}

@Composable
private fun LeaderboardUserRow(
    user: PredictorLeaderboardUser
) {
    val isTop3 = user.rank <= 3
    val rankBadgeColor = when (user.rank) {
        1 -> PrimaryGold
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (user.isUser) 2.dp else 0.dp,
                color = if (user.isUser) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = rankBadgeColor,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#${user.rank}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isTop3) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${user.countryFlag} ${user.username}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (user.isUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "VOUS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${user.correctCount}/${user.totalPredictions} pronos exacts • Série 🔥 ${user.streak}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${user.points} pts",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PrimaryGold
                )
            }
        }
    }
}
