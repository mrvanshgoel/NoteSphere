const express = require('express');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');
const jwt = require('jsonwebtoken');
const multer = require('multer');
const { v4: uuidv4 } = require('uuid');
const crypto = require('crypto');
const { search } = require('duck-duck-scrape');
const dotenv = require('dotenv');
const { GoogleGenerativeAI } = require('@google/generative-ai');

dotenv.config();

const app = express();
const port = process.env.PORT || 5000;

// Initialize Gemini
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const gemini = genAI.getGenerativeModel({ model: "gemini-flash-latest" });

// Initialize Supabase
const supabaseUrl = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_SERVICE_ROLE_KEY || process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY;

if (!process.env.SUPABASE_SERVICE_ROLE_KEY) {
  console.warn('WARNING: SUPABASE_SERVICE_ROLE_KEY is missing. Database operations may fail due to RLS.');
}

const supabase = createClient(supabaseUrl, supabaseKey);

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

    const filePath = `${req.user.id}/${Date.now()}_${file.originalname}`;
    const { error: uploadError } = await supabase.storage
      .from('materials')
      .upload(filePath, file.buffer, { contentType: file.mimetype });

    if (uploadError) throw uploadError;

    const { data: { publicUrl } } = supabase.storage.from('materials').getPublicUrl(filePath);

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
        file_path: filePath, // Saved for sharing
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
app.post('/api/ai/chat', verifyToken, async (req, res) => {
  try {
    const { messages, systemPrompt } = req.body;
    const lastUserMessage = messages[messages.length - 1]?.content || "";
    const chat = gemini.startChat({
      history: messages.slice(0, -1).map(m => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }],
      })),
    });
    const result = await chat.sendMessage(`Instructions: ${systemPrompt}\nUser: ${lastUserMessage}`);
    res.json({ content: result.response.text(), role: 'assistant' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post('/api/ai/summary', verifyToken, async (req, res) => {
  try {
    const result = await gemini.generateContent(`Summarize this material: ${req.body.text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/notes', verifyToken, async (req, res) => {
  try {
    const result = await gemini.generateContent(`Create study notes: ${req.body.text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/ai/questions', verifyToken, async (req, res) => {
  try {
    const result = await gemini.generateContent(`Generate practice questions: ${req.body.text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 7. P2P SHARING SYSTEM
const activeShares = new Map();

app.post('/api/share/generate', verifyToken, async (req, res) => {
  try {
    const { materialId } = req.body;
    const { data: material } = await supabase.from('materials').select('*').eq('id', materialId).single();
    if (!material) throw new Error('Material not found');

    const path = material.file_path || material.file_url.split('/').pop();
    const { data: signedUrl } = await supabase.storage.from('materials').createSignedUrl(path, 3600 * 24);

    const shareCode = crypto.randomBytes(3).toString('hex').toUpperCase();
    activeShares.set(shareCode, { name: material.title, url: signedUrl.signedUrl, expiresAt: Date.now() + 86400000 });

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
