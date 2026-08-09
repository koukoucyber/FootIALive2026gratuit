package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.NewsArticle
import com.example.data.model.PlayerProfile
import com.example.ui.components.NewsFeedComponent
import com.example.ui.components.StatProgressBar
import com.example.ui.components.TeamLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsAndPlayersScreen(
    newsList: List<NewsArticle>,
    playersList: List<PlayerProfile>,
    isNewsLoading: Boolean = false,
    newsSource: String = "API Publique Direct",
    onRefreshNews: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Actualités Football", "Joueurs Vedettes")

    var selectedPlayer by remember { mutableStateOf<PlayerProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actualités & Star System", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            when (selectedTab) {
                0 -> { // News Feed via NewsFeedComponent
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        NewsFeedComponent(
                            newsList = newsList,
                            isLoading = isNewsLoading,
                            newsSource = newsSource,
                            onRefresh = onRefreshNews
                        )
                    }
                }

                1 -> { // Players Catalog
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playersList) { player ->
                            Card(
                                onClick = { selectedPlayer = player },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_profile_${player.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = player.photoUrl,
                                            contentDescription = player.name,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = player.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                        Text(text = "${player.position} • ${player.team}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(text = player.nationality, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                                                Text(text = "${player.goals} Buts", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
                                                Text(text = "Valeur: ${player.marketValue}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                            }
                                        }
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Player Profile Detail Dialog
        if (selectedPlayer != null) {
            val p = selectedPlayer!!
            AlertDialog(
                onDismissRequest = { selectedPlayer = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamLogo(url = p.teamLogo, teamName = p.team, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(p.name, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(text = "${p.position} • ${p.team} (${p.nationality})", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Valeur estimée : ${p.marketValue} • Rating : ⭐ ${p.rating}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = p.bio, fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Attributs & Competences (sur 100):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        StatProgressBar("Vitesse", p.speed, 100 - p.speed)
                        StatProgressBar("Tir", p.shooting, 100 - p.shooting)
                        StatProgressBar("Passe", p.passing, 100 - p.passing)
                        StatProgressBar("Dribble", p.dribbling, 100 - p.dribbling)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedPlayer = null }) { Text("Fermer") }
                }
            )
        }
    }
}
