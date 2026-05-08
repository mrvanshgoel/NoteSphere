import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { createClient } from '@supabase/supabase-js';
import Groq from 'groq-sdk';
import multer from 'multer';

dotenv.config();

const app = express();
const port = process.env.PORT || 5000;
const upload = multer({ storage: multer.memoryStorage() });

// Optimized CORS
app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

app.use(express.json());

const supabase = createClient(
  process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL,
  process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY
);

const groq = new Groq({
  apiKey: process.env.GROQ_API_KEY,
});

// Middleware to verify Supabase Auth token
const authenticateUser = async (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({ error: 'Unauthorized: No token provided' });
  }

  const { data: { user }, error } = await supabase.auth.getUser(token);
  
  if (error || !user) {
    return res.status(401).json({ error: 'Unauthorized: Invalid token' });
  }

  req.user = user;
  next();
};

// --- AUTH ROUTES ---
app.post('/api/auth/register', async (req, res) => {
  const { email, password } = req.body;
  const { data, error } = await supabase.auth.signUp({ email, password });
  if (error) return res.status(400).json({ error: error.message });
  res.json(data);
});

app.post('/api/auth/login', async (req, res) => {
  const { email, password } = req.body;
  const { data, error } = await supabase.auth.signInWithPassword({ email, password });
  if (error) return res.status(400).json({ error: error.message });
  res.json(data);
});

// --- SUBJECT ROUTES ---
app.get('/api/subjects', authenticateUser, async (req, res) => {
  const { data, error } = await supabase
    .from('subjects')
    .select('*')
    .eq('user_id', req.user.id);
  if (error) return res.status(400).json({ error: error.message });
  res.json(data);
});

app.post('/api/subjects', authenticateUser, async (req, res) => {
  const { name, color } = req.body;
  const { data, error } = await supabase
    .from('subjects')
    .insert([{ name, color, user_id: req.user.id }])
    .select();
  if (error) return res.status(400).json({ error: error.message });
  res.json(data[0]);
});

app.delete('/api/subjects/:id', authenticateUser, async (req, res) => {
  const { error } = await supabase
    .from('subjects')
    .delete()
    .eq('id', req.params.id)
    .eq('user_id', req.user.id);
  if (error) return res.status(400).json({ error: error.message });
  res.json({ message: 'Subject deleted' });
});

// --- MATERIAL ROUTES ---
app.get('/api/materials/:subjectId', authenticateUser, async (req, res) => {
  const { data, error } = await supabase
    .from('materials')
    .select('*')
    .eq('subject_id', req.params.subjectId)
    .eq('user_id', req.user.id);
  if (error) return res.status(400).json({ error: error.message });
  res.json(data);
});

app.post('/api/materials/upload', authenticateUser, upload.single('file'), async (req, res) => {
  const { subjectId, name, type } = req.body;
  const file = req.file;

  if (!file) return res.status(400).json({ error: 'No file uploaded' });

  const fileName = `${req.user.id}/${Date.now()}_${file.originalname}`;
  const { data: uploadData, error: uploadError } = await supabase.storage
    .from('study_materials')
    .upload(fileName, file.buffer, { contentType: file.mimetype });

  if (uploadError) return res.status(400).json({ error: uploadError.message });

  const { data: { publicUrl } } = supabase.storage
    .from('study_materials')
    .getPublicUrl(fileName);

  const { data, error } = await supabase
    .from('materials')
    .insert([{
      name,
      type,
      subject_id: subjectId,
      user_id: req.user.id,
      file_url: publicUrl,
      storage_path: fileName
    }])
    .select();

  if (error) return res.status(400).json({ error: error.message });
  res.json(data[0]);
});

app.delete('/api/materials/:id', authenticateUser, async (req, res) => {
  // First get the material to find the storage path
  const { data: material } = await supabase
    .from('materials')
    .select('storage_path')
    .eq('id', req.params.id)
    .single();

  if (material) {
    await supabase.storage.from('study_materials').remove([material.storage_path]);
  }

  const { error } = await supabase
    .from('materials')
    .delete()
    .eq('id', req.params.id)
    .eq('user_id', req.user.id);

  if (error) return res.status(400).json({ error: error.message });
  res.json({ message: 'Material deleted' });
});

// --- AI ROUTES ---
const processAIRequest = async (documentText, type, res) => {
  let systemPrompt = '';
  if (type === 'summary') systemPrompt = 'Provide a concise summary.';
  else if (type === 'notes') systemPrompt = 'Extract detailed study notes in Markdown.';
  else if (type === 'questions') systemPrompt = 'Generate 5 practice questions and answers.';

  try {
    const completion = await groq.chat.completions.create({
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: `Text: ${documentText}` }
      ],
      model: 'llama3-70b-8192',
    });
    res.json({ result: completion.choices[0].message.content });
  } catch (error) {
    res.status(500).json({ error: 'AI processing failed' });
  }
};

app.post('/api/ai/summary', authenticateUser, (req, res) => processAIRequest(req.body.documentText, 'summary', res));
app.post('/api/ai/notes', authenticateUser, (req, res) => processAIRequest(req.body.documentText, 'notes', res));
app.post('/api/ai/questions', authenticateUser, (req, res) => processAIRequest(req.body.documentText, 'questions', res));

app.post('/api/ai/chat', authenticateUser, async (req, res) => {
  const { documentText, question, history } = req.body;
  try {
    const completion = await groq.chat.completions.create({
      messages: [
        { role: 'system', content: "Answer based on context." },
        ...(history || []),
        { role: 'user', content: `Context: ${documentText}\nQuestion: ${question}` }
      ],
      model: 'llama3-70b-8192',
    });
    res.json({ result: completion.choices[0].message.content });
  } catch (error) {
    res.status(500).json({ error: 'AI chat failed' });
  }
});

// Health check
app.get('/health', (req, res) => res.json({ status: "ok" }));

app.listen(port, () => console.log(`Server running on port ${port}`));
