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

test("Unauthenticated user: cannot read or write gameData, completedLevels, or dailyChallenges", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("gameData").doc("summary").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("gameData").doc("summary").set({
      userId: ALICE_UID,
      highestUnlockedLevel: 1,
      currentLevel: 1,
      hintsRemaining: 3,
      livesRemaining: 3,
      soundEnabled: true,
      musicEnabled: true,
      livesSystemEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("completedLevels").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("dailyChallenges").get());
});

test("Authenticated owner: valid GameSummary create, partial update, and list/get succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const summaryRef = aliceDb.collection("users").doc(ALICE_UID).collection("gameData").doc("summary");
  await assertSucceeds(
    summaryRef.set({
      userId: ALICE_UID,
      highestUnlockedLevel: 1,
      currentLevel: 1,
      hintsRemaining: 3,
      livesRemaining: 3,
      soundEnabled: true,
      musicEnabled: true,
      livesSystemEnabled: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(summaryRef.get());

  await assertSucceeds(
    summaryRef.update({
      highestUnlockedLevel: 4,
      currentLevel: 4,
      hintsRemaining: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field & immutable field changes & future timestamp
  await assertFails(
    summaryRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    summaryRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    summaryRef.update({
      highestUnlockedLevel: 5,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Authenticated owner: completedLevels and dailyChallenges create, update, list succeed; invalid fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const levelRef = aliceDb.collection("users").doc(ALICE_UID).collection("completedLevels").doc("1");
  await assertSucceeds(
    levelRef.set({
      userId: ALICE_UID,
      levelNumber: 1,
      stars: 2,
      bestMoves: 6,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    levelRef.update({
      stars: 3,
      bestMoves: 5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("completedLevels").get());

  // Reject invalid stars (> 3) and @DocumentId `id` injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("completedLevels").doc("2").set({
      id: "2",
      userId: ALICE_UID,
      levelNumber: 2,
      stars: 3,
      bestMoves: 4,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    levelRef.update({
      stars: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Daily challenge create & update
  const dailyRef = aliceDb.collection("users").doc(ALICE_UID).collection("dailyChallenges").doc("2026-11-20");
  await assertSucceeds(
    dailyRef.set({
      userId: ALICE_UID,
      dateKey: "2026-11-20",
      stars: 3,
      moves: 12,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("dailyChallenges").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailyChallenges").doc("bad-date").set({
      userId: ALICE_UID,
      dateKey: "bad-date",
      stars: 3,
      moves: 12,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const now = new Date(Date.now() - 5000);
    await context.firestore().collection("users").doc(ALICE_UID).collection("gameData").doc("summary").set({
      userId: ALICE_UID,
      highestUnlockedLevel: 10,
      currentLevel: 10,
      hintsRemaining: 5,
      livesRemaining: 3,
      soundEnabled: true,
      musicEnabled: true,
      livesSystemEnabled: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("gameData").doc("summary").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("completedLevels").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("gameData").doc("summary").update({
      highestUnlockedLevel: 99,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
