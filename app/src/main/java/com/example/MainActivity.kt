package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.sp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.data.model.Match
import com.example.data.model.PredictionChoice
import com.example.data.repository.FootballRepository
import com.example.ui.screens.*
import com.example.ui.theme.FootIALiveTheme
import com.example.util.AppLanguage
import com.example.util.Strings

sealed class NavScreen(val route: String, val defaultTitle: String, val icon: ImageVector) {
    object Home : NavScreen("home", "Accueil", Icons.Default.Home)
    object Matches : NavScreen("matches", "Matchs", Icons.Default.SportsSoccer)
    object Standings : NavScreen("standings", "Classements", Icons.Default.FormatListNumbered)
    object Favorite : NavScreen("favorite", "Mon Équipe", Icons.Default.Star)
    object News : NavScreen("news", "Actus", Icons.Default.Newspaper)
    object Predictions : NavScreen("predictions", "Pronos", Icons.Default.EmojiEvents)

    fun getTitle(lang: AppLanguage): String {
        return Strings.get("nav_$route", lang)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
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
            val isRefreshingMatches by repository.isRefreshingMatches.collectAsStateWithLifecycle()
            val matchesSyncSummary by repository.matchesSyncSummary.collectAsStateWithLifecycle()
            val mercatoNews by repository.mercatoNewsState.collectAsStateWithLifecycle()
            val isMercatoLoading by repository.isMercatoLoading.collectAsStateWithLifecycle()
            val mercatoLastSync by repository.mercatoLastSync.collectAsStateWithLifecycle()
            val geminiMercatoAnalysis by repository.geminiMercatoAnalysis.collectAsStateWithLifecycle()
            val aiMatchSearchState by repository.aiMatchSearchState.collectAsStateWithLifecycle()
            val postMatchSummaryState by repository.postMatchVideoSummaryState.collectAsStateWithLifecycle()
            val notifications by repository.notificationsState.collectAsStateWithLifecycle()
            val favoriteTeams by repository.favoriteTeams.collectAsStateWithLifecycle(initialValue = emptyList())
            val currentApiKey by repository.footballApiKey.collectAsStateWithLifecycle()

            val isDarkThemeRepo by repository.isDarkTheme.collectAsStateWithLifecycle()
            var isDarkTheme by remember { mutableStateOf(isDarkThemeRepo) }

            val appLanguage by repository.appLanguage.collectAsStateWithLifecycle()
            val predictions by repository.predictionsState.collectAsStateWithLifecycle()

            fun toggleLanguage() {
                val nextLang = if (appLanguage == AppLanguage.FRENCH) AppLanguage.ENGLISH else AppLanguage.FRENCH
                repository.setAppLanguage(nextLang)
            }

            fun toggleTheme() {
                isDarkTheme = !isDarkTheme
                repository.toggleDarkTheme()
            }

            var currentNavScreen by remember { mutableStateOf<NavScreen>(NavScreen.Home) }
            var selectedLeagueFilter by remember { mutableStateOf("Toutes") }
            var selectedMatchDetail by remember { mutableStateOf<Match?>(null) }
            var pendingAiMatchSearchQuery by remember { mutableStateOf<String?>(null) }

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
                            val items = listOf(
                                NavScreen.Home,
                                NavScreen.Matches,
                                NavScreen.Predictions,
                                NavScreen.Standings,
                                NavScreen.Favorite,
                                NavScreen.News
                            )

                            // Wide screen Navigation Rail
                            if (isWideScreen && selectedMatchDetail == null) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    items.forEach { screen ->
                                        NavigationRailItem(
                                            selected = currentNavScreen == screen,
                                            onClick = { currentNavScreen = screen },
                                            icon = { Icon(screen.icon, contentDescription = screen.getTitle(appLanguage)) },
                                            label = { Text(screen.getTitle(appLanguage), fontSize = 11.sp) }
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
                                                items.forEach { screen ->
                                                    NavigationBarItem(
                                                        selected = currentNavScreen == screen,
                                                        onClick = { currentNavScreen = screen },
                                                        icon = { Icon(screen.icon, contentDescription = screen.getTitle(appLanguage)) },
                                                        label = { Text(screen.getTitle(appLanguage), fontSize = 10.sp, maxLines = 1) },
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
                                                allMatches = matches,
                                                onSelectMatch = { selectedMatchDetail = it },
                                                onBack = { selectedMatchDetail = null },
                                                onAskAI = { question, lang -> repository.askMatchAI(currentMatch.id, question, lang) },
                                                onGenerateDebrief = { lang -> repository.generateFreshDebrief(currentMatch.id, lang) },
                                                onGenerateLiveCommentary = { m, min, prompt, lang -> repository.generateLiveCommentary(m, min, prompt, lang) },
                                                postMatchSummaryState = postMatchSummaryState,
                                                onAnalyzeFinishedHighlights = { m -> repository.analyzeFinishedMatchHighlightsWithAI(m) },
                                                isDarkTheme = isDarkTheme,
                                                onToggleTheme = { toggleTheme() }
                                            )
                                        } else {
                                            BackHandler(enabled = currentNavScreen != NavScreen.Home) {
                                                currentNavScreen = NavScreen.Home
                                            }
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
                                                    onToggleTheme = { toggleTheme() },
                                                    appLanguage = appLanguage,
                                                    onToggleLanguage = { toggleLanguage() },
                                                    isRefreshingMatches = isRefreshingMatches,
                                                    matchesSyncSummary = matchesSyncSummary,
                                                    onRefreshMatches = {
                                                        repository.refreshRealMatchesFromApi()
                                                        repository.refreshMercatoNews()
                                                    },
                                                    onSearchMatchesWithAI = { query ->
                                                        val trimmed = query.trim()
                                                        if (trimmed.isNotEmpty()) {
                                                            pendingAiMatchSearchQuery = trimmed
                                                            repository.searchMatchesWithAI(trimmed)
                                                            currentNavScreen = NavScreen.Matches
                                                        }
                                                    }
                                                )

                                                NavScreen.Matches -> MatchesScreen(
                                                    matches = matches,
                                                    onMatchClick = { selectedMatchDetail = it },
                                                    onTriggerSimulatedAction = { matchId, eventType, player, isHome ->
                                                        coroutineScope.launch {
                                                            repository.addManualEventToMatch(matchId, eventType, player, isHome)
                                                        }
                                                    },
                                                    isRefreshingMatches = isRefreshingMatches,
                                                    matchesSyncSummary = matchesSyncSummary,
                                                    onRefreshMatches = { repository.refreshRealMatchesFromApi() },
                                                    isDarkTheme = isDarkTheme,
                                                    onToggleTheme = { toggleTheme() },
                                                    aiMatchSearchState = aiMatchSearchState,
                                                    onSearchMatchesWithAI = { query -> repository.searchMatchesWithAI(query) },
                                                    onClearAiMatchSearch = { repository.clearAiMatchSearch() },
                                                    externalSearchQuery = pendingAiMatchSearchQuery,
                                                    onExternalSearchConsumed = { pendingAiMatchSearchQuery = null }
                                                )

                                                NavScreen.Predictions -> PredictionsScreen(
                                                    matches = matches,
                                                    predictions = predictions,
                                                    onMakePrediction = { matchId, choice, hs, asScore ->
                                                        repository.savePrediction(matchId, choice, hs, asScore)
                                                    },
                                                    appLanguage = appLanguage,
                                                    onToggleLanguage = { toggleLanguage() },
                                                    isDarkTheme = isDarkTheme,
                                                    onToggleTheme = { toggleTheme() },
                                                    onMatchClick = { selectedMatchDetail = it },
                                                    onRefreshMatches = { repository.refreshRealMatchesFromApi() }
                                                )

                                                NavScreen.Standings -> StandingsScreen(
                                                    standingsMap = standings,
                                                    isDarkTheme = isDarkTheme,
                                                    onToggleTheme = { toggleTheme() }
                                                )

                                                NavScreen.Favorite -> FavoriteTeamScreen(
                                                    favoriteTeams = favoriteTeams,
                                                    allMatches = matches,
                                                    onToggleFavorite = { team, league, logo ->
                                                        coroutineScope.launch {
                                                            repository.toggleFavoriteTeam(team, league, logo)
                                                        }
                                                    },
                                                    onMatchClick = { selectedMatchDetail = it },
                                                    isDarkTheme = isDarkTheme,
                                                    onToggleTheme = { toggleTheme() }
                                                )

                                                NavScreen.News -> NewsAndPlayersScreen(
                                                    newsList = news,
                                                    playersList = players,
                                                    mercatoList = mercatoNews,
                                                    geminiAnalysis = geminiMercatoAnalysis,
                                                    isNewsLoading = isNewsLoading,
                                                    isMercatoLoading = isMercatoLoading,
                                                    newsSource = newsSource,
                                                    mercatoLastSync = mercatoLastSync,
                                                    onRefreshNews = { repository.refreshNewsFromPublicApi() },
                                                    onRefreshMercato = { repository.refreshMercatoNews() },
                                                    onAnalyzeMercato = { query -> repository.generateGeminiMercatoAnalysis(query) },
                                                    isDarkTheme = isDarkTheme,
                                                    onToggleTheme = { toggleTheme() }
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
