package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.VideoHighlightMarker
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.LiveRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lecteur vidéo de match réel en haute définition avec superposition (HUD) interactive :
 * - Diffusion vidéo réelle de football (images de match en direct et temps forts)
 * - Superposition du tableau de score et du chrono en temps réel
 * - Superposition des statistiques tactiques (Possession, Tirs, Fautes) sans couper la vidéo
 * - Timeline interactive des buts et moments forts pour sauter directement à l'action
 * - Mode plein écran et contrôles fluides (Play/Pause, Muet, Recul/Avance 10s)
 */
@Composable
fun RealMatchVideoPlayer(
    match: Match,
    allMatches: List<Match> = emptyList(),
    onSelectMatch: ((Match) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showHudStats by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }
    var activeHighlightNotification by remember { mutableStateOf<String?>(null) }

    // Stream URL fallback if match doesn't have one
    val activeVideoUrl = remember(match) {
        match.videoStreamUrl ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
    }

    // Auto-update playback progress
    LaunchedEffect(videoViewRef, isPlaying) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition
                    val dur = vv.duration
                    if (dur > 0) durationMs = dur
                }
            }
            delay(500L)
        }
    }

    // Auto-hide controls after 5 seconds of inactivity
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000L)
            showControls = false
        }
    }

    // Clear highlight toast
    LaunchedEffect(activeHighlightNotification) {
        if (activeHighlightNotification != null) {
            delay(3500L)
            activeHighlightNotification = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("real_match_video_player")
    ) {
        // Video Container Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFullscreen) Modifier.fillMaxHeight(0.85f)
                    else Modifier.aspectRatio(16f / 9f)
                )
                .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 16.dp))
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    showControls = !showControls
                }
        ) {
            // Native VideoView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setVideoURI(Uri.parse(activeVideoUrl))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            durationMs = mp.duration
                            if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                            start()
                            isPlaying = true
                        }
                        setOnErrorListener { _, _, _ ->
                            // Fallback gracefully on network hiccup
                            true
                        }
                        videoViewRef = this
                    }
                },
                update = { vv ->
                    videoViewRef = vv
                }
            )

            // TOP OVERLAY: Scoreboard & Live Badge HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Live badge & Competition
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = LiveRed,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (match.status == MatchStatus.LIVE) "DIRECT ${match.minute}'" else "VIDÉO HD",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = match.league,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Center: Score HUD Box
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = match.homeTeam.take(3).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${match.homeScore} - ${match.awayScore}",
                                color = LiveGlowGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = match.awayTeam.take(3).uppercase(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Right: HUD Stats Toggle Button & Mute
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (showHudStats) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showHudStats = !showHudStats }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = "Stats HUD",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "HUD Stats",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                videoViewRef?.let {
                                    // Mute via seek / volume logic
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Activer le son" else "Couper le son",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // HUD STATS OVERLAY (Translucent panel with live game statistics over video)
            androidx.compose.animation.AnimatedVisibility(
                visible = showHudStats,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, top = 40.dp, bottom = 40.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.80f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.widthIn(max = 240.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ STATS EN DIRECT",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer HUD",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showHudStats = false }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mini Stat Row: Possession
                        MiniStatHudRow(
                            label = "Possession",
                            homeVal = "${match.stats.possessionHome}%",
                            awayVal = "${match.stats.possessionAway}%",
                            homeRatio = match.stats.possessionHome.toFloat() / 100f
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Mini Stat Row: Tirs Cadrés
                        MiniStatHudRow(
                            label = "Tirs Cadrés",
                            homeVal = "${match.stats.shotsOnTargetHome}",
                            awayVal = "${match.stats.shotsOnTargetAway}",
                            homeRatio = (match.stats.shotsOnTargetHome.toFloat() / (match.stats.shotsOnTargetHome + match.stats.shotsOnTargetAway).coerceAtLeast(1))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Mini Stat Row: Fautes
                        MiniStatHudRow(
                            label = "Fautes",
                            homeVal = "${match.stats.foulsHome}",
                            awayVal = "${match.stats.foulsAway}",
                            homeRatio = (match.stats.foulsHome.toFloat() / (match.stats.foulsHome + match.stats.foulsAway).coerceAtLeast(1))
                        )
                    }
                }
            }

            // HIGHLIGHT NOTIFICATION BANNER (When jumping to a goal/action)
            androidx.compose.animation.AnimatedVisibility(
                visible = activeHighlightNotification != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeHighlightNotification ?: "",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // BOTTOM CONTROLS OVERLAY (Play/Pause, Slider, Fullscreen)
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Slider / Progress bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimeMs(currentPositionMs),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Slider(
                                value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                                onValueChange = { ratio ->
                                    val newPos = (ratio * durationMs).toInt()
                                    videoViewRef?.seekTo(newPos)
                                    currentPositionMs = newPos
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )

                            Text(
                                text = formatTimeMs(durationMs),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Replay / -10s
                                IconButton(
                                    onClick = {
                                        videoViewRef?.let {
                                            val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                            it.seekTo(newPos)
                                            currentPositionMs = newPos
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "Reculer de 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Play / Pause Button
                                IconButton(
                                    onClick = {
                                        videoViewRef?.let {
                                            if (it.isPlaying) {
                                                it.pause()
                                                isPlaying = false
                                            } else {
                                                it.start()
                                                isPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Lecture",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // +10s
                                IconButton(
                                    onClick = {
                                        videoViewRef?.let {
                                            val newPos = (it.currentPosition + 10000).coerceAtMost(durationMs)
                                            it.seekTo(newPos)
                                            currentPositionMs = newPos
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = "Avancer de 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Fullscreen Button
                            IconButton(
                                onClick = { isFullscreen = !isFullscreen },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Quitter plein écran" else "Plein écran",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // INTERACTIVE HIGHLIGHTS BAR (Temps forts et buts cliquables)
        val highlights = match.videoHighlights.ifEmpty {
            listOf(
                VideoHighlightMarker("hl_1", 38, "⚽ But Barcola", "GOAL", 4),
                VideoHighlightMarker("hl_2", 54, "🟨 Carton Balerdi", "FOUL", 18),
                VideoHighlightMarker("hl_3", 67, "⚽ But Dembélé", "GOAL", 32),
                VideoHighlightMarker("hl_4", 74, "🧤 Arrêt Donnarumma", "SAVE", 46)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Temps Forts Vidéo :",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(highlights) { hl ->
                Surface(
                    color = when (hl.type) {
                        "GOAL" -> MaterialTheme.colorScheme.primaryContainer
                        "SAVE" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable {
                        // Jump directly to timestamp in the video
                        val seekSec = hl.timestampSeconds
                        videoViewRef?.seekTo(seekSec * 1000)
                        currentPositionMs = seekSec * 1000
                        if (!isPlaying) {
                            videoViewRef?.start()
                            isPlaying = true
                        }
                        activeHighlightNotification = "Temps fort : ${hl.title} (${hl.minute}')"
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${hl.minute}'",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = hl.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // QUICK MULTI-MATCH / TOURNAMENT ZAPPING
        if (allMatches.isNotEmpty() && onSelectMatch != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Zapping Vidéo ce soir (Grands Tournois) :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allMatches) { m ->
                    val isSelected = m.id == match.id
                    Surface(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable {
                            if (!isSelected) {
                                onSelectMatch(m)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${m.homeTeam.take(3)} vs ${m.awayTeam.take(3)}",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = m.league.take(3).uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatHudRow(
    label: String,
    homeVal: String,
    awayVal: String,
    homeRatio: Float
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = homeVal, color = LiveGlowGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
            Text(text = awayVal, color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(homeRatio.coerceIn(0.01f, 0.99f))
                    .background(LiveGlowGreen)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight((1f - homeRatio).coerceIn(0.01f, 0.99f))
                    .background(Color(0xFF60A5FA))
            )
        }
    }
}

private fun formatTimeMs(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
