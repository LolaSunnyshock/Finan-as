package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.NoteItem
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.repository.FinanceRepository
import com.example.data.repository.NotesRepository
import com.example.data.repository.ParsedVersionManifest
import com.example.data.repository.SpaceRepository
import com.example.data.repository.UpdateRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FinanceAndNotesRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createSpace_andAddTransaction_succeeds() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val spaceRepo = SpaceRepository(firestore, auth)
    val financeRepo = FinanceRepository(firestore, auth)

    val space = withTimeout(DEFAULT_TIMEOUT_MS) {
      spaceRepo.getOrCreateUserDefaultSpace()
    }
    assertNotNull(space.id)
    assertTrue(space.inviteCode.startsWith("FIN"))

    val tx = TransactionItem(
      spaceId = space.id,
      description = "Mercado Semanal",
      amount = 320.0,
      type = TransactionType.EXPENSE.name,
      category = "Mercado",
      date = "2026-11-10"
    )

    val txResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.addTransaction(space.id, tx)
    }
    assertTrue("Transaction creation in shared space should succeed", txResult.isSuccess)
    val txId = txResult.getOrThrow()
    assertNotNull(txId)

    val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.updateTransaction(
        space.id,
        tx.copy(
          id = txId,
          description = "Mercado Mensal Editado",
          amount = 410.0,
          date = "2026-12-10"
        )
      )
    }
    assertTrue("Updating existing transaction in shared space should succeed", updateResult.isSuccess)

    val batchResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.addTransactionsBatch(
        space.id,
        listOf(
          tx.copy(description = "Notebook (1/2)", amount = 1500.0, date = "2026-11-15"),
          tx.copy(description = "Notebook (2/2)", amount = 1500.0, date = "2026-12-15")
        )
      )
    }
    assertTrue("Batch installment creation should succeed", batchResult.isSuccess)
    assertEquals(2, batchResult.getOrThrow().size)

    val convertResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.updateTransactionWithInstallments(
        spaceId = space.id,
        updatedFirst = tx.copy(
          id = txId,
          description = "Mercado Parcelado (1/2)",
          amount = 205.0,
          date = "2026-12-10",
          installmentGroupId = "grp_mercado"
        ),
        extraInstallments = listOf(
          tx.copy(
            description = "Mercado Parcelado (2/2)",
            amount = 205.0,
            date = "2027-01-10",
            installmentGroupId = "grp_mercado"
          )
        )
      )
    }
    assertTrue("Converting existing transaction into installments should succeed", convertResult.isSuccess)
    val extraIds = convertResult.getOrThrow()
    assertEquals(1, extraIds.size)

    // Test creating a fixed monthly bill (Conta Fixa) and setting an end month cutoff
    val fixedTx = tx.copy(
      description = "Aluguel Apartamento",
      amount = 1800.0,
      category = "Moradia",
      date = "2026-11-05",
      isFixed = true,
      fixedEndMonth = ""
    )
    val fixedId = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.addTransaction(space.id, fixedTx).getOrThrow()
    }
    val fixedEndUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.updateTransaction(
        space.id,
        fixedTx.copy(id = fixedId, isFixed = true, fixedEndMonth = "2027-03")
      )
    }
    assertTrue("Updating fixedEndMonth on Conta Fixa should succeed", fixedEndUpdate.isSuccess)

    // Test deleting an entire installment plan across all months atomically
    val deleteBatchResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.deleteTransactionsBatch(space.id, listOf(txId) + extraIds)
    }
    assertTrue("Deleting full installment plan batch should succeed", deleteBatchResult.isSuccess)

    val remaining = withTimeout(DEFAULT_TIMEOUT_MS) {
      financeRepo.observeTransactions(space.id).first { list -> list.any { it.id == fixedId } }
    }
    assertTrue(remaining.none { it.id == txId || it.id in extraIds })
    assertEquals("2027-03", remaining.first { it.id == fixedId }.fixedEndMonth)
  }

  @Test
  fun sharedSpace_bobJoinsAndAliceObservesTransactions() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceSpaceRepo = SpaceRepository(firestore, auth)
    val aliceSpace = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceSpaceRepo.getOrCreateUserDefaultSpace()
    }

    // Bob joins using Alice's invite code
    val bobUid = signInTestUser(BOB_EMAIL)
    val bobSpaceRepo = SpaceRepository(firestore, auth)
    val bobFinanceRepo = FinanceRepository(firestore, auth)

    val joinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobSpaceRepo.joinSpaceByCode(aliceSpace.inviteCode)
    }
    assertTrue("Bob should successfully join space", joinResult.isSuccess)

    // Bob creates a transaction
    val bobTx = TransactionItem(
      spaceId = aliceSpace.id,
      userName = "Bob",
      description = "Café da Manhã",
      amount = 45.0,
      type = TransactionType.EXPENSE.name,
      category = "Mercado",
      date = "2026-11-10"
    )
    val addTxResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobFinanceRepo.addTransaction(aliceSpace.id, bobTx)
    }
    assertTrue(addTxResult.isSuccess)
    val txId = addTxResult.getOrThrow()

    // Switch back to Alice and observe Bob's transaction
    signInTestUser(ALICE_EMAIL)
    val aliceFinanceRepo = FinanceRepository(firestore, auth)
    val list = withTimeout(FLOW_TIMEOUT_MS) {
      aliceFinanceRepo.observeTransactions(aliceSpace.id).first { items -> items.any { it.id == txId } }
    }
    assertTrue(list.any { it.id == txId })
  }

  @Test
  fun sharedNotes_membersCanCollaborate() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val spaceRepo = SpaceRepository(firestore, auth)
    val notesRepo = NotesRepository(firestore, auth)
    val space = withTimeout(DEFAULT_TIMEOUT_MS) {
      spaceRepo.getOrCreateUserDefaultSpace()
    }

    // Alice adds a note with folder
    val note = NoteItem(
      spaceId = space.id,
      authorName = "Alice",
      title = "Lista de Mercado",
      content = "Leite, Pão, Ovos",
      color = "#FEF08A",
      isPinned = true,
      folder = "Compras"
    )
    val noteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      notesRepo.addNote(space.id, note)
    }
    assertTrue(noteResult.isSuccess)
    val noteId = noteResult.getOrThrow()

    // Bob joins space and updates the note
    val bobUid = signInTestUser(BOB_EMAIL)
    val bobSpaceRepo = SpaceRepository(firestore, auth)
    val bobNotesRepo = NotesRepository(firestore, auth)
    val joinRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobSpaceRepo.joinSpaceByCode(space.inviteCode)
    }
    assertTrue("Bob should be able to join space", joinRes.isSuccess)

    val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobNotesRepo.updateNote(
        spaceId = space.id,
        noteId = noteId,
        title = "Lista de Mercado (Atualizada)",
        content = "Leite, Pão, Ovos, Café",
        color = "#BBF7D0",
        isPinned = true,
        folder = "Mercado"
      )
    }
    assertTrue("Bob should be able to update shared note", updateResult.isSuccess)
  }

  @Test
  fun nonMember_accessFailsWithPermissionDenied() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val spaceRepo = SpaceRepository(firestore, auth)
    val space = withTimeout(DEFAULT_TIMEOUT_MS) {
      spaceRepo.getOrCreateUserDefaultSpace()
    }

    // Non-member Eve tries to access Alice's space transactions directly
    signInTestUser(EVE_EMAIL)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        firestore.collection("spaces").document(space.id).collection("transactions").get().await()
      }
      fail("Non-member Eve must be rejected with PERMISSION_DENIED")
    } catch (e: Exception) {
      val firestoreException = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got: $e", firestoreException)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }
  }

  @Test
  fun updateRepository_syncManifestToCloud_updatesFirestoreVersionDoc() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val updateRepo = UpdateRepository(firestore, auth)

    val manifest = ParsedVersionManifest(
      latestVersionCode = 8L,
      latestVersionName = "1.7",
      releaseNotes = "Sincronização automática GitHub -> Firestore",
      apkPath = "app-debug.apk",
      forceUpdate = false
    )
    val downloadUrl = UpdateRepository.buildApkDownloadUrl("guilherme/financas", "main", manifest.apkPath)

    val syncRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      updateRepo.syncManifestToCloud(
        manifest = manifest,
        repo = "guilherme/financas",
        branch = "main",
        downloadUrl = downloadUrl
      )
    }
    assertTrue("Syncing GitHub manifest to Firestore app_config/version should succeed", syncRes.isSuccess)

    val doc = withTimeout(DEFAULT_TIMEOUT_MS) {
      firestore.collection("app_config").document("version").get().await()
    }
    assertEquals(8L, doc.getLong("latestVersionCode"))
    assertEquals("1.7", doc.getString("latestVersionName"))
    assertEquals("guilherme/financas", doc.getString("githubRepo"))
    assertEquals(downloadUrl, doc.getString("downloadUrl"))
  }

  private companion object {
    const val ALICE_EMAIL = "alice@financas.test"
    const val BOB_EMAIL = "bob@financas.test"
    const val EVE_EMAIL = "eve@financas.test"
    const val DEFAULT_TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 6000L
  }
}
