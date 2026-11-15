package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.LiveRed
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Graphique circulaire interactif natif (style donut / PieChart) affichant la possession
 * de balle en direct au sein de la fiche détaillée d'un match, avec animation fluide,
 * sélection tactile de secteur et infobulle (Tooltip) détaillée.
 */
@Composable
fun LivePossessionPieChartCard(
    match: Match,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val homePctRaw = match.stats.possessionHome.coerceIn(0, 100)
    val awayPctRaw = match.stats.possessionAway.coerceIn(0, 100)
    val totalPct = (homePctRaw + awayPctRaw).coerceAtLeast(1)
    val homeNormalized = (homePctRaw.toFloat() / totalPct.toFloat()) * 100f
    val awayNormalized = 100f - homeNormalized

    val animatedHomeSweep by animateFloatAsState(
        targetValue = (homeNormalized / 100f) * 360f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "homePossessionSweep"
    )

    // null = vue globale, true = secteur domicile sélectionné, false = secteur extérieur sélectionné
    var selectedIsHome by remember(match.id) { mutableStateOf<Boolean?>(null) }

    val homeStrokeExtra by animateDpAsState(
        targetValue = if (selectedIsHome == true) 5.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "homeSectorExpand"
    )
    val awayStrokeExtra by animateDpAsState(
        targetValue = if (selectedIsHome == false) 5.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "awaySectorExpand"
    )

    val homeColor = MaterialTheme.colorScheme.primary
    val awayColor = MaterialTheme.colorScheme.tertiary
    val separatorColor = MaterialTheme.colorScheme.surface
    val chartSize = if (compact) 136.dp else 172.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_possession_pie_chart_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
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
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Possession de balle (Graphique circulaire)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = when (match.status) {
                        MatchStatus.LIVE -> LiveRed.copy(alpha = 0.14f)
                        MatchStatus.FINISHED -> LiveGlowGreen.copy(alpha = 0.14f)
                        MatchStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = when (match.status) {
                            MatchStatus.LIVE -> "EN DIRECT ${match.minute}'"
                            MatchStatus.FINISHED -> "FINAL"
                            MatchStatus.UPCOMING -> "PRÉ-MATCH"
                        },
                        color = when (match.status) {
                            MatchStatus.LIVE -> LiveRed
                            MatchStatus.FINISHED -> LiveGlowGreen
                            MatchStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Donut Chart + Interactive Side Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(chartSize)
                        .semantics {
                            contentDescription =
                                "Possession de balle : ${match.homeTeam} $homePctRaw%, ${match.awayTeam} $awayPctRaw%"
                        }
                        .testTag("possession_pie_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(match.id, animatedHomeSweep) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val dist = hypot(dx, dy)
                                    val outerR = minOf(size.width, size.height).toFloat() / 2f
                                    val innerR = outerR * 0.42f
                                    if (dist in innerR..outerR) {
                                        // Convert touch coordinates to clockwise angle starting from 12 o'clock (-90°)
                                        var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                        if (angleDeg < 0f) angleDeg += 360f
                                        val tappedHome = angleDeg <= animatedHomeSweep
                                        selectedIsHome = if (selectedIsHome == tappedHome) null else tappedHome
                                    } else {
                                        selectedIsHome = null
                                    }
                                }
                            }
                    ) {
                        val baseStrokePx = (if (compact) 24.dp else 28.dp).toPx()
                        val homeExtraPx = homeStrokeExtra.toPx()
                        val awayExtraPx = awayStrokeExtra.toPx()
                        val maxPad = baseStrokePx / 2f + 6.dp.toPx()
                        val arcDiameter = size.minDimension - maxPad * 2f
                        val topLeft = Offset(
                            x = (size.width - arcDiameter) / 2f,
                            y = (size.height - arcDiameter) / 2f
                        )
                        val arcSize = Size(arcDiameter, arcDiameter)

                        val gapDeg = if (animatedHomeSweep in 4f..356f) 3f else 0f
                        val homeSweepDraw = (animatedHomeSweep - gapDeg).coerceAtLeast(0f)
                        val awaySweepDraw = (360f - animatedHomeSweep - gapDeg).coerceAtLeast(0f)

                        // Home sector
                        if (homeSweepDraw > 0f) {
                            drawArc(
                                color = if (selectedIsHome == false) homeColor.copy(alpha = 0.45f) else homeColor,
                                startAngle = -90f + gapDeg / 2f,
                                sweepAngle = homeSweepDraw,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = baseStrokePx + homeExtraPx,
                                    cap = StrokeCap.Butt
                                )
                            )
                        }

                        // Away sector
                        if (awaySweepDraw > 0f) {
                            drawArc(
                                color = if (selectedIsHome == true) awayColor.copy(alpha = 0.45f) else awayColor,
                                startAngle = -90f + animatedHomeSweep + gapDeg / 2f,
                                sweepAngle = awaySweepDraw,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = baseStrokePx + awayExtraPx,
                                    cap = StrokeCap.Butt
                                )
                            )
                        }

                        // Crisp inner divider ring
                        drawCircle(
                            color = separatorColor.copy(alpha = 0.25f),
                            radius = (arcDiameter - baseStrokePx) / 2f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Center label inside Donut
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        val centerValue = when (selectedIsHome) {
                            true -> "$homePctRaw%"
                            false -> "$awayPctRaw%"
                            null -> "$homePctRaw% / $awayPctRaw%"
                        }
                        val centerCaption = when (selectedIsHome) {
                            true -> match.homeTeam.take(10)
                            false -> match.awayTeam.take(10)
                            null -> "Possession"
                        }
                        Text(
                            text = centerValue,
                            fontSize = if (selectedIsHome == null) 14.sp else 20.sp,
                            fontWeight = FontWeight.Black,
                            color = when (selectedIsHome) {
                                true -> homeColor
                                false -> awayColor
                                null -> MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = centerCaption,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Tappable Legend Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PossessionLegendItem(
                        teamName = match.homeTeam,
                        percentage = homePctRaw,
                        passes = match.stats.passesHome,
                        accuracy = match.stats.passAccuracyHome,
                        color = homeColor,
                        isSelected = selectedIsHome == true,
                        modifier = Modifier.testTag("possession_legend_home"),
                        onClick = {
                            selectedIsHome = if (selectedIsHome == true) null else true
                        }
                    )
                    PossessionLegendItem(
                        teamName = match.awayTeam,
                        percentage = awayPctRaw,
                        passes = match.stats.passesAway,
                        accuracy = match.stats.passAccuracyAway,
                        color = awayColor,
                        isSelected = selectedIsHome == false,
                        modifier = Modifier.testTag("possession_legend_away"),
                        onClick = {
                            selectedIsHome = if (selectedIsHome == false) null else false
                        }
                    )
                }
            }

            // Interactive Tooltip Card when a slice/team is tapped
            AnimatedVisibility(
                visible = selectedIsHome != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val isHome = selectedIsHome == true
                val activeTeam = if (isHome) match.homeTeam else match.awayTeam
                val activePct = if (isHome) homePctRaw else awayPctRaw
                val activePasses = if (isHome) match.stats.passesHome else match.stats.passesAway
                val activeAccuracy = if (isHome) match.stats.passAccuracyHome else match.stats.passAccuracyAway
                val activeColor = if (isHome) homeColor else awayColor

                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = activeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("possession_tooltip_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$activeTeam • $activePct% de possession",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = activeColor
                                )
                                Text(
                                    text = "$activePasses passes • $activeAccuracy% précision",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Fermer",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedIsHome = null }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PossessionLegendItem(
    teamName: String,
    percentage: Int,
    passes: Int,
    accuracy: Int,
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = teamName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$passes passes ($accuracy%)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$percentage%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}
