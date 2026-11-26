const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_senior_123";
const BOB_UID = "bob_senior_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

// --- UNAUTHENTICATED ACCESS ---

test("Unauthenticated user: cannot list, get, create, update, or delete memos", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const memosRef = unauthDb.collection("users").doc(ALICE_UID).collection("memos");

  await assertFails(memosRef.orderBy("createdAt", "desc").get());
  await assertFails(memosRef.doc("memo_1").get());
  await assertFails(
    memosRef.doc("memo_1").set({
      userId: ALICE_UID,
      content: "내일 오전 10시 병원 예약",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(memosRef.doc("memo_1").delete());
});

// --- KOTLIN-MIRRORED CREATE, QUERY, UPDATE, DELETE ---

test("Authenticated owner: valid Kotlin create, orderBy query, partial update, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const memosRef = aliceDb.collection("users").doc(ALICE_UID).collection("memos");
  const created = new Date(Date.now() - 5000);

  // Mirrors VoiceMemoRepository.addMemo payload (NO @DocumentId `id` field)
  await assertSucceeds(
    memosRef.doc("memo_1").set({
      userId: ALICE_UID,
      content: "오늘 오후 3시 손자 하원 픽업하기",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Mirrors VoiceMemoRepository.observeMemos query
  await assertSucceeds(memosRef.orderBy("createdAt", "desc").get());
  await assertSucceeds(memosRef.doc("memo_1").get());

  // Mirrors partial update preserving immutable userId & createdAt
  await assertSucceeds(
    memosRef.doc("memo_1").update({
      content: "오늘 오후 3시 30분 손자 하원 픽업하기",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Mirrors VoiceMemoRepository.deleteMemo
  await assertSucceeds(memosRef.doc("memo_1").delete());
});

// --- CROSS-USER ISOLATION ---

test("Authenticated user: cannot read, list, create, update, or delete another user's memos", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("memos")
      .doc("bob_memo_1")
      .set({
        userId: BOB_UID,
        content: "혈압약 아침 식후 복용",
        createdAt: new Date(Date.now() - 5000),
        updatedAt: new Date(Date.now() - 5000),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobMemosRef = aliceDb.collection("users").doc(BOB_UID).collection("memos");

  await assertFails(bobMemosRef.orderBy("createdAt", "desc").get());
  await assertFails(bobMemosRef.doc("bob_memo_1").get());
  await assertFails(
    bobMemosRef.doc("bob_memo_2").set({
      userId: ALICE_UID,
      content: "다른 사람 계정에 쓰기 시도",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobMemosRef.doc("bob_memo_1").update({
      content: "변조 시도",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobMemosRef.doc("bob_memo_1").delete());
});

// --- SCHEMA, IMMUTABILITY, AND RESOURCE POISONING BOUNDS ---

test("Schema validation rejects invalid payloads, ghost fields, oversized text, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const memosRef = aliceDb.collection("users").doc(ALICE_UID).collection("memos");
  const nowPast = new Date(Date.now() - 2000);

  // Reject empty content
  await assertFails(
    memosRef.doc("memo_empty").set({
      userId: ALICE_UID,
      content: "",
      createdAt: nowPast,
      updatedAt: nowPast,
    })
  );

  // Reject oversized content (> 5000 chars)
  await assertFails(
    memosRef.doc("memo_huge").set({
      userId: ALICE_UID,
      content: "가".repeat(5001),
      createdAt: nowPast,
      updatedAt: nowPast,
    })
  );

  // Reject ghost/extra field (including `@DocumentId` 'id')
  await assertFails(
    memosRef.doc("memo_ghost").set({
      id: "memo_ghost",
      userId: ALICE_UID,
      content: "잘못된 id 필드 포함",
      createdAt: nowPast,
      updatedAt: nowPast,
    })
  );

  // Reject spoofed userId in payload
  await assertFails(
    memosRef.doc("memo_spoof").set({
      userId: BOB_UID,
      content: "다른 사용자 ID 위조",
      createdAt: nowPast,
      updatedAt: nowPast,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    memosRef.doc("memo_future").set({
      userId: ALICE_UID,
      content: "미래 시각 위조",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Seed valid memo and verify immutable field protection on update
  await assertSucceeds(
    memosRef.doc("memo_immutable").set({
      userId: ALICE_UID,
      content: "원본 메모",
      createdAt: nowPast,
      updatedAt: nowPast,
    })
  );

  // Reject mutating createdAt
  await assertFails(
    memosRef.doc("memo_immutable").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject shadow field during update
  await assertFails(
    memosRef.doc("memo_immutable").update({
      content: "수정된 메모",
      isVerified: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
