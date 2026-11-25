const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_atelier_01";
const BOB_UID = "bob_atelier_02";

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

test("Unauthenticated user cannot read or write any user profile or subcollection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    gender: "Women",
    createdAt: now,
    updatedAt: now,
  }));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("outfits").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("plannedEvents").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("discountAlerts").get());
});

test("User profile: owner create, partial update, cross-user denial, and shadow field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create mirroring Kotlin repository payload
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Ananya",
      gender: "Women",
      stylePreferences: ["Minimalist", "Ethnic / Festive"],
      budgetTier: "Contemporary",
      bodyType: "Hourglass",
      heightCm: 165,
      chestCm: 88,
      waistCm: 68,
      hipCm: 94,
      shoulderCm: 40,
      skinTone: "Medium",
      skinUndertone: "Warm",
      bestColors: ["Terracotta", "Ivory", "Emerald"],
      avoidColors: ["Neon Yellow"],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner get succeeds; listing /users is strictly denied
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());

  // Cross-user read/update denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({
    gender: "Men",
    updatedAt: new Date(Date.now() - 1000),
  }));

  // Valid partial body scan update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fitSummary: "Structured waists and draped silks flatter balanced shoulders and hips.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow field injection fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp tampering fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      gender: "Non-Binary",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Wardrobe items: owner CRUD, list query, category enum check, and @DocumentId id key rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Empty subcollection list succeeds for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").get());

  // Valid create with materialType, Shoes category, and condition assessment
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_01").set({
      userId: ALICE_UID,
      name: "Handcrafted Leather Loafers",
      category: "Shoes",
      colorName: "Espresso Brown",
      colorHex: "#4E342E",
      materialType: "Leather",
      fabric: "Full-Grain Leather",
      season: "All Season",
      formality: "Office / Corporate",
      conditionStatus: "Good",
      keepDecision: "Keep",
      conditionNotes: "Soles intact, polish recommended before winter.",
      ecoScore: 86,
      wearCount: 4,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial condition assessment update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_01").update({
      conditionStatus: "Fair / Needs Care",
      keepDecision: "Repair / Dry Clean",
      conditionNotes: "Heel tap needs quick cobbler replacement.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid materialType rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_bad_mat").set({
      userId: ALICE_UID,
      name: "Unknown Fabric Shirt",
      category: "Top",
      colorName: "White",
      materialType: "Unobtanium",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject unexpected @DocumentId `id` field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_02").set({
      id: "item_02",
      userId: ALICE_UID,
      name: "Tailored Linen Blazer",
      category: "Outerwear",
      colorName: "Sandstone",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid category enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_03").set({
      userId: ALICE_UID,
      name: "Mystery Item",
      category: "InvalidCategory",
      colorName: "Black",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial wearCount update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_01").update({
      wearCount: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user list & delete fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_01").delete());

  // Owner delete succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("wardrobeItems").doc("item_01").delete());
});

test("Outfits, Planned Events & Discount Alerts: full owner lifecycle & cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Outfit create & try-on image attach update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("outfits").doc("outfit_01").set({
      userId: ALICE_UID,
      title: "Sangeet Ivory & Gold Edit",
      occasion: "Wedding / Marriage",
      itemIds: ["item_01"],
      itemSummary: "Ivory Raw Silk Kurta + Gold Mojris",
      colorHarmonyScore: 94,
      fitScore: 92,
      stylingNotes: "Warm ivory elevates golden undertones.",
      sourceType: "MIX_MATCH",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("outfits").doc("outfit_01").update({
      tryOnImagePath: "/data/user/0/app/files/tryon/outfit_01.jpg",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Planned Event create & reminder toggle
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("plannedEvents").doc("evt_01").set({
      userId: ALICE_UID,
      eventTitle: "Rohan's Reception",
      occasion: "Wedding / Marriage",
      dateIso: "2026-12-12",
      timeLabel: "19:30",
      outfitId: "outfit_01",
      outfitTitle: "Sangeet Ivory & Gold Edit",
      reminderLeadMinutes: 120,
      reminderEnabled: true,
      reminderEpochMillis: 1797100000000,
      notes: "Steam silk kurta an hour prior",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("plannedEvents").doc("evt_01").update({
      reminderEnabled: false,
      reminderEpochMillis: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Discount Alert create & toggle
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("discountAlerts").doc("alert_01").set({
      userId: ALICE_UID,
      itemName: "Unbleached Belgian Linen Blazer",
      brandName: "Nicobar Sustainable Edit",
      category: "Outerwear",
      targetDiscountPercent: 25,
      currentDiscountPercent: 20,
      ecoCertified: true,
      activeAlert: true,
      seasonalTag: "Winter Archive Sale",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("discountAlerts").doc("alert_01").update({
      activeAlert: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user isolation checks
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("outfits").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("plannedEvents").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("discountAlerts").get());
});
