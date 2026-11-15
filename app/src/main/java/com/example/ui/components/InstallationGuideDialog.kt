package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.ui.theme.LiveGlowGreen
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Dialogue illustré avec guide visuel étape par étape et bouton interactif
 * pour ouvrir et installer directement le fichier APK sur un smartphone Android.
 */
@Composable
fun InstallationGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Installation FootIA Live",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Installer l'application sur votre téléphone",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Tutorial Infographic Banner Image
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_install_guide),
                            contentDescription = "Guide visuel installation APK",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                    )
                                )
                        )
                        Surface(
                            color = LiveGlowGreen,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "APK PRÊT • INSTALLATION DIRECTE",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bouton interactif direct : OUVRIR / INSTALLER L'APK
                // Bouton interactif direct : TÉLÉCHARGER ET INSTALLER SUR LE TÉLÉPHONE
                Card(
                    onClick = { downloadOrInstallApk(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_open_apk_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LiveGlowGreen),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black
                        ) {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = null,
                                tint = LiveGlowGreen,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "INSTALLER SUR MON TÉLÉPHONE",
                                color = Color.Black,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Appuyez ici : télécharge et installe FootIA-Live.apk",
                                color = Color.Black.copy(alpha = 0.85f),
                                fontSize = 10.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { launchApkInstaller(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Installateur", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { shareApkFile(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Partager", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { copyDownloadLink(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copier", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Étapes pas à pas :",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Étape 1 : Téléchargement du fichier
                VisualStepCard(
                    stepNumber = "1",
                    stepColor = Color(0xFF3B82F6),
                    icon = Icons.Default.FileDownload,
                    title = "Télécharger 'FootIA-Live.apk'",
                    description = "Le fichier complet est à la racine. Cliquez sur le bouton vert ci-dessus ou téléchargez 'FootIA-Live.apk' (25 Mo).",
                    badge = "Fichier : FootIA-Live.apk (25 Mo)"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Étape 2 : Ouvrir le fichier
                VisualStepCard(
                    stepNumber = "2",
                    stepColor = Color(0xFFF59E0B),
                    icon = Icons.Default.TouchApp,
                    title = "Ouvrir le fichier téléchargé",
                    description = "Touchez le fichier dans vos notifications ou votre dossier 'Téléchargements' sur votre téléphone.",
                    badge = "Ouverture instantanée"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Étape 3 : Autoriser l'installation
                VisualStepCard(
                    stepNumber = "3",
                    stepColor = Color(0xFF10B981),
                    icon = Icons.Default.Security,
                    title = "Activer 'Autoriser cette source'",
                    description = "Si un message Android s'affiche, appuyez sur 'Paramètres' et activez 'Autoriser cette source'.",
                    badge = "Sécurité Android : Standard"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Étape 4 : Terminer et lancer
                VisualStepCard(
                    stepNumber = "4",
                    stepColor = Color(0xFF8B5CF6),
                    icon = Icons.Default.CheckCircle,
                    title = "Appuyer sur 'Installer'",
                    description = "Validez l'installation. L'application FootIA Live apparaît sur votre écran d'accueil avec toutes les vidéos en direct !",
                    badge = "Prêt à l'emploi 🎉"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { launchApkInstaller(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.InstallMobile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ouvrir l'APK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

/**
 * Déclenche l'installation directe de l'APK via l'installateur natif Android (FileProvider),
 * sans passer par une URL de prévisualisation externe.
 */
private fun downloadOrInstallApk(context: Context) {
    launchApkInstaller(context)
}

/**
 * Partage le fichier APK ou copie le lien de téléchargement fonctionnel
 */
private fun copyDownloadLink(context: Context) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(
            "FootIA-Live.apk",
            "https://gofile.io/d/Wx4DeUBs"
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Lien Gofile copié ! Collez-le dans votre navigateur mobile.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Fichier disponible à la racine : FootIA-Live.apk", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Lance l'installateur d'APK natif Android via FileProvider
 */
private fun launchApkInstaller(context: Context) {
    try {
        val srcApk = File(context.packageCodePath)
        val dstApk = File(context.cacheDir, "FootIA-Live.apk")
        if (srcApk.exists()) {
            if (!dstApk.exists() || dstApk.length() != srcApk.length()) {
                FileInputStream(srcApk).use { input ->
                    FileOutputStream(dstApk).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dstApk
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            Toast.makeText(context, "Lancement de l'installation FootIA Live...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Fichier APK disponible à la racine : FootIA-Live.apk", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        // En cas d'impossibilité de lancer l'installateur directement, proposer le partage
        shareApkFile(context)
    }
}

/**
 * Permet d'envoyer ou sauvegarder le fichier APK via WhatsApp, Drive, Fichiers, Bluetooth, etc.
 */
private fun shareApkFile(context: Context) {
    try {
        val srcApk = File(context.packageCodePath)
        val dstApk = File(context.cacheDir, "FootIA-Live.apk")
        if (srcApk.exists() && (!dstApk.exists() || dstApk.length() != srcApk.length())) {
            FileInputStream(srcApk).use { input ->
                FileOutputStream(dstApk).use { output ->
                    input.copyTo(output)
                }
            }
        }
        val targetFile = if (dstApk.exists()) dstApk else srcApk
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            putExtra(Intent.EXTRA_SUBJECT, "FootIA Live APK")
            putExtra(Intent.EXTRA_TEXT, "Voici le fichier d'installation de l'application FootIA Live.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Enregistrer ou Installer FootIA Live"))
    } catch (e: Exception) {
        Toast.makeText(context, "Fichier 'FootIA-Live.apk' présent à la racine de vos fichiers.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun VisualStepCard(
    stepNumber: String,
    stepColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    badge: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, stepColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(stepColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    color = stepColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        color = stepColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
