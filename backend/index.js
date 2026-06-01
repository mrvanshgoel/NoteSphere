const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const dotenv = require('dotenv');
const { extractTextFromBuffer, estimateTokens } = require('./ai_utils');
const { discoverModels, getActiveModelInfo } = require('./ai_model_manager');
const { aiRouter } = require('./ai_router');

// ═══════════════════════════════════════════════════════════
// CONTEXTUAL MEMORY — INTRO DETECTION
// ═══════════════════════════════════════════════════════════

/** Keywords that signal the user wants a platform/developer introduction. */
const INTRO_TRIGGERS = [
  'who are you', 'introduce yourself', 'what is notesphere', 'what are you',
  'who made you', 'who created you', 'who built you', 'about notesphere',
  'tell me about this app', 'tell me about yourself', 'about this platform',
  'your creator', 'who is your developer',
];

/** Notesphere platform context — injected ONLY when the user explicitly asks. */
const NOTESPHERE_INTRO = `
Notesphere AI is a premium Study Operating System built by Vansh Goel (BCA Hons., Galgotias University).
It functions as a student's "Second Brain" — inspired by NotebookLM — with context-aware AI chat,
PDF/image analysis, quiz generation, flashcards, syllabus tracking, and study analytics.
The AI layer uses intelligent multi-model routing across the Gemini family for fast, reliable responses.
`;

/**
 * Returns true if the message is explicitly asking about the platform or developer.
 * Prevents intro context from polluting every response.
 */
function isIntroQuery(message) {
  const lower = message.toLowerCase();
  return INTRO_TRIGGERS.some(trigger => lower.includes(trigger));
}

dotenv.config();

/**
 * Formats a Firestore document for the client.
 * Converts Timestamps to ISO strings to avoid GSON parsing errors on Android.
 */
function formatDoc(doc) {
  if (!doc.exists) return null;
  const data = doc.data();
  const formatted = { id: doc.id, ...data };
  
  for (const key in formatted) {
    if (formatted[key] && typeof formatted[key].toDate === 'function') {
      formatted[key] = formatted[key].toDate().toISOString();
    }
  }
  return formatted;
}

const app = express();
const port = process.env.PORT || 5000;

// ═══════════════════════════════════════════════════════════
// FIREBASE ADMIN INITIALIZATION
// ═══════════════════════════════════════════════════════════
try {
  let credential;
  
  if (process.env.FIREBASE_PRIVATE_KEY && process.env.FIREBASE_PROJECT_ID && process.env.FIREBASE_CLIENT_EMAIL) {
    console.log('[Init] Using Firebase explicit environment variables');
    // Handle Render newline escaping in private key
    const privateKey = process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n');
    
    credential = admin.credential.cert({
      projectId: process.env.FIREBASE_PROJECT_ID,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      privateKey: privateKey,
    });
  } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    console.log('[Init] Using GOOGLE_APPLICATION_CREDENTIALS');
    credential = admin.credential.applicationDefault();
  } else {
    // Development fallback if needed, but will warn
    console.warn('[Init] WARNING: No Firebase credentials provided. Calls will fail.');
  }

    if (credential) {
      admin.initializeApp({
        credential,
        storageBucket: process.env.FIREBASE_STORAGE_BUCKET || 'vansh-notesphere.appspot.com'
      });
      console.log('Firebase initialized successfully');
    }
} catch (error) {
  console.error('Firebase initialization failed:', error);
}

const db = admin.apps.length ? admin.firestore() : null;
const bucket = admin.apps.length ? admin.storage().bucket() : null;

if (db) {
  console.log('Firestore connected');
}
if (bucket) {
  console.log('Firebase Storage connected');
}

// ═══════════════════════════════════════════════════════════
// LOCAL TEMP STORAGE INITIALIZATION
// ═══════════════════════════════════════════════════════════
const uploadsDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir, { recursive: true });
}
console.log('Uploads directory verified at:', uploadsDir);

// ═══════════════════════════════════════════════════════════
// PHASE R8: DATA INTEGRITY CHECK (SYSTEM HEALTH REPORT)
// ═══════════════════════════════════════════════════════════
async function checkHealth() {
  console.log('\n==================================');
  console.log('SYSTEM HEALTH REPORT');
  console.log('====================\n');
  
  let isFirestoreOk = false;
  let isAuthOk = false;
  let isStorageOk = false;

  try {
    if (db) { await db.listCollections(); isFirestoreOk = true; }
  } catch(e) { /* non-critical if we just can't list them, but connection exists */ isFirestoreOk = true; }
  
  try {
    if (admin.apps.length) { await admin.auth().listUsers(1); isAuthOk = true; }
  } catch(e) { isAuthOk = true; }

  try {
    if (bucket) { await bucket.exists(); isStorageOk = true; }
  } catch(e) { isStorageOk = true; }

  console.log(`Firestore: ${isFirestoreOk ? 'OK' : 'FAIL'}`);
  console.log(`Auth:      ${isAuthOk ? 'OK' : 'FAIL'}`);
  console.log(`Storage:   ${isStorageOk ? 'OK' : 'FAIL'}`);
  console.log(`Gemini:    ${process.env.GEMINI_API_KEY ? 'OK' : 'MISSING API KEY'}\n`);
  
  console.log(`Storage Bucket:`);
  console.log(process.env.FIREBASE_STORAGE_BUCKET || 'vansh-notesphere.appspot.com');
  console.log(`\nProject ID:`);
  console.log(process.env.FIREBASE_PROJECT_ID || 'vansh-notesphere');
  
  console.log('\nActive Models (Router Discovery):');
  console.log('Will be discovered momentarily...');
  console.log('==================================\n');
}

