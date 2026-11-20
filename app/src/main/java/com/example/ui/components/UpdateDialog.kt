package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.repository.VersionCheckStatus
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CoralRed
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyPrimary

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasDirectApkUrl = updateInfo.downloadUrl.isNotBlank() &&
        !updateInfo.downloadUrl.contains("ais-pre-") &&
        !updateInfo.downloadUrl.contains("ais-dev-")

    AlertDialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate) {
                onDismiss()
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = EmeraldDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Nova Atualização do App!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Versão v${updateInfo.latestVersionName} • ${updateInfo.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldDark
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Versão no aparelho",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Nova versão",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDark
                            )
                            Text(
                                text = "v${updateInfo.latestVersionName} (Build ${updateInfo.latestVersionCode})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldDark
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "Novidades desta atualização:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes.ifBlank { "Melhorias e novas funcionalidades." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 20.sp
                        )
                    }
                }

                if (hasDirectApkUrl) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Ao tocar em Baixar Atualização, o arquivo app-debug.apk será baixado direto do seu GitHub. Depois é só tocar no arquivo baixado para atualizar mantendo seus dados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AmberGold.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Conecte seu repositório do GitHub em Versão do Aplicativo para baixar o APK automaticamente com 1 toque.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasDirectApkUrl) {
                Button(
                    onClick = {
                        openDownloadUrl(context, updateInfo.downloadUrl)
                        if (!updateInfo.forceUpdate) {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                    modifier = Modifier.testTag("update_now_button")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Baixar Atualização", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                    modifier = Modifier.testTag("update_now_button")
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!updateInfo.forceUpdate && hasDirectApkUrl) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_update_button")
                ) {
                    Text("Lembrar Depois")
                }
            }
        }
    )
}

@Composable
fun VersionStatusDialog(
    status: VersionCheckStatus,
    onSaveGithubConfig: (repo: String, branch: String) -> Unit,
    onRecheckNow: () -> Unit,
    onPublishBuild: (directApkUrl: String, releaseNotes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var githubRepoInput by remember(status.githubRepo) { mutableStateOf(status.githubRepo) }
    var githubBranchInput by remember(status.githubBranch) { mutableStateOf(status.githubBranch.ifBlank { "main" }) }
    var showAdvancedPublish by remember { mutableStateOf(false) }
    var directUrlInput by remember(status.downloadUrl) { mutableStateOf(status.downloadUrl) }
    var notesInput by remember(status.releaseNotes) { mutableStateOf(status.releaseNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldDark,
                    modifier = Modifier.size(26.dp)
                )
                Text("Versão do Aplicativo", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Neste aparelho:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "v${status.installedVersionName} (Build ${status.installedVersionCode})",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = BrandBlue
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Última (${status.checkSource}):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val remoteLabel = if (status.remoteVersionCode != null) {
                                "v${status.remoteVersionName ?: "?"} (Build ${status.remoteVersionCode})"
                            } else {
                                "Não encontrado"
                            }
                            Text(
                                remoteLabel,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldDark
                            )
                        }
                        if (status.githubRepo.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("No Firestore:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val fsLabel = if (status.firestoreVersionCode != null) {
                                    "v${status.firestoreVersionName ?: "?"} (Build ${status.firestoreVersionCode})" +
                                        if (status.firestoreSynced) " ✓" else ""
                                } else {
                                    "Aguardando sincronização"
                                }
                                Text(
                                    fsLabel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (status.firestoreSynced) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Repositório:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${status.githubRepo} (${status.githubBranch})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrandBlue
                                )
                            }
                        }
                    }
                }

                if (status.errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = CoralRed.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = status.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = CoralRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // GitHub Auto-Update Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldGreen.copy(alpha = 0.08f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Atualização Automática via GitHub",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldDark
                            )
                        }
                        Text(
                            text = "1. No Google AI Studio, clique em Push to GitHub (repositório público).\n" +
                                   "2. Informe abaixo seu usuario/repositorio uma única vez.\n" +
                                   "3. A cada novo Push no AI Studio, o app detecta sozinho e libera o botão de baixar o APK!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = githubRepoInput,
                            onValueChange = { githubRepoInput = it },
                            label = { Text("GitHub (usuario/repositorio)") },
                            placeholder = { Text("ex: guilherme/financas-notas") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_repo_input")
                        )

                        OutlinedTextField(
                            value = githubBranchInput,
                            onValueChange = { githubBranchInput = it },
                            label = { Text("Branch (padrão: main)") },
                            placeholder = { Text("main") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_branch_input")
                        )

                        Button(
                            onClick = { onSaveGithubConfig(githubRepoInput, githubBranchInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_github_repo_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar e Verificar Atualização", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (status.isSignedIn) {
                    OutlinedButton(
                        onClick = { showAdvancedPublish = !showAdvancedPublish },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (showAdvancedPublish) "Ocultar Publicação Manual" else "Publicação Manual (Firestore)")
                    }

                    if (showAdvancedPublish) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = BrandBlue.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Opcional: publique manualmente o número deste Build e um link de APK no Firestore.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        OutlinedTextField(
                            value = directUrlInput,
                            onValueChange = { directUrlInput = it },
                            label = { Text("Link direto do APK (opcional)") },
                            placeholder = { Text("https://github.com/.../app-debug.apk") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text("Notas desta versão") },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onPublishBuild(directUrlInput, notesInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Publicar Build ${status.installedVersionCode} no Firestore", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRecheckNow,
                modifier = Modifier.testTag("recheck_version_button")
            ) {
                Text("Verificar Agora", fontWeight = FontWeight.Bold, color = BrandBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        }
    )
}

fun openDownloadUrl(context: Context, url: String) {
    try {
        val cleanUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Não foi possível abrir o link: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
