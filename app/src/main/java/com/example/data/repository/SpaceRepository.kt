package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SpaceItem
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
import java.security.SecureRandom
import java.util.Locale

class SpaceRepository(
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

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val sb = StringBuilder("FIN")
        repeat(3) {
            sb.append(chars[random.nextInt(chars.length)])
        }
        return sb.toString()
    }

    suspend fun getOrCreateUserDefaultSpace(): SpaceItem {
        val uid = requireUserId()
        val userDocRef = db.collection("users").document(uid)

        try {
            val userSnap = userDocRef.get().await()
            val activeSpaceId = userSnap.getString("activeSpaceId")
            if (!activeSpaceId.isNullOrBlank()) {
                val spaceSnap = db.collection("spaces").document(activeSpaceId).get().await()
                if (spaceSnap.exists()) {
                    val space = spaceSnap.toObject(SpaceItem::class.java)
                    if (space != null) return space.copy(id = spaceSnap.id)
                }
            }
        } catch (e: Exception) {
            // Proceed to create default space if not found
        }

        // Create initial personal/shared space
        val newSpaceRef = db.collection("spaces").document()
        val userDisplayName = UserNameFormatter.fromFirebaseUser(auth.currentUser, "Usuário")
        val code = generateInviteCode()
        val spacePayload = mapOf(
            "name" to "Finanças Compartilhadas",
            "inviteCode" to code,
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "memberNames" to listOf(userDisplayName),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        newSpaceRef.set(spacePayload).await()

        val userPayload = mapOf(
            "userId" to uid,
            "activeSpaceId" to newSpaceRef.id,
            "email" to (auth.currentUser?.email ?: ""),
            "displayName" to userDisplayName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        userDocRef.set(userPayload).await()

        return SpaceItem(
            id = newSpaceRef.id,
            name = "Finanças Compartilhadas",
            inviteCode = code,
            ownerId = uid,
            memberUids = listOf(uid),
            memberNames = listOf(userDisplayName)
        )
    }

    suspend fun joinSpaceByCode(code: String): Result<SpaceItem> {
        val uid = requireUserId()
        val cleanCode = code.trim().uppercase(Locale.ROOT)
        return try {
            val querySnapshot = db.collection("spaces")
                .whereEqualTo("inviteCode", cleanCode)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(IllegalArgumentException("Código de espaço inválido ou não encontrado."))
            }

            val doc = querySnapshot.documents.first()
            val space = doc.toObject(SpaceItem::class.java)
                ?: return Result.failure(IllegalStateException("Formato de espaço inválido."))

            val userName = UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")

            if (!space.memberUids.contains(uid)) {
                val updatedMembers = space.memberUids + uid
                val updatedNames = space.memberNames + userName
                doc.reference.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "memberNames" to updatedNames,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Set as active space for current user
            db.collection("users").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "activeSpaceId" to doc.id,
                    "email" to (auth.currentUser?.email ?: ""),
                    "displayName" to userName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(space.copy(id = doc.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "spaces/join")
            Result.failure(e)
        }
    }

    fun observeSpace(spaceId: String): Flow<SpaceItem?> = flow {
        requireUserId()
        emitAll(
            db.collection("spaces").document(spaceId)
                .snapshots()
                .map { snap -> snap.toObject(SpaceItem::class.java)?.copy(id = snap.id) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    throw error
                }
        )
    }
}
