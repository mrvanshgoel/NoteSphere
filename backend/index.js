const express = require('express');
const cors = require('cors');
const multer = require('multer');
const { v4: uuidv4 } = require('uuid');
const crypto = require('crypto');
const fs = require('fs').promises;
const path = require('path');
const dotenv = require('dotenv');
const admin = require('firebase-admin');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const aiUtils = require('./ai_utils');

dotenv.config();

const app = express();
const port = process.env.PORT || 5000;
const currentDate = new Date().toISOString();

// Initialize Firebase Admin
if (process.env.FIREBASE_PROJECT_ID) {
  admin.initializeApp({
    credential: admin.credential.cert({
      projectId: process.env.FIREBASE_PROJECT_ID,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      privateKey: process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n'),
    })
  });
} else {
  console.error("CRITICAL: Missing Firebase Configuration. Check environment variables.");
}

const db = admin.firestore();
// Firebase Storage disabled due to region/plan limits. Using Local Storage for now.
const bucket = null; 

// Initialize Gemini Models
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const geminiFlash = genAI.getGenerativeModel({ model: "gemini-1.5-flash" });
const geminiPro = genAI.getGenerativeModel({ model: "gemini-1.5-pro" });

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
const upload = multer({ 
  storage: multer.memoryStorage(),
  limits: { fileSize: 25 * 1024 * 1024 } // 25MB limit
});

// Middleware - Firebase JWT Auth
const verifyToken = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'No token provided' });
    }

    const token = authHeader.split(' ')[1];
    const decodedToken = await admin.auth().verifyIdToken(token);
    
    if (!decodedToken) {
      return res.status(401).json({ error: 'Invalid token' });
    }

    req.user = { id: decodedToken.uid, email: decodedToken.email };
    next();
  } catch (err) {
    console.error('VerifyToken exception:', err);
    res.status(401).json({ error: 'Authentication failed' });
  }
};

// 2. HEALTH CHECK
app.get('/', (req, res) => res.json({ status: 'ok', message: 'Server is running' }));
app.get('/health', (req, res) => res.json({ status: 'ok', message: 'Server is running' }));

// 3. AUTH ROUTES

// 3. AUTH & PROFILE ROUTES (FIRESTORE)

