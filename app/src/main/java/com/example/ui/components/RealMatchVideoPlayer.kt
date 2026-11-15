package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ai.AiPostMatchVideoSummaryState
import com.example.data.ai.AiVideoHighlightClip
import com.example.data.model.EventType
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.VideoHighlightMarker
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.LiveRed
import kotlinx.coroutines.delay

/**
 * Lecteur vidéo de match en direct haute disponibilité :
 * - Utilise TextureView + MediaPlayer (compatible LazyColumn et émulateur streaming sans écran noir SurfaceView)
 * - Vue Terrain TV animée en direct en fallback transparent ou sur bascule "Radar TV"
 * - Superposition HUD score, date, heure française (FR), chaîne TV et statistiques tactiques
 */
@Composable
fun RealMatchVideoPlayer(
    match: Match,
    allMatches: List<Match> = emptyList(),
    onSelectMatch: ((Match) -> Unit)? = null,
    postMatchSummaryState: AiPostMatchVideoSummaryState = AiPostMatchVideoSummaryState.Idle,
    onRetryPostMatchAnalysis: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var activeSurface by remember { mutableStateOf<Surface?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showHudStats by remember { mutableStateOf(false) }
    var showTacticalPitch by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(15000) }
    var activeHighlightNotification by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var isVideoReady by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    val localRawUri = remember(context) {
        "android.resource://${context.packageName}/${com.example.R.raw.match_live_stream}"
    }

    val fallbackUrls = remember(match, localRawUri) {
        val list = mutableListOf<String>()
        val rawUrl = match.videoStreamUrl
        if (!rawUrl.isNullOrBlank() &&
            !rawUrl.contains("commondatastorage.googleapis.com") &&
            !rawUrl.contains("mov_bbb") &&
            !rawUrl.contains("big_buck_bunny")
        ) {
            if (rawUrl.startsWith("android.resource://com.example/")) {
                list.add(localRawUri)
            } else {
                list.add(rawUrl)
            }
        }
        list.add(localRawUri)
        list.distinct()
    }
    var currentUrlIndex by remember(match.id) { mutableIntStateOf(0) }
    val currentUrl = fallbackUrls.getOrElse(currentUrlIndex % fallbackUrls.size) { localRawUri }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
            } catch (_: Exception) {
            }
            try {
                mediaPlayerRef?.release()
            } catch (_: Exception) {
            }
            mediaPlayerRef = null
            try {
                activeSurface?.release()
            } catch (_: Exception) {
            }
            activeSurface = null
        }
    }

    // Apply mute state whenever MediaPlayer is ready or toggled
    LaunchedEffect(isMuted, mediaPlayerRef) {
        try {
            mediaPlayerRef?.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
        } catch (_: Exception) {
        }
    }

    // Prepare MediaPlayer safely once SurfaceTexture & URL are ready
    LaunchedEffect(match.id, currentUrl, activeSurface, reloadNonce) {
        val surface = activeSurface ?: return@LaunchedEffect
        isBuffering = true
        isVideoReady = false
        hasError = false
        try {
            mediaPlayerRef?.let { old ->
                try {
                    if (old.isPlaying) old.stop()
                } catch (_: Exception) {
                }
                try {
                    old.release()
                } catch (_: Exception) {
                }
            }
            mediaPlayerRef = null

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setSurface(surface)
                setDataSource(context, Uri.parse(currentUrl))
                isLooping = true
                setOnPreparedListener { readyMp ->
                    mediaPlayerRef = readyMp
                    durationMs = readyMp.duration.coerceAtLeast(15000)
                    try {
                        readyMp.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                    } catch (_: Exception) {
                    }
                    readyMp.start()
                    isPlaying = true
                    isBuffering = false
                    isVideoReady = true
                    hasError = false
                }
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("RealMatchVideoPlayer", "MediaPlayer Error ($what, $extra) on URL: $currentUrl")
                    if (currentUrlIndex < fallbackUrls.size - 1) {
                        currentUrlIndex++
                    } else {
                        isBuffering = false
                        isVideoReady = false
                        hasError = true
                    }
                    true
                }
            }
            mediaPlayerRef = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            android.util.Log.e("RealMatchVideoPlayer", "Video init exception: ${e.message}")
            isBuffering = false
            isVideoReady = false
            hasError = true
        }
    }

    // Timeout guard: never leave spinner spinning forever if codec/emulator fails to prepare
    LaunchedEffect(match.id, currentUrl, isBuffering, reloadNonce) {
        if (isBuffering) {
            delay(6000L)
            if (isBuffering && !isVideoReady) {
                if (currentUrl != localRawUri && fallbackUrls.contains(localRawUri)) {
                    currentUrlIndex = fallbackUrls.indexOf(localRawUri)
                } else {
                    isBuffering = false
                    hasError = true
                }
            }
        }
    }

    // Auto-update playback progress
    LaunchedEffect(mediaPlayerRef, isPlaying, isVideoReady) {
        while (isPlaying) {
            val mp = mediaPlayerRef
            if (mp != null && isVideoReady) {
                try {
                    if (mp.isPlaying) {
                        currentPositionMs = mp.currentPosition
                        val dur = mp.duration
                        if (dur > 0) durationMs = dur
                    }
                } catch (_: Exception) {
                }
            } else {
                // Keep simulated broadcast ticker advancing when in Radar TV fallback mode
                currentPositionMs = (currentPositionMs + 500) % durationMs.coerceAtLeast(15000)
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
                .background(Color(0xFF0B132B))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    showControls = !showControls
                }
        ) {
            // TextureView renders reliably inside LazyColumn & virtualized streaming emulators
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        isOpaque = false
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                activeSurface?.release()
                                activeSurface = Surface(st)
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                try {
                                    mediaPlayerRef?.stop()
                                } catch (_: Exception) {
                                }
                                try {
                                    mediaPlayerRef?.release()
                                } catch (_: Exception) {
                                }
                                mediaPlayerRef = null
                                activeSurface?.release()
                                activeSurface = null
                                isVideoReady = false
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                }
            )

            // Live Broadcast Animated Pitch Overlay (shown while buffering, on video codec fallback, or in Radar TV mode)
            if (showTacticalPitch || hasError || !isVideoReady) {
                LiveBroadcastPitchCanvas(
                    match = match,
                    isBuffering = isBuffering && !hasError,
                    isFallbackMode = hasError && !isVideoReady,
                    onRetryVideo = {
                        hasError = false
                        isBuffering = true
                        currentUrlIndex = (currentUrlIndex + 1) % fallbackUrls.size
                        reloadNonce++
                    }
                )
            }

            // TOP OVERLAY: Scoreboard, Date/Heure FR & Live Badge HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Left: Live/Schedule badge & Competition
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = when (match.status) {
                                    MatchStatus.LIVE -> LiveRed
                                    MatchStatus.UPCOMING -> Color(0xFF0077B6)
                                    MatchStatus.FINISHED -> Color(0xFF334155)
                                },
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
                                        text = when (match.status) {
                                            MatchStatus.LIVE -> "DIRECT ${match.minute}'"
                                            MatchStatus.UPCOMING -> "AVANT-MATCH"
                                            MatchStatus.FINISHED -> "🎬 RÉSUMÉ ${match.homeScore}-${match.awayScore}"
                                        },
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = match.league,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Center: Score HUD Box
                        Surface(
                            color = Color.Black.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = match.homeTeam.take(3).uppercase(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (match.status == MatchStatus.UPCOMING) "VS" else "${match.homeScore}-${match.awayScore}",
                                    color = LiveGlowGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = match.awayTeam.take(3).uppercase(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right: Tactical Pitch Toggle, HUD Stats Toggle & Mute
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (showTacticalPitch) MaterialTheme.colorScheme.secondary else Color.White.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { showTacticalPitch = !showTacticalPitch }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stadium,
                                    contentDescription = "Radar Terrain TV",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp, vertical = 4.dp)
                                        .size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = if (showHudStats) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { showHudStats = !showHudStats }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "Stats HUD",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Stats",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    try {
                                        mediaPlayerRef?.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                                    } catch (_: Exception) {
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

                    // Date & French time + Broadcast Channel Sub-bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val timeFr = if (match.time.contains("FR")) match.time else "${match.time} (FR)"
                        Text(
                            text = "🕒 ${match.date} • $timeFr",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!match.broadcastChannel.isNullOrBlank()) {
                            Text(
                                text = "📺 ${match.broadcastChannel}",
                                color = LiveGlowGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                    try {
                                        if (isVideoReady) mediaPlayerRef?.seekTo(newPos)
                                    } catch (_: Exception) {
                                    }
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
                                        val current = try {
                                            if (isVideoReady) mediaPlayerRef?.currentPosition ?: currentPositionMs else currentPositionMs
                                        } catch (_: Exception) {
                                            currentPositionMs
                                        }
                                        val newPos = (current - 10000).coerceAtLeast(0)
                                        try {
                                            if (isVideoReady) mediaPlayerRef?.seekTo(newPos)
                                        } catch (_: Exception) {
                                        }
                                        currentPositionMs = newPos
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
                                        val mp = mediaPlayerRef
                                        if (isVideoReady && mp != null) {
                                            try {
                                                if (mp.isPlaying) {
                                                    mp.pause()
                                                    isPlaying = false
                                                } else {
                                                    mp.start()
                                                    isPlaying = true
                                                }
                                            } catch (_: Exception) {
                                                isPlaying = !isPlaying
                                            }
                                        } else {
                                            isPlaying = !isPlaying
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
                                        val current = try {
                                            if (isVideoReady) mediaPlayerRef?.currentPosition ?: currentPositionMs else currentPositionMs
                                        } catch (_: Exception) {
                                            currentPositionMs
                                        }
                                        val newPos = (current + 10000).coerceAtMost(durationMs)
                                        try {
                                            if (isVideoReady) mediaPlayerRef?.seekTo(newPos)
                                        } catch (_: Exception) {
                                        }
                                        currentPositionMs = newPos
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

        // REAL MATCH HIGHLIGHTS BAR (derived strictly from actual match highlights/events)
        val highlights = remember(match.id, match.videoHighlights, match.events) {
            if (match.videoHighlights.isNotEmpty()) {
                match.videoHighlights
            } else {
                match.events.sortedBy { it.minute }.mapIndexed { idx, ev ->
                    val hlType = when (ev.type) {
                        EventType.GOAL -> "GOAL"
                        EventType.RED_CARD, EventType.YELLOW_CARD -> "FOUL"
                        else -> "ACTION"
                    }
                    val prefix = when (ev.type) {
                        EventType.GOAL -> "⚽ But"
                        EventType.PENALTY -> "⚽ Penalty"
                        EventType.RED_CARD -> "🟥 Rouge"
                        EventType.YELLOW_CARD -> "🟨 Jaune"
                        EventType.SUB -> "🔄 Remp."
                        EventType.VAR -> "📺 VAR"
                        EventType.HALF_TIME -> "⏱️ Mi-temps"
                        EventType.FULL_TIME -> "🏁 Fin"
                    }
                    VideoHighlightMarker(
                        id = "dyn_hl_${match.id}_$idx",
                        minute = ev.minute,
                        title = "$prefix • ${ev.playerName}",
                        type = hlType,
                        timestampSeconds = ((ev.minute * 13) / 90).coerceIn(1, 13)
                    )
                }
            }
        }

        val seekToVideoSeconds: (Int, String) -> Unit = { sec, label ->
            val seekMs = (sec * 1000).coerceIn(0, durationMs.coerceAtLeast(15000))
            try {
                if (isVideoReady) mediaPlayerRef?.seekTo(seekMs)
            } catch (_: Exception) {
            }
            currentPositionMs = seekMs
            if (!isPlaying) {
                try {
                    if (isVideoReady) mediaPlayerRef?.start()
                } catch (_: Exception) {
                }
                isPlaying = true
            }
            activeHighlightNotification = label
        }

        if (match.status == MatchStatus.FINISHED) {
            PostMatchAiVideoSummaryCard(
                match = match,
                postMatchSummaryState = postMatchSummaryState,
                onSeekToClip = { clip ->
                    val icon = if (clip.category == "GOAL") "⚽" else "🔥"
                    seekToVideoSeconds(clip.timestampSeconds, "$icon ${clip.minute}' • ${clip.title}")
                },
                onRetryAnalysis = onRetryPostMatchAnalysis
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (highlights.isNotEmpty()) {
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
                    text = if (match.status == MatchStatus.FINISHED) {
                        "Temps Forts du Match (${match.homeTeam} vs ${match.awayTeam}) :"
                    } else {
                        "Temps Forts en Direct :"
                    },
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
                    val isYa2ne = hl.type == "YA2NE_DRIBBLE"
                    Surface(
                        color = when (hl.type) {
                            "YA2NE_DRIBBLE" -> Color(0xFFFF4500).copy(alpha = 0.25f)
                            "GOAL" -> MaterialTheme.colorScheme.primaryContainer
                            "SAVE" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (isYa2ne) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4500)) else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable {
                            seekToVideoSeconds(
                                hl.timestampSeconds,
                                if (isYa2ne) "🔥 TCHIEEEEY ! ${hl.title} (${hl.minute}')" else "Temps fort : ${hl.title} (${hl.minute}')"
                            )
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
                                color = if (isYa2ne) Color(0xFFFF4500) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = hl.title,
                                fontSize = 11.sp,
                                fontWeight = if (isYa2ne) FontWeight.Bold else FontWeight.Medium,
                                color = if (isYa2ne) Color(0xFFFF9900) else MaterialTheme.colorScheme.onSurface
                            )
                        }
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

@Composable
private fun LiveBroadcastPitchCanvas(
    match: Match,
    isBuffering: Boolean,
    isFallbackMode: Boolean,
    onRetryVideo: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pitch_broadcast")
    val ballPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ball_phase"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A2E1A),
                        Color(0xFF0F4C29),
                        Color(0xFF082414)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padX = w * 0.06f
            val padY = h * 0.16f
            val pitchW = w - padX * 2f
            val pitchH = h - padY * 1.65f

            // Alternating turf stripes
            val stripeCount = 8
            val stripeW = pitchW / stripeCount
            for (i in 0 until stripeCount) {
                if (i % 2 == 0) {
                    drawRect(
                        color = Color(0xFF145C33).copy(alpha = 0.45f),
                        topLeft = Offset(padX + i * stripeW, padY),
                        size = Size(stripeW, pitchH)
                    )
                }
            }

            val lineColor = Color.White.copy(alpha = 0.30f)
            val stroke = Stroke(width = 2f)

            // Outer pitch boundary
            drawRect(
                color = lineColor,
                topLeft = Offset(padX, padY),
                size = Size(pitchW, pitchH),
                style = stroke
            )

            // Halfway line & center circle
            drawLine(
                color = lineColor,
                start = Offset(w / 2f, padY),
                end = Offset(w / 2f, padY + pitchH),
                strokeWidth = 2f
            )
            drawCircle(
                color = lineColor,
                radius = pitchH * 0.22f,
                center = Offset(w / 2f, padY + pitchH / 2f),
                style = stroke
            )

            // Left & right penalty boxes
            val boxW = pitchW * 0.15f
            val boxH = pitchH * 0.56f
            val boxY = padY + (pitchH - boxH) / 2f
            drawRect(color = lineColor, topLeft = Offset(padX, boxY), size = Size(boxW, boxH), style = stroke)
            drawRect(color = lineColor, topLeft = Offset(padX + pitchW - boxW, boxY), size = Size(boxW, boxH), style = stroke)

            // Animated home (emerald) and away (amber) players
            val shift = kotlin.math.sin(ballPhase) * (pitchW * 0.04f)
            val homePositions = listOf(
                0.08f to 0.50f,
                0.24f to 0.24f,
                0.26f to 0.76f,
                0.42f to 0.36f,
                0.46f to 0.65f
            )
            val awayPositions = listOf(
                0.92f to 0.50f,
                0.76f to 0.26f,
                0.74f to 0.74f,
                0.58f to 0.35f,
                0.55f to 0.64f
            )

            homePositions.forEachIndexed { idx, (rx, ry) ->
                val jitter = if (idx == 0) 0f else shift
                val cx = padX + pitchW * rx + jitter
                val cy = padY + pitchH * ry + kotlin.math.cos(ballPhase + idx) * (pitchH * 0.03f)
                drawCircle(color = LiveGlowGreen, radius = 6f, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 6f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
            }

            awayPositions.forEachIndexed { idx, (rx, ry) ->
                val jitter = if (idx == 0) 0f else shift
                val cx = padX + pitchW * rx + jitter
                val cy = padY + pitchH * ry + kotlin.math.sin(ballPhase + idx) * (pitchH * 0.03f)
                drawCircle(color = Color(0xFFFFB703), radius = 6f, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 6f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
            }

            // Moving football
            val bx = padX + pitchW * (0.5f + 0.30f * kotlin.math.sin(ballPhase))
            val by = padY + pitchH * (0.5f + 0.26f * kotlin.math.cos(ballPhase * 1.5f))
            drawCircle(color = LiveGlowGreen.copy(alpha = 0.38f), radius = 12f, center = Offset(bx, by))
            drawCircle(color = Color.White, radius = 5.5f, center = Offset(bx, by))
        }

        // Center broadcast telemetry banner
        Surface(
            color = Color.Black.copy(alpha = 0.68f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (match.status == MatchStatus.LIVE) LiveRed.copy(alpha = pulseAlpha)
                                else LiveGlowGreen.copy(alpha = pulseAlpha)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (match.status) {
                            MatchStatus.LIVE -> "DIFFUSION TERRAIN TV EN DIRECT (${match.minute}')"
                            MatchStatus.UPCOMING -> "ANTENNE TV • COUP D'ENVOI ${match.time}"
                            MatchStatus.FINISHED -> "REPLAY TERRAIN TV • TERMINÉ"
                        },
                        color = LiveGlowGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (match.status == MatchStatus.UPCOMING) {
                        "${match.homeTeam}  vs  ${match.awayTeam}"
                    } else {
                        "${match.homeTeam}  ${match.homeScore} - ${match.awayScore}  ${match.awayTeam}"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val metaLine = buildString {
                    append(match.stadium)
                    if (!match.broadcastChannel.isNullOrBlank()) {
                        append(" • ")
                        append(match.broadcastChannel)
                    }
                }
                Text(
                    text = metaLine,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isBuffering) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = LiveGlowGreen,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.8.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chargement du flux vidéo HD...",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp
                        )
                    }
                } else if (isFallbackMode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onRetryVideo() }
                    ) {
                        Text(
                            text = "↻ Réessayer le flux vidéo HD",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun PostMatchAiVideoSummaryCard(
    match: Match,
    postMatchSummaryState: AiPostMatchVideoSummaryState,
    onSeekToClip: (AiVideoHighlightClip) -> Unit,
    onRetryAnalysis: (() -> Unit)?
) {
    var selectedCategoryFilter by remember(match.id) { mutableStateOf("Tout") }
    val categories = listOf("Tout", "⚽ Buts", "🔥 Actions Clés")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_match_ai_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Gemini Résumé Vidéo IA",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🎬 Résumé Vidéo IA • Buts & Actions Clés",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onRetryAnalysis != null) {
                    TextButton(
                        onClick = onRetryAnalysis,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("post_match_ai_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Relancer le filtrage Gemini",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Actualiser IA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            when {
                postMatchSummaryState is AiPostMatchVideoSummaryState.Loading &&
                    postMatchSummaryState.matchId == match.id -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Gemini IA filtre les buts et actions décisives de ${match.homeTeam} vs ${match.awayTeam}...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                postMatchSummaryState is AiPostMatchVideoSummaryState.Error &&
                    postMatchSummaryState.matchId == match.id -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = postMatchSummaryState.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (onRetryAnalysis != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onRetryAnalysis,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Réessayer l'analyse IA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                postMatchSummaryState is AiPostMatchVideoSummaryState.Success &&
                    postMatchSummaryState.matchId == match.id -> {
                    val filteredClips = remember(postMatchSummaryState.clips, selectedCategoryFilter) {
                        when (selectedCategoryFilter) {
                            "⚽ Buts" -> postMatchSummaryState.clips.filter { it.category == "GOAL" }
                            "🔥 Actions Clés" -> postMatchSummaryState.clips.filter { it.category != "GOAL" }
                            else -> postMatchSummaryState.clips
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = postMatchSummaryState.summaryHeadline,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(categories) { cat ->
                                FilterChip(
                                    selected = selectedCategoryFilter == cat,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = {
                                        Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    },
                                    modifier = Modifier.testTag("post_match_filter_$cat")
                                )
                            }
                        }
                        if (filteredClips.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            FilledTonalButton(
                                onClick = { onSeekToClip(filteredClips.first()) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("post_match_play_summary_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Lancer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredClips.isEmpty()) {
                        Text(
                            text = "Aucun moment filtré dans cette catégorie pour ce match.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredClips.forEachIndexed { idx, clip ->
                                val isGoal = clip.category == "GOAL"
                                val badgeColor = when (clip.category) {
                                    "GOAL" -> LiveGlowGreen
                                    "RED_CARD" -> LiveRed
                                    "YELLOW_CARD" -> Color(0xFFF59E0B)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                val badgeText = when (clip.category) {
                                    "GOAL" -> "⚽ BUT"
                                    "RED_CARD" -> "🟥 CARTON ROUGE"
                                    "YELLOW_CARD" -> "🟨 AVERTISSEMENT"
                                    else -> "🔥 ACTION CLÉ"
                                }
                                Surface(
                                    color = if (isGoal) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSeekToClip(clip) }
                                        .testTag("post_match_clip_$idx")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = badgeColor.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "${clip.minute}'",
                                                color = badgeColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = badgeColor
                                            )
                                            Text(
                                                text = clip.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = clip.reason,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "▶ Voir",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Le filtrage vidéo Gemini analyse automatiquement les buts et actions décisives de fin de match.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
