package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FavoriteTeamEntity
import com.example.data.model.Match
import com.example.ui.components.MatchCard
import com.example.ui.components.TeamLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteTeamScreen(
    favoriteTeams: List<FavoriteTeamEntity>,
    allMatches: List<Match>,
    onToggleFavorite: (teamName: String, league: String, logoUrl: String) -> Unit,
    onMatchClick: (Match) -> Unit,
    isDarkTheme: Boolean = true,
    onToggleTheme: (() -> Unit)? = null
) {
    val defaultClubs = listOf(
        Triple("Paris SG", "Ligue 1", "https://media.api-sports.io/football/teams/85.png"),
        Triple("Real Madrid", "LaLiga", "https://media.api-sports.io/football/teams/541.png"),
        Triple("Arsenal", "Premier League", "https://media.api-sports.io/football/teams/42.png"),
        Triple("Olympique de Marseille", "Ligue 1", "https://media.api-sports.io/football/teams/81.png"),
        Triple("FC Barcelone", "LaLiga", "https://media.api-sports.io/football/teams/529.png"),
        Triple("Bayern Munich", "Bundesliga", "https://media.api-sports.io/football/teams/157.png"),
        Triple("France", "Euro", "https://media.api-sports.io/football/teams/2.png"),
        Triple("Espagne", "Euro", "https://media.api-sports.io/football/teams/9.png"),
        Triple("Allemagne", "Euro", "https://media.api-sports.io/football/teams/25.png"),
        Triple("Brésil", "Matchs Amicaux", "https://media.api-sports.io/football/teams/6.png"),
        Triple("Argentine", "Matchs Amicaux", "https://media.api-sports.io/football/teams/26.png"),
        Triple("Sénégal", "CAN", "https://flagcdn.com/w160/sn.png"),
        Triple("Côte d'Ivoire", "CAN", "https://flagcdn.com/w160/ci.png")
    )

    var selectedFavName by remember(favoriteTeams) {
        mutableStateOf(favoriteTeams.firstOrNull()?.teamName ?: "Paris SG")
    }

    val teamMatches = remember(allMatches, selectedFavName) {
        allMatches.filter { it.homeTeam.contains(selectedFavName, ignoreCase = true) || it.awayTeam.contains(selectedFavName, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mon Équipe Favorite ⭐", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
                actions = {
                    if (onToggleTheme != null) {
                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("fav_theme_toggle")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Club Selection Chips
            item {
                Text(text = "Sélectionnez votre club / sélection :", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(defaultClubs) { (name, league, logo) ->
                        val isFav = favoriteTeams.any { it.teamName == name }
                        val isSelected = selectedFavName == name

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFavName = name
                            },
                            label = { Text(name, fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (isFav) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onToggleFavorite(name, league, logo) }
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // Dashboard Card for Selected Team
            item {
                val currentClubInfo = defaultClubs.find { it.first == selectedFavName }
                val isFav = favoriteTeams.any { it.teamName == selectedFavName }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TeamLogo(
                                    url = currentClubInfo?.third ?: "",
                                    teamName = selectedFavName,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = selectedFavName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                    Text(
                                        text = currentClubInfo?.second ?: "Football Club",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val league = currentClubInfo?.second ?: "Ligue"
                                    val logo = currentClubInfo?.third ?: ""
                                    onToggleFavorite(selectedFavName, league, logo)
                                },
                                modifier = Modifier.testTag("favorite_toggle_star")
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favori",
                                    tint = if (isFav) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Forme", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "🟢 🟢 🟡 🟢", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Prochain Match", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "Ce soir 20:45", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Alerte Buts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "Activée 🔔", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Team Fixtures Title
            item {
                Text(
                    text = "Matchs de $selectedFavName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (teamMatches.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "Aucun match programmé cette semaine pour $selectedFavName.",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(teamMatches) { match ->
                    MatchCard(match = match, onClick = { onMatchClick(match) })
                }
            }
        }
    }
}