app.post('/api/auth/register', async (req, res) => {
  try {
    const { name, email, password, uid } = req.body;
    let userId = uid;

    if (!userId) {
        // Create user in Firebase Auth if not already created on client
        const userRecord = await admin.auth().createUser({
          email,
          password,
          displayName: name
        });
        userId = userRecord.uid;
    }

    // Create or sync profile in Firestore
    const profileRef = db.collection('profiles').doc(userId);
    const profileDoc = await profileRef.get();
    
    if (!profileDoc.exists) {
        await profileRef.set({
          id: userId,
          name: name || 'User',
          email,
          avatar_url: null,
          created_at: admin.firestore.FieldValue.serverTimestamp()
        });
    }

    res.json({ 
      user: { id: userId, name, email, avatar_url: null } 
    });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// Note: firebase-admin doesn't support password login. 
// Android app should use Firebase Auth SDK to login and send ID Token to backend.
app.post('/api/auth/login', async (req, res) => {
    res.status(400).json({ error: "Please use Firebase Auth SDK on Android to login." });
});

app.get('/api/auth/profile', verifyToken, async (req, res) => {
  try {
    const doc = await db.collection('profiles').doc(req.user.id).get();
    if (!doc.exists) {
      return res.status(404).json({ error: 'Profile not found' });
    }
    res.json(doc.data());
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.put('/api/auth/profile', verifyToken, async (req, res) => {
  try {
    const { name, avatar_url } = req.body;
    const updateData = { updated_at: admin.firestore.FieldValue.serverTimestamp() };
    if (name) updateData.name = name;
    if (avatar_url) updateData.avatar_url = avatar_url;

    await db.collection('profiles').doc(req.user.id).update(updateData);
    const doc = await db.collection('profiles').doc(req.user.id).get();
    res.json(doc.data());
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/auth/upload-avatar', verifyToken, upload.single('avatar'), async (req, res) => {
  try {
    const file = req.file;
    if (!file) throw new Error('No file uploaded');

    const fileExt = file.originalname.split('.').pop();
    const fileName = `avatars/${req.user.id}-${Date.now()}.${fileExt}`;
    const filePath = path.join(__dirname, 'uploads', fileName);

    // Create avatars directory if it doesn't exist
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.writeFile(filePath, file.buffer);

    const publicUrl = `${req.protocol}://${req.get('host')}/uploads/${fileName}`;
    
    await db.collection('profiles').doc(req.user.id).update({ avatar_url: publicUrl });
    res.json({ avatar_url: publicUrl });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 4. SUBJECTS ROUTES (FIRESTORE)
app.get('/api/subjects', verifyToken, async (req, res) => {
  try {
    const snapshot = await db.collection('subjects')
      .where('user_id', '==', req.user.id)
      .get();
    
    const subjects = snapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data()
    }));
    
    res.json(subjects);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/subjects', verifyToken, async (req, res) => {
  try {
    const { name, color, icon } = req.body;
    const userId = req.user.id;
    
    const subjectData = {
      name,
      color: color || '#7C4DFF',
      icon: icon || '📚',
      user_id: userId,
      material_count: 0,
      created_at: admin.firestore.FieldValue.serverTimestamp()
    };
    
    const docRef = await db.collection('subjects').add(subjectData);
    res.json({ id: docRef.id, ...subjectData });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 5. MATERIALS ROUTES (FIRESTORE)
app.get('/api/materials/:subjectId', verifyToken, async (req, res) => {
  try {
    const snapshot = await db.collection('materials')
      .where('subject_id', '==', req.params.subjectId)
      .where('user_id', '==', req.user.id)
      .get();
    
    const materials = snapshot.docs.map(doc => ({
      id: doc.id,
      ...doc.data()
    }));
    
    res.json(materials);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/materials/upload', verifyToken, upload.single('file'), async (req, res) => {
  try {
    const { subjectId, name } = req.body;
    const file = req.file;

    if (!file || !subjectId) {
        return res.status(400).json({ error: "Missing file or subjectId" });
    }

    const materialName = name || file.originalname;
    const fileName = `materials/${Date.now()}_${file.originalname}`;
    const filePath = path.join(__dirname, 'uploads', fileName);

    // Create materials directory if it doesn't exist
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.writeFile(filePath, file.buffer);

    const publicUrl = `${req.protocol}://${req.get('host')}/uploads/${fileName}`;

    const materialData = {
      name: materialName,
      title: materialName,
      subject_id: subjectId,
      file_path: fileName,
      file_type: file.mimetype,
      file_url: publicUrl,
      user_id: req.user.id,
      created_at: admin.firestore.FieldValue.serverTimestamp(),
      indexed: false
    };

    const docRef = await db.collection('materials').add(materialData);
    
    // Update material count in subject
    await db.collection('subjects').doc(subjectId).update({
        material_count: admin.firestore.FieldValue.increment(1)
    });

    res.status(201).json({ id: docRef.id, ...materialData });

    // Background processing for AI
    (async () => {
        try {
            console.log(`[RAG] Starting background processing for material ${docRef.id}`);
            const text = await aiUtils.extractTextFromBuffer(file.buffer, file.mimetype);
            if (!text || text.trim().length === 0) return;

            const chunks = aiUtils.chunkText(text);
            const vectorData = await Promise.all(chunks.map(async (chunkText, index) => {
                const embedding = await aiUtils.generateEmbedding(chunkText);
                return { content: chunkText, embedding };
            }));

            const cachePath = path.join(__dirname, 'vector_cache', `${docRef.id}.json`);
            await fs.mkdir(path.dirname(cachePath), { recursive: true });
            await fs.writeFile(cachePath, JSON.stringify(vectorData));
            
            await docRef.update({ indexed: true });
            console.log(`[RAG] Finished background processing for material ${docRef.id}`);
        } catch (bgErr) {
            console.error(`[RAG] Background processing failed for ${docRef.id}:`, bgErr.message);
        }
    })();
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.delete('/api/materials/:id', verifyToken, async (req, res) => {
  try {
    const materialRef = db.collection('materials').doc(req.params.id);
    const doc = await materialRef.get();
    
    if (!doc.exists || doc.data().user_id !== req.user.id) {
        return res.status(404).json({ error: "Material not found" });
    }

    const { file_path, subject_id } = doc.data();
    
    // 1. Delete from Local Storage
    const filePath = path.join(__dirname, 'uploads', file_path);
    await fs.unlink(filePath).catch(() => {});
    
    // 2. Delete Vector Cache
    const cachePath = path.join(__dirname, 'vector_cache', `${req.params.id}.json`);
    await fs.unlink(cachePath).catch(() => {});
    
    // 3. Delete from Firestore
    await materialRef.delete();
    
    // 4. Update subject count
    await db.collection('subjects').doc(subject_id).update({
        material_count: admin.firestore.FieldValue.increment(-1)
    });

    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 6. AI ROUTES
app.post('/api/ai/chat', verifyToken, async (req, res) => {
  try {
    const { messages } = req.body;
    const lastUserMessage = messages[messages.length - 1]?.content || "";
    
    // Default chat uses Flash
    const chat = geminiFlash.startChat({
      history: messages.slice(0, -1).map(m => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }],
      })),
      generationConfig: { maxOutputTokens: 1024, temperature: 0.7 },
    });

    const prompt = `System Instructions: You are Note Sphere's AI tutor. Be helpful and concise.\nUser: ${lastUserMessage}`;
    const result = await chat.sendMessage(prompt);
    res.json({ content: result.response.text(), role: 'assistant' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Phase 2: RAG Doubt Solver (JSON-based Vector Retrieval)
app.post('/api/ai/doubt', verifyToken, async (req, res) => {
  try {
    const { question, materialId } = req.body;
    
    if (!question || !materialId) {
        return res.status(400).json({ error: "Missing question or materialId" });
    }

    // Load Vector Cache
    const cachePath = path.join(__dirname, 'vector_cache', `${materialId}.json`);
    let vectorData;
    try {
        const fileContent = await fs.readFile(cachePath, 'utf8');
        vectorData = JSON.parse(fileContent);
    } catch (err) {
        console.error("Vector cache miss or error:", err.message);
        return res.status(404).json({ error: "Material not yet indexed for AI. Please wait a moment." });
    }

    console.log(`[RAG] Generating embedding for question: "${question}"`);
    const queryEmbedding = await aiUtils.generateEmbedding(question);

    // Perform local cosine similarity
    console.log(`[RAG] Searching local JSON cache for relevant chunks...`);
    const scoredChunks = vectorData.map(item => ({
        content: item.content,
        similarity: aiUtils.cosineSimilarity(queryEmbedding, item.embedding)
    }));

    // Sort by similarity and take top 5
    const topChunks = scoredChunks
        .sort((a, b) => b.similarity - a.similarity)
        .slice(0, 5);

    const contextText = topChunks.map(c => c.content).join("\n\n---\n\n");

    // Use Pro model for reasoning
    const systemPrompt = `You are an expert tutor answering a student's doubt.
You have been provided with contextual snippets from their study material.
Answer the student's question strictly using the provided context. If the answer is not in the context, clearly state that.

CONTEXT:
${contextText}

STUDENT QUESTION:
${question}`;

    const result = await geminiPro.generateContent(systemPrompt);
    res.json({ content: result.response.text() });
  } catch (err) {
    console.error("AI Doubt Error:", err.message);
    res.status(500).json({ error: "Doubt solving failed" });
  }
});

// Phase 2: Structured Quiz Generation
app.post('/api/ai/quiz', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    
    const prompt = `You are an expert professor. Generate a high-quality 5-question multiple choice quiz based ONLY on the following text.
Respond purely in valid JSON format matching this schema:
[
  {
    "question": "The question text",
    "options": ["A", "B", "C", "D"],
    "correctAnswerIndex": 0,
    "explanation": "Why this is correct"
  }
]

TEXT:
${text}`;

    // Flash is fast enough for simple quizzes
    const result = await geminiFlash.generateContent(prompt);
    let outputText = result.response.text();
    // Clean up markdown formatting if present
    outputText = outputText.replace(/```json/g, '').replace(/```/g, '').trim();
    
    res.json({ content: outputText });
  } catch (err) {
    console.error("AI Quiz Error:", err.message);
    res.status(500).json({ error: "Quiz generation failed" });
  }
});

// Phase 2: Material Summarization
app.post('/api/ai/summarize', verifyToken, async (req, res) => {
  try {
    const { text, mode } = req.body; // mode: Quick, Detailed, Exam
    
    let instructions = "Summarize this material in an exhaustive, structured way.";
    if (mode === 'Quick') instructions = "Provide a very brief 3-bullet point summary of the core concepts.";
    if (mode === 'Exam') instructions = "Create an exam prep guide with key definitions, formulas, and concepts highlighted.";

    // Use Pro model for deep summarization
    const prompt = `System: You are an Expert Academic Professor.
Task: ${instructions}
Material: ${text}`;
    
    const result = await geminiPro.generateContent(prompt);
    res.json({ content: result.response.text() });
  } catch (err) {
    console.error("AI Summary Error:", err.message);
    res.status(500).json({ error: "Summary generation failed" });
  }
});

// Phase 2: Syllabus Parsing
app.post('/api/syllabus/parse', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const prompt = `You are an AI that converts raw syllabus text into a structured JSON tree.
Extract the units and topics from the provided text.
Respond purely in valid JSON format matching this schema:
[
  {
    "unit": "Unit Name",
    "topics": [
      { "name": "Topic Name", "completed": false }
    ]
  }
]

SYLLABUS TEXT:
${text}`;

    const result = await geminiPro.generateContent(prompt);
    let outputText = result.response.text();
    outputText = outputText.replace(/```json/g, '').replace(/```/g, '').trim();
    
    res.json({ content: outputText });
  } catch (err) {
    console.error("Syllabus Parse Error:", err.message);
    res.status(500).json({ error: "Parsing failed" });
  }
});

// Phase 2: Syllabus CRUD (FIRESTORE)
app.get('/api/syllabus/:userId', verifyToken, async (req, res) => {
    try {
        const snapshot = await db.collection('syllabuses')
            .where('user_id', '==', req.user.id)
            .get();
        
        const syllabuses = snapshot.docs.map(doc => ({
            id: doc.id,
            ...doc.data()
        }));
        res.json(syllabuses);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.put('/api/syllabus/:syllabusId', verifyToken, async (req, res) => {
    try {
        const { content_tree, progress_percentage } = req.body;
        await db.collection('syllabuses').doc(req.params.syllabusId).update({
            content_tree,
            progress_percentage,
            updated_at: admin.firestore.FieldValue.serverTimestamp()
        });
        
        const doc = await db.collection('syllabuses').doc(req.params.syllabusId).get();
        res.json({ id: doc.id, ...doc.data() });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// 7. P2P SHARING SYSTEM
const activeShares = new Map();

app.post('/api/share/generate', verifyToken, async (req, res) => {
  try {
    const { materialId } = req.body;
    const doc = await db.collection('materials').doc(materialId).get();
    if (!doc.exists) throw new Error('Material not found');
    
    const material = doc.data();
    const publicUrl = `${req.protocol}://${req.get('host')}/uploads/${material.file_path}`;

    const shareCode = crypto.randomBytes(3).toString('hex').toUpperCase();
    activeShares.set(shareCode, { name: material.title, url: publicUrl, expiresAt: Date.now() + 86400000 });

    res.json({
      shareCode,
      shareUrl: `${req.protocol}://${req.get('host')}/share/${shareCode}`,
      name: material.title
    });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.get('/share/:code', (req, res) => {
  const share = activeShares.get(req.params.code.toUpperCase());
  if (!share || Date.now() > share.expiresAt) return res.status(404).send('Link Expired');

  res.send(`
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Download \${share.name}</title>
        <style>
            body { background: #121212; color: white; font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
            .card { background: #1E1E1E; padding: 40px; border-radius: 20px; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
            .btn { background: #6C63FF; color: white; padding: 15px 30px; border-radius: 10px; text-decoration: none; display: inline-block; margin-top: 20px; font-weight: bold; }
        </style>
    </head>
    <body>
        <div class="card">
            <h2>\${share.name}</h2>
            <p>Your study material is ready.</p>
            <a href="\${share.url}" class="btn" download>Download PDF</a>
        </div>
    </body>
    </html>
  `);
});

app.listen(port, () => console.log(`Server running on port ${port}`));
