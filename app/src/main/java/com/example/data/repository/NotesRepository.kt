package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.NoteItem
import com.example.data.util.OperationType
import com.example.data.util.UserNameFormatter
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NotesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeNotes(spaceId: String): Flow<List<NoteItem>> = flow {
        requireUserId()
        val path = "spaces/$spaceId/notes"
        emitAll(
            db.collection("spaces").document(spaceId).collection("notes")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(NoteItem::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addNote(spaceId: String, note: NoteItem): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("spaces").document(spaceId).collection("notes").document()
            val authorName = if (note.authorName.isNotBlank()) {
                UserNameFormatter.sanitizeStoredName(note.authorName, "Membro")
            } else {
                UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")
            }
            val payload = mapOf(
                "spaceId" to spaceId,
                "userId" to uid,
                "authorName" to authorName,
                "title" to note.title,
                "content" to note.content,
                "color" to note.color,
                "isPinned" to note.isPinned,
                "folder" to note.folder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "spaces/$spaceId/notes")
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        spaceId: String,
        noteId: String,
        title: String,
        content: String,
        color: String,
        isPinned: Boolean,
        folder: String = ""
    ): Result<Unit> {
        return try {
            requireUserId()
            val docRef = db.collection("spaces").document(spaceId).collection("notes").document(noteId)
            val authorName = UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")
            val updates = mapOf(
                "authorName" to authorName,
                "title" to title,
                "content" to content,
                "color" to color,
                "isPinned" to isPinned,
                "folder" to folder,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "spaces/$spaceId/notes/$noteId")
            Result.failure(e)
        }
    }

    suspend fun deleteNote(spaceId: String, noteId: String): Result<Unit> {
        return try {
            requireUserId()
            val docRef = db.collection("spaces").document(spaceId).collection("notes").document(noteId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "spaces/$spaceId/notes/$noteId")
            Result.failure(e)
        }
    }
}
