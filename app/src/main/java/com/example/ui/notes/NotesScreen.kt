package com.example.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteItem
import com.example.data.util.UserNameFormatter
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralRed
import com.example.ui.theme.NavyPrimary

val NOTE_COLOR_OPTIONS = listOf(
    "#FFFFFF" to Color(0xFFFFFFFF),
    "#FEF08A" to Color(0xFFFEF08A),
    "#BBF7D0" to Color(0xFFBBF7D0),
    "#BAE6FD" to Color(0xFFBAE6FD),
    "#DDD6FE" to Color(0xFFDDD6FE),
    "#FECDD3" to Color(0xFFFECDD3),
    "#FED7AA" to Color(0xFFFED7AA)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()

    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var selectedNoteForEdit by remember { mutableStateOf<NoteItem?>(null) }
    var selectedNoteForView by remember { mutableStateOf<NoteItem?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(operationStatus) {
        operationStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedNoteForEdit = null
                    showAddOrEditDialog = true
                },
                containerColor = NavyPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Nova Anotação")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("notes_search_input"),
                    placeholder = { Text("Pesquisar anotações...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Pesquisar")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
            }

            // Samsung Notes Style: Folders Row with "+ Criar Pasta" button
            item {
                SamsungNotesFoldersRow(
                    folders = uiState.folders,
                    selectedFolder = uiState.selectedFolder,
                    folderCounts = uiState.folderCounts,
                    onSelectFolder = { viewModel.selectFolder(it) },
                    onCreateFolderClick = { showCreateFolderDialog = true }
                )
            }

            // Active Folder & Count Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.selectedFolder == "Todas") Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (uiState.selectedFolder == "Todas") "Todas as anotações" else uiState.selectedFolder,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "${uiState.notes.size} de ${uiState.allNotesCount} notas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Loading or Empty State
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NavyPrimary)
                    }
                }
            } else if (uiState.notes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "Nenhum resultado para \"$searchQuery\""
                                } else if (uiState.selectedFolder != "Todas") {
                                    "Pasta \"${uiState.selectedFolder}\" está vazia"
                                } else {
                                    "Nenhuma anotação criada"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Toque no botão '+' abaixo para criar sua anotação e organizá-la em pastas como no Samsung Notes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.notes,
                    key = { it.id.ifBlank { it.hashCode().toString() } }
                ) { note ->
                    NoteCardItem(
                        note = note,
                        onNoteClick = {
                            selectedNoteForView = note
                        },
                        onEditClick = {
                            selectedNoteForEdit = note
                            showAddOrEditDialog = true
                        }
                    )
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                viewModel.createFolder(folderName) { success ->
                    if (success) showCreateFolderDialog = false
                }
            }
        )
    }

    // Read-Only Viewer Dialog (triggered when tapping anywhere on the note card)
    if (selectedNoteForView != null) {
        val viewingNote = uiState.notes.find { it.id == selectedNoteForView!!.id } ?: selectedNoteForView!!
        NoteViewerDialog(
            note = viewingNote,
            onDismiss = { selectedNoteForView = null },
            onEditClick = {
                selectedNoteForView = null
                selectedNoteForEdit = viewingNote
                showAddOrEditDialog = true
            }
        )
    }

    // Add / Edit Dialog (With Samsung Notes Style Folder Assignment + Delete inside Edit)
    if (showAddOrEditDialog) {
        NoteEditorDialog(
            existingNote = selectedNoteForEdit,
            availableFolders = uiState.folders,
            initialFolder = selectedNoteForEdit?.folder?.ifBlank {
                if (uiState.selectedFolder != "Todas" && uiState.selectedFolder != "Sem pasta") {
                    uiState.selectedFolder
                } else ""
            } ?: "",
            onDismiss = { showAddOrEditDialog = false },
            onCreateNewFolder = { newName -> viewModel.createFolder(newName) },
            onDeleteClick = selectedNoteForEdit?.let { editing ->
                {
                    noteToDelete = editing
                }
            },
            onSave = { title, content, color, isPinned, folder ->
                if (selectedNoteForEdit == null) {
                    viewModel.addNote(title, content, color, isPinned, folder) { success ->
                        if (success) showAddOrEditDialog = false
                    }
                } else {
                    viewModel.updateNote(selectedNoteForEdit!!.id, title, content, color, isPinned, folder) { success ->
                        if (success) showAddOrEditDialog = false
                    }
                }
            }
        )
    }

    // Confirm Delete Dialog
    if (noteToDelete != null) {
        val note = noteToDelete!!
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Excluir Anotação") },
            text = { Text("Deseja realmente excluir a anotação \"${note.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        noteToDelete = null
                        if (selectedNoteForEdit?.id == note.id) {
                            showAddOrEditDialog = false
                            selectedNoteForEdit = null
                        }
                        if (selectedNoteForView?.id == note.id) {
                            selectedNoteForView = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_delete_note_button")
                ) {
                    Text("Excluir", color = CoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SamsungNotesFoldersRow(
    folders: List<String>,
    selectedFolder: String,
    folderCounts: Map<String, Int>,
    onSelectFolder: (String) -> Unit,
    onCreateFolderClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Todas" folder chip
        val isAllSelected = selectedFolder == "Todas"
        val allCount = folderCounts["Todas"] ?: 0
        FilterChip(
            selected = isAllSelected,
            onClick = { onSelectFolder("Todas") },
            label = {
                Text(
                    text = "Todas ($allCount)",
                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isAllSelected) Color.White else NavyPrimary
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NavyPrimary,
                selectedLabelColor = Color.White
            ),
            modifier = Modifier.testTag("folder_chip_all")
        )

        // Custom Folders from Samsung Notes model
        folders.forEach { folderName ->
            val isSelected = selectedFolder.equals(folderName, ignoreCase = true)
            val count = folderCounts[folderName] ?: 0
            FilterChip(
                selected = isSelected,
                onClick = { onSelectFolder(folderName) },
                label = {
                    Text(
                        text = "$folderName ($count)",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NavyPrimary,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("folder_chip_$folderName")
            )
        }

        // Action button to create new folder
        Surface(
            onClick = onCreateFolderClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .height(34.dp)
                .testTag("add_folder_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "Criar pasta",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "+ Pasta",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun NoteCardItem(
    note: NoteItem,
    onNoteClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val cardColor = remember(note.color) {
        try {
            Color(android.graphics.Color.parseColor(note.color))
        } catch (e: Exception) {
            Color(0xFFFFFFFF)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onNoteClick)
            .testTag("note_item_${note.id}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Nota fixada",
                            tint = AmberGold,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar anotação",
                            tint = NavyPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF334155),
                        lineHeight = 20.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Samsung Notes style folder badge + creator first name
            val authorFirstName = UserNameFormatter.sanitizeStoredName(note.authorName, "")
            if (note.folder.isNotBlank() || authorFirstName.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (note.folder.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color(0xFF334155)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = note.folder,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF334155)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (authorFirstName.isNotBlank()) {
                        Text(
                            text = "Por $authorFirstName",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF475569),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (folderName: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = NavyPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Criar Pasta (Samsung Notes)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Dê um nome para a pasta para agrupar e direcionar suas anotações com facilidade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        if (it.isNotBlank()) hasError = false
                    },
                    label = { Text("Nome da Pasta (ex: Trabalho, Pessoal)") },
                    singleLine = true,
                    isError = hasError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (folderName.trim().isNotBlank()) {
                        onConfirm(folderName.trim())
                    } else {
                        hasError = true
                    }
                },
                modifier = Modifier.testTag("confirm_create_folder_button")
            ) {
                Text("Criar Pasta", fontWeight = FontWeight.Bold, color = NavyPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorDialog(
    existingNote: NoteItem?,
    availableFolders: List<String>,
    initialFolder: String,
    onDismiss: () -> Unit,
    onCreateNewFolder: (String) -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onSave: (title: String, content: String, color: String, isPinned: Boolean, folder: String) -> Unit
) {
    var title by remember { mutableStateOf(existingNote?.title ?: "") }
    var content by remember { mutableStateOf(existingNote?.content ?: "") }
    var selectedColor by remember { mutableStateOf(existingNote?.color ?: "#FEF08A") }
    var isPinned by remember { mutableStateOf(existingNote?.isPinned ?: false) }
    var selectedFolder by remember { mutableStateOf(initialFolder) }
    var hasError by remember { mutableStateOf(false) }

    var folderDropdownExpanded by remember { mutableStateOf(false) }
    var showInlineFolderCreate by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingNote == null) "Nova Anotação" else "Editar Anotação",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Folder Selector (Samsung Notes Style)
                Column {
                    Text(
                        text = "Pasta de Destino",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = folderDropdownExpanded,
                        onExpandedChange = { folderDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedFolder.isBlank()) "Sem pasta (Geral)" else selectedFolder,
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (selectedFolder.isBlank()) Icons.Outlined.Folder else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = NavyPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("note_folder_dropdown")
                        )

                        ExposedDropdownMenu(
                            expanded = folderDropdownExpanded,
                            onDismissRequest = { folderDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sem pasta (Geral)") },
                                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                onClick = {
                                    selectedFolder = ""
                                    folderDropdownExpanded = false
                                }
                            )
                            availableFolders.forEach { fName ->
                                DropdownMenuItem(
                                    text = { Text(fName) },
                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = NavyPrimary) },
                                    onClick = {
                                        selectedFolder = fName
                                        folderDropdownExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ Criar nova pasta...", color = NavyPrimary, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = NavyPrimary) },
                                onClick = {
                                    folderDropdownExpanded = false
                                    showInlineFolderCreate = true
                                }
                            )
                        }
                    }
                }

                if (showInlineFolderCreate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            label = { Text("Nova pasta") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val trimmed = newFolderNameInput.trim()
                                if (trimmed.isNotBlank()) {
                                    onCreateNewFolder(trimmed)
                                    selectedFolder = trimmed
                                    newFolderNameInput = ""
                                    showInlineFolderCreate = false
                                }
                            }
                        ) {
                            Text("Salvar", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título da anotação") },
                    singleLine = true,
                    isError = hasError && title.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                // Content
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Conteúdo / Detalhes") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )

                // Color Selection
                Text(
                    text = "Cor da nota",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NOTE_COLOR_OPTIONS.forEach { (hex, composeColor) ->
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(composeColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) NavyPrimary else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                // Pin toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = if (isPinned) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fixar no topo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = AmberGold)
                    )
                }

                // Delete option inside Edit dialog
                if (existingNote != null && onDeleteClick != null) {
                    OutlinedButton(
                        onClick = onDeleteClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Excluir Anotação",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, content, selectedColor, isPinned, selectedFolder)
                    } else {
                        hasError = true
                    }
                },
                modifier = Modifier.testTag("save_note_button")
            ) {
                Text(
                    text = if (existingNote == null) "Criar Nota" else "Salvar Alterações",
                    fontWeight = FontWeight.Bold,
                    color = NavyPrimary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
