package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.InstallationGuideDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeploymentInfoDialog(
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentApiKey) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showVisualGuide by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Publication & API Football", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Guide Déploiement", fontSize = 11.sp, modifier = Modifier.padding(8.dp)) }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text("API & Gemini", fontSize = 11.sp, modifier = Modifier.padding(8.dp)) }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "✅ APK Compilé & Prêt !", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Le fichier 'FootIA-Live.apk' (24 Mo) a été généré et placé directement à la racine de vos fichiers. Vous pouvez faire un clic droit dessus dans l'explorateur de fichiers pour le télécharger instantanément.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showVisualGuide = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📲 Voir le Guide Pas à Pas (Images)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "1. Export Android APK / AAB :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "• Dans AI Studio, vous pouvez aussi cliquer en haut à droite sur le menu d'export pour générer un nouvel APK ou télécharger le code complet (ZIP).\n• Compatible avec tous les smartphones Android (Samsung, Xiaomi, Pixel...).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "2. Déploiement GitHub Actions Automatique :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "• Le workflow '.github/workflows/build-apk.yml' est déjà configuré.\n• Chaque mise à jour génère automatiquement votre APK dans l'onglet 'Actions' sur GitHub.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(text = "Clé API Football Externe (Facultative):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "FootIA Live intègre un moteur de simulation en direct temps réel. Pour connecter l'application à une API comme API-Football ou Football-Data.org, saisissez votre clé ci-dessous :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("Clé API-Football (ex: xxxxx...)") },
                        modifier = Modifier.fillMaxWidth().testTag("api_key_input_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Commentateur IA Gemini : Les clés Gemini API sont gérées automatiquement via le panneau Secrets d'AI Studio.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveApiKey(apiKeyInput)
                    onDismiss()
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )

    if (showVisualGuide) {
        InstallationGuideDialog(onDismiss = { showVisualGuide = false })
    }
}
