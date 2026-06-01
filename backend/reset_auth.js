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
});

async function resetAuth() {
  console.log('Fetching Firebase Auth users...');
  
  let nextPageToken;
  let deletedCount = 0;

  try {
    do {
      // Fetch up to 1000 users at a time
      const listUsersResult = await admin.auth().listUsers(1000, nextPageToken);
      
      const uids = listUsersResult.users.map(user => user.uid);
      
      if (uids.length > 0) {
        console.log(`Deleting ${uids.length} users in this batch...`);
        const deleteResult = await admin.auth().deleteUsers(uids);
        deletedCount += deleteResult.successCount;
        
        if (deleteResult.failureCount > 0) {
          console.error(`Failed to delete ${deleteResult.failureCount} users.`);
          deleteResult.errors.forEach(err => console.error(err.error.message));
        }
      }

      nextPageToken = listUsersResult.pageToken;
    } while (nextPageToken);

    console.log(`Successfully deleted ${deletedCount} users from Firebase Auth.`);
  } catch (error) {
    console.error('Error wiping Firebase Auth:', error);
  }
  
  process.exit(0);
}

resetAuth();
