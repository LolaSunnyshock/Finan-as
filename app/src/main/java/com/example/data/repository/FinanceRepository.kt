package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.TransactionItem
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

class FinanceRepository(
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

    fun observeTransactions(spaceId: String): Flow<List<TransactionItem>> = flow {
        requireUserId()
        val path = "spaces/$spaceId/transactions"
        emitAll(
            db.collection("spaces").document(spaceId).collection("transactions")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(TransactionItem::class.java) }
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

    suspend fun addTransaction(spaceId: String, transaction: TransactionItem): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = db.collection("spaces").document(spaceId).collection("transactions").document()
            val userName = if (transaction.userName.isNotBlank()) {
                UserNameFormatter.sanitizeStoredName(transaction.userName, "Membro")
            } else {
                UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")
            }
            val payload = mapOf(
                "spaceId" to spaceId,
                "userId" to uid,
                "userName" to userName,
                "description" to transaction.description,
                "amount" to transaction.amount,
                "type" to transaction.type,
                "category" to transaction.category,
                "date" to transaction.date,
                "isFixed" to transaction.isFixed,
                "fixedEndMonth" to transaction.fixedEndMonth,
                "installmentGroupId" to transaction.installmentGroupId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "spaces/$spaceId/transactions")
            Result.failure(e)
        }
    }

    suspend fun addTransactionsBatch(spaceId: String, transactions: List<TransactionItem>): Result<List<String>> {
        if (transactions.isEmpty()) return Result.success(emptyList())
        return try {
            val uid = requireUserId()
            val collectionRef = db.collection("spaces").document(spaceId).collection("transactions")
            val createdIds = mutableListOf<String>()
            transactions.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { transaction ->
                    val docRef = collectionRef.document()
                    createdIds.add(docRef.id)
                    val userName = if (transaction.userName.isNotBlank()) {
                        UserNameFormatter.sanitizeStoredName(transaction.userName, "Membro")
                    } else {
                        UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")
                    }
                    val payload = mapOf(
                        "spaceId" to spaceId,
                        "userId" to uid,
                        "userName" to userName,
                        "description" to transaction.description,
                        "amount" to transaction.amount,
                        "type" to transaction.type,
                        "category" to transaction.category,
                        "date" to transaction.date,
                        "isFixed" to transaction.isFixed,
                        "fixedEndMonth" to transaction.fixedEndMonth,
                        "installmentGroupId" to transaction.installmentGroupId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                }
                batch.commit().await()
            }
            Result.success(createdIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "spaces/$spaceId/transactions")
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(spaceId: String, transaction: TransactionItem): Result<Unit> {
        return try {
            requireUserId()
            require(transaction.id.isNotBlank()) { "Transaction ID must not be blank for update" }
            val docRef = db.collection("spaces").document(spaceId).collection("transactions").document(transaction.id)
            val userName = if (transaction.userName.isNotBlank()) {
                UserNameFormatter.sanitizeStoredName(transaction.userName, "Membro")
            } else {
                UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")
            }
            val updates = mapOf(
                "description" to transaction.description,
                "amount" to transaction.amount,
                "type" to transaction.type,
                "category" to transaction.category,
                "date" to transaction.date,
                "userName" to userName,
                "isFixed" to transaction.isFixed,
                "fixedEndMonth" to transaction.fixedEndMonth,
                "installmentGroupId" to transaction.installmentGroupId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "spaces/$spaceId/transactions/${transaction.id}")
            Result.failure(e)
        }
    }

    suspend fun updateTransactionWithInstallments(
        spaceId: String,
        updatedFirst: TransactionItem,
        extraInstallments: List<TransactionItem>
    ): Result<List<String>> {
        return try {
            val uid = requireUserId()
            require(updatedFirst.id.isNotBlank()) { "Transaction ID must not be blank for update" }
            val collectionRef = db.collection("spaces").document(spaceId).collection("transactions")
            val firstRef = collectionRef.document(updatedFirst.id)
            val createdIds = mutableListOf<String>()

            val firstUserName = if (updatedFirst.userName.isNotBlank()) {
                UserNameFormatter.sanitizeStoredName(updatedFirst.userName, "Membro")
            } else {
                UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")
            }
            val updates = mapOf(
                "description" to updatedFirst.description,
                "amount" to updatedFirst.amount,
                "type" to updatedFirst.type,
                "category" to updatedFirst.category,
                "date" to updatedFirst.date,
                "userName" to firstUserName,
                "isFixed" to updatedFirst.isFixed,
                "fixedEndMonth" to updatedFirst.fixedEndMonth,
                "installmentGroupId" to updatedFirst.installmentGroupId,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.update(firstRef, updates)
            extraInstallments.take(440).forEach { extra ->
                val newRef = collectionRef.document()
                createdIds.add(newRef.id)
                val extraUserName = if (extra.userName.isNotBlank()) {
                    UserNameFormatter.sanitizeStoredName(extra.userName, "Membro")
                } else {
                    UserNameFormatter.fromFirebaseUser(auth.currentUser, "Membro")
                }
                val payload = mapOf(
                    "spaceId" to spaceId,
                    "userId" to uid,
                    "userName" to extraUserName,
                    "description" to extra.description,
                    "amount" to extra.amount,
                    "type" to extra.type,
                    "category" to extra.category,
                    "date" to extra.date,
                    "isFixed" to extra.isFixed,
                    "fixedEndMonth" to extra.fixedEndMonth,
                    "installmentGroupId" to extra.installmentGroupId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(newRef, payload)
            }
            batch.commit().await()
            Result.success(createdIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "spaces/$spaceId/transactions/${updatedFirst.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(spaceId: String, transactionId: String): Result<Unit> {
        return try {
            requireUserId()
            val docRef = db.collection("spaces").document(spaceId).collection("transactions").document(transactionId)
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "spaces/$spaceId/transactions/$transactionId")
            Result.failure(e)
        }
    }

    suspend fun deleteTransactionsBatch(spaceId: String, transactionIds: List<String>): Result<Unit> {
        val cleanIds = transactionIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return Result.success(Unit)
        return try {
            requireUserId()
            val collectionRef = db.collection("spaces").document(spaceId).collection("transactions")
            cleanIds.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { txId ->
                    batch.delete(collectionRef.document(txId))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "spaces/$spaceId/transactions/batch")
            Result.failure(e)
        }
    }
}
