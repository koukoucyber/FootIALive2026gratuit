package com.example.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MercatoNewsItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MercatoGeminiComponent(
    mercatoItems: List<MercatoNewsItem>,
    geminiAnalysis: String?,
    isLoading: Boolean,
    lastSyncText: String = "",
    onRefresh: () -> Unit,
    onAnalyzeWithGemini: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSource by remember { mutableStateOf("Toutes") }
    var userPrompt by remember { mutableStateOf("") }
    var selectedItemDetail by remember { mutableStateOf<MercatoNewsItem?>(null) }

    val sources = listOf("Toutes", "Le Parisien", "Foot Mercato", "Le Monde")

    val filteredItems = remember(mercatoItems, selectedSource) {
        if (selectedSource == "Toutes") {
            mercatoItems
        } else {
            mercatoItems.filter { it.source.contains(selectedSource, ignoreCase = true) }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mercato_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Banner: Gemini Mercato Intelligence
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_mercato_hero_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0F2027),
                                    Color(0xFF203A43),
                                    Color(0xFF2C5364)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF00B4D8).copy(alpha = 0.25f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🤖", fontSize = 20.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "GEMINI MERCATO SCANNER",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF00B4D8)
                                    )
                                    Text(
                                        text = "Le Parisien • Foot Mercato • Le Monde",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onRefresh,
                                enabled = !isLoading,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("mercato_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualiser",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(if (isLoading) angle else 0f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isLoading) "Scan..." else "Actualiser",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "L'intelligence artificielle Gemini analyse en temps réel les rumeurs, transferts officiels et coulisses financières vérifiées par les trois grands médias français de référence.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick prompt input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = userPrompt,
                                onValueChange = { userPrompt = it },
                                placeholder = {
                                    Text(
                                        "Ex: Où va signer Gyökeres ?",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("gemini_mercato_prompt_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00B4D8),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    onAnalyzeWithGemini(userPrompt.ifBlank { null })
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8)),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("gemini_analyze_submit_button")
                            ) {
                                Text("⚡ Analyser", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Gemini Analysis Result Card
        if (geminiAnalysis != null || isLoading) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_mercato_analysis_result"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00B4D8), Color(0xFF7209B7))
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✨", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Synthèse & Décryptage IA Gemini",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF38B000).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "3 SOURCES VÉRIFIÉES",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38B000),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isLoading && geminiAnalysis == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Gemini analyse les informations de Le Parisien, Foot Mercato et Le Monde...",
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        } else if (geminiAnalysis != null) {
                            Text(
                                text = geminiAnalysis,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Source Filter Chips
        item {
            Column {
                Text(
                    text = "Filtrer par source d'information :",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sources) { source ->
                        val isSelected = selectedSource == source
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSource = source },
                            label = {
                                Text(
                                    text = when (source) {
                                        "Le Parisien" -> "📰 Le Parisien"
                                        "Foot Mercato" -> "🔥 Foot Mercato"
                                        "Le Monde" -> "🗞️ Le Monde"
                                        else -> "Toutes les sources"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }

        // Section Title: Latest Transfer Stories
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dossiers & Rumeurs en Cours (${filteredItems.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lastSyncText.isNotBlank()) lastSyncText else "Actualisé en direct",
                    fontSize = 11.sp,
                    color = Color(0xFF38B000),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Transfer Items List
        items(filteredItems, key = { it.id }) { item ->
            MercatoNewsCard(
                item = item,
                onClick = { selectedItemDetail = item }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Detail Dialog
    selectedItemDetail?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemDetail = null },
            title = {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (item.source) {
                                "Le Parisien" -> Color(0xFF0077B6)
                                "Foot Mercato" -> Color(0xFFE63946)
                                else -> Color(0xFF495057)
                            }
                        ) {
                            Text(
                                text = item.source,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "Indice de fiabilité : ${item.reliabilityIndex}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.reliabilityIndex >= 85) Color(0xFF38B000) else Color(0xFFFFB703)
                        )
                    }

                    Text(
                        text = item.summary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "💡 Insight IA Gemini :",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.geminiInsight,
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Montant estimé: ${item.estimatedFee}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.publishedAt,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (item.url.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lire l'article complet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val query = item.title
                        selectedItemDetail = null
                        onAnalyzeWithGemini(query)
                    }
                ) {
                    Text("🤖 Analyser avec Gemini")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemDetail = null }) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
fun MercatoNewsCard(
    item: MercatoNewsItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("mercato_item_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Source & Reliability & Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (item.source) {
                            "Le Parisien" -> Color(0xFF0077B6)
                            "Foot Mercato" -> Color(0xFFE63946)
                            else -> Color(0xFF495057)
                        }
                    ) {
                        Text(
                            text = item.source,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (item.transferType) {
                            "Officiel" -> Color(0xFF38B000)
                            "Prolongation" -> Color(0xFF7209B7)
                            "Négociations" -> Color(0xFFFFB703)
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = item.transferType.uppercase(),
                            color = if (item.transferType == "Rumeur") MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Reliability Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.reliabilityIndex >= 85) Color(0xFF38B000).copy(alpha = 0.15f) else Color(0xFFFFB703).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fiabilité ${item.reliabilityIndex}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.reliabilityIndex >= 85) Color(0xFF38B000) else Color(0xFFFFB703)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Player / Club & Estimated Fee
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
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.playerOrClub,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "💰 ${item.estimatedFee}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary
            Text(
                text = item.summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Footer with Gemini Insight snippet
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🤖", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.geminiInsight,
                        fontSize = 10.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.publishedAt,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
