const express = require('express');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');
const Groq = require('groq-sdk');
const jwt = require('jsonwebtoken');
const multer = require('multer');
const dotenv = require('dotenv');

dotenv.config();

const app = express();
const port = process.env.PORT || 5000;

// Initialize Supabase
const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_ANON_KEY
);

// Initialize Groq
const groq = new Groq({ apiKey: process.env.GROQ_API_KEY });

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
const upload = multer({ storage: multer.memoryStorage() });

// Middleware - JWT Auth
const verifyToken = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'No token provided' });
    }

    const token = authHeader.split(' ')[1];
    const { data: { user }, error } = await supabase.auth.getUser(token);

    if (error || !user) {
      return res.status(401).json({ error: 'Invalid or expired token' });
    }

    req.user = user;
    next();
  } catch (err) {
    res.status(500).json({ error: 'Authentication error' });
  }
};

// 2. HEALTH CHECK
app.get('/', (req, res) => res.json({ status: 'ok', message: 'Server is running' }));
app.get('/health', (req, res) => res.json({ status: 'ok', message: 'Server is running' }));

// 3. AUTH ROUTES

app.post('/api/auth/register', async (req, res) => {
  try {
    const { name, email, password } = req.body;
    const { data, error } = await supabase.auth.signUp({ 
      email, 
      password, 
      options: { data: { name } } 
    });

    if (error) throw error;

    const user = data.user;
    const { error: profileError } = await supabase
      .from('profiles')
      .insert({ id: user.id, name, email, avatar_url: null });

    if (profileError) throw profileError;

    res.json({ 
      token: data.session?.access_token, 
      user: { id: user.id, name, email, avatar_url: null } 
    });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/auth/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    const { data, error } = await supabase.auth.signInWithPassword({ email, password });

    if (error) throw error;

    const { data: profile, error: profileError } = await supabase
      .from('profiles')
      .select('*')
      .eq('id', data.user.id)
      .single();

    if (profileError) throw profileError;

    res.json({ 
      token: data.session.access_token, 
      user: { 
        id: profile.id, 
        name: profile.name, 
        email: profile.email, 
        avatar_url: profile.avatar_url 
      } 
    });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.get('/api/auth/profile', verifyToken, async (req, res) => {
  try {
    const { data: profile, error } = await supabase
      .from('profiles')
      .select('*')
      .eq('id', req.user.id)
      .single();

    if (error) throw error;
    res.json(profile);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.put('/api/auth/profile', verifyToken, async (req, res) => {
  try {
    const { name, avatar_url } = req.body;
    const { data, error } = await supabase
      .from('profiles')
      .update({ name, avatar_url, updated_at: new Date() })
      .eq('id', req.user.id)
      .select()
      .single();

    if (error) throw error;
    res.json(data);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/auth/upload-avatar', verifyToken, upload.single('avatar'), async (req, res) => {
  try {
    const file = req.file;
    if (!file) throw new Error('No file uploaded');

    const fileExt = file.originalname.split('.').pop();
    const fileName = `${req.user.id}-${Date.now()}.${fileExt}`;
    const filePath = `avatars/${fileName}`;

    const { error: uploadError } = await supabase.storage
      .from('avatars')
      .upload(filePath, file.buffer, { contentType: file.mimetype });

    if (uploadError) throw uploadError;

    const { data: { publicUrl } } = supabase.storage
      .from('avatars')
      .getPublicUrl(filePath);

    const { data: profile, error: updateError } = await supabase
      .from('profiles')
      .update({ avatar_url: publicUrl })
      .eq('id', req.user.id)
      .select()
      .single();

    if (updateError) throw updateError;

    res.json({ avatar_url: publicUrl });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 4. SUBJECTS ROUTES

app.get('/api/subjects', verifyToken, async (req, res) => {
  try {
    const { data, error } = await supabase
      .from('subjects')
      .select('*')
      .eq('user_id', req.user.id);

    if (error) throw error;
    res.json(data);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/subjects', verifyToken, async (req, res) => {
  try {
    const { name, color, icon } = req.body;
    const { data, error } = await supabase
      .from('subjects')
      .insert({ name, color, icon, user_id: req.user.id })
      .select()
      .single();

    if (error) throw error;
    res.json(data);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.delete('/api/subjects/:id', verifyToken, async (req, res) => {
  try {
    const { error } = await supabase
      .from('subjects')
      .delete()
      .eq('id', req.params.id)
      .eq('user_id', req.user.id);

    if (error) throw error;
    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 5. MATERIALS ROUTES

app.get('/api/materials/:subjectId', verifyToken, async (req, res) => {
  try {
    const { data, error } = await supabase
      .from('materials')
      .select('*')
      .eq('subject_id', req.params.subjectId)
      .eq('user_id', req.user.id);

    if (error) throw error;
    res.json(data);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/materials/upload', verifyToken, upload.single('file'), async (req, res) => {
  try {
    const { subjectId } = req.body;
    const file = req.file;
    if (!file) throw new Error('No file uploaded');

    const fileName = `${req.user.id}/${Date.now()}_${file.originalname}`;
    const { error: uploadError } = await supabase.storage
      .from('materials')
      .upload(fileName, file.buffer, { contentType: file.mimetype });

    if (uploadError) throw uploadError;

    const { data: { publicUrl } } = supabase.storage
      .from('materials')
      .getPublicUrl(fileName);

    let contentText = '';
    if (file.originalname.endsWith('.txt')) {
      contentText = file.buffer.toString('utf-8');
    }

    const { data, error } = await supabase
      .from('materials')
      .insert({
        user_id: req.user.id,
        subject_id: subjectId,
        title: file.originalname,
        file_url: publicUrl,
        file_type: file.mimetype,
        content_text: contentText
      })
      .select()
      .single();

    if (error) throw error;
    res.json(data);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.delete('/api/materials/:id', verifyToken, async (req, res) => {
  try {
    // Get material to find subject_id for count update
    const { data: material } = await supabase
      .from('materials')
      .select('subject_id')
      .eq('id', req.params.id)
      .single();

    const { error } = await supabase
      .from('materials')
      .delete()
      .eq('id', req.params.id)
      .eq('user_id', req.user.id);

    if (error) throw error;

    res.json({ success: true });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 6. AI ROUTES

app.post('/api/ai/summary', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const completion = await groq.chat.completions.create({
      messages: [{ role: 'user', content: `Create a comprehensive summary of this study material: ${text}` }],
      model: "llama3-70b-8192",
    });
    res.json({ content: completion.choices[0].message.content });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/notes', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const completion = await groq.chat.completions.create({
      messages: [{ role: 'user', content: `Create detailed study notes with key points, definitions and important concepts from: ${text}` }],
      model: "llama3-70b-8192",
    });
    res.json({ content: completion.choices[0].message.content });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/questions', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const completion = await groq.chat.completions.create({
      messages: [{ role: 'user', content: `Generate 10 practice questions with answers from this study material: ${text}` }],
      model: "llama3-70b-8192",
    });
    res.json({ content: completion.choices[0].message.content });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/chat', verifyToken, async (req, res) => {
  try {
    const { messages, systemPrompt } = req.body;
    const completion = await groq.chat.completions.create({
      messages: [
        { role: 'system', content: systemPrompt || "You are an expert AI study assistant like ChatGPT. Help students understand their study material, answer questions clearly, explain concepts, and make learning engaging. Be conversational, smart and helpful." },
        ...messages
      ],
      model: "llama3-70b-8192",
    });
    res.json({ content: completion.choices[0].message.content, role: 'assistant' });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.listen(port, () => console.log(`Server running on port ${port}`));
