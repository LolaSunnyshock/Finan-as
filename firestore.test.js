const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const EVE_UID = "eve_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read spaces or transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("spaces").get());
  await assertFails(unauthDb.collection("spaces").doc("space_1").collection("transactions").get());
});

test("Authenticated user Alice: can create shared space", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("spaces").doc("space_1").set({
      name: "Finanças Casa",
      inviteCode: "CASA26",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: ["Alice"],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Bob can join Alice's space and both can share transactions", async () => {
  // 1. Seed Alice's space
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("spaces").doc("space_1").set({
      name: "Finanças Casa",
      inviteCode: "CASA26",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: ["Alice"],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  // 2. Bob joins the space (adds his UID to memberUids)
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("spaces").doc("space_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: ["Alice", "Bob"],
      updatedAt: new Date(),
    })
  );

  // 3. Bob adds a transaction
  await assertSucceeds(
    bobDb.collection("spaces").doc("space_1").collection("transactions").doc("tx_bob").set({
      spaceId: "space_1",
      userId: BOB_UID,
      userName: "Bob",
      description: "Supermercado",
      amount: 180.50,
      type: "EXPENSE",
      category: "Mercado",
      date: "2026-11-10",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // 4. Alice can read and update Bob's transaction with fixed bill fields
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("spaces").doc("space_1").collection("transactions").doc("tx_bob").get()
  );
  await assertSucceeds(
    aliceDb.collection("spaces").doc("space_1").collection("transactions").get()
  );
  await assertSucceeds(
    aliceDb.collection("spaces").doc("space_1").collection("transactions").doc("tx_bob").update({
      isFixed: true,
      fixedEndMonth: "2027-02",
      updatedAt: new Date(),
    })
  );

  // 5. Non-member Eve CANNOT read or write in Alice and Bob's space
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  await assertFails(
    eveDb.collection("spaces").doc("space_1").collection("transactions").doc("tx_bob").get()
  );
  await assertFails(
    eveDb.collection("spaces").doc("space_1").collection("transactions").get()
  );
});

test("Shared Notes: members can create and read shared notes", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("spaces").doc("space_1").set({
      name: "Finanças Casa",
      inviteCode: "CASA26",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: ["Alice", "Bob"],
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("spaces").doc("space_1").collection("notes").doc("note_1").set({
      spaceId: "space_1",
      userId: ALICE_UID,
      authorName: "Alice",
      title: "Lista de Compras",
      content: "Pão, Café, Frutas",
      color: "#FEF08A",
      isPinned: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("spaces").doc("space_1").collection("notes").doc("note_1").get()
  );
  await assertSucceeds(
    bobDb.collection("spaces").doc("space_1").collection("notes").doc("note_1").update({
      authorName: "Bob",
      title: "Lista Atualizada",
      content: "Pão, Café, Frutas, Leite",
      color: "#BBF7D0",
      isPinned: true,
      updatedAt: new Date(),
    })
  );
});
