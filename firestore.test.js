import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_001";
const USER_A_UID = "user_a_111";
const USER_B_UID = "user_b_222";

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

test("Unauthenticated user: cannot read, list, create, or redeem license keys or user accounts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const keyId = "MTK-0030-A1B2-C3D4-E5F6";
  const accId = "acc_0123456789abcdef0123456789abcdef";
  await assertFails(unauthDb.collection("license_keys").doc(keyId).get());
  await assertFails(unauthDb.collection("license_keys").get());
  await assertFails(unauthDb.collection("user_accounts").doc(accId).get());
  await assertFails(
    unauthDb.collection("license_keys").doc(keyId).set({
      keyCode: keyId,
      durationDays: 30,
      durationLabel: "30 Days",
      status: "UNUSED",
      createdByUid: ADMIN_UID,
      boundHwid: "",
      redeemedByUid: "",
      signatureHex: "0123456789abcdef0123456789abcdef",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    unauthDb.collection("user_accounts").doc(accId).set({
      email: "test@example.com",
      username: "Tester",
      passwordHash: "abcdef0123456789abcdef0123456789",
      passwordSalt: "salt1234",
      hwid: "MTK-HWID-11223344",
      deviceModel: "Redmi Note 12",
      activeKeyCode: keyId,
      durationDays: 30,
      expiryEpochSec: 1800000000,
      offlineCert: "cert0123456789abcdef0123456789ab",
      status: "ACTIVE",
      authUid: USER_A_UID,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Admin creates UNUSED one-time key; User A redeems it once; User B is blocked from reusing it", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const userADb = testEnv.authenticatedContext(USER_A_UID).firestore();
  const userBDb = testEnv.authenticatedContext(USER_B_UID).firestore();

  const keyId = "MTK-0030-A1B2-C3D4-E5F6";
  const createdTime = new Date(Date.now() - 10000);

  // 1. Admin creates UNUSED key
  await assertSucceeds(
    adminDb.collection("license_keys").doc(keyId).set({
      keyCode: keyId,
      durationDays: 30,
      durationLabel: "30 Days",
      status: "UNUSED",
      createdByUid: ADMIN_UID,
      boundHwid: "",
      redeemedByUid: "",
      signatureHex: "0123456789abcdef0123456789abcdef",
      createdAt: createdTime,
      updatedAt: createdTime,
    })
  );

  // 2. User A looks up the key by ID and redeems it
  await assertSucceeds(userADb.collection("license_keys").doc(keyId).get());
  await assertSucceeds(
    userADb.collection("license_keys").doc(keyId).update({
      status: "USED",
      boundHwid: "MTK-HWID-99887766",
      redeemedByUid: USER_A_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 3. User B tries to redeem the already-USED key -> MUST FAIL
  await assertFails(
    userBDb.collection("license_keys").doc(keyId).update({
      status: "USED",
      boundHwid: "MTK-HWID-11223344",
      redeemedByUid: USER_B_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 4. User B tries to reset the key back to UNUSED -> MUST FAIL
  await assertFails(
    userBDb.collection("license_keys").doc(keyId).update({
      status: "UNUSED",
      boundHwid: "",
      redeemedByUid: "",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 5. Admin (creator) can reset or revoke the key
  await assertSucceeds(
    adminDb.collection("license_keys").doc(keyId).update({
      status: "REVOKED",
      boundHwid: "MTK-HWID-99887766",
      redeemedByUid: USER_A_UID,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("User Account: User creates account on key activation, can get and update it", async () => {
  const userADb = testEnv.authenticatedContext(USER_A_UID).firestore();
  const accId = "acc_e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  const nowTime = new Date(Date.now() - 1000);

  // User A creates their account document
  await assertSucceeds(
    userADb.collection("user_accounts").doc(accId).set({
      email: "linpapa789@gmail.com",
      username: "Lin Pa Pa",
      passwordHash: "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8",
      passwordSalt: "salt_12345678",
      hwid: "MTK-HWID-11223344",
      deviceModel: "Xiaomi Redmi Note 12 Pro 5G",
      activeKeyCode: "MTK-0030-A1B2-C3D4-E5F6",
      durationDays: 30,
      expiryEpochSec: 1800000000,
      offlineCert: "0123456789abcdef0123456789abcdef",
      status: "ACTIVE",
      authUid: USER_A_UID,
      createdAt: nowTime,
      updatedAt: nowTime,
    })
  );

  // User A can read their account (e.g. on restore after app clear data)
  const fetched = await assertSucceeds(userADb.collection("user_accounts").doc(accId).get());
  const data = fetched.data();
  if (!data || data.username !== "Lin Pa Pa") {
    throw new Error("Failed to read user account correctly");
  }

  // User A updates their account (e.g. renewing key or re-verifying)
  await assertSucceeds(
    userADb.collection("user_accounts").doc(accId).update({
      deviceModel: "Xiaomi Redmi Note 12 Pro 5G Updated",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
