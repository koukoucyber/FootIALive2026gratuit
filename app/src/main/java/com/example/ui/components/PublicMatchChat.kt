package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.data.model.Match
import com.example.ui.theme.LiveGlowGreen
import com.example.ui.theme.LiveRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: String,
    val senderName: String,
    val senderTeam: String,
    val messageText: String,
    val timestamp: String,
    val isUser: Boolean = false,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val avatarUrl: String? = null
)

@Composable
fun PublicMatchChat(
    match: Match,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var userName by remember { mutableStateOf("Supporter") }
    var selectedTeamAffiliation by remember { mutableStateOf(match.homeTeam) }
    var inputText by remember { mutableStateOf("") }
    var isSimulatingLive by remember { mutableStateOf(true) }

    // Sample initial public chat messages customized for the match
    val initialMessages = remember(match) {
        listOf(
            ChatMessage(
                id = "m1",
                senderName = "Mamadou 🇨🇮",
                senderTeam = match.homeTeam,
                messageText = "Bienvenue à tous dans le chat public gratuit de ${match.homeTeam} vs ${match.awayTeam} ! 🔥",
                timestamp = "1'",
                likesCount = 12
            ),
            ChatMessage(
                id = "m2",
                senderName = "Abdoulaye 🇸🇳",
                senderTeam = match.awayTeam,
                messageText = "Allez ! Grosse confrontation aujourd'hui. Que le meilleur gagne ⚽🙌",
                timestamp = "3'",
                likesCount = 8
            ),
            ChatMessage(
                id = "m3",
                senderName = "Sofi 🇩🇿",
                senderTeam = match.homeTeam,
                messageText = "Le rythme est super intense dès les premières minutes !",
                timestamp = "8'",
                likesCount = 5
            ),
            ChatMessage(
                id = "m4",
                senderName = "Koffi 🇨🇮",
                senderTeam = match.homeTeam,
                messageText = "Allez les gars, accélérez sur les côtés ! 💥",
                timestamp = "${match.minute}'",
                likesCount = 15
            )
        )
    }

    var messages by remember(match) { mutableStateOf(initialMessages) }
    var onlineCount by remember { mutableIntStateOf(1420) }

    // Simulated live incoming messages from other supporters
    LaunchedEffect(isSimulatingLive, match) {
        if (!isSimulatingLive) return@LaunchedEffect

        val randomFans = listOf(
            Triple("Ibrahim 🇨🇮", match.homeTeam, listOf("Magnifique passe !", "On pousse les gars !", "Allez 🇨🇮🔥", "Le gardien est solide !")),
            Triple("Cheick 🇸🇳", match.awayTeam, listOf("Superbe pressing !", "Attention au contre !", "On lâche rien !", "Quelle frappe !")),
            Triple("Fatou 🇨🇮", match.homeTeam, listOf("Allez la Côte d'Ivoire !! 🇨🇮🎉", "Incroyable ambiance !", "Gros match !")),
            Triple("Alexandre 🇫🇷", "Neutre", listOf("Du très beau jeu ce soir 👌", "Arbitrage parfait pour l'instant.", "Quelle intensité !"))
        )

        var counter = 0
        while (isSimulatingLive) {
            delay(8000) // Every 8 seconds a new supporter message appears
            counter++
            val fan = randomFans.random()
            val text = fan.third.random()
            val newMsg = ChatMessage(
                id = "sim_${counter}_msg_${System.currentTimeMillis()}",
                senderName = fan.first,
                senderTeam = fan.second,
                messageText = text,
                timestamp = "${match.minute}'",
                likesCount = (1..5).random()
            )
            messages = messages + newMsg
            onlineCount += (-3..5).random()
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Header / Banner
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = LiveGlowGreen.copy(alpha = 0.2f),
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(LiveGlowGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CHAT PUBLIC GRATUIT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LiveGlowGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• $onlineCount supporters",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Simulation toggle
                    FilterChip(
                        selected = isSimulatingLive,
                        onClick = { isSimulatingLive = !isSimulatingLive },
                        label = {
                            Text(
                                if (isSimulatingLive) "Live Actif" else "Pause",
                                fontSize = 10.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isSimulatingLive) Icons.Default.FlashOn else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Select team tag for user
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Mon équipe :",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(match.homeTeam, match.awayTeam, "Neutre").forEach { team ->
                        FilterChip(
                            selected = selectedTeamAffiliation == team,
                            onClick = { selectedTeamAffiliation = team },
                            label = { Text(team, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Quick reaction bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickReactions = listOf(
                "⚽ GOOAL !!",
                "🔥 Quel arrêt !",
                "🟨 Faute là !",
                "🇨🇮 Allez les Éléphants !",
                "🇸🇳 Allez Gaindé !",
                "👏 Bravo !",
                "❤️ Allez !"
            )
            items(quickReactions) { reaction ->
                SuggestionChip(
                    onClick = {
                        val newMsg = ChatMessage(
                            id = "msg_${System.currentTimeMillis()}",
                            senderName = if (userName.isBlank()) "Supporter" else userName,
                            senderTeam = selectedTeamAffiliation,
                            messageText = reaction,
                            timestamp = "${match.minute}'",
                            isUser = true
                        )
                        messages = messages + newMsg
                        coroutineScope.launch {
                            delay(100)
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    label = { Text(reaction, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    onLikeClick = {
                        messages = messages.map { m ->
                            if (m.id == msg.id) {
                                m.copy(
                                    isLiked = !m.isLiked,
                                    likesCount = if (m.isLiked) m.likesCount - 1 else m.likesCount + 1
                                )
                            } else m
                        }
                    }
                )
            }
        }

        // Message Input Area
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Pseudo", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .width(100.dp)
                            .padding(end = 6.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Message public gratuit...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        val userText = inputText.trim()
                                        inputText = ""
                                        val newMsg = ChatMessage(
                                            id = "user_${System.currentTimeMillis()}",
                                            senderName = if (userName.isBlank()) "Supporter" else userName,
                                            senderTeam = selectedTeamAffiliation,
                                            messageText = userText,
                                            timestamp = "${match.minute}'",
                                            isUser = true
                                        )
                                        messages = messages + newMsg

                                        coroutineScope.launch {
                                            delay(100)
                                            listState.animateScrollToItem(messages.size - 1)

                                            // Simulate a supporter response after 2.5s
                                            delay(2500)
                                            val responderName = if (selectedTeamAffiliation == match.homeTeam) "Yao 🇨🇮" else "Moussa 🇸🇳"
                                            val responseMsg = ChatMessage(
                                                id = "reply_${System.currentTimeMillis()}",
                                                senderName = responderName,
                                                senderTeam = selectedTeamAffiliation,
                                                messageText = "D'accord avec toi @$userName ! 🔥",
                                                timestamp = "${match.minute}'",
                                                likesCount = 1
                                            )
                                            messages = messages + responseMsg
                                            listState.animateScrollToItem(messages.size - 1)
                                        }
                                    }
                                },
                                enabled = inputText.isNotBlank(),
                                modifier = Modifier.testTag("send_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Envoyer",
                                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    onLikeClick: () -> Unit
) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Top)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = message.senderName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = message.senderTeam,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = "• ${message.timestamp}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 12.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = message.messageText,
                        fontSize = 13.sp,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Like interaction
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { onLikeClick() }
            ) {
                Icon(
                    imageVector = if (message.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "J'aime",
                    tint = if (message.isLiked) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                if (message.likesCount > 0) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${message.likesCount}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Top)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
