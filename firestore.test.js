const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_mayeetree";
const OWNER_EMAIL = "mayeetreemilans@gmail.com";
const COEDITOR_UID = "coeditor_alice";
const COEDITOR_EMAIL = "alice@journal.org";
const MEMBER_UID = "member_bob";
const OUTSIDER_UID = "member_carol";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
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

async function bootstrapOwnerAndMember() {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  const now = pastTimestamp();
  const batch = ownerDb.batch();
  batch.set(ownerDb.collection("admin_emails").doc(OWNER_EMAIL), {
    email: OWNER_EMAIL,
    addedBy: OWNER_UID,
    isOwner: true,
    createdAt: now,
  });
  batch.set(ownerDb.collection("users").doc(OWNER_UID), {
    userId: OWNER_UID,
    displayName: "Mayeetree",
    role: "admin",
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  const bobDb = testEnv.authenticatedContext(MEMBER_UID, {
    email: "bob@journal.org",
    email_verified: true,
  }).firestore();
  await assertSucceeds(bobDb.collection("users").doc(MEMBER_UID).set({
    userId: MEMBER_UID,
    displayName: "Dr. Bob",
    role: "member",
    createdAt: now,
    updatedAt: now,
  }));

  return { ownerDb, bobDb };
}

test("Unauthenticated user cannot read or list any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("targets").where("workspaceId", "==", "default").get());
  await assertFails(unauthDb.collection("tasks").where("workspaceId", "==", "default").get());
  await assertFails(unauthDb.collection("users").doc("someone").get());
  await assertFails(unauthDb.collection("admin_emails").get());
});

test("Primary owner email always logs in as Admin (even if pre-existing user doc was member) and is protected", async () => {
  const now = pastTimestamp();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      displayName: "Mayeetree",
      role: "member",
      createdAt: now,
      updatedAt: now,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: "MayeetreeMilans@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(ownerDb.collection("admin_emails").doc(OWNER_EMAIL).set({
    email: OWNER_EMAIL,
    addedBy: OWNER_UID,
    isOwner: true,
    createdAt: now,
  }));
  await assertSucceeds(ownerDb.collection("users").doc(OWNER_UID).update({
    role: "admin",
    updatedAt: now,
  }));
  await assertFails(ownerDb.collection("admin_emails").doc(OWNER_EMAIL).delete());
});

