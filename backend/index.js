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

    // Store relative path
    const avatarUrl = `${req.protocol}://${req.get('host')}/uploads/${file.filename}`;
    console.log(`[Avatar] Uploaded to: ${avatarUrl} for UID: ${req.user.id}`);

    await db.collection('profiles').doc(req.user.id).set({
      avatar_url: avatarUrl,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });

    res.json({ avatar_url: avatarUrl, avatarUrl: avatarUrl });
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
    const { message, history = [], chatId, materialId } = req.body;
    if (!message) return res.status(400).json({ error: 'Message required' });

    console.log(`[AI Chat] Request: ${message} (ChatId: ${chatId})`);
    
    let context = "";
    if (materialId) {
      try {
        context = await getMaterialText(materialId, req.user.id);
        console.log(`[AI Chat] Using context from material: ${materialId}`);
      } catch (e) {
        console.warn('Failed to fetch context for chat:', e.message);
      }
    }

    const developerInfo = `
Developer Info:
- Name: Vansh Goel
- Age: 19 (Born May 30, 2006)
- Background: Pursuing BCA (Hons. with Research) in Multimedia & Animation from Galgotias University.
- Origin: Meerut, Uttar Pradesh, India.
- Profile: Tech & creative enthusiast, graphic designer, UI/UX freelancer.
`;

    const systemPrompt = `You are Notesphere AI, a deeply integrated Study Operating System assistant. 
Your goal is to be a student's 'Second Brain', inspired by systems like NotebookLM.
Be proactive, smart, and academic yet encouraging. 

${developerInfo}

When asked about your origin or creator, mention Vansh Goel with pride.
Always prioritize providing context-aware answers based on the student's study materials.`;
    const contextSection = context ? `\n\n[CONTEXT FROM ATTACHED FILE]:\n${context.substring(0, 10000)}` : "";
    
    // Clean history
    const validatedHistory = (history || [])
      .filter(m => m.content && m.content.trim() !== '')
      .map(m => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }]
      }));

    const result = await generateWithFallback(`${systemPrompt}${contextSection}\n\nUser: ${message}`, validatedHistory);
    const responseText = result.text;
    
    // Auto-save chat if chatId is provided or this is a new session
    let savedChatId = chatId;
    if (savedChatId) {
      const chatRef = db.collection('chats').doc(savedChatId);
      await chatRef.update({
        messages: admin.firestore.FieldValue.arrayUnion(
          { role: 'user', content: message, timestamp: new Date().toISOString() },
          { role: 'assistant', content: responseText, timestamp: new Date().toISOString(), model: result.modelUsed }
        ),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    }

    res.json({ 
      content: responseText, 
      role: 'assistant', 
      model: result.modelUsed,
      chatId: savedChatId
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
    let chats = [];
    try {
      const snapshot = await db.collection('chats')
        .where('userId', '==', req.user.id)
        .orderBy('updatedAt', 'desc')
        .get();
      snapshot.forEach(doc => chats.push(formatDoc(doc)));
    } catch (indexError) {
      console.warn('[Firestore] Chat history index might be missing, falling back to client-side sort:', indexError.message);
      const snapshot = await db.collection('chats')
        .where('userId', '==', req.user.id)
        .get();
      snapshot.forEach(doc => chats.push(formatDoc(doc)));
      // Manual sort fallback
      chats.sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0));
    }
    res.json(chats);
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
    const { materialId, text: providedText, count = 10 } = req.body;
    
    let text = providedText;
    if (!text && materialId) {
      text = await getMaterialText(materialId, req.user.id);
    }
    if (!text) return res.status(400).json({ error: 'No text provided' });

    const prompt = `Generate exactly ${count} educational flashcards from the following material.
Each flashcard must have a "question" (or term) and an "answer" (or definition).
Format as JSON:
{
  "flashcards": [
    {"question": "...", "answer": "..."}
  ]
}

Material:
${text}`;

    const result = await generateWithFallback(prompt);
    const rawText = result.text.trim();
    const jsonMatch = rawText.match(/\{[\s\S]*\}/);
    if (!jsonMatch) throw new Error('Invalid AI response format');

    const parsed = JSON.parse(jsonMatch[0]);
    res.json(parsed);
  } catch (err) {
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
