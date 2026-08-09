package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EventType
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.components.MatchCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    matches: List<Match>,
    onMatchClick: (Match) -> Unit,
    onTriggerSimulatedAction: (matchId: String, eventType: EventType, player: String, isHome: Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLeague by remember { mutableStateOf("Toutes") }
    var selectedStatusTab by remember { mutableStateOf("Tous") }

    val leagues = listOf("Toutes", "Matchs Amicaux", "Euro", "Ligue 1", "Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue des Champions", "CAN")
    val statusTabs = listOf("Tous", "En Direct", "Terminé", "À Venir")

    val filteredMatches = remember(matches, searchQuery, selectedLeague, selectedStatusTab) {
        matches.filter { match ->
            val matchesLeague = selectedLeague == "Toutes" || match.league == selectedLeague
            val matchesSearch = searchQuery.isEmpty() ||
                    match.homeTeam.contains(searchQuery, ignoreCase = true) ||
                    match.awayTeam.contains(searchQuery, ignoreCase = true)

            val matchesStatus = when (selectedStatusTab) {
                "En Direct" -> match.status == MatchStatus.LIVE
                "Terminé" -> match.status == MatchStatus.FINISHED
                "À Venir" -> match.status == MatchStatus.UPCOMING
                else -> true
            }

            matchesLeague && matchesSearch && matchesStatus
        }
    }

    var showSimDialog by remember { mutableStateOf(false) }
    var selectedMatchForSim by remember { mutableStateOf<Match?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Matchs & Scores en Direct",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher un club (ex: PSG, Real, City...)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("match_search_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Status Tabs
            TabRow(
                selectedTabIndex = statusTabs.indexOf(selectedStatusTab),
                modifier = Modifier.padding(vertical = 4.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                statusTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedStatusTab == tab,
                        onClick = { selectedStatusTab = tab },
                        text = { Text(tab, fontWeight = FontWeight.Bold) }
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

            // Match List
            if (filteredMatches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aucun match ne correspond aux filtres",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMatches, key = { it.id }) { match ->
                        Column {
                            MatchCard(
                                match = match,
                                onClick = { onMatchClick(match) }
                            )

                            // Quick simulation trigger for Live matches
                            if (match.status == MatchStatus.LIVE) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = {
                                            selectedMatchForSim = match
                                            showSimDialog = true
                                        },
                                        label = { Text("Simuler évènement IA", fontSize = 11.sp) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.FlashOn,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Simulation Dialog
        if (showSimDialog && selectedMatchForSim != null) {
            val m = selectedMatchForSim!!
            AlertDialog(
                onDismissRequest = { showSimDialog = false },
                title = { Text("Simuler une Action en Direct", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Déclenchez un évènement instantané pour tester le score et le commentateur IA en temps réel:")
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onTriggerSimulatedAction(m.id, EventType.GOAL, "K. Mbappé", true)
                                showSimDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⚽ Simuler But pour ${m.homeTeam}")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = {
                                onTriggerSimulatedAction(m.id, EventType.GOAL, "E. Haaland", false)
                                showSimDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("⚽ Simuler But pour ${m.awayTeam}")
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = {
                                onTriggerSimulatedAction(m.id, EventType.YELLOW_CARD, "A. Hakimi", true)
                                showSimDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🟨 Simuler Carton Jaune")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSimDialog = false }) {
                        Text("Fermer")
                    }
                }
            )
        }
    }
}