test("Unlisted user joins as Member and cannot self-promote or edit admin_emails", async () => {
  const { bobDb } = await bootstrapOwnerAndMember();
  const now = pastTimestamp();

  const carolDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: "carol@journal.org",
    email_verified: true,
  }).firestore();

  await assertFails(carolDb.collection("users").doc(OUTSIDER_UID).set({
    userId: OUTSIDER_UID,
    displayName: "Carol",
    role: "admin",
    createdAt: now,
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("users").doc(MEMBER_UID).update({
    role: "admin",
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("admin_emails").doc("bob@journal.org").set({
    email: "bob@journal.org",
    addedBy: MEMBER_UID,
    isOwner: false,
    createdAt: now,
  }));

  await assertFails(bobDb.collection("admin_emails").get());
});

test("Allowlisted email added by Admin allows that user to log in as Admin", async () => {
  const { ownerDb } = await bootstrapOwnerAndMember();
  const now = pastTimestamp();

  await assertSucceeds(ownerDb.collection("admin_emails").doc(COEDITOR_EMAIL).set({
    email: COEDITOR_EMAIL,
    addedBy: OWNER_UID,
    isOwner: false,
    createdAt: now,
  }));

  const aliceDb = testEnv.authenticatedContext(COEDITOR_UID, {
    email: COEDITOR_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(aliceDb.collection("admin_emails").doc(COEDITOR_EMAIL).get());
  await assertSucceeds(aliceDb.collection("users").doc(COEDITOR_UID).set({
    userId: COEDITOR_UID,
    displayName: "Dr. Alice",
    role: "admin",
    createdAt: now,
    updatedAt: now,
  }));
  await assertSucceeds(aliceDb.collection("admin_emails").get());
});

test("Email spoofing test: unverified owner email is NOT treated as admin", async () => {
  await bootstrapOwnerAndMember();
  const now = pastTimestamp();
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofDb.collection("users").doc("spoof_uid").set({
    userId: "spoof_uid",
    displayName: "Spoof",
    role: "admin",
    createdAt: now,
    updatedAt: now,
  }));

  await assertFails(spoofDb.collection("targets").doc("t1").set({
    workspaceId: "default",
    name: "Spoof Issue",
    description: "Desc",
    deadline: "2026-12-31",
    stages: [],
    createdBy: "spoof_uid",
    createdAt: now,
    updatedAt: now,
  }));
});

test("PII isolation: non-admin cannot read another user's private info", async () => {
  const { ownerDb, bobDb } = await bootstrapOwnerAndMember();
  const now = pastTimestamp();

  await assertSucceeds(ownerDb.collection("users").doc(OWNER_UID).collection("private").doc("info").set({
    userId: OWNER_UID,
    email: OWNER_EMAIL,
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("users").doc(OWNER_UID).collection("private").doc("info").get());
  await assertSucceeds(ownerDb.collection("users").doc(OWNER_UID).collection("private").doc("info").get());
});

test("Admin can write targets, teams, templates; Members can read with matching query but cannot write", async () => {
  const { ownerDb, bobDb } = await bootstrapOwnerAndMember();
  const now = pastTimestamp();

  await assertSucceeds(ownerDb.collection("targets").doc("target_1").set({
    workspaceId: "default",
    name: "Vol 14 Issue 2",
    description: "Autumn Special",
    deadline: "2026-12-20",
    stages: [{ id: "s1", name: "Publish", offsetDays: 0, dueDate: "2026-12-20" }],
    createdBy: OWNER_UID,
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(bobDb.collection("targets").where("workspaceId", "==", "default").get());
  await assertFails(bobDb.collection("targets").get());

  await assertFails(bobDb.collection("targets").doc("target_2").set({
    workspaceId: "default",
    name: "Unauthorized",
    description: "",
    deadline: "2026-12-20",
    stages: [],
    createdBy: MEMBER_UID,
    createdAt: now,
    updatedAt: now,
  }));
});

test("Member can update ONLY status on direct or team-assigned tasks, and comment on assigned tasks", async () => {
  const { ownerDb, bobDb } = await bootstrapOwnerAndMember();
  const now = pastTimestamp();
  const carolDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: "carol@journal.org",
    email_verified: true,
  }).firestore();

  await assertSucceeds(ownerDb.collection("targets").doc("target_1").set({
    workspaceId: "default",
    name: "Vol 14",
    description: "Issue",
    deadline: "2026-12-20",
    stages: [],
    createdBy: OWNER_UID,
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(ownerDb.collection("teams").doc("team_1").set({
    workspaceId: "default",
    name: "Copyediting",
    description: "Copyeditors",
    leadId: OWNER_UID,
    memberIds: [MEMBER_UID],
    createdBy: OWNER_UID,
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(ownerDb.collection("tasks").doc("task_team").set({
    workspaceId: "default",
    title: "Copyedit Paper 42",
    description: "Check citations",
    targetId: "target_1",
    stageName: "Copyediting",
    dueDate: "2026-11-25",
    priority: "high",
    status: "todo",
    assigneeType: "team",
    assigneeId: "team_1",
    createdBy: OWNER_UID,
    createdAt: now,
    updatedAt: now,
  }));

  await assertSucceeds(bobDb.collection("tasks").doc("task_team").update({
    status: "in_progress",
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("tasks").doc("task_team").update({
    title: "Hijacked Title",
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("tasks").doc("task_team").update({
    dueDate: "2026-12-30",
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("tasks").doc("task_team").update({
    priority: "low",
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("tasks").doc("task_team").update({
    status: "done",
    ghostField: true,
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("tasks").doc("task_member_new").set({
    workspaceId: "default",
    title: "Unauthorized Member Task",
    description: "",
    targetId: "target_1",
    stageName: "Copyediting",
    dueDate: "2026-11-28",
    priority: "medium",
    status: "todo",
    assigneeType: "user",
    assigneeId: MEMBER_UID,
    createdBy: MEMBER_UID,
    createdAt: now,
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("tasks").doc("task_team").delete());

  await assertFails(bobDb.collection("targets").doc("target_1").update({
    name: "Hijacked Target",
    updatedAt: now,
  }));

  await assertFails(bobDb.collection("targets").doc("target_1").delete());

  await assertFails(carolDb.collection("tasks").doc("task_team").update({
    status: "done",
    updatedAt: now,
  }));
  await assertFails(carolDb.collection("tasks").doc("task_team").collection("comments").doc("c1").set({
    taskId: "task_team",
    authorId: OUTSIDER_UID,
    authorName: "Carol",
    body: "Unassigned comment",
    createdAt: now,
  }));

  await assertSucceeds(bobDb.collection("tasks").doc("task_team").collection("comments").doc("c2").set({
    taskId: "task_team",
    authorId: MEMBER_UID,
    authorName: "Dr. Bob",
    body: "Started reviewing bibliography.",
    createdAt: now,
  }));
});
