package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.AiMatchSearchState
import com.example.data.model.EventType
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.components.MatchCard
import com.example.ui.components.MonthlyMatchesCalendarDialog

private data class AiQuickFilterChip(val label: String, val query: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    matches: List<Match>,
    onMatchClick: (Match) -> Unit,
    onTriggerSimulatedAction: (matchId: String, eventType: EventType, player: String, isHome: Boolean) -> Unit = { _, _, _, _ -> },
    isRefreshingMatches: Boolean = false,
    matchesSyncSummary: String = "Scores et prochains matchs actualisés en direct !",
    onRefreshMatches: () -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleTheme: (() -> Unit)? = null,
    aiMatchSearchState: AiMatchSearchState = AiMatchSearchState.Idle,
    onSearchMatchesWithAI: (String) -> Unit = {},
    onClearAiMatchSearch: () -> Unit = {},
    externalSearchQuery: String? = null,
    onExternalSearchConsumed: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLeague by remember { mutableStateOf("Toutes") }
    var selectedStatusTab by remember { mutableStateOf("Tous") }
    var showMonthlyCalendar by remember { mutableStateOf(false) }
    var showRefreshBanner by remember { mutableStateOf(false) }

    LaunchedEffect(externalSearchQuery) {
        if (!externalSearchQuery.isNullOrBlank()) {
            searchQuery = externalSearchQuery
            selectedLeague = "Toutes"
            selectedStatusTab = "Tous"
            onExternalSearchConsumed()
        }
    }

    fun triggerAiSearch(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isNotEmpty()) {
            searchQuery = q
            selectedLeague = "Toutes"
            selectedStatusTab = "Tous"
            onSearchMatchesWithAI(q)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "matches_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val leagues = listOf(
        "Toutes",
        "Qualifications CAN 2027",
        "RSL (Saudi Pro League)",
        "Ligue 1",
        "Premier League",
        "LaLiga",
        "Ligue des Champions",
        "Ligue Europa",
        "CAN",
        "Bundesliga",
        "Serie A",
        "Euro / Ligue des Nations",
        "Matchs Amicaux"
    )
    val statusTabs = listOf("Tous", "⚡ Aujourd'hui", "🌅 Demain", "🗓️ Hier", "En Direct", "À Venir", "Terminé")

    val aiQuickChips = remember {
        listOf(
            AiQuickFilterChip("🔴 Directs", "Matchs actuellement en direct"),
            AiQuickFilterChip("🌙 Ce soir (FR)", "Matchs joués ce soir en heure française"),
            AiQuickFilterChip("🇪🇸 Real / Barça", "Matchs du Real Madrid ou FC Barcelone"),
            AiQuickFilterChip("🇫🇷 Clubs FR", "Matchs de Ligue 1 ou clubs français"),
            AiQuickFilterChip("🌍 Afrique / CAN", "Matchs d'équipes africaines ou CAN"),
            AiQuickFilterChip("⚽ Matchs à buts", "Matchs avec le plus de buts")
        )
    }

    val isAiFilterActive = aiMatchSearchState is AiMatchSearchState.Success &&
        aiMatchSearchState.query.equals(searchQuery.trim(), ignoreCase = true)

    val filteredMatches = remember(matches, searchQuery, selectedLeague, selectedStatusTab, aiMatchSearchState) {
        fun normalizeSearch(text: String): String {
            return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase()
                .trim()
        }

        val parisTz = java.util.TimeZone.getTimeZone("Europe/Paris")
        val dayFmt = java.text.SimpleDateFormat("dd MMM", java.util.Locale.FRENCH).apply { timeZone = parisTz }
        val todayShort = dayFmt.format(java.util.Date(com.example.data.network.FootballNewsApiService.currentParisNowMillis()))
            .replace(".", "").trim().lowercase()
        val tomorrowShort = com.example.data.network.FootballNewsApiService.getCurrentParisCalendar()
            .apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            .let { dayFmt.format(it.time).replace(".", "").trim().lowercase() }
        val yesterdayShort = com.example.data.network.FootballNewsApiService.getCurrentParisCalendar()
            .apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
            .let { dayFmt.format(it.time).replace(".", "").trim().lowercase() }

        fun matchesTabs(match: Match): Boolean {
            val matchesLeague = selectedLeague == "Toutes" || match.league == selectedLeague
            val dateLower = match.date.lowercase()
            val matchesStatus = when (selectedStatusTab) {
                "⚡ Aujourd'hui" -> dateLower.contains("aujourd'hui") || dateLower.contains(todayShort) || match.status == MatchStatus.LIVE
                "🌅 Demain" -> dateLower.contains("demain") || dateLower.contains(tomorrowShort)
                "🗓️ Hier" -> dateLower.contains("hier") || dateLower.contains(yesterdayShort)
                "En Direct" -> match.status == MatchStatus.LIVE
                "Terminé" -> match.status == MatchStatus.FINISHED
                "À Venir" -> match.status == MatchStatus.UPCOMING
                else -> true
            }
            return matchesLeague && matchesStatus
        }

        if (aiMatchSearchState is AiMatchSearchState.Success &&
            aiMatchSearchState.query.equals(searchQuery.trim(), ignoreCase = true)
        ) {
            val byId = matches.associateBy { it.id }
            aiMatchSearchState.matchingIds
                .mapNotNull { id -> byId[id] }
                .filter { matchesTabs(it) }
        } else {
            val queryNorm = normalizeSearch(searchQuery)
            matches.filter { match ->
                val matchesSearch = queryNorm.isEmpty() ||
                    normalizeSearch(match.homeTeam).contains(queryNorm) ||
                    normalizeSearch(match.awayTeam).contains(queryNorm) ||
                    normalizeSearch(match.league).contains(queryNorm) ||
                    normalizeSearch(match.stadium).contains(queryNorm) ||
                    normalizeSearch(match.date).contains(queryNorm) ||
                    normalizeSearch(match.time).contains(queryNorm) ||
                    normalizeSearch(match.broadcastChannel.orEmpty()).contains(queryNorm)

                matchesSearch && matchesTabs(match)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Matchs & Scores en Direct",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showMonthlyCalendar = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("open_calendar_from_matches_btn")
                    ) {
                        Text("📅 Calendrier", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = {
                            onRefreshMatches()
                            showRefreshBanner = true
                        },
                        enabled = !isRefreshingMatches,
                        modifier = Modifier.testTag("matches_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualiser les Matchs",
                            modifier = Modifier.rotate(if (isRefreshingMatches) angle else 0f)
                        )
                    }

                    if (onToggleTheme != null) {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("matches_theme_toggle")
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
            AnimatedVisibility(visible = isRefreshingMatches || showRefreshBanner) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRefreshingMatches) Color(0xFF0077B6) else Color(0xFF38B000)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRefreshingMatches) "🔄 Actualisation des vrais matchs officiels..." else "✅ $matchesSyncSummary",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isRefreshingMatches) {
                            IconButton(
                                onClick = { showRefreshBanner = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Search Input + Gemini AI Filter Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        if (newValue.isBlank()) {
                            onClearAiMatchSearch()
                        }
                    },
                    placeholder = {
                        Text(
                            text = "Rechercher ou filtrer par IA (ex: Real ce soir)...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    onClearAiMatchSearch()
                                },
                                modifier = Modifier.testTag("match_search_clear_input")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer la recherche")
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { triggerAiSearch(searchQuery) }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("match_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Button(
                    onClick = { triggerAiSearch(searchQuery) },
                    enabled = searchQuery.isNotBlank() && aiMatchSearchState !is AiMatchSearchState.Loading,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("ai_search_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Filtrer avec l'IA Gemini",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("IA", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
            }

            // Quick AI Natural-Language Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("ai_quick_chips_row")
            ) {
                items(aiQuickChips) { chip ->
                    val isSelected = isAiFilterActive && searchQuery.trim().equals(chip.query, ignoreCase = true)
                    AssistChip(
                        onClick = { triggerAiSearch(chip.query) },
                        label = {
                            Text(
                                text = chip.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = if (isSelected) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        }
                    )
                }
            }

            // AI Search State Card (Loading / Success / Error)
            when (aiMatchSearchState) {
                is AiMatchSearchState.Loading -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("ai_search_loading_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Gemini IA analyse ${matches.size} matchs réels pour « ${aiMatchSearchState.query} »...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is AiMatchSearchState.Success -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("ai_search_result_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Filtre IA Gemini • ${aiMatchSearchState.matchingIds.size} match(s)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        searchQuery = ""
                                        onClearAiMatchSearch()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.testTag("ai_search_clear_button")
                                ) {
                                    Text("Réinitialiser", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = aiMatchSearchState.explanation,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                is AiMatchSearchState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("ai_search_error_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = aiMatchSearchState.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { triggerAiSearch(aiMatchSearchState.query) },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("Réessayer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AiMatchSearchState.Idle -> Unit
            }

            // Status Tabs
            ScrollableTabRow(
                selectedTabIndex = statusTabs.indexOf(selectedStatusTab).coerceAtLeast(0),
                modifier = Modifier.padding(vertical = 4.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 12.dp
            ) {
                statusTabs.forEach { tab ->
                    Tab(
                        selected = selectedStatusTab == tab,
                        onClick = { selectedStatusTab = tab },
                        text = { Text(tab, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            }

            // League Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(leagues) { league ->
                    FilterChip(
                        selected = selectedLeague == league,
                        onClick = { selectedLeague = league },
                        label = { Text(league) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Match List (Strict Real Matches Only)
            if (matches.isEmpty() && isRefreshingMatches) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Chargement des vrais matchs officiels en direct...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Synchronisation ESPN multi-ligues • Heure de Paris (FR)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (filteredMatches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (matches.isEmpty()) {
                                "Aucun match officiel disponible hors-ligne"
                            } else {
                                "Aucun match ne correspond aux filtres"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRefreshMatches) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Actualiser les matchs")
                            }
                            if (searchQuery.isNotEmpty() || selectedLeague != "Toutes" || selectedStatusTab != "Tous" || aiMatchSearchState !is AiMatchSearchState.Idle) {
                                OutlinedButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedLeague = "Toutes"
                                        selectedStatusTab = "Tous"
                                        onClearAiMatchSearch()
                                    }
                                ) {
                                    Text("Réinitialiser")
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMatches, key = { it.id }) { match ->
                        MatchCard(
                            match = match,
                            onClick = { onMatchClick(match) }
                        )
                    }
                }
            }
        }

        // Monthly Calendar Dialog
        if (showMonthlyCalendar) {
            MonthlyMatchesCalendarDialog(
                matches = matches,
                onDismiss = { showMonthlyCalendar = false },
                onMatchClick = onMatchClick
            )
        }
    }
}
