package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Match
import com.example.data.model.MatchStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyMatchesCalendarDialog(
    matches: List<Match>,
    onDismiss: () -> Unit,
    onMatchClick: (Match) -> Unit
) {
    var selectedLeague by remember { mutableStateOf("Toutes") }
    var selectedFilterDay by remember { mutableStateOf("Tous") }

    val leagues = listOf(
        "Toutes",
        "Qualifications CAN 2027",
        "RSL (Saudi Pro League)",
        "Ligue 1",
        "Premier League",
        "Ligue des Champions",
        "LaLiga",
        "Ligue Europa",
        "Bundesliga",
        "Serie A",
        "Euro / Ligue des Nations",
        "Matchs Amicaux",
        "CAN"
    )

    val parisTz = remember { java.util.TimeZone.getTimeZone("Europe/Paris") }
    val monthHeaderTitle = remember {
        val fmt = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.FRENCH).apply { timeZone = parisTz }
        fmt.format(java.util.Date(com.example.data.network.FootballNewsApiService.currentParisNowMillis()))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.FRENCH) else it.toString() }
    }
    val todayShortLabel = remember {
        val fmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.FRENCH).apply { timeZone = parisTz }
        fmt.format(java.util.Date(com.example.data.network.FootballNewsApiService.currentParisNowMillis())).replace(".", "").trim()
    }
    val tomorrowShortLabel = remember {
        val fmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.FRENCH).apply { timeZone = parisTz }
        com.example.data.network.FootballNewsApiService.getCurrentParisCalendar()
            .apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            .let { fmt.format(it.time).replace(".", "").trim() }
    }
    val yesterdayShortLabel = remember {
        val fmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.FRENCH).apply { timeZone = parisTz }
        com.example.data.network.FootballNewsApiService.getCurrentParisCalendar()
            .apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
            .let { fmt.format(it.time).replace(".", "").trim() }
    }

    val dayFilters = remember(todayShortLabel, tomorrowShortLabel, yesterdayShortLabel, matches) {
        val dynamicDates = matches.map { it.date }
            .filter { !it.contains("Aujourd'hui", true) && !it.contains("Demain", true) && !it.contains("Hier", true) }
            .distinct()
            .take(5)
        listOf(
            "Tous",
            "⚡ Aujourd'hui ($todayShortLabel)",
            "🌅 Demain ($tomorrowShortLabel)",
            "🗓️ Hier ($yesterdayShortLabel)"
        ) + dynamicDates
    }

    val filteredMatches = remember(matches, selectedLeague, selectedFilterDay) {
        matches.filter { match ->
            val matchLeague = when (selectedLeague) {
                "Toutes" -> true
                "RSL (Saudi Pro League)" -> match.league.contains("RSL", ignoreCase = true) || match.league.contains("Saudi", ignoreCase = true)
                "Qualifications CAN 2027" -> match.league.contains("Qualifications CAN", ignoreCase = true)
                "CAN" -> match.league.equals("CAN", ignoreCase = true) || match.league.contains("CAN", ignoreCase = true)
                else -> match.league.equals(selectedLeague, ignoreCase = true)
            }

            val dateLower = match.date.lowercase()
            val matchDay = when {
                selectedFilterDay == "Tous" -> true
                selectedFilterDay.startsWith("⚡ Aujourd'hui") -> dateLower.contains("aujourd'hui") || dateLower.contains(todayShortLabel.lowercase()) || match.status == MatchStatus.LIVE
                selectedFilterDay.startsWith("🌅 Demain") -> dateLower.contains("demain") || dateLower.contains(tomorrowShortLabel.lowercase())
                selectedFilterDay.startsWith("🗓️ Hier") -> dateLower.contains("hier") || dateLower.contains(yesterdayShortLabel.lowercase())
                else -> match.date.equals(selectedFilterDay, ignoreCase = true)
            }

            matchLeague && matchDay
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📅",
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tableau des Matchs du Mois",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "$monthHeaderTitle • Heure Française (FR)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_calendar_dialog")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Day Selector Chips
                Text(
                    text = "Période du mois :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dayFilters) { filter ->
                        FilterChip(
                            selected = selectedFilterDay == filter,
                            onClick = { selectedFilterDay = filter },
                            label = { Text(filter, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // League Selector Chips
                Text(
                    text = "Compétition :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(leagues) { league ->
                        FilterChip(
                            selected = selectedLeague == league,
                            onClick = { selectedLeague = league },
                            label = { Text(league, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Summary Stats Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredMatches.size} match(s) programmés",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38B000))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Diffusions TV Officielles",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Matches Table List
                if (filteredMatches.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aucun match ne correspond aux filtres",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredMatches, key = { it.id }) { match ->
                            MonthlyMatchTableRow(
                                match = match,
                                onClick = {
                                    onMatchClick(match)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyMatchTableRow(
    match: Match,
    onClick: () -> Unit
) {
    val isLive = match.status == MatchStatus.LIVE
    val isFinished = match.status == MatchStatus.FINISHED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("calendar_match_row_${match.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                match.homeTeam.contains("Côte d'Ivoire") || match.awayTeam.contains("Côte d'Ivoire") -> Color(0xFFFF9F1C).copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = if (isLive) CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFFE63946), Color(0xFFFFB703)))) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Top Bar: Date, League Badge, TV Channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isLive -> Color(0xFFE63946)
                            match.date.contains("Aujourd'hui") -> Color(0xFF38B000)
                            match.date.contains("Demain") -> Color(0xFFFF9F1C)
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = when {
                                isLive -> "🔴 EN DIRECT (${match.minute}')"
                                match.date.contains("Aujourd'hui") -> "AUJOURD'HUI • ${match.time}"
                                match.date.contains("Demain") -> "DEMAIN • ${match.time}"
                                else -> "${match.date} • ${match.time}"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isLive || match.date.contains("Aujourd'hui") || match.date.contains("Demain") -> Color.White
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = match.league,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Broadcast Channel Badge
                if (!match.broadcastChannel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "📺 ${match.broadcastChannel}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Teams Row & Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Team
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamLogo(
                        url = match.homeTeamLogo,
                        teamName = match.homeTeam,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = match.homeTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Score or VS Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isLive -> Color(0xFFE63946)
                        isFinished -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        else -> MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (isLive || isFinished) "${match.homeScore} - ${match.awayScore}" else "VS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isLive) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Away Team
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = match.awayTeam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TeamLogo(
                        url = match.awayTeamLogo,
                        teamName = match.awayTeam,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stadium & Direct View Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stadium,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = match.stadium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Text(
                        text = "Voir le match",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
