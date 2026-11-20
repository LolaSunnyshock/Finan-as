package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.data.model.AppUpdateInfo
import com.example.data.repository.UpdateRepository
import com.example.data.repository.VersionCheckStatus
import com.example.data.util.UserNameFormatter
import com.example.ui.auth.AuthManager
import com.example.ui.components.AppDrawer
import com.example.ui.components.MultiDeviceSyncDialog
import com.example.ui.components.UpdateDialog
import com.example.ui.components.VersionStatusDialog
import com.example.ui.finance.FinanceScreen
import com.example.ui.finance.FinanceViewModel
import com.example.ui.notes.NotesScreen
import com.example.ui.notes.NotesViewModel
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentUserId: String,
    onSignOutComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSyncInfoDialog by remember { mutableStateOf(false) }

    val currentUser = Firebase.auth.currentUser
    val userEmail = currentUser?.email ?: "Usuário Conectado"
    val userName = UserNameFormatter.fromFirebaseUser(currentUser, "Visitante")

    // Automatic in-app update detector (syncs with Cloud Firestore)
    val updateRepository = remember { UpdateRepository(context) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var versionStatusDialogData by remember { mutableStateOf<VersionCheckStatus?>(null) }

    // Automatic check for updates on startup
    LaunchedEffect(Unit) {
        val checkResult = updateRepository.checkForUpdate()
        checkResult.onSuccess { update ->
            if (update != null) {
                availableUpdate = update
            }
        }
    }

    // Scoped ViewModels per user
    val application = context.applicationContext as Application
    val financeViewModel: FinanceViewModel = viewModel(key = "finance_$currentUserId") {
        FinanceViewModel(application)
    }
    val notesViewModel: NotesViewModel = viewModel(key = "notes_$currentUserId") {
        NotesViewModel(application)
    }

    val currentSpace by financeViewModel.currentSpace.collectAsState()

    // Handle back button: close drawer first, or return to first tab
    BackHandler(enabled = drawerState.isOpen || selectedTab != 0) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (selectedTab != 0) {
            selectedTab = 0
        }
    }

    // Facebook-style layout: Lateral Navigation Drawer + Superior Navigation Menu
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentUserEmail = userEmail,
                currentUserName = userName,
                currentSpace = currentSpace,
                onOpenSyncDialog = { showSyncInfoDialog = true },
                onCheckUpdate = {
                    scope.launch {
                        val status = updateRepository.checkVersionStatus()
                        if (status.updateInfo != null) {
                            availableUpdate = status.updateInfo
                        } else {
                            versionStatusDialogData = status
                        }
                    }
                },
                onSignOut = {
                    AuthManager.signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = onSignOutComplete
                    )
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Superior
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Abrir menu lateral da conta",
                                    tint = BrandBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Finanças & Notas",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = BrandBlue
                                    )
                                )
                            }
                        },

                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = BrandBlue
                        )
                    )

                    // Menu Superior estilo Facebook (Abas Finanças | Anotações)
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = BrandBlue,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                height = 3.5.dp,
                                color = BrandBlue
                            )
                        },
                        divider = {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = "Finanças",
                                    tint = if (selectedTab == 0) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = "Finanças",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 0) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("top_tab_finance")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Default.EditNote else Icons.Outlined.EditNote,
                                    contentDescription = "Anotações",
                                    tint = if (selectedTab == 1) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = "Anotações",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 1) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("top_tab_notes")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                // Conteúdo da Aba Selecionada
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> FinanceScreen(viewModel = financeViewModel)
                        1 -> NotesScreen(viewModel = notesViewModel)
                    }
                }
            }
        }
    }

    // Modal de Espaço Compartilhado
    if (showSyncInfoDialog) {
        MultiDeviceSyncDialog(
            currentSpace = currentSpace,
            onJoinSpace = { code, onComplete ->
                financeViewModel.joinSpaceByCode(code) { success ->
                    if (success) {
                        notesViewModel.loadSpace()
                    }
                    onComplete(success)
                }
            },
            onDismiss = { showSyncInfoDialog = false }
        )
    }

    // In-App Update Prompt Dialog
    if (availableUpdate != null) {
        UpdateDialog(
            updateInfo = availableUpdate!!,
            onDismiss = { availableUpdate = null }
        )
    }

    // Detailed Version & Publishing Dialog
    if (versionStatusDialogData != null) {
        VersionStatusDialog(
            status = versionStatusDialogData!!,
            onSaveGithubConfig = { repo, branch ->
                scope.launch {
                    val saveRes = updateRepository.saveGithubConfig(repo, branch)
                    if (saveRes.isFailure) {
                        Toast.makeText(
                            context,
                            saveRes.exceptionOrNull()?.message ?: "Repositório inválido",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val newStatus = updateRepository.checkVersionStatus()
                        if (newStatus.updateInfo != null) {
                            versionStatusDialogData = null
                            availableUpdate = newStatus.updateInfo
                        } else {
                            versionStatusDialogData = newStatus
                            if (newStatus.errorMessage == null) {
                                Toast.makeText(
                                    context,
                                    "Conectado! Seu app já está na versão mais recente (v${newStatus.installedVersionName}).",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            },
            onRecheckNow = {
                scope.launch {
                    val newStatus = updateRepository.checkVersionStatus()
                    if (newStatus.updateInfo != null) {
                        versionStatusDialogData = null
                        availableUpdate = newStatus.updateInfo
                    } else {
                        versionStatusDialogData = newStatus
                        if (newStatus.errorMessage == null) {
                            Toast.makeText(
                                context,
                                "Você já está na última versão (v${newStatus.installedVersionName}).",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            onPublishBuild = { directUrl, notes ->
                scope.launch {
                    val res = updateRepository.syncCurrentBuildToCloud(
                        preserveExistingUrl = directUrl,
                        customReleaseNotes = notes
                    )
                    if (res.isSuccess) {
                        Toast.makeText(context, "Versão v${BuildConfig.VERSION_NAME} publicada na nuvem!", Toast.LENGTH_SHORT).show()
                        versionStatusDialogData = updateRepository.checkVersionStatus()
                    } else {
                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Erro ao publicar", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { versionStatusDialogData = null }
        )
    }
}
