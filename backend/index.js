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
const currentDate = new Date().toISOString();

// Initialize Gemini
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);
const gemini = genAI.getGenerativeModel({ model: "gemini-flash-latest" });

// Initialize Supabase Clients
const supabaseUrl = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
const serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY;
const anonKey = process.env.SUPABASE_ANON_KEY || process.env.VITE_SUPABASE_ANON_KEY || serviceRoleKey;

if (!supabaseUrl || (!serviceRoleKey && !anonKey)) {
  console.error("CRITICAL: Missing Supabase Configuration. Check environment variables.");
}

// Client 1: For auth only
const supabase = createClient(supabaseUrl, anonKey);

// Client 2: For database operations (God Mode)
const supabaseAdmin = createClient(supabaseUrl, serviceRoleKey || anonKey);

// Auto-create buckets (Robustness Fix)
const initializeStorage = async () => {
  try {
    const buckets = ['materials', 'avatars'];
    for (const b of buckets) {
      const { data, error } = await supabaseAdmin.storage.getBucket(b);
      if (error && error.message.includes('not found')) {
        console.log(`Creating missing bucket: ${b}`);
        await supabaseAdmin.storage.createBucket(b, { public: true });
      } else {
        // Ensure it is public even if it exists
        await supabaseAdmin.storage.updateBucket(b, { public: true });
      }
    }
  } catch (err) {
    console.error('Storage initialization warning:', err.message);
  }
};
initializeStorage();

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '50mb' }));
const upload = multer({ storage: multer.memoryStorage() });

