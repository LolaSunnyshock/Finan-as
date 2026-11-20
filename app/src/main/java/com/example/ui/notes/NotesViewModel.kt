package com.example.ui.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NoteItem
import com.example.data.model.SpaceItem
import com.example.data.repository.NotesRepository
import com.example.data.repository.SpaceRepository
import com.example.data.util.UserNameFormatter
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val currentSpace: SpaceItem? = null,
    val notes: List<NoteItem> = emptyList(),
    val allNotesCount: Int = 0,
    val folders: List<String> = emptyList(),
    val selectedFolder: String = "Todas",
    val folderCounts: Map<String, Int> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel @JvmOverloads constructor(
    application: Application,
    private val notesRepository: NotesRepository = NotesRepository(application.applicationContext),
    private val spaceRepository: SpaceRepository = SpaceRepository(application.applicationContext)
) : AndroidViewModel(application) {

    private val isGuest: Boolean
        get() = Firebase.auth.currentUser == null

    private val _currentSpace = MutableStateFlow<SpaceItem?>(null)
    val currentSpace: StateFlow<SpaceItem?> = _currentSpace.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolder = MutableStateFlow("Todas")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _customFolders = MutableStateFlow<List<String>>(
        listOf("Pessoal", "Trabalho", "Ideias", "Lembretes")
    )
    val customFolders: StateFlow<List<String>> = _customFolders.asStateFlow()

    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus.asStateFlow()

    // In-memory guest notes for rapid interactive testing without Google Sign-In
    private val _guestNotes = MutableStateFlow<List<NoteItem>>(
        listOf(
            NoteItem(
                id = "guest_note_1",
                spaceId = "guest_space",
                userId = "guest_user",
                authorName = "Visitante",
                title = "Lista de Mercado",
                content = "- Café e Leite\n- Frutas e Verduras\n- Pão Integral\n- Queijo e Ovos",
                color = "#FEF08A",
                isPinned = true,
                folder = "Pessoal"
            ),
            NoteItem(
                id = "guest_note_2",
                spaceId = "guest_space",
                userId = "guest_user",
                authorName = "Visitante",
                title = "Metas da Semana",
                content = "1. Organizar planilha de gastos\n2. Guardar reserva de emergência\n3. Revisar assinaturas mensais",
                color = "#BBF7D0",
                isPinned = false,
                folder = "Trabalho"
            ),
            NoteItem(
                id = "guest_note_3",
                spaceId = "guest_space",
                userId = "guest_user",
                authorName = "Visitante",
                title = "Lembrete de Pagamento",
                content = "Pagar conta de energia até o dia 15 para evitar multas.",
                color = "#BAE6FD",
                isPinned = false,
                folder = "Lembretes"
            )
        )
    )

    init {
        loadSpace()
    }

    fun loadSpace() {
        viewModelScope.launch {
            if (isGuest) {
                _currentSpace.value = SpaceItem(
                    id = "guest_space",
                    name = "Espaço Local (Visitante)",
                    inviteCode = "DEMO99",
                    ownerId = "guest_user",
                    memberUids = listOf("guest_user"),
                    memberNames = listOf("Visitante")
                )
            } else {
                try {
                    val space = spaceRepository.getOrCreateUserDefaultSpace()
                    _currentSpace.value = space
                } catch (e: Exception) {
                    _operationStatus.value = "Erro ao carregar espaço: ${e.message}"
                }
            }
        }
    }

    fun joinSpaceByCode(code: String, onComplete: (Boolean) -> Unit) {
        if (isGuest) {
            _operationStatus.value = "Conecte sua conta Google para sincronizar anotações na nuvem."
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val result = spaceRepository.joinSpaceByCode(code)
            if (result.isSuccess) {
                val space = result.getOrNull()
                _currentSpace.value = space
                _operationStatus.value = "Conectado ao espaço compartilhado com sucesso!"
                onComplete(true)
            } else {
                _operationStatus.value = result.exceptionOrNull()?.message ?: "Falha ao conectar ao espaço"
                onComplete(false)
            }
        }
    }

    val uiState: StateFlow<NotesUiState> = _currentSpace
        .filterNotNull()
        .flatMapLatest { space ->
            val notesFlow = if (isGuest) {
                _guestNotes
            } else {
                notesRepository.observeNotes(space.id)
            }
            combine(notesFlow, _searchQuery, _selectedFolder, _customFolders) { rawNotes, query, selFolder, customList ->
                // Derive all folders: predefined/custom + any folders found in notes
                val noteFolders = rawNotes.mapNotNull { it.folder.trim().takeIf { f -> f.isNotBlank() } }
                val mergedFolders = (customList + noteFolders).distinct().sorted()

                // Calculate note count per folder
                val counts = mutableMapOf<String, Int>()
                counts["Todas"] = rawNotes.size
                counts["Sem pasta"] = rawNotes.count { it.folder.isBlank() }
                for (folder in mergedFolders) {
                    counts[folder] = rawNotes.count { it.folder.equals(folder, ignoreCase = true) }
                }

                // Filter by folder
                val folderFiltered = when (selFolder) {
                    "Todas" -> rawNotes
                    "Sem pasta" -> rawNotes.filter { it.folder.isBlank() }
                    else -> rawNotes.filter { it.folder.equals(selFolder, ignoreCase = true) }
                }

                // Filter by search query
                val searchFiltered = if (query.isBlank()) {
                    folderFiltered
                } else {
                    folderFiltered.filter {
                        it.title.contains(query, ignoreCase = true) ||
                        it.content.contains(query, ignoreCase = true) ||
                        it.folder.contains(query, ignoreCase = true)
                    }
                }

                val sorted = searchFiltered.sortedWith(
                    compareByDescending<NoteItem> { it.isPinned }
                )

                NotesUiState(
                    currentSpace = space,
                    notes = sorted,
                    allNotesCount = rawNotes.size,
                    folders = mergedFolders,
                    selectedFolder = selFolder,
                    folderCounts = counts,
                    searchQuery = query,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            emit(
                NotesUiState(
                    currentSpace = _currentSpace.value,
                    isLoading = false,
                    errorMessage = rootError.message ?: "Erro ao carregar anotações"
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = NotesUiState(isLoading = true)
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun createFolder(folderName: String, onComplete: (Boolean) -> Unit = {}) {
        val trimmed = folderName.trim()
        if (trimmed.isBlank()) {
            _operationStatus.value = "O nome da pasta não pode estar vazio"
            onComplete(false)
            return
        }
        if (_customFolders.value.any { it.equals(trimmed, ignoreCase = true) }) {
            _selectedFolder.value = trimmed
            _operationStatus.value = "Pasta \"$trimmed\" já existe e foi selecionada"
            onComplete(true)
            return
        }

        _customFolders.value = _customFolders.value + trimmed
        _selectedFolder.value = trimmed
        _operationStatus.value = "Pasta \"$trimmed\" criada com sucesso!"
        onComplete(true)
    }

    fun deleteFolder(folderName: String) {
        val trimmed = folderName.trim()
        _customFolders.value = _customFolders.value.filterNot { it.equals(trimmed, ignoreCase = true) }
        if (_selectedFolder.value.equals(trimmed, ignoreCase = true)) {
            _selectedFolder.value = "Todas"
        }
        _operationStatus.value = "Pasta \"$trimmed\" removida"
    }

    fun addNote(
        title: String,
        content: String,
        color: String,
        isPinned: Boolean,
        folder: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val space = _currentSpace.value ?: return
        val trimmedFolder = folder.trim()

        if (trimmedFolder.isNotBlank() && !_customFolders.value.any { it.equals(trimmedFolder, ignoreCase = true) }) {
            _customFolders.value = _customFolders.value + trimmedFolder
        }

        viewModelScope.launch {
            val note = NoteItem(
                id = if (isGuest) "guest_note_${System.currentTimeMillis()}" else "",
                spaceId = space.id,
                userId = if (isGuest) "guest_user" else (Firebase.auth.currentUser?.uid ?: ""),
                authorName = if (isGuest) "Visitante" else UserNameFormatter.fromFirebaseUser(Firebase.auth.currentUser, "Usuário"),
                title = title.trim(),
                content = content.trim(),
                color = color,
                isPinned = isPinned,
                folder = trimmedFolder
            )
            if (isGuest) {
                _guestNotes.value = listOf(note) + _guestNotes.value
                _operationStatus.value = "Nota criada com sucesso!"
                onComplete(true)
            } else {
                val result = notesRepository.addNote(space.id, note)
                if (result.isSuccess) {
                    _operationStatus.value = "Nota criada com sucesso!"
                    onComplete(true)
                } else {
                    _operationStatus.value = "Falha ao salvar nota: ${result.exceptionOrNull()?.message}"
                    onComplete(false)
                }
            }
        }
    }

    fun updateNote(
        noteId: String,
        title: String,
        content: String,
        color: String,
        isPinned: Boolean,
        folder: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val trimmedFolder = folder.trim()
        if (trimmedFolder.isNotBlank() && !_customFolders.value.any { it.equals(trimmedFolder, ignoreCase = true) }) {
            _customFolders.value = _customFolders.value + trimmedFolder
        }

        if (isGuest) {
            _guestNotes.value = _guestNotes.value.map { existing ->
                if (existing.id == noteId) {
                    existing.copy(
                        title = title.trim(),
                        content = content.trim(),
                        color = color,
                        isPinned = isPinned,
                        folder = trimmedFolder
                    )
                } else existing
            }
            _operationStatus.value = "Nota atualizada!"
            onComplete(true)
        } else {
            val space = _currentSpace.value ?: return
            viewModelScope.launch {
                val result = notesRepository.updateNote(
                    spaceId = space.id,
                    noteId = noteId,
                    title = title.trim(),
                    content = content.trim(),
                    color = color,
                    isPinned = isPinned,
                    folder = trimmedFolder
                )
                if (result.isSuccess) {
                    _operationStatus.value = "Nota atualizada!"
                    onComplete(true)
                } else {
                    _operationStatus.value = "Falha ao atualizar: ${result.exceptionOrNull()?.message}"
                    onComplete(false)
                }
            }
        }
    }

    fun deleteNote(noteId: String) {
        if (isGuest) {
            _guestNotes.value = _guestNotes.value.filterNot { it.id == noteId }
            _operationStatus.value = "Nota excluída"
        } else {
            val space = _currentSpace.value ?: return
            viewModelScope.launch {
                val result = notesRepository.deleteNote(space.id, noteId)
                if (result.isSuccess) {
                    _operationStatus.value = "Nota excluída"
                } else {
                    _operationStatus.value = "Falha ao excluir nota: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun clearStatusMessage() {
        _operationStatus.value = null
    }
}