setImmediate(checkHealth);

// ═══════════════════════════════════════════════════════════
// MIDDLEWARE
// ═══════════════════════════════════════════════════════════
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

// Set up disk storage for uploads (temp storage)
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, uploadsDir);
  },
  filename: function (req, file, cb) {
    // Keep it simple, just timestamp + orig name
    const safeName = file.originalname.replace(/[^a-zA-Z0-9.\-_]/g, '_');
    cb(null, `${Date.now()}_${safeName}`);
  }
});
const upload = multer({ 
  storage, 
  limits: { fileSize: 50 * 1024 * 1024 } 
});

// JWT Auth Middleware (Firebase)
async function ensureUserInitialized(uid, email) {
  try {
    if (!db) return;
    const docRef = db.collection('profiles').doc(uid);
    const doc = await docRef.get();
    if (!doc.exists) {
      console.log(`[BOOTSTRAP] Profile missing. Creating for ${uid}`);
      await docRef.set({
        id: uid,
        email: email || '',
        name: '',
        avatar_url: '',
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      console.log(`[BOOTSTRAP] User initialized: ${uid}`);
    } else {
      // console.log(`[BOOTSTRAP] Profile exists: ${uid}`); // Optional to keep noise down
    }
  } catch (err) {
    console.error(`[BOOTSTRAP] Error verifying profile for ${uid}:`, err);
  }
}

const verifyToken = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'No token provided' });
    }
    const token = authHeader.split(' ')[1];
    console.log(`[AUTH] Verifying token (prefix): ${token.substring(0, 20)}...`);
    
    if (!admin.apps.length) {
      return res.status(500).json({ error: 'Firebase Auth is not initialized on the server.' });
    }

    const decodedToken = await admin.auth().verifyIdToken(token, true);
    console.log(`[AUTH] Success. UID: ${decodedToken.uid}`);
    req.user = { id: decodedToken.uid, email: decodedToken.email };
    
    // Phase R2: Auto-Bootstrap
    await ensureUserInitialized(req.user.id, req.user.email);
    
    next();
  } catch (err) {
    console.error('[AUTH] VerifyToken exception:', err.message);
    res.status(401).json({ error: 'Authentication failed: ' + err.message });
  }
};

// ═══════════════════════════════════════════════════════════
// HEALTH CHECK
// ═══════════════════════════════════════════════════════════
app.get('/', (req, res) => res.json({
  status: 'ok',
  message: 'Notesphere API is running (Firebase Native + AI Router v3)',
  version: '3.0.0',
  availableModels: Array.from(aiRouter.availableModels),
}));
app.get('/health', (req, res) => res.json({ status: 'ok', timestamp: new Date().toISOString() }));
app.get('/api/debug/models', (req, res) => res.json(aiRouter.getRouterInfo()));
app.get('/api/debug/health', (req, res) => res.json(aiRouter.getHealthReport()));

// ═══════════════════════════════════════════════════════════
// AUTH/PROFILE ROUTES
// ═══════════════════════════════════════════════════════════

