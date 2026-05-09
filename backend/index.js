const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const dotenv = require('dotenv');
const { extractTextFromBuffer } = require('./ai_utils');
const { discoverModels, generateWithFallback, getActiveModelInfo } = require('./ai_model_manager');

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
    admin.initializeApp({ credential });
    console.log('Firebase initialized successfully');
  }
} catch (error) {
  console.error('Firebase initialization failed:', error);
}

const db = admin.apps.length ? admin.firestore() : null;
if (db) {
  console.log('Firestore connected');
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
const verifyToken = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'No token provided' });
    }
    const token = authHeader.split(' ')[1];
    console.log(`[Auth] Verifying token (prefix): ${token.substring(0, 20)}...`);
    
    if (!admin.apps.length) {
      return res.status(500).json({ error: 'Firebase Auth is not initialized on the server.' });
    }

    const decodedToken = await admin.auth().verifyIdToken(token);
    console.log(`[Auth] Success. UID: ${decodedToken.uid}`);
    req.user = { id: decodedToken.uid, email: decodedToken.email };
    next();
  } catch (err) {
    console.error('VerifyToken exception:', err.message);
    res.status(401).json({ error: 'Authentication failed: ' + err.message });
  }
};

// ═══════════════════════════════════════════════════════════
// HEALTH CHECK
// ═══════════════════════════════════════════════════════════
app.get('/', (req, res) => res.json({
  status: 'ok',
  message: 'Notesphere API is running (Firebase Native)',
  version: '2.5.0',
  aiModel: getActiveModelInfo().activeModel
}));
app.get('/health', (req, res) => res.json({ status: 'ok', timestamp: new Date().toISOString() }));
app.get('/api/debug/models', (req, res) => res.json(getActiveModelInfo()));

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
    const { name, avatar_url } = req.body;
    await db.collection('profiles').doc(req.user.id).set({
      name, 
      avatar_url, 
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
    
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

    // Store relative path
    const avatarUrl = `${req.protocol}://${req.get('host')}/uploads/${file.filename}`;

    await db.collection('profiles').doc(req.user.id).set({
      avatar_url: avatarUrl,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    res.json({ avatar_url: avatarUrl });
  } catch (err) {
    console.error('Avatar upload error:', err);
    res.status(500).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// SUBJECTS
// ═══════════════════════════════════════════════════════════

app.get('/api/subjects', verifyToken, async (req, res) => {
  try {
    const snapshot = await db.collection('subjects').where('userId', '==', req.user.id).get();
    const subjects = [];
    snapshot.forEach(doc => {
      subjects.push(formatDoc(doc));
    });
    res.json(subjects);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/subjects', verifyToken, async (req, res) => {
  try {
    const { name, color, icon } = req.body;
    if (!name) return res.status(400).json({ error: 'Name is required' });
    
    const docRef = await db.collection('subjects').add({
      name,
      color: color || '#6C63FF',
      icon: icon || '📚',
      userId: req.user.id,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    const newDoc = await docRef.get();
    res.json(formatDoc(newDoc));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.delete('/api/subjects/:id', verifyToken, async (req, res) => {
  try {
    // Verify ownership
    const doc = await db.collection('subjects').doc(req.params.id).get();
    if (doc.exists && doc.data().userId === req.user.id) {
      await db.collection('subjects').doc(req.params.id).delete();
      res.json({ success: true });
    } else {
      res.status(403).json({ error: 'Not authorized or does not exist' });
    }
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// MATERIALS
// ═══════════════════════════════════════════════════════════

app.get('/api/materials/:subjectId', verifyToken, async (req, res) => {
  try {
    const snapshot = await db.collection('materials')
      .where('userId', '==', req.user.id)
      .where('subjectId', '==', req.params.subjectId)
      .get();
      
    const materials = [];
    snapshot.forEach(doc => {
      materials.push(formatDoc(doc));
    });
    res.json(materials);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/materials/upload', verifyToken, upload.single('file'), async (req, res) => {
  try {
    const { subjectId, name } = req.body;
    const file = req.file;
    if (!file) return res.status(400).json({ error: 'No file uploaded' });

    // Store relative path so AI pipeline can read it later
    const localFilePath = path.join('uploads', file.filename);

    const docRef = await db.collection('materials').add({
      subjectId,
      filePath: localFilePath, // Path to temp storage
      fileType: file.mimetype,
      title: name || file.originalname,
      userId: req.user.id,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    const newDoc = await docRef.get();
    res.status(201).json(formatDoc(newDoc));
  } catch (err) {
    console.error('Upload error:', err);
    res.status(500).json({ error: err.message });
  }
});

app.delete('/api/materials/:id', verifyToken, async (req, res) => {
  try {
    const docRef = db.collection('materials').doc(req.params.id);
    const doc = await docRef.get();
    
    if (doc.exists && doc.data().userId === req.user.id) {
      // Clean up local temp file if it exists
      if (doc.data().filePath) {
        const fullPath = path.join(__dirname, doc.data().filePath);
        if (fs.existsSync(fullPath)) {
          fs.unlinkSync(fullPath);
        }
      }
      await docRef.delete();
      res.json({ success: true });
    } else {
      res.status(403).json({ error: 'Not authorized or does not exist' });
    }
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// HELPER: FETCH MATERIAL TEXT FOR AI
// ═══════════════════════════════════════════════════════════
async function getMaterialText(materialId, userId) {
  if (!materialId) throw new Error('materialId is required');
  
  const doc = await db.collection('materials').doc(materialId).get();
  if (!doc.exists) throw new Error('Material not found');
  
  const data = doc.data();
  if (data.userId !== userId) throw new Error('Unauthorized access to material');
  if (!data.filePath) throw new Error('Material has no associated file');

  const fullPath = path.join(__dirname, data.filePath);
  if (!fs.existsSync(fullPath)) {
    throw new Error('File not found in temp storage. It may have been cleared.');
  }

  const buffer = fs.readFileSync(fullPath);
  // Extractor logic from ai_utils (PDF/TXT supported)
  const text = await extractTextFromBuffer(buffer, data.fileType);
  if (!text || text.trim() === '') {
    throw new Error('Could not extract text from this file');
  }
  return text;
}

// ═══════════════════════════════════════════════════════════
// AI PIPELINES
// ═══════════════════════════════════════════════════════════

app.post('/api/ai/chat', verifyToken, async (req, res) => {
  try {
    const { message, history = [] } = req.body;
    if (!message) return res.status(400).json({ error: 'Message required' });

    console.log(`[AI Chat] Request: ${message}`);
    const systemPrompt = "You are Notesphere AI, an advanced, concise, and helpful study assistant.";
    
    // Clean history to ensure it's valid for Gemini (alternating user/model)
    const cleanedHistory = (history || [])
      .filter(m => m.content && m.content.trim() !== '')
      .map(m => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }]
      }));
    
    // Ensure history doesn't start with model and doesn't have consecutive same roles
    const validatedHistory = [];
    cleanedHistory.forEach(msg => {
      if (validatedHistory.length === 0) {
        if (msg.role === 'user') validatedHistory.push(msg);
      } else {
        if (validatedHistory[validatedHistory.length - 1].role !== msg.role) {
          validatedHistory.push(msg);
        }
      }
    });

    const result = await generateWithFallback(`${systemPrompt}\n\nUser: ${message}`, validatedHistory);
    const responseText = result.text;
    console.log(`[AI Chat] Response (Model: ${result.modelUsed}): ${responseText.substring(0, 100)}...`);
    res.json({ content: responseText, role: 'assistant', model: result.modelUsed });
  } catch (err) {
    console.error('[AI Chat Error]', err.message);
    res.status(500).json({ error: 'AI chat failed', detail: err.message });
  }
});

app.post('/api/ai/summarize', verifyToken, async (req, res) => {
  try {
    const { materialId, text: providedText, mode = 'summary' } = req.body;
    
    // Auto-extract from backend temp storage if text is missing
    let text = providedText;
    if (!text && materialId) {
      text = await getMaterialText(materialId, req.user.id);
    }
    
    if (!text) return res.status(400).json({ error: 'No text or materialId provided' });

    const prompts = {
      summary: `You are an expert academic summarizer. Create a comprehensive, structured summary of the following material. Include: key concepts, main ideas, important definitions, and a brief overview. Use markdown formatting.\n\nMaterial:\n${text}`,
      notes: `You are an expert academic note-taker. Create detailed, exam-ready study notes from the following material. Use ## headings, **bold** for key terms, bullet points for facts, and examples. Aim for completeness.\n\nMaterial:\n${text}`,
      concepts: `Extract and explain the KEY CONCEPTS from the following academic material. For each concept: name, definition, importance, and example. Format as a structured list.\n\nMaterial:\n${text}`,
      viva: `Generate 15 important viva/interview questions with comprehensive model answers based on this material. Format: Q: [question]\nA: [answer]\n\nMaterial:\n${text}`
    };

    const prompt = prompts[mode] || prompts.summary;
    const result = await generateWithFallback(prompt);
    const responseText = result.text;
    console.log(`[AI Summarize] Success (Model: ${result.modelUsed})`);
    res.json({ content: responseText, mode, model: result.modelUsed });
  } catch (err) {
    console.error('[AI Summarize Error]', err.message);
    res.status(500).json({ error: 'Summarization failed', detail: err.message });
  }
});

app.post('/api/ai/quiz', verifyToken, async (req, res) => {
  try {
    const { materialId, text: providedText, count = 10, difficulty = 'medium' } = req.body;
    
    let text = providedText;
    if (!text && materialId) {
      text = await getMaterialText(materialId, req.user.id);
    }
    if (!text) return res.status(400).json({ error: 'No text or materialId provided' });

    const prompt = `You are an expert exam question generator. Generate exactly ${count} multiple-choice questions from the following material.

Difficulty: ${difficulty}
Rules:
- Each question must have exactly 4 options (A, B, C, D)
- Only one correct answer per question
- Questions should test understanding, not just memorization

IMPORTANT: Respond with ONLY valid JSON in this exact format:
{
  "questions": [
    {
      "question": "Question text here?",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "correctIndex": 0,
      "explanation": "Brief explanation of why this is correct"
    }
  ]
}

Material:
${text}`;

    console.log(`[AI Quiz] Generating with fallback...`);
    const result = await generateWithFallback(prompt);
    const rawText = result.text.trim();

    const jsonMatch = rawText.match(/\{[\s\S]*\}/);
    if (!jsonMatch) throw new Error('Invalid AI response format');

    const parsed = JSON.parse(jsonMatch[0]);
    res.json({ ...parsed, model: result.modelUsed });
  } catch (err) {
    console.error('[AI Quiz Error]', err.message);
    res.status(500).json({ error: 'Quiz generation failed', detail: err.message });
  }
});

app.post('/api/ai/doubt', verifyToken, async (req, res) => {
  try {
    const { materialId, question, context: providedContext, history = [] } = req.body;
    if (!question) return res.status(400).json({ error: 'No question provided' });

    let context = providedContext;
    if (!context && materialId) {
      try {
        context = await getMaterialText(materialId, req.user.id);
      } catch (e) {
        console.warn('Failed to fetch context for doubt:', e.message);
        // Continue without context if fetching fails, though it might be less accurate
      }
    }

    console.log(`[AI Doubt] Question: ${question}`);
    const contextSection = context ? `\n\nReference Material:\n${context.substring(0, 8000)}` : '';

    const prompt = `You are a knowledgeable study assistant helping a student understand their learning material. 
Answer the following question clearly and helpfully. If the answer is in the provided material, cite it. Use markdown formatting for clarity.${contextSection}

Student Question: ${question}`;

    const validatedHistory = history.map(m => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }]
    }));

    const result = await generateWithFallback(prompt, validatedHistory);
    res.json({ content: result.text, role: 'assistant', model: result.modelUsed });
  } catch (err) {
    console.error('[AI Doubt Error]', err.message);
    res.status(500).json({ error: 'Could not process question', detail: err.message });
  }
});

// ═══════════════════════════════════════════════════════════
// SYLLABUS
// ═══════════════════════════════════════════════════════════

app.get('/api/syllabus/:userId', verifyToken, async (req, res) => {
  try {
    const snapshot = await db.collection('syllabi').where('userId', '==', req.params.userId).get();
    const syllabi = [];
    snapshot.forEach(doc => syllabi.push(formatDoc(doc)));
    res.json(syllabi);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/syllabus', verifyToken, async (req, res) => {
  try {
    const { subjectId, contentTree, progress, totalTopics, completedTopics } = req.body;
    
    const docRef = await db.collection('syllabi').add({
      subjectId,
      contentTree,
      progress,
      totalTopics,
      completedTopics,
      userId: req.user.id,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });
    
    const newDoc = await docRef.get();
    res.json(formatDoc(newDoc));
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.put('/api/syllabus/:id', verifyToken, async (req, res) => {
  try {
    const { contentTree, progress, totalTopics, completedTopics } = req.body;
    await db.collection('syllabi').doc(req.params.id).set({
      contentTree,
      progress,
      totalTopics,
      completedTopics,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
    
    const updated = await db.collection('syllabi').doc(req.params.id).get();
    res.json(formatDoc(updated));
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/share/generate', verifyToken, async (req, res) => {
  res.status(501).json({ error: 'Not Implemented without Cloud Storage' });
});

// ═══════════════════════════════════════════════════════════
// GLOBAL ERROR HANDLER
// ═══════════════════════════════════════════════════════════
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
