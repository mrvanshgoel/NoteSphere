import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { createClient } from '@supabase/supabase-js';
import Groq from 'groq-sdk';

dotenv.config();

const app = express();
const port = process.env.PORT || 5000;

// Optimized CORS for Mobile App and Web
app.use(cors({
  origin: '*', // Allows all for testing; you can restrict this to your Vercel URL later
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

app.use(express.json());

const supabase = createClient(
  process.env.SUPABASE_URL || 'http://localhost:54321',
  process.env.SUPABASE_ANON_KEY || 'anon-key'
);

const groq = new Groq({
  apiKey: process.env.GROQ_API_KEY || 'dummy-key',
});

// Middleware to verify Supabase Auth token
const authenticateUser = async (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  const { data: { user }, error } = await supabase.auth.getUser(token);
  
  if (error || !user) {
    return res.status(401).json({ error: 'Unauthorized' });
  }

  req.user = user;
  next();
};

// Health check route
app.get('/health', (req, res) => {
  res.json({ status: "ok", message: "Server is running" });
});

// Endpoint to process document via Groq (Summary/Notes)
app.post('/api/ai/process-document', authenticateUser, async (req, res) => {
  const { documentText, type } = req.body;
  
  if (!documentText || !type) {
    return res.status(400).json({ error: 'Missing document text or processing type' });
  }

  try {
    let systemPrompt = '';
    
    if (type === 'summary') {
      systemPrompt = 'You are an AI study assistant. Your task is to provide a concise and comprehensive summary of the provided text.';
    } else if (type === 'notes') {
      systemPrompt = 'You are an AI study assistant. Extract key points, definitions, and important formulas from the provided text and format them as detailed study notes using Markdown.';
    } else if (type === 'questions') {
      systemPrompt = 'You are an AI study assistant. Generate 5 practice questions based on the provided text, along with their answers at the end.';
    } else {
      return res.status(400).json({ error: 'Invalid processing type' });
    }

    const completion = await groq.chat.completions.create({
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: `Here is the study material:\n\n${documentText}` }
      ],
      model: 'llama3-70b-8192',
    });

    res.json({ result: completion.choices[0].message.content });
  } catch (error) {
    console.error('AI Error:', error);
    res.status(500).json({ error: 'Failed to process document' });
  }
});

// Endpoint for chat/Q&A on a document
app.post('/api/ai/chat', authenticateUser, async (req, res) => {
  const { documentText, question, history } = req.body;
  
  if (!documentText || !question) {
    return res.status(400).json({ error: 'Missing document text or question' });
  }

  try {
    const messages = [
      { 
        role: 'system', 
        content: "You are an AI study assistant. Answer the user's question based ONLY on the provided context document. If the answer is not in the document, say so politely." 
      },
      ... (history ? history.map(h => ({
        role: h.role,
        content: h.content
      })) : []),
      {
        role: 'user',
        content: `Context document:\n${documentText}\n\nQuestion: ${question}`
      }
    ];

    const completion = await groq.chat.completions.create({
      messages,
      model: 'llama3-70b-8192',
    });

    res.json({ result: completion.choices[0].message.content });
  } catch (error) {
    console.error('AI Chat Error:', error);
    res.status(500).json({ error: 'Failed to answer question' });
  }
});

app.listen(port, () => {
  console.log(`Backend running on port ${port}`);
});