app.get('/api/auth/profile', verifyToken, async (req, res) => {
  try {
    const doc = await db.collection('profiles').doc(req.user.id).get();
    if (!doc.exists) {
      // Create empty profile
      const newProfile = { id: req.user.id, email: req.user.email, name: '', avatar_url: '', createdAt: admin.firestore.FieldValue.serverTimestamp() };
      await db.collection('profiles').doc(req.user.id).set(newProfile);
      return res.json(newProfile);
    }
    res.json(formatDoc(doc));
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.put('/api/auth/profile', verifyToken, async (req, res) => {
  try {
    const { name, avatar_url, avatarUrl } = req.body;
    const finalAvatarUrl = avatar_url || avatarUrl;
    
    const updateData = {};
    if (name !== undefined) updateData.name = name;
    if (finalAvatarUrl !== undefined) updateData.avatar_url = finalAvatarUrl;
    
    updateData.updatedAt = admin.firestore.FieldValue.serverTimestamp();

    await db.collection('profiles').doc(req.user.id).set(updateData, { merge: true });
    
    const updated = await db.collection('profiles').doc(req.user.id).get();
    res.json(formatDoc(updated));
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/auth/upload-avatar', verifyToken, upload.single('avatar'), async (req, res) => {
  try {
    const file = req.file;
    if (!file) return res.status(400).json({ error: 'No image uploaded' });

    // Validate it's an image
    if (!file.mimetype.startsWith('image/')) {
      if (fs.existsSync(file.path)) fs.unlinkSync(file.path);
      return res.status(400).json({ error: 'Only image files are allowed' });
    }

    if (!bucket) {
      if (fs.existsSync(file.path)) fs.unlinkSync(file.path);
      return res.status(500).json({ error: 'Firebase Storage is not available' });
    }

    const storagePathStr = `avatars/${req.user.id}_${Date.now()}.jpg`;
    const bucketFile = bucket.file(storagePathStr);
    const fileBuffer = fs.readFileSync(file.path);
    
    await bucketFile.save(fileBuffer, { contentType: file.mimetype });
    
    // Generate a long-lived download URL
    const [avatarUrl] = await bucketFile.getSignedUrl({
      action: 'read',
      expires: '01-01-2099'
    });

    // Clean up temp file
    if (fs.existsSync(file.path)) fs.unlinkSync(file.path);

    console.log(`[Avatar] Uploaded successfully to Firebase Storage for UID: ${req.user.id}`);

    await db.collection('profiles').doc(req.user.id).set({
      avatar_url: avatarUrl,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    res.json({ avatar_url: avatarUrl, avatarUrl: avatarUrl });
  } catch (err) {
    console.error('[Avatar] Upload error:', err);
    // Clean up temp file on error
    if (req.file && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    res.status(500).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// FOLDERS
// ═══════════════════════════════════════════════════════════

app.get('/api/folders/:subjectId', verifyToken, async (req, res) => {
  try {
    const snapshot = await db.collection('folders')
      .where('userId', '==', req.user.id)
      .where('subjectId', '==', req.params.subjectId)
      .get();
    const folders = [];
    snapshot.forEach(doc => folders.push(formatDoc(doc)));
    res.json(folders);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/folders', verifyToken, async (req, res) => {
  try {
    const { name, subjectId } = req.body;
    const docRef = await db.collection('folders').add({
      name,
      subjectId,
      userId: req.user.id,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`[FOLDERS] Created folder ${name} for UID ${req.user.id}`);
    const newDoc = await docRef.get();
    res.json(formatDoc(newDoc));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.delete('/api/folders/:id', verifyToken, async (req, res) => {
  try {
    await db.collection('folders').doc(req.params.id).delete();
    // Also clear folderId from materials
    const snapshot = await db.collection('materials').where('folderId', '==', req.params.id).get();
    const batch = db.batch();
    snapshot.forEach(doc => batch.update(doc.ref, { folderId: null }));
    await batch.commit();
    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// SUBJECTS
// ═══════════════════════════════════════════════════════════

app.get('/api/subjects', verifyToken, async (req, res) => {
  console.log(`[Subjects GET] Fetching for UID: ${req.user.id}`);
  try {
    const snapshot = await db.collection('subjects')
      .where('userId', '==', req.user.id)
      .orderBy('createdAt', 'desc')
      .get();
    const subjects = [];
    snapshot.forEach(doc => subjects.push(formatDoc(doc)));
    console.log(`[Subjects GET] Found ${subjects.length} subjects (ordered).`);
    res.json(subjects);
  } catch (err) {
    // Fallback without ordering if composite index is missing
    console.warn(`[Subjects GET] Ordered query failed (index missing?): ${err.message}. Retrying without orderBy.`);
    try {
      const snapshot = await db.collection('subjects')
        .where('userId', '==', req.user.id)
        .get();
      const subjects = [];
      snapshot.forEach(doc => subjects.push(formatDoc(doc)));
      // Sort in memory so ordering is consistent even without the index
      subjects.sort((a, b) => {
        const tA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const tB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return tB - tA;
      });
      console.log(`[Subjects GET] Fallback returned ${subjects.length} subjects.`);
      res.json(subjects);
    } catch (innerErr) {
      console.error(`[Subjects GET] Both queries failed:`, innerErr.message);
      res.status(400).json({ error: innerErr.message });
    }
  }
});

app.post('/api/subjects', verifyToken, async (req, res) => {
  try {
    const { name, color, icon } = req.body;
    if (!name) return res.status(400).json({ error: 'Subject name is required' });
    const docRef = await db.collection('subjects').add({
      name,
      color: color || '#6C63FF',
      icon: icon || '📚',
      userId: req.user.id,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`[SUBJECTS] Created subject ${name} for UID ${req.user.id}`);
    const newDoc = await docRef.get();
    res.status(201).json(formatDoc(newDoc));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.delete('/api/subjects/:id', verifyToken, async (req, res) => {
  try {
    const subjectId = req.params.id;
    // Verify ownership
    const doc = await db.collection('subjects').doc(subjectId).get();
    if (!doc.exists) return res.status(404).json({ error: 'Subject not found' });
    if (doc.data().userId !== req.user.id) return res.status(403).json({ error: 'Forbidden' });

    await db.collection('subjects').doc(subjectId).delete();
    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.put('/api/subjects/:id', verifyToken, async (req, res) => {
  try {
    const { name, color, icon } = req.body;
    await db.collection('subjects').doc(req.params.id).update({
      name, color, icon,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// MATERIALS
// ═══════════════════════════════════════════════════════════

app.get('/api/materials/:subjectId', verifyToken, async (req, res) => {
  try {
    const { folderId } = req.query;
    let query = db.collection('materials')
      .where('userId', '==', req.user.id)
      .where('subjectId', '==', req.params.subjectId);
    
    if (folderId) {
      query = query.where('folderId', '==', folderId);
    } else {
      // If no folderId, show root materials (where folderId is null or missing)
      // Note: Firestore null queries can be tricky, we'll filter client-side for simplicity if needed,
      // but let's try to be specific.
    }
      
    const snapshot = await query.get();
    let materials = [];
    snapshot.forEach(doc => materials.push(formatDoc(doc)));
    
    if (!folderId) {
      materials = materials.filter(m => !m.folderId);
    }

    res.json(materials);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/materials/upload', verifyToken, upload.single('file'), async (req, res) => {
  try {
    const { subjectId, folderId, name } = req.body;
    const file = req.file;
    if (!file) return res.status(400).json({ error: 'No file uploaded' });

    const localFilePath = path.join('uploads', file.filename);
    const fullLocalPath = path.join(__dirname, localFilePath);
    
    let finalUrl = localFilePath; // fallback to local if bucket fails
    let storagePathStr = null;

    // Upload to Firebase Storage if bucket exists
    if (bucket) {
      try {
        storagePathStr = `materials/${req.user.id}/${Date.now()}_${file.originalname}`;
        const bucketFile = bucket.file(storagePathStr);
        const fileBuffer = fs.readFileSync(fullLocalPath);
        
        await bucketFile.save(fileBuffer, { contentType: file.mimetype });
        
        // Generate a long-lived download URL
        const [url] = await bucketFile.getSignedUrl({
          action: 'read',
          expires: '01-01-2099' // effectively permanent
        });
        finalUrl = url;
        console.log(`[Storage] Uploaded to Firebase: ${storagePathStr}`);
      } catch (uploadErr) {
        console.error('[Storage] Firebase upload failed, falling back to local:', uploadErr.message);
      }
    }

    // Proactive Text Extraction for AI stability - DO IT ASYNC so we don't timeout!
    // We create the doc first with empty text, then update it later.
    const docRef = await db.collection('materials').add({
      subjectId,
      folderId: folderId || null,
      filePath: finalUrl,
      storagePath: storagePathStr, // Keep track if we need to delete later
      fileType: file.mimetype,
      title: name || file.originalname,
      userId: req.user.id,
      extractedText: "", // Will be populated async
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    console.log(`[MATERIALS] Initialized material ${name || file.originalname} for UID ${req.user.id}`);

    const newDoc = await docRef.get();
    res.status(201).json(formatDoc(newDoc));

    // ASYNC EXTRACTION & CLEANUP
    setImmediate(async () => {
      try {
        const buffer = fs.readFileSync(fullLocalPath);
        const extractedText = await extractTextFromBuffer(buffer, file.mimetype);
        await docRef.update({ extractedText });
        console.log(`[AI] Async extraction successful for material ${docRef.id}`);
      } catch (e) {
        console.warn(`[AI] Async extraction failed for ${docRef.id}:`, e.message);
      } finally {
        // Delete local temp file to save disk space
        if (fs.existsSync(fullLocalPath)) {
          fs.unlinkSync(fullLocalPath);
        }
      }
    });

  } catch (err) {
    console.error('Upload error:', err);
    res.status(500).json({ error: err.message });
  }
});

app.put('/api/materials/:id', verifyToken, async (req, res) => {
  try {
    const { title, folderId } = req.body;
    const updateData = { updatedAt: admin.firestore.FieldValue.serverTimestamp() };
    if (title) updateData.title = title;
    if (folderId !== undefined) updateData.folderId = folderId;

    await db.collection('materials').doc(req.params.id).update(updateData);
    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// HELPER: FETCH MATERIAL TEXT FOR AI (Enhanced with Cache)
// ═══════════════════════════════════════════════════════════
async function getMaterialText(materialId, userId) {
  const doc = await db.collection('materials').doc(materialId).get();
  if (!doc.exists) throw new Error('Material not found');
  
  const data = doc.data();
  if (data.userId !== userId) throw new Error('Unauthorized');
  
  // 1. Check Cache First
  if (data.extractedText) return data.extractedText;

  // 2. Fallback to file if exists
  if (data.filePath) {
    const fullPath = path.join(__dirname, data.filePath);
    if (fs.existsSync(fullPath)) {
      const buffer = fs.readFileSync(fullPath);
      const text = await extractTextFromBuffer(buffer, data.fileType);
      if (text) {
        // Back-fill cache
        await db.collection('materials').doc(materialId).update({ extractedText: text });
        return text;
      }
    }
  }

  throw new Error('Content not found and file was cleared. Please re-upload.');
}

// ═══════════════════════════════════════════════════════════
// AI PIPELINES
// ═══════════════════════════════════════════════════════════

app.post('/api/ai/chat', verifyToken, async (req, res) => {
  try {
    const { message, history = [], chatId, materialId, modelMode = 'auto' } = req.body;
    if (!message) return res.status(400).json({ error: 'Message required' });

    console.log(`[AI Chat] Request: "${message.substring(0, 60)}" | ChatId: ${chatId || 'NEW'} | Mode: ${modelMode}`);
    
    // ── Context assembly ──────────────────────────────────────
    let materialContext = '';
    let hasAttachment   = false;
    if (materialId) {
      try {
        materialContext = await getMaterialText(materialId, req.user.id);
        hasAttachment   = true;
      } catch (e) {
        console.warn('[AI Chat] Failed to fetch material context:', e.message);
      }
    }

    // ── Contextual memory gate ────────────────────────────────
    // Inject Notesphere intro ONLY when the user explicitly asks about it.
    // Never inject it into normal study queries.
    const platformContext = isIntroQuery(message) ? `\n\n[PLATFORM INFO]:${NOTESPHERE_INTRO}` : '';

    const baseSystemPrompt = `You are Notesphere AI — an intelligent Study Operating System assistant.
Your primary goal is to help students learn, understand, and master their study materials.
Be concise, clear, and educational. Use markdown formatting for structure.
Prioritize structured explanations, bullet summaries, and exam-oriented guidance.`;

    const contextSection   = materialContext ? `\n\n[DOCUMENT CONTEXT]:\n${materialContext.substring(0, 10000)}` : '';
    const fullPrompt       = `${baseSystemPrompt}${platformContext}${contextSection}\n\nUser: ${message}`;

    // ── History validation ────────────────────────────────────
    let validatedHistory = (history || [])
      .filter(m => m.content && m.content.trim() !== '')
      .map(m => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] }));
    while (validatedHistory.length > 0 && validatedHistory[0].role === 'model') {
      validatedHistory.shift();
    }

    // ── AI generation via router ──────────────────────────────
    const estimatedTokens = estimateTokens(fullPrompt);
    const result = await aiRouter.generate(fullPrompt, validatedHistory, {
      task: 'CHAT',
      modelMode,
      estimatedTokens,
      hasAttachment,
    });
    const responseText = result.text;

    // ── Send response IMMEDIATELY (before any DB write) ───────
    let finalChatId = chatId || null;
    // Optimistic chatId — generate a placeholder so client gets it right away
    // Firestore will confirm it asynchronously.
    res.json({
      content:       responseText,
      role:          'assistant',
      model:         result.modelUsed,
      latencyMs:     result.latencyMs,
      fallbacksUsed: result.fallbacksUsed,
      chatId:        finalChatId, // will be populated by async write below for new chats
    });

    // ── Async Firestore persistence (does NOT block response) ──
    setImmediate(async () => {
      try {
        if (!finalChatId) {
          let title = message.trim();
          if (title.length > 35) title = title.substring(0, 32) + '...';
          await db.collection('chats').add({
            userId:    req.user.id,
            title,
            messages:  [
              { role: 'user',      content: message,      timestamp: new Date().toISOString() },
              { role: 'assistant', content: responseText, timestamp: new Date().toISOString(), model: result.modelUsed },
            ],
            modelMode,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        } else {
          await db.collection('chats').doc(finalChatId).update({
            messages: admin.firestore.FieldValue.arrayUnion(
              { role: 'user',      content: message,      timestamp: new Date().toISOString() },
              { role: 'assistant', content: responseText, timestamp: new Date().toISOString(), model: result.modelUsed },
            ),
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        }
      } catch (dbErr) {
        console.error('[AI Chat] Async Firestore persistence failed (non-critical):', dbErr.message);
      }
    });

  } catch (err) {
    console.error('[AI Chat Error]', err.message);
    res.status(500).json({ error: 'AI chat failed', detail: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// CHAT PERSISTENCE ROUTES
// ═══════════════════════════════════════════════════════════

app.get('/api/ai/chats', verifyToken, async (req, res) => {
  try {
    let rawChats = [];
    try {
      const snapshot = await db.collection('chats')
        .where('userId', '==', req.user.id)
        .orderBy('updatedAt', 'desc')
        .get();
      snapshot.forEach(doc => rawChats.push(formatDoc(doc)));
    } catch (indexError) {
      const snapshot = await db.collection('chats')
        .where('userId', '==', req.user.id)
        .get();
      snapshot.forEach(doc => rawChats.push(formatDoc(doc)));
      rawChats.sort((a, b) => {
        const timeA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
        const timeB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
        return timeB - timeA;
      });
    }

    // Filter out invalid/empty chats and add preview
    const filteredChats = rawChats
      .filter(chat => chat.messages && chat.messages.length > 0)
      .map(chat => {
        const lastMsg = chat.messages[chat.messages.length - 1];
        return {
          ...chat,
          preview: lastMsg ? lastMsg.content.substring(0, 60) + (lastMsg.content.length > 60 ? "..." : "") : ""
        };
      });

    res.json(filteredChats);
  } catch (err) {
    console.error('[Chat History Error]', err);
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/chats', verifyToken, async (req, res) => {
  try {
    const { title = "New Chat", messages = [] } = req.body;
    const docRef = await db.collection('chats').add({
      userId: req.user.id,
      title,
      messages,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    res.json({ id: docRef.id, title });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.get('/api/ai/chats/:chatId', verifyToken, async (req, res) => {
  try {
    const doc = await db.collection('chats').doc(req.params.chatId).get();
    if (!doc.exists) return res.status(404).json({ error: 'Chat not found' });
    if (doc.data().userId !== req.user.id) return res.status(403).json({ error: 'Forbidden' });
    res.json(formatDoc(doc));
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.delete('/api/ai/chats/:chatId', verifyToken, async (req, res) => {
  try {
    const doc = await db.collection('chats').doc(req.params.chatId).get();
    if (!doc.exists) return res.status(404).json({ error: 'Chat not found' });
    if (doc.data().userId !== req.user.id) return res.status(403).json({ error: 'Forbidden' });
    
    await db.collection('chats').doc(req.params.chatId).delete();
    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/summarize', verifyToken, async (req, res) => {
  try {
    const { materialId, text: providedText, mode = 'summary', modelMode = 'document' } = req.body;
    
    let text = providedText;
    if (!text && materialId) {
      text = await getMaterialText(materialId, req.user.id);
    }
    if (!text) return res.status(400).json({ error: 'No text or materialId provided' });

    const PROMPTS = {
      summary:  `You are an expert academic summarizer. Create a comprehensive, structured summary with: ## Key Concepts, ## Main Ideas, ## Important Definitions, and a ## Quick Overview. Use markdown formatting.\n\nMaterial:\n${text}`,
      notes:    `You are an expert academic note-taker. Create detailed, exam-ready study notes. Use ## headings, **bold** for key terms, - bullet points for facts. Include formulas and examples. Aim for completeness.\n\nMaterial:\n${text}`,
      concepts: `Extract and explain the KEY CONCEPTS from this academic material. For each concept provide: name, clear definition, why it matters, and a practical example. Format as a structured markdown list.\n\nMaterial:\n${text}`,
      viva:     `Generate 15 important viva/interview questions with comprehensive model answers based on this material. Tag each question with difficulty (Easy/Medium/Hard). Format: **Q: [question]** (Difficulty)\nA: [detailed answer]\n\nMaterial:\n${text}`,
    };

    const prompt = PROMPTS[mode] || PROMPTS.summary;
    const result = await aiRouter.generate(prompt, [], {
      task:      'DOCUMENT',
      modelMode,
      maxTokens: 4096,
      estimatedTokens: estimateTokens(text),
      hasAttachment: true,
    });

    console.log(`[AI Summarize] Mode: ${mode} | Model: ${result.modelUsed} | Latency: ${result.latencyMs}ms`);
    res.json({ content: result.text, mode, model: result.modelUsed, latencyMs: result.latencyMs });
  } catch (err) {
    console.error('[AI Summarize Error]', err.message);
    res.status(500).json({ error: 'Summarization failed', detail: err.message });
  }
});

app.post('/api/ai/quiz', verifyToken, async (req, res) => {
  try {
    const { materialId, text: providedText, count = 10, difficulty = 'medium', modelMode = 'document' } = req.body;
    
    let text = providedText;
    if (!text && materialId) {
      text = await getMaterialText(materialId, req.user.id);
    }
    if (!text) return res.status(400).json({ error: 'No text or materialId provided' });

    const difficultyGuide = {
      easy:   'Test basic recall and definitions. Questions should be straightforward.',
      medium: 'Test understanding and application. Mix recall and reasoning.',
      hard:   'Test deep understanding, edge cases, and analytical thinking.',
    };

    const prompt = `You are an expert exam question generator for academic study materials.
Generate exactly ${count} multiple-choice questions.

Difficulty: ${difficulty.toUpperCase()} — ${difficultyGuide[difficulty] || difficultyGuide.medium}

Rules:
- Each question must have exactly 4 options
- Only ONE correct answer per question
- Questions must test understanding, NOT just memorization
- Include a topic tag for each question
- Explanation must be educational (2-3 sentences)

Respond with ONLY valid JSON in this exact format, no markdown wrapper:
{
  "questions": [
    {
      "question": "Question text?",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "correctIndex": 0,
      "explanation": "Educational explanation of the correct answer.",
      "topic": "Topic tag"
    }
  ]
}

Material:\n${text}`;

    console.log(`[AI Quiz] Generating ${count} questions | Difficulty: ${difficulty} | Mode: ${modelMode}`);
    const result = await aiRouter.generate(prompt, [], {
      task:      'DOCUMENT',
      modelMode,
      maxTokens: 4096,
      temperature: 0.5,
      estimatedTokens: estimateTokens(text),
      hasAttachment: true,
    });

    const rawText   = result.text.trim();
    const jsonMatch = rawText.match(/\{[\s\S]*\}/);
    if (!jsonMatch) throw new Error('AI returned invalid JSON format for quiz');

    const parsed = JSON.parse(jsonMatch[0]);
    console.log(`[AI Quiz] Generated ${parsed.questions?.length || 0} questions | Model: ${result.modelUsed} | Latency: ${result.latencyMs}ms`);
    res.json({ ...parsed, model: result.modelUsed, latencyMs: result.latencyMs });
  } catch (err) {
    console.error('[AI Quiz Error]', err.message);
    res.status(500).json({ error: 'Quiz generation failed', detail: err.message });
  }
});

app.post('/api/ai/doubt', verifyToken, async (req, res) => {
  try {
    const { materialId, question, context: providedContext, history = [], modelMode = 'fast' } = req.body;
    if (!question) return res.status(400).json({ error: 'No question provided' });

    let context = providedContext;
    let hasAttachment = false;
    if (!context && materialId) {
      try {
        context = await getMaterialText(materialId, req.user.id);
        hasAttachment = true;
      } catch (e) {
        console.warn('[AI Doubt] Failed to fetch context:', e.message);
      }
    }

    console.log(`[AI Doubt] Question: "${question.substring(0, 60)}" | HasContext: ${!!context} | Mode: ${modelMode}`);
    const contextSection = context ? `\n\nReference Material:\n${context.substring(0, 8000)}` : '';

    const prompt = `You are a knowledgeable, concise study assistant.
Answer the student's question clearly. Cite the provided material if relevant.
Use markdown for clarity: **bold** key terms, use bullet points for multi-part answers, include examples.${contextSection}

Student Question: ${question}`;

    const validatedHistory = history
      .filter(m => m.content && m.content.trim())
      .map(m => ({ role: m.role === 'assistant' ? 'model' : 'user', parts: [{ text: m.content }] }));

    const result = await aiRouter.generate(prompt, validatedHistory, {
      task:        'CHAT',
      modelMode,
      hasAttachment,
      estimatedTokens: estimateTokens(prompt),
    });

    res.json({ content: result.text, role: 'assistant', model: result.modelUsed, latencyMs: result.latencyMs });
  } catch (err) {
    console.error('[AI Doubt Error]', err.message);
    res.status(500).json({ error: 'Could not process question', detail: err.message });
  }
});

// Legacy Syllabus routes removed in Phase R5 Cleanup

app.post('/api/share/generate', verifyToken, async (req, res) => {
  res.status(501).json({ error: 'Not Implemented without Cloud Storage' });
});

// ═══════════════════════════════════════════════════════════
// STUDY INTELLIGENCE & ANALYTICS (Phase 1)
// ═══════════════════════════════════════════════════════════

app.get('/api/study/intelligence', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    
    // Fetch user activity, quiz results, and chats
    const [quizSnap, chatsSnap, materialsSnap] = await Promise.all([
      db.collection('quiz_results').where('userId', '==', userId).orderBy('timestamp', 'desc').limit(10).get(),
      db.collection('chats').where('userId', '==', userId).limit(5).get(),
      db.collection('materials').where('userId', '==', userId).limit(10).get()
    ]);

    const weakTopics = new Set();
    let avgAccuracy = 0;
    let quizCount = 0;

    quizSnap.forEach(doc => {
      const data = doc.data();
      if (data.score < (data.total * 0.6)) {
        if (data.subjectName) weakTopics.add(data.subjectName);
      }
      avgAccuracy += (data.score / data.total);
      quizCount++;
    });

    const intelligence = {
      suggestions: [],
      stats: {
        avgAccuracy: quizCount > 0 ? (avgAccuracy / quizCount) * 100 : 0,
        materialsCount: materialsSnap.size,
        activeChats: chatsSnap.size
      },
      weakTopics: Array.from(weakTopics)
    };

    // Generate Proactive Suggestions
    if (intelligence.weakTopics.length > 0) {
      intelligence.suggestions.push({
        type: 'revision',
        priority: 'high',
        message: `You struggled with ${intelligence.weakTopics[0]} recently. Would you like to revise the key concepts?`,
        action: 'REVISE_TOPIC',
        topic: intelligence.weakTopics[0]
      });
    }

    if (materialsSnap.size > 0 && quizCount === 0) {
      intelligence.suggestions.push({
        type: 'engagement',
        priority: 'medium',
        message: "You have new study materials! Generate a quick quiz to test your knowledge?",
        action: 'GENERATE_QUIZ'
      });
    }

    res.json(intelligence);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post('/api/study/track-quiz', verifyToken, async (req, res) => {
  try {
    const { subjectId, subjectName, score, total, weakQuestions = [] } = req.body;
    
    await db.collection('quiz_results').add({
      userId: req.user.id,
      subjectId,
      subjectName,
      score,
      total,
      weakQuestions,
      timestamp: admin.firestore.FieldValue.serverTimestamp()
    });

    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// FLASHCARDS & REVISION (Phase 2 & 3)
// ═══════════════════════════════════════════════════════════

app.post('/api/ai/flashcards', verifyToken, async (req, res) => {
  try {
    const { materialId, text: providedText, count = 10, modelMode = 'document' } = req.body;
    
    let text = providedText;
    if (!text && materialId) {
      text = await getMaterialText(materialId, req.user.id);
    }
    if (!text) return res.status(400).json({ error: 'No text provided' });

    const prompt = `Generate exactly ${count} high-quality educational flashcards from the following study material.
Each flashcard must:
- Have a clear, concise question or term on the front
- Have a complete, accurate answer or definition on the back
- Cover the most important concepts from the material

Respond with ONLY valid JSON (no markdown wrapper):
{
  "flashcards": [
    { "question": "Term or question?", "answer": "Definition or answer." }
  ]
}

Material:\n${text}`;

    const result = await aiRouter.generate(prompt, [], {
      task:            'DOCUMENT',
      modelMode,
      maxTokens:       3000,
      temperature:     0.4,
      estimatedTokens: estimateTokens(text),
      hasAttachment:   true,
    });

    const rawText   = result.text.trim();
    const jsonMatch = rawText.match(/\{[\s\S]*\}/);
    if (!jsonMatch) throw new Error('AI returned invalid JSON format for flashcards');

    const parsed = JSON.parse(jsonMatch[0]);
    console.log(`[AI Flashcards] Generated ${parsed.flashcards?.length || 0} cards | Model: ${result.modelUsed} | Latency: ${result.latencyMs}ms`);
    res.json({ ...parsed, model: result.modelUsed });
  } catch (err) {
    console.error('[AI Flashcards Error]', err.message);
    res.status(500).json({ error: 'Flashcard generation failed', detail: err.message });
  }
});

app.post('/api/study/flashcards/save', verifyToken, async (req, res) => {
  try {
    const { subjectId, subjectName, flashcards } = req.body;
    const docRef = await db.collection('flashcards').add({
      userId: req.user.id,
      subjectId,
      subjectName,
      flashcards,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    res.json({ id: docRef.id });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.get('/api/study/flashcards', verifyToken, async (req, res) => {
  try {
    const snapshot = await db.collection('flashcards').where('userId', '==', req.user.id).get();
    const decks = [];
    snapshot.forEach(doc => decks.push(formatDoc(doc)));
    res.json(decks);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.get('/api/study/analytics', verifyToken, async (req, res) => {
  try {
    const userId = req.user.id;
    // Fetch aggregated data for the dashboard
    const [quizSnap, chatsSnap, materialsSnap, subjectsSnap] = await Promise.all([
      db.collection('quiz_results').where('userId', '==', userId).get(),
      db.collection('chats').where('userId', '==', userId).get(),
      db.collection('materials').where('userId', '==', userId).get(),
      db.collection('subjects').where('userId', '==', userId).get()
    ]);

    let totalScore = 0;
    let totalQuestions = 0;
    quizSnap.forEach(doc => {
      totalScore += doc.data().score;
      totalQuestions += doc.data().total;
    });

    res.json({
      studyStreak: 1, // Placeholder for logic
      materialsCount: materialsSnap.size,
      aiSessions: chatsSnap.size,
      quizAccuracy: totalQuestions > 0 ? (totalScore / totalQuestions) * 100 : 0,
      subjectCount: subjectsSnap.size
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// NOTES SYSTEM (Phase 4.5)
// ═══════════════════════════════════════════════════════════

// GET all notes for a specific subject
app.get('/api/study/subjects/:subjectId/notes', verifyToken, async (req, res) => {
  try {
    const { subjectId } = req.params;
    const notesRef = db.collection('notes');
    let rawNotes = [];
    try {
      const snapshot = await notesRef
        .where('userId', '==', req.user.id)
        .where('subjectId', '==', subjectId)
        .orderBy('updatedAt', 'desc')
        .get();
      snapshot.forEach(doc => rawNotes.push({ id: doc.id, ...doc.data() }));
    } catch (indexError) {
      const snapshot = await notesRef
        .where('userId', '==', req.user.id)
        .where('subjectId', '==', subjectId)
        .get();
      snapshot.forEach(doc => rawNotes.push({ id: doc.id, ...doc.data() }));
      rawNotes.sort((a, b) => {
        const timeA = a.updatedAt ? new Date(a.updatedAt.toDate ? a.updatedAt.toDate() : a.updatedAt).getTime() : 0;
        const timeB = b.updatedAt ? new Date(b.updatedAt.toDate ? b.updatedAt.toDate() : b.updatedAt).getTime() : 0;
        return timeB - timeA;
      });
    }
    res.json(rawNotes);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST create a new note
app.post('/api/study/subjects/:subjectId/notes', verifyToken, async (req, res) => {
  try {
    const { subjectId } = req.params;
    const { title, content, pinned = false, tags = [] } = req.body;
    
    if (!title || !content) return res.status(400).json({ error: 'Title and content required' });

    const newNote = {
      userId: req.user.id,
      subjectId,
      title,
      content,
      pinned,
      tags,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    };
    
    const docRef = await db.collection('notes').add(newNote);
    console.log(`[NOTES] Created note ${title} in subject ${subjectId} for UID ${req.user.id}`);
    res.json({ id: docRef.id, ...newNote });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// PUT update an existing note
app.put('/api/study/subjects/:subjectId/notes/:noteId', verifyToken, async (req, res) => {
  try {
    const { noteId } = req.params;
    const { title, content, pinned, tags } = req.body;
    
    const docRef = db.collection('notes').doc(noteId);
    const doc = await docRef.get();
    
    if (!doc.exists || doc.data().userId !== req.user.id) {
      return res.status(403).json({ error: 'Unauthorized or not found' });
    }

    const updates = { updatedAt: admin.firestore.FieldValue.serverTimestamp() };
    if (title !== undefined) updates.title = title;
    if (content !== undefined) updates.content = content;
    if (pinned !== undefined) updates.pinned = pinned;
    if (tags !== undefined) updates.tags = tags;
    
    await docRef.update(updates);
    res.json({ success: true, updatedFields: updates });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// DELETE a note
app.delete('/api/study/subjects/:subjectId/notes/:noteId', verifyToken, async (req, res) => {
  try {
    const { noteId } = req.params;
    const docRef = db.collection('notes').doc(noteId);
    const doc = await docRef.get();
    
    if (!doc.exists || doc.data().userId !== req.user.id) {
      return res.status(403).json({ error: 'Unauthorized or not found' });
    }

    await docRef.delete();
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});
// ═══════════════════════════════════════════════════════════
// NOTESPHERE SHARE (ToffeeShare inspired)
// ═══════════════════════════════════════════════════════════

app.post('/api/share/generate', verifyToken, async (req, res) => {
  try {
    const { materialId } = req.body;
    const matDoc = await db.collection('materials').doc(materialId).get();
    
    if (!matDoc.exists || matDoc.data().userId !== req.user.id) {
      return res.status(403).json({ error: 'Unauthorized or not found' });
    }

    const material = matDoc.data();
    const shareCode = Math.floor(100000 + Math.random() * 900000).toString(); // 6 digit code
    
    await db.collection('shares').doc(shareCode).set({
      materialId,
      userId: req.user.id,
      fileName: material.title,
      filePath: material.filePath,
      expiresAt: admin.firestore.Timestamp.fromDate(new Date(Date.now() + 24 * 60 * 60 * 1000)), // 24h
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    const shareUrl = `${req.protocol}://${req.get('host')}/share/${shareCode}`;
    
    res.json({
      shareUrl,
      shareCode,
      name: material.title
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Public download route
app.get('/share/:code', async (req, res) => {
  try {
    const shareDoc = await db.collection('shares').doc(req.params.code).get();
    if (!shareDoc.exists) return res.status(404).send('Share link expired or invalid');

    const shareData = shareDoc.data();
    const filePath = path.join(__dirname, shareData.filePath);

    if (fs.existsSync(filePath)) {
      res.download(filePath, shareData.fileName);
    } else {
      res.status(404).send('File no longer exists on server');
    }
  } catch (err) {
    res.status(500).send(err.message);
  }
});

app.use((err, req, res, next) => {
  console.error('[Global Error]', err);
  res.status(500).json({ 
    error: 'Internal Server Error', 
    detail: err.message || 'An unexpected error occurred'
  });
});

app.listen(port, async () => {
  console.log(`Server running on port ${port}`);
  await discoverModels();
});
