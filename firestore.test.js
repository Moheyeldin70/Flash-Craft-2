const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDeckPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    title: "Biology 101",
    description: "Cellular respiration and mitosis",
    colorHex: "#2D5BFF",
    iconName: "Science",
    cardCount: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validCardPayload(uid, deckId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    deckId,
    front: "What is the powerhouse of the cell?",
    back: "Mitochondria — produces ATP via oxidative phosphorylation.",
    hint: "Starts with M",
    tags: ["biology", "cells"],
    frontMediaUri: "",
    frontMediaType: "NONE",
    frontMediaName: "",
    backMediaUri: "",
    backMediaType: "NONE",
    backMediaName: "",
    repetitions: 0,
    intervalDays: 0.0,
    easeFactor: 2.5,
    stability: 0.0,
    difficulty: 5.0,
    leitnerBox: 1,
    dueAtMillis: 0,
    lastReviewedMillis: 0,
    lapses: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validStudyLogPayload(uid, dateKey = "2026-11-17", overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    dateKey,
    reviewsCount: 1,
    correctCount: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated users cannot read or write decks, cards, or studyLogs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("decks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_1").set(validDeckPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cards").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("studyLogs").get());
});

test("Authenticated owner can create, list, update, and delete decks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const deckRef = aliceDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_1");

  await assertSucceeds(deckRef.set(validDeckPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("decks").get());

  // Action 1: update metadata
  await assertSucceeds(
    deckRef.update({
      title: "Biology 102",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Action 2: update cardCount
  await assertSucceeds(
    deckRef.update({
      cardCount: 5,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject ghost field
  await assertFails(
    deckRef.update({
      title: "Hacked",
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    deckRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject @DocumentId "id" inside body
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_2").set(
      validDeckPayload(ALICE_UID, { id: "deck_2" })
    )
  );

  await assertSucceeds(deckRef.delete());
});

test("Flashcards require existing parent deck (or same batch) and support content + SRS updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const deckRef = aliceDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_bio");
  const cardRef = aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("card_1");

  // Fails if parent deck does not exist
  await assertFails(cardRef.set(validCardPayload(ALICE_UID, "deck_bio")));

  // Succeeds in atomic batch creating deck + card together
  const batch = aliceDb.batch();
  batch.set(deckRef, validDeckPayload(ALICE_UID, { cardCount: 1 }));
  batch.set(cardRef, validCardPayload(ALICE_UID, "deck_bio"));
  await assertSucceeds(batch.commit());

  // Content + media update succeeds
  await assertSucceeds(
    cardRef.update({
      front: "Updated question",
      frontMediaType: "IMAGE",
      frontMediaUri: "file:///data/user/0/app/files/media/img1.jpg",
      frontMediaName: "cell_diagram.jpg",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // SRS study review update succeeds
  await assertSucceeds(
    cardRef.update({
      repetitions: 1,
      intervalDays: 1.5,
      easeFactor: 2.6,
      stability: 2.4,
      difficulty: 4.5,
      leitnerBox: 2,
      dueAtMillis: Date.now() + 86400000,
      lastReviewedMillis: Date.now(),
      lapses: 0,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject invalid media type
  await assertFails(
    cardRef.update({
      frontMediaType: "EXECUTABLE",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject changing deckId
  await assertFails(
    cardRef.update({
      deckId: "other_deck",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user isolation blocks Bob from reading or writing Alice's decks, cards, and studyLogs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_a").set(validDeckPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cards").doc("card_a").set(validCardPayload(ALICE_UID, "deck_a"))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyLogs").doc("2026-11-17").set(validStudyLogPayload(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("decks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("decks").doc("deck_a").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cards").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cards").doc("card_a").delete());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("studyLogs").get());
});

test("StudyLogs allow create and review count updates while rejecting future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("studyLogs").doc("2026-11-17");

  await assertSucceeds(logRef.set(validStudyLogPayload(ALICE_UID)));
  await assertSucceeds(
    logRef.update({
      reviewsCount: 2,
      correctCount: 2,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyLogs").doc("2026-11-18").set(
      validStudyLogPayload(ALICE_UID, "2026-11-18", { createdAt: new Date(Date.now() + 60000) })
    )
  );
});
