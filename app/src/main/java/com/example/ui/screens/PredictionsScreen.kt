package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.PredictionChoice
import com.example.data.model.PredictorLeaderboardUser
import com.example.data.model.UserMatchPrediction
import com.example.ui.components.TeamLogo
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.LiveRed
import com.example.ui.theme.PrimaryGold
import com.example.util.AppLanguage
import com.example.util.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionsScreen(
    matches: List<Match>,
    predictions: Map<String, UserMatchPrediction>,
    onMakePrediction: (matchId: String, choice: PredictionChoice, homeScore: Int, awayScore: Int) -> Unit,
    appLanguage: AppLanguage = AppLanguage.FRENCH,
    onToggleLanguage: () -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleTheme: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Strings.get("nav_predictions", appLanguage),
        Strings.get("leaderboard", appLanguage)
    )

    // Calculate user points and stats
    val userPredictionCount = predictions.size
    val userPoints = 240 + (userPredictionCount * 50)
    val userRank = if (userPoints > 400) 2 else if (userPoints > 300) 3 else 5

    // Sample Leaderboard Users
    val leaderboardUsers = remember(userPoints) {
        listOf(
            PredictorLeaderboardUser(1, "Alex_Prono 🏆", "⚽", "🇫🇷", 450, 9, 11, 6),
            PredictorLeaderboardUser(2, "Mamadou_Goal", "🔥", "🇨🇮", 390, 8, 10, 4),
            PredictorLeaderboardUser(userRank, "Vous (Supporter)", "🎯", "🇫🇷", userPoints, 5 + userPredictionCount, 7 + userPredictionCount, 3, isUser = true),
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
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    // Language Switcher
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
            // User Score Summary Header
            UserPointsHeader(
                userPoints = userPoints,
                userRank = leaderboardUsers.find { it.isUser }?.rank ?: userRank,
                predictionsCount = userPredictionCount,
                appLanguage = appLanguage
            )

            // Tabs Header
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
                                fontSize = 14.sp
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
                    appLanguage = appLanguage
                )
                1 -> LeaderboardView(
                    users = leaderboardUsers,
                    appLanguage = appLanguage
                )
            }
        }
    }
}

@Composable
private fun UserPointsHeader(
    userPoints: Int,
    userRank: Int,
    predictionsCount: Int,
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
                    Column {
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

                    Surface(
                        color = PrimaryGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryGold)
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
                        value = if (predictionsCount > 0) "78%" else "75%",
                        icon = Icons.Default.PieChart
                    )
                    HeaderStatItem(
                        title = Strings.get("streak", appLanguage),
                        value = "🔥 3",
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
    appLanguage: AppLanguage
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(matches, key = { it.id }) { match ->
            PredictionCard(
                match = match,
                currentPrediction = predictions[match.id],
                onMakePrediction = { choice, homeScore, awayScore ->
                    onMakePrediction(match.id, choice, homeScore, awayScore)
                },
                appLanguage = appLanguage
            )
        }
    }
}

@Composable
private fun PredictionCard(
    match: Match,
    currentPrediction: UserMatchPrediction?,
    onMakePrediction: (choice: PredictionChoice, homeScore: Int, awayScore: Int) -> Unit,
    appLanguage: AppLanguage
) {
    var homeScoreInput by remember(currentPrediction) { mutableIntStateOf(currentPrediction?.predictedHomeScore ?: 2) }
    var awayScoreInput by remember(currentPrediction) { mutableIntStateOf(currentPrediction?.predictedAwayScore ?: 1) }

    val isFinished = match.status == MatchStatus.FINISHED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prediction_card_${match.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (match.status == MatchStatus.LIVE) {
                    Surface(
                        color = LiveRed.copy(alpha = 0.2f),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "🔴 ${Strings.get("live_now", appLanguage)} ${match.minute}'",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiveRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else if (isFinished) {
                    Text(
                        text = Strings.get("finished", appLanguage),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "${match.date} • ${match.time}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Teams display
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
                    TeamLogo(url = match.homeTeamLogo, teamName = match.homeTeam, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.homeTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // Score or VS
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (match.status != MatchStatus.UPCOMING) {
                        Text(
                            text = "${match.homeScore} - ${match.awayScore}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
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

                // Away Team
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamLogo(url = match.awayTeamLogo, teamName = match.awayTeam, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.awayTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Prediction Buttons (1 / N / 2)
            Text(
                text = Strings.get("predict_match", appLanguage),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Home Win Button (1)
                PredictionOptionButton(
                    label = "1 (${match.homeTeam.take(8)})",
                    odds = "1.95",
                    isSelected = currentPrediction?.choice == PredictionChoice.HOME_WIN,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onMakePrediction(PredictionChoice.HOME_WIN, homeScoreInput, awayScoreInput)
                    }
                )

                // Draw Button (N)
                PredictionOptionButton(
                    label = "N (${Strings.get("draw", appLanguage).take(3)})",
                    odds = "3.20",
                    isSelected = currentPrediction?.choice == PredictionChoice.DRAW,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onMakePrediction(PredictionChoice.DRAW, homeScoreInput, awayScoreInput)
                    }
                )

                // Away Win Button (2)
                PredictionOptionButton(
                    label = "2 (${match.awayTeam.take(8)})",
                    odds = "3.80",
                    isSelected = currentPrediction?.choice == PredictionChoice.AWAY_WIN,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onMakePrediction(PredictionChoice.AWAY_WIN, homeScoreInput, awayScoreInput)
                    }
                )
            }

            if (currentPrediction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = LiveGlowGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
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
                            text = "Pronostic enregistré avec succès ! (+50 pts potentiels)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LiveGlowGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionOptionButton(
    label: String,
    odds: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, PrimaryGold) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Côte $odds",
                fontSize = 10.sp,
                color = if (isSelected) PrimaryGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LeaderboardView(
    users: List<PredictorLeaderboardUser>,
    appLanguage: AppLanguage
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(users, key = { it.username }) { user ->
            LeaderboardUserRow(user = user, appLanguage = appLanguage)
        }
    }
}

@Composable
private fun LeaderboardUserRow(
    user: PredictorLeaderboardUser,
    appLanguage: AppLanguage
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
            // Rank Badge
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

            // User Info
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
                    text = "${user.correctCount}/${user.totalPredictions} pronos exacts • Serie 🔥 ${user.streak}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Points
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
