package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.data.model.Match
import com.example.data.repository.FootballRepository
import com.example.ui.screens.*
import com.example.ui.theme.FootIALiveTheme

sealed class NavScreen(val route: String, val title: String, val icon: ImageVector) {
    object Home : NavScreen("home", "Accueil", Icons.Default.Home)
    object Matches : NavScreen("matches", "Matchs", Icons.Default.SportsSoccer)
    object Standings : NavScreen("standings", "Classements", Icons.Default.FormatListNumbered)
    object Favorite : NavScreen("favorite", "Mon Équipe", Icons.Default.Star)
    object News : NavScreen("news", "Actus & Stars", Icons.Default.Newspaper)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val repository = remember { FootballRepository(context) }

            val matches by repository.matchesState.collectAsStateWithLifecycle()
            val standings by repository.standingsState.collectAsStateWithLifecycle()
            val players by repository.playersState.collectAsStateWithLifecycle()
            val news by repository.newsState.collectAsStateWithLifecycle()
            val isNewsLoading by repository.isNewsLoading.collectAsStateWithLifecycle()
            val newsSource by repository.newsSource.collectAsStateWithLifecycle()
            val notifications by repository.notificationsState.collectAsStateWithLifecycle()
            val favoriteTeams by repository.favoriteTeams.collectAsStateWithLifecycle(initialValue = emptyList())
            val currentApiKey by repository.footballApiKey.collectAsStateWithLifecycle()

            val isSystemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(isSystemDark) }

            var currentNavScreen by remember { mutableStateOf<NavScreen>(NavScreen.Home) }
            var selectedLeagueFilter by remember { mutableStateOf("Toutes") }
            var selectedMatchDetail by remember { mutableStateOf<Match?>(null) }

            var showDeploymentDialog by remember { mutableStateOf(false) }
            var showNotificationsDialog by remember { mutableStateOf(false) }

            val unreadNotifCount = remember(notifications) { notifications.count { !it.isRead } }

            FootIALiveTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoxWithConstraints {
                        val isWideScreen = maxWidth > 600.dp

                        Row(modifier = Modifier.fillMaxSize()) {
                            // Wide screen Navigation Rail
                            if (isWideScreen && selectedMatchDetail == null) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    val items = listOf(
                                        NavScreen.Home,
                                        NavScreen.Matches,
                                        NavScreen.Standings,
                                        NavScreen.Favorite,
                                        NavScreen.News
                                    )
                                    items.forEach { screen ->
                                        NavigationRailItem(
                                            selected = currentNavScreen == screen,
                                            onClick = { currentNavScreen = screen },
                                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                                            label = { Text(screen.title) }
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                Scaffold(
                                    bottomBar = {
                                        // Phone Navigation Bar
                                        if (!isWideScreen && selectedMatchDetail == null) {
                                            NavigationBar(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            ) {
                                                val items = listOf(
                                                    NavScreen.Home,
                                                    NavScreen.Matches,
                                                    NavScreen.Standings,
                                                    NavScreen.Favorite,
                                                    NavScreen.News
                                                )
                                                items.forEach { screen ->
                                                    NavigationBarItem(
                                                        selected = currentNavScreen == screen,
                                                        onClick = { currentNavScreen = screen },
                                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                                        label = { Text(screen.title) },
                                                        modifier = Modifier.testTag("nav_item_${screen.route}")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        if (selectedMatchDetail != null) {
                                            val currentMatch = matches.find { it.id == selectedMatchDetail!!.id } ?: selectedMatchDetail!!
                                            MatchDetailScreen(
                                                match = currentMatch,
                                                onBack = { selectedMatchDetail = null },
                                                onAskAI = { question, lang -> repository.askMatchAI(currentMatch.id, question, lang) },
                                                onGenerateDebrief = { lang -> repository.generateFreshDebrief(currentMatch.id, lang) },
                                                onGenerateLiveCommentary = { m, min, prompt, lang -> repository.generateLiveCommentary(m, min, prompt, lang) }
                                            )
                                        } else {
                                            when (currentNavScreen) {
                                                NavScreen.Home -> HomeScreen(
                                                    matches = matches,
                                                    players = players,
                                                    news = news,
                                                    selectedLeague = selectedLeagueFilter,
                                                    onLeagueSelect = { selectedLeagueFilter = it },
                                                    onMatchClick = { selectedMatchDetail = it },
                                                    onNavigateToMatches = { currentNavScreen = NavScreen.Matches },
                                                    onNavigateToTeam = { currentNavScreen = NavScreen.Favorite },
                                                    onNavigateToNews = { currentNavScreen = NavScreen.News },
                                                    onOpenDeploymentDialog = { showDeploymentDialog = true },
                                                    onOpenNotifications = { showNotificationsDialog = true },
                                                    unreadNotifCount = unreadNotifCount,
                                                    isDarkTheme = isDarkTheme,
                                                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                                                )

                                                NavScreen.Matches -> MatchesScreen(
                                                    matches = matches,
                                                    onMatchClick = { selectedMatchDetail = it },
                                                    onTriggerSimulatedAction = { matchId, eventType, player, isHome ->
                                                        coroutineScope.launch {
                                                            repository.addManualEventToMatch(matchId, eventType, player, isHome)
                                                        }
                                                    }
                                                )

                                                NavScreen.Standings -> StandingsScreen(
                                                    standingsMap = standings
                                                )

                                                NavScreen.Favorite -> FavoriteTeamScreen(
                                                    favoriteTeams = favoriteTeams,
                                                    allMatches = matches,
                                                    onToggleFavorite = { team, league, logo ->
                                                        coroutineScope.launch {
                                                            repository.toggleFavoriteTeam(team, league, logo)
                                                        }
                                                    },
                                                    onMatchClick = { selectedMatchDetail = it }
                                                )

                                                NavScreen.News -> NewsAndPlayersScreen(
                                                    newsList = news,
                                                    playersList = players,
                                                    isNewsLoading = isNewsLoading,
                                                    newsSource = newsSource,
                                                    onRefreshNews = { repository.refreshNewsFromPublicApi() }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dialog Overlays
                if (showDeploymentDialog) {
                    DeploymentInfoDialog(
                        currentApiKey = currentApiKey,
                        onSaveApiKey = { repository.setFootballApiKey(it) },
                        onDismiss = { showDeploymentDialog = false }
                    )
                }

                if (showNotificationsDialog) {
                    NotificationsDialog(
                        notifications = notifications,
                        onMarkAsRead = { repository.markNotificationRead(it) },
                        onClearAll = { repository.clearNotifications() },
                        onDismiss = { showNotificationsDialog = false }
                    )
                }
            }
        }
    }
}
