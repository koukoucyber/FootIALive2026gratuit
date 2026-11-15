package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.NewsArticle
import com.example.data.model.PlayerProfile
import com.example.ui.components.AICommentaryCard
import com.example.ui.components.InstallationGuideDialog
import com.example.ui.components.MatchCard
import com.example.ui.components.MonthlyMatchesCalendarDialog
import com.example.ui.components.TeamLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    matches: List<Match>,
    players: List<PlayerProfile>,
    news: List<NewsArticle>,
    selectedLeague: String,
    onLeagueSelect: (String) -> Unit,
    onMatchClick: (Match) -> Unit,
    onNavigateToMatches: () -> Unit,
    onNavigateToTeam: () -> Unit,
    onNavigateToNews: () -> Unit,
    onOpenDeploymentDialog: () -> Unit,
    onOpenNotifications: () -> Unit,
    unreadNotifCount: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    appLanguage: com.example.util.AppLanguage = com.example.util.AppLanguage.FRENCH,
    onToggleLanguage: () -> Unit = {},
    isRefreshingMatches: Boolean = false,
    matchesSyncSummary: String = "Scores et prochains matchs actualisés en direct !",
    onRefreshMatches: () -> Unit = {},
    onSearchMatchesWithAI: ((String) -> Unit)? = null
) {
    var showInstallationGuide by remember { mutableStateOf(false) }
    var showMonthlyCalendar by remember { mutableStateOf(false) }
    var showRefreshSuccessBanner by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var homeAiQuery by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "home_refresh_spin")
    val refreshAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshAngle"
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

    val filteredMatches = remember(matches, selectedLeague) {
        if (selectedLeague == "Toutes") matches else matches.filter { it.league == selectedLeague }
    }

    val liveMatches = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.LIVE }
    }

    val upcomingMatches = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.UPCOMING }
    }

    val featuredMatch = remember(matches) {
        matches.firstOrNull { it.status == MatchStatus.LIVE } ?: matches.firstOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsSoccer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "FootIA Live",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = com.example.util.Strings.get("app_subtitle", appLanguage),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Calendrier Mensuel
                    IconButton(
                        onClick = { showMonthlyCalendar = true },
                        modifier = Modifier.testTag("open_calendar_from_home_btn")
                    ) {
                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Calendrier des Matchs")
                    }

                    // Actualiser toutes les ligues
                    IconButton(
                        onClick = {
                            onRefreshMatches()
                            showRefreshSuccessBanner = true
                        },
                        enabled = !isRefreshingMatches,
                        modifier = Modifier.testTag("refresh_live_matches_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualiser les Matchs du Jour",
                            modifier = Modifier.rotate(if (isRefreshingMatches) refreshAngle else 0f)
                        )
                    }

                    // Notifications
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifCount > 0) {
                                    Badge { Text("$unreadNotifCount") }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }

                    // Overflow menu for Theme, Language, Deployment, APK Guide
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("home_more_menu_button")
                        ) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Plus d'options")
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (isDarkTheme) "Mode Clair" else "Mode Sombre")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleTheme()
                                },
                                modifier = Modifier.testTag("theme_toggle_button")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (appLanguage == com.example.util.AppLanguage.FRENCH) "🇬🇧 Passer en Anglais" else "🇫🇷 Passer en Français")
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Translate, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleLanguage()
                                },
                                modifier = Modifier.testTag("home_lang_toggle")
                            )
                            DropdownMenuItem(
                                text = { Text("📲 Installer APK sur Téléphone") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showInstallationGuide = true
                                },
                                modifier = Modifier.testTag("open_install_guide_button")
                            )
                            DropdownMenuItem(
                                text = { Text("☁️ API & Déploiement") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenDeploymentDialog()
                                },
                                modifier = Modifier.testTag("deployment_info_button")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Live Feedback Banner on Refresh
            AnimatedVisibility(visible = isRefreshingMatches || showRefreshSuccessBanner) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("refresh_feedback_banner"),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isRefreshingMatches) "🔄" else "✅", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRefreshingMatches) "Synchronisation de toutes les ligues et des prochains matchs..." else matchesSyncSummary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (!isRefreshingMatches) {
                            IconButton(
                                onClick = { showRefreshSuccessBanner = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
            // Hero Banner - Match Vidéo en Direct ce Soir
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "VIDÉOS EN DIRECT CE SOIR",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "Ligue Europa • L1 • PL • LaLiga • CAN • UCL",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (featuredMatch != null) "${featuredMatch.homeTeam} vs ${featuredMatch.awayTeam}" else "Vivez le Football en Vidéo",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            lineHeight = 26.sp
                        )

                        Text(
                            text = "Vraies images de match, flux vidéo HD, temps forts interactifs et statistiques tactiques en superposition.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (featuredMatch != null) {
                                Button(
                                    onClick = { onMatchClick(featuredMatch) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("hero_watch_live_video_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regarder", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = onNavigateToMatches,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Directs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = { showInstallationGuide = true },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("hero_install_guide_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Installer APK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Match à l'Affiche (Côte d'Ivoire si disponible aujourd'hui, sinon affiche phare du moment)
            item {
                val civMatch = matches.firstOrNull {
                    it.homeTeam.contains("Côte d'Ivoire", ignoreCase = true) ||
                        it.awayTeam.contains("Côte d'Ivoire", ignoreCase = true)
                } ?: featuredMatch
                if (civMatch != null) {
                    val timeFr = if (civMatch.time.contains("FR")) civMatch.time else "${civMatch.time} (FR)"
                    val headerPillText = when (civMatch.status) {
                        MatchStatus.LIVE -> "🔴 EN DIRECT ${civMatch.minute}' • ${civMatch.league.uppercase()}"
                        MatchStatus.FINISHED -> "🏁 TERMINÉ • ${civMatch.date.uppercase()} • $timeFr"
                        MatchStatus.UPCOMING -> "⚽ À L'AFFICHE • ${civMatch.date.uppercase()} • $timeFr"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onMatchClick(civMatch) }
                            .testTag("civ_match_special_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFE76F51),
                                            Color(0xFFF4A261),
                                            Color(0xFF2A9D8F)
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
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.35f),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            text = headerPillText,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    if (!civMatch.broadcastChannel.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF003049)
                                        ) {
                                            Text(
                                                text = "📺 ${civMatch.broadcastChannel.substringBefore("•").trim()}",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        TeamLogo(url = civMatch.homeTeamLogo, teamName = civMatch.homeTeam, modifier = Modifier.size(34.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = civMatch.homeTeam,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = if (civMatch.status == MatchStatus.UPCOMING) timeFr else "${civMatch.homeScore} - ${civMatch.awayScore}",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = civMatch.awayTeam,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        TeamLogo(url = civMatch.awayTeamLogo, teamName = civMatch.awayTeam, modifier = Modifier.size(34.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📍 ${civMatch.stadium} • ${civMatch.league}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = "Diffuser >",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Compact Gemini AI Match Search & Smart Filter
            if (onSearchMatchesWithAI != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("home_ai_search_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = homeAiQuery,
                                    onValueChange = { homeAiQuery = it },
                                    placeholder = {
                                        Text(
                                            text = "✨ Filtrer un match par IA (ex: Real ce soir)...",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            if (homeAiQuery.isNotBlank()) {
                                                onSearchMatchesWithAI(homeAiQuery.trim())
                                            }
                                        }
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("home_ai_search_input")
                                )
                                Button(
                                    onClick = {
                                        if (homeAiQuery.isNotBlank()) {
                                            onSearchMatchesWithAI(homeAiQuery.trim())
                                        }
                                    },
                                    enabled = homeAiQuery.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 50.dp)
                                        .testTag("home_ai_search_button")
                                ) {
                                    Text("IA", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val quick = listOf(
                                    "🔴 Directs" to "Matchs actuellement en direct",
                                    "🌙 Ce soir (FR)" to "Matchs joués ce soir en heure française",
                                    "🌍 Afrique / CAN" to "Matchs d'équipes africaines ou CAN"
                                )
                                items(quick) { (label, prompt) ->
                                    AssistChip(
                                        onClick = { onSearchMatchesWithAI(prompt) },
                                        label = { Text(label, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Leagues Quick Selector Chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    items(leagues) { league ->
                        FilterChip(
                            selected = selectedLeague == league,
                            onClick = { onLeagueSelect(league) },
                            label = { Text(league, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = if (selectedLeague == league) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // Featured Match / Live Section
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Matchs en Direct",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (liveMatches.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${liveMatches.size}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        TextButton(onClick = onNavigateToMatches) {
                            Text("Tout voir", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredMatches.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (matches.isEmpty() && isRefreshingMatches) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = "Synchronisation des vrais matchs officiels en direct...",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = if (matches.isEmpty()) {
                                        "Aucun match officiel chargé. Vérifiez votre connexion et appuyez sur Actualiser."
                                    } else {
                                        "Aucun match disponible pour cette ligue actuellement."
                                    },
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val homeDisplayMatches = remember(filteredMatches, liveMatches) {
                            if (liveMatches.isNotEmpty()) {
                                (liveMatches + filteredMatches.filter { it.status != MatchStatus.LIVE }.take(6)).distinctBy { it.id }
                            } else {
                                filteredMatches.take(10)
                            }
                        }
                        homeDisplayMatches.forEach { match ->
                            MatchCard(
                                match = match,
                                onClick = { onMatchClick(match) },
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Prochains Matchs Programmés (Upcoming matches carousel)
            if (upcomingMatches.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Prochains Matchs Programmés",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            TextButton(onClick = onNavigateToMatches) {
                                Text("Voir calendrier >", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        upcomingMatches.take(12).forEach { match ->
                            MatchCard(
                                match = match,
                                onClick = { onMatchClick(match) },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Live AI Commentary Highlights
            if (featuredMatch != null && featuredMatch.aiCommentaries.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Direct Commentateur IA TV",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        AICommentaryCard(commentary = featuredMatch.aiCommentaries.first())
                    }
                }
            }

            // Star Players Section
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Joueurs Vedettes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        TextButton(onClick = onNavigateToNews) {
                            Text("Voir tout", fontWeight = FontWeight.Bold)
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        items(players) { player ->
                            Card(
                                onClick = onNavigateToNews,
                                modifier = Modifier
                                    .width(160.dp)
                                    .testTag("player_card_${player.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = player.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = player.team,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "${player.goals} buts • ⭐ ${player.rating}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Latest News Highlights
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Actualités Football",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        TextButton(onClick = onNavigateToNews) {
                            Text("Plus d'actus", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    news.take(2).forEach { article ->
                        Card(
                            onClick = onNavigateToNews,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = article.category.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = article.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${article.author} • ${article.date}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

    if (showInstallationGuide) {
        InstallationGuideDialog(onDismiss = { showInstallationGuide = false })
    }

    if (showMonthlyCalendar) {
        MonthlyMatchesCalendarDialog(
            matches = matches,
            onDismiss = { showMonthlyCalendar = false },
            onMatchClick = onMatchClick
        )
    }
}

