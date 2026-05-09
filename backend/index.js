const express = require('express');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');
const Groq = require('groq-sdk');
const jwt = require('jsonwebtoken');
const multer = require('multer');
const { search } = require('duck-duck-scrape');
const dotenv = require('dotenv');

dotenv.config();

const app = express();
const port = process.env.PORT || 5000;

// Initialize Supabase
const supabaseUrl = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY;
const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!supabaseUrl) {
  console.error("ERROR: SUPABASE_URL is missing!");
}

// Use Service Role Key if available for administrative actions/robustness
const supabase = createClient(
  supabaseUrl,
  serviceRoleKey || supabaseKey
);

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
      console.error('Auth verification failed:', error?.message);
      return res.status(401).json({ error: 'Invalid or expired token. Please login again.' });
    }

    req.user = user;
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
    // Use upsert to be safe
    const { error: profileError } = await supabase
      .from('profiles')
      .upsert({ id: user.id, name, email, avatar_url: null });

    if (profileError) console.error('Profile creation warning:', profileError);

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

    let { data: profile, error: profileError } = await supabase
      .from('profiles')
      .select('*')
      .eq('id', data.user.id)
      .single();

    // If profile doesn't exist, create it now (robustness fix)
    if (profileError || !profile) {
      console.log('Profile missing for user, creating now...');
      const name = data.user.user_metadata?.name || email.split('@')[0];
      const { data: newProfile, error: createError } = await supabase
        .from('profiles')
        .upsert({ id: data.user.id, name, email, avatar_url: null })
        .select()
        .single();
      
      if (createError) throw createError;
      profile = newProfile;
    }

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
    console.error('Login error:', err);
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
    console.log('Attempting to create subject:', { name, color, icon, user_id: req.user.id });
    
    const { data, error } = await supabase
      .from('subjects')
      .insert({ 
        name: name || 'New Subject', 
        color: color || '#6C63FF', 
        icon: icon || 'book', 
        user_id: req.user.id 
      })
      .select()
      .single();

    if (error) {
      console.error('SUPABASE INSERT ERROR:', error);
      return res.status(400).json({ error: error.message });
    }
    
    res.json(data);
  } catch (err) {
    console.error('SERVER EXCEPTION:', err);
    res.status(500).json({ error: err.message });
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
    const result = await gemini.generateContent(`Summarize this study material comprehensively: ${text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/notes', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const result = await gemini.generateContent(`Create detailed study notes with key points, definitions and important concepts from: ${text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/questions', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const completion = await gemini.generateContent(`Generate 10 practice questions with answers from this study material: ${text}`);
    res.json({ content: completion.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 6. P2P SHARING ROUTE
app.post('/api/share/generate', verifyToken, async (req, res) => {
  try {
    const { materialId } = req.body;
    
    // 1. Get material info
    const { data: material, error: mError } = await supabase
      .from('materials')
      .select('*')
      .eq('id', materialId)
      .eq('user_id', req.user.id)
      .single();

    if (mError || !material) throw new Error('Material not found');

    // 2. Generate a long-lived signed URL (7 days)
    // Extract file path from URL or use stored path
    const filePath = material.url.split('/').pop();
    const { data: signData, error: sError } = await supabase.storage
      .from('materials')
      .createSignedUrl(filePath, 60 * 60 * 24 * 7);

    if (sError) throw sError;

    res.json({ 
      shareUrl: signData.signedUrl,
      name: material.name,
      expiresIn: '7 days'
    });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 7. GEMINI AI ROUTES
app.post('/api/ai/chat', verifyToken, async (req, res) => {
  try {
    const { messages, systemPrompt } = req.body;
    const lastUserMessage = messages[messages.length - 1]?.content || "";
    const currentDate = new Date().toLocaleString('en-US', { timeZone: 'Asia/Kolkata' });

    console.log('Gemini Chat request:', lastUserMessage);

    const chat = gemini.startChat({
      history: messages.slice(0, -1).map(m => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }],
      })),
      generationConfig: { maxOutputTokens: 1000 },
    });

    const prompt = `System Instructions: ${systemPrompt || "You are an expert study assistant."}
    Current Time: ${currentDate}.
    User Question: ${lastUserMessage}`;

    const result = await chat.sendMessage(prompt);
    const response = await result.response;
    
    res.json({ content: response.text(), role: 'assistant' });
  } catch (err) {
    console.error('GEMINI ERROR:', err);
    res.status(500).json({ error: 'Gemini AI Error: ' + err.message });
  }
});

app.post('/api/ai/summary', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const result = await gemini.generateContent(`Summarize this study material comprehensively: ${text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/notes', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const result = await gemini.generateContent(`Create detailed study notes with key points, definitions and important concepts from: ${text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.listen(port, () => console.log(`Server running on port ${port}`));
