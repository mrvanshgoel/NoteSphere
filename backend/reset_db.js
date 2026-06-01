const admin = require('firebase-admin');
const dotenv = require('dotenv');
dotenv.config();

// Initialize Firebase Admin
let credential;
if (process.env.FIREBASE_PRIVATE_KEY) {
  credential = admin.credential.cert({
    projectId: process.env.FIREBASE_PROJECT_ID,
    clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
    privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n')
  });
} else {
  credential = admin.credential.applicationDefault();
}

admin.initializeApp({
  credential,
  storageBucket: process.env.FIREBASE_STORAGE_BUCKET || 'vansh-notesphere.firebasestorage.app'
});

const db = admin.firestore();

const collections = [
  'profiles',
  'folders',
  'materials',
  'subjects',
  'chats',
  'syllabi',
  'quiz_results',
  'flashcards',
  'notes',
  'shares'
];

async function deleteCollection(db, collectionPath, batchSize) {
  const collectionRef = db.collection(collectionPath);
  const query = collectionRef.orderBy('__name__').limit(batchSize);

  return new Promise((resolve, reject) => {
    deleteQueryBatch(db, query, resolve).catch(reject);
  });
}

async function deleteQueryBatch(db, query, resolve) {
  const snapshot = await query.get();

  const batchSize = snapshot.size;
  if (batchSize === 0) {
    resolve();
    return;
  }

  const batch = db.batch();
  snapshot.docs.forEach((doc) => {
    batch.delete(doc.ref);
  });
  await batch.commit();

  process.nextTick(() => {
    deleteQueryBatch(db, query, resolve);
  });
}

async function resetDb() {
  console.log('Starting Firestore DB reset...');
  for (const coll of collections) {
    console.log(`Deleting collection: ${coll}...`);
    await deleteCollection(db, coll, 500);
    console.log(`Deleted collection: ${coll}`);
  }
  console.log('Database reset complete.');
  process.exit(0);
}

resetDb().catch(console.error);