// Middleware - JWT Auth
const verifyToken = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    console.log('=== VERIFY TOKEN ===');
    console.log('Auth header:', authHeader ? 'Present' : 'MISSING');
    
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
    const { error: profileError } = await supabaseAdmin
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

    let { data: profile, error: profileError } = await supabaseAdmin
      .from('profiles')
      .select('*')
      .eq('id', data.user.id)
      .single();

    // If profile doesn't exist, create it now (robustness fix)
    if (profileError || !profile) {
      console.log('Profile missing for user, creating now...');
      const name = data.user.user_metadata?.name || email.split('@')[0];
      const { data: newProfile, error: createError } = await supabaseAdmin
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
    const { data: profile, error } = await supabaseAdmin
      .from('profiles')
      .select('*')
      .eq('id', req.user.id)
      .single();

    if (error) {
        console.error("GET PROFILE ERROR:", error.message);
        throw error;
    }
    
    console.log(`Profile fetched for user ${req.user.id}. Avatar: ${profile.avatar_url}`);
    res.json({
        id: profile.id,
        name: profile.name,
        email: profile.email,
        avatar_url: profile.avatar_url
    });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.put('/api/auth/profile', verifyToken, async (req, res) => {
  try {
    const { name, avatar_url } = req.body;
    const { data, error } = await supabaseAdmin
      .from('profiles')
      .update({ name, avatar_url, updated_at: new Date() })
      .eq('id', req.user.id)
      .select()
      .single();

    if (error) throw error;
    res.json({
        id: data.id,
        name: data.name,
        email: data.email,
        avatar_url: data.avatar_url
    });
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
    const filePath = fileName; // Simplified path

    console.log(`STEP 1: Starting Supabase Storage upload for ${filePath}...`);
    const { error: uploadError } = await supabaseAdmin.storage
      .from('avatars')
      .upload(filePath, file.buffer, { contentType: file.mimetype });

    if (uploadError) {
        console.error("STEP 1 FAILED (Storage):", uploadError.message);
        throw uploadError;
    }
    console.log("STEP 1 SUCCESS: File uploaded to storage.");

    console.log("STEP 2: Generating Public URL...");
    const { data: { publicUrl } } = supabaseAdmin.storage
      .from('avatars')
      .getPublicUrl(filePath);
    console.log(`STEP 2 SUCCESS: Generated URL: ${publicUrl}`);

    console.log("STEP 3: Updating profiles table in database...");
    const { data: profile, error: updateError } = await supabaseAdmin
      .from('profiles')
      .update({ avatar_url: publicUrl })
      .eq('id', req.user.id)
      .select()
      .single();

    if (updateError) {
        console.error("STEP 3 FAILED (Database):", updateError.message);
        throw updateError;
    }
    console.log("STEP 3 SUCCESS: Profile updated in database.");

    res.json({ avatar_url: publicUrl });
  } catch (err) {
    console.error("AVATAR UPLOAD CRITICAL ERROR:", err.message);
    res.status(400).json({ error: err.message });
  }
});

app.delete('/api/auth/avatar', verifyToken, async (req, res) => {
  try {
    const { error } = await supabaseAdmin
      .from('profiles')
      .update({ avatar_url: null, updated_at: new Date() })
      .eq('id', req.user.id);

    if (error) throw error;
    res.json({ success: true, message: 'Avatar removed' });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

// 4. SUBJECTS ROUTES
app.get('/api/subjects', verifyToken, async (req, res) => {
  try {
    const { data, error } = await supabaseAdmin
      .from('subjects')
      .select('*')
      .eq('user_id', req.user.id);
    
    if (error) {
        console.error("GET SUBJECTS ERROR:", error.message);
        throw error;
    }
    
    console.log(`Found ${data.length} subjects for user: ${req.user.id}`);
    
    const mapped = data.map(s => ({
        id: s.id,
        name: s.name,
        icon: s.icon,
        color: s.color,
        userId: s.user_id,
        materialCount: s.material_count || 0
    }));
    
    res.json(mapped);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/subjects', verifyToken, async (req, res) => {
  try {
    console.log('=== CREATE SUBJECT ===');
    console.log('Body:', req.body);
    console.log('User:', req.user);
    console.log('User ID:', req.user?.id);
    
    const { name, color, icon } = req.body;
    const userId = req.user?.id;
    
    if (!userId) {
      console.log('ERROR: No user ID found!');
      return res.status(401).json({ error: 'No user ID' });
    }
    
    if (!name) {
      console.log('ERROR: No name provided!');
      return res.status(400).json({ error: 'Name is required' });
    }
    
    const insertData = { 
      name: name, 
      color: color || '#6C63FF', 
      icon: icon || '📚',
      user_id: userId 
    };
    console.log('Inserting:', insertData);
    
    const { data, error } = await supabaseAdmin
      .from('subjects')
      .insert([insertData])
      .select();
    
    if (error) {
      console.log('INSERT ERROR:', JSON.stringify(error));
      return res.status(400).json({ error: error.message });
    }
    
    console.log('INSERT SUCCESS:', data);
    res.json(data[0]);
    
  } catch (err) {
    console.log('CRASH:', err.message);
    res.status(500).json({ error: err.message });
  }
});

// 5. MATERIALS ROUTES
app.get('/api/materials/:subjectId', verifyToken, async (req, res) => {
  try {
    const { data, error } = await supabaseAdmin
      .from('materials')
      .select('*')
      .eq('subject_id', req.params.subjectId)
      .eq('user_id', req.user.id);
    
    if (error) throw error;
    
    // Map to Android Model Keys
    const mapped = data.map(m => {
        let url = m.file_url;
        if (!url && m.file_path) {
            url = supabaseAdmin.storage.from('materials').getPublicUrl(m.file_path).data.publicUrl;
        }
        return {
            id: m.id,
            title: m.title,
            fileUrl: url || '',
            fileType: m.file_type || 'application/octet-stream',
            subjectId: m.subject_id,
            createdAt: m.created_at
        };
    });
    
    res.json(mapped);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post('/api/materials/upload', verifyToken, upload.single('file'), async (req, res) => {
  try {
    const { subjectId } = req.body;
    const file = req.file;
    if (!file) {
        console.error("Upload Error: No file found in request.");
        return res.status(400).json({ error: "No file uploaded" });
    }

    const originalName = file.originalname;
    const mimeType = file.mimetype;
    const fileName = `${Date.now()}_${originalName}`;
    
    console.log(`Uploading file: ${originalName} to storage...`);

    const { data: storageData, error: storageError } = await supabaseAdmin.storage
        .from('materials')
        .upload(fileName, file.buffer, {
            contentType: mimeType,
            upsert: true
        });

    if (storageError) {
        console.error("SUPABASE STORAGE ERROR:", storageError.message);
        return res.status(500).json({ error: "Storage upload failed: " + storageError.message });
    }

    const { data: { publicUrl } } = supabaseAdmin.storage.from('materials').getPublicUrl(fileName);

    console.log("File uploaded. Inserting into database...");

    const { data: materialData, error: materialError } = await supabaseAdmin
        .from('materials')
        .insert([{
            subject_id: subjectId,
            title: originalName,
            file_path: fileName,
            user_id: req.user.id
        }])
        .select()
        .single();

    if (materialError) {
        console.error("SUPABASE DB ERROR (Materials):", materialError.message);
        return res.status(500).json({ error: "Database insert failed: " + materialError.message });
    }

    // Map to Android Model Keys for immediate UI update
    const responseData = {
        id: materialData.id,
        title: materialData.title,
        fileUrl: materialData.file_url,
        fileType: materialData.file_type,
        subjectId: materialData.subject_id,
        createdAt: materialData.created_at
    };

    console.log("Material created successfully.");
    res.status(201).json(responseData);
  } catch (err) {
    console.error("GENERAL UPLOAD ERROR:", err.message);
    res.status(500).json({ error: "Internal server error during upload" });
  }
});

app.delete('/api/materials/:id', verifyToken, async (req, res) => {
  try {
    const { error } = await supabaseAdmin
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
    const { messages } = req.body;
    const lastUserMessage = messages[messages.length - 1]?.content || "";
    const isExplainMode = lastUserMessage.toLowerCase().includes('explain');

    const activeSystemPrompt = isExplainMode ? 
        `You are an expert professor and study assistant. 
        When asked to explain something, provide:
        - Complete detailed explanation with NO topics skipped
        - Clear headings and subheadings
        - Examples for every concept
        - Key points and definitions
        - Step by step breakdowns where needed
        - Minimum 500 words, be thorough and exhaustive` : 
        `You are a helpful AI assistant like Gemini. 
        Be conversational, concise and friendly.
        Answer naturally without over-explaining.`;

    const maxTokens = isExplainMode ? 4096 : 1024;

    const chat = gemini.startChat({
      history: messages.slice(0, -1).map(m => ({
        role: m.role === 'assistant' ? 'model' : 'user',
        parts: [{ text: m.content }],
      })),
      generationConfig: { maxOutputTokens: maxTokens, temperature: 0.7 },
    });

    const prompt = `System Instructions: ${activeSystemPrompt}
    Current Time: ${currentDate}.
    User Question: ${lastUserMessage}`;

    const result = await chat.sendMessage(prompt);
    res.json({ content: result.response.text(), role: 'assistant' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.post('/api/ai/summary', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const result = await gemini.generateContent(`System: You are an Expert Academic Professor.
    Task: Summarize this material in an exhaustive, structured way. Do NOT skip key concepts.
    Material: ${text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    console.error("AI Summary Error:", err.message);
    res.status(400).json({ error: "Summary generation failed" });
  }
});

app.post('/api/ai/notes', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const result = await gemini.generateContent({
      contents: [{
        role: 'user',
        parts: [{
          text: `You are an expert academic note-taker. Create COMPLETE, DETAILED study notes from the following material.
Requirements:
- Cover EVERY topic mentioned, skip NOTHING
- Use clear headings (##) for each section
- Bold (**) important terms and definitions  
- Include bullet points for key facts
- Add examples where helpful
- Minimum 800 words
- Structure: Overview → Main Topics → Key Definitions → Summary

Material: ${text}`
        }]
      }],
      generationConfig: { maxOutputTokens: 4096 }
    });
    res.json({ content: result.response.text() });
  } catch (err) {
    console.error("AI Notes Error:", err.message);
    res.status(400).json({ error: "Notes generation failed" });
  }
});

app.post('/api/ai/questions', verifyToken, async (req, res) => {
  try {
    const { text } = req.body;
    const result = await gemini.generateContent(`System: You are an Expert Academic Professor.
    Task: Generate high-quality practice questions (Multiple Choice and Short Answer) based on this material.
    Material: ${text}`);
    res.json({ content: result.response.text() });
  } catch (err) {
    console.error("AI Questions Error:", err.message);
    res.status(400).json({ error: "Questions generation failed" });
  }
});

// 7. P2P SHARING SYSTEM
const activeShares = new Map();

app.post('/api/share/generate', verifyToken, async (req, res) => {
  try {
    const { materialId } = req.body;
    const { data: material } = await supabaseAdmin.from('materials').select('*').eq('id', materialId).single();
    if (!material) throw new Error('Material not found');

    const path = material.file_path || material.file_url.split('/').pop();
    const { data: signedUrl } = await supabaseAdmin.storage.from('materials').createSignedUrl(path, 3600 * 24);

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
