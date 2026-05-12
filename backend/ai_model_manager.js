const { GoogleGenerativeAI } = require('@google/generative-ai');
const dotenv = require('dotenv');
dotenv.config();

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

const FALLBACK_ORDER = [
  'gemini-1.5-flash',
  'gemini-1.5-flash-latest',
  'gemini-2.5-flash',
  'gemini-2.0-flash',
  'gemini-3.1-flash-lite',
  'gemini-pro',
  'gemini-1.0-pro'
];

let availableModels = [];
let activeModelName = null;

/**
 * Discover available Gemini models on startup via REST API
 */
async function discoverModels() {
  console.log('\n================================');
  console.log('AVAILABLE GEMINI MODELS');
  console.log('=======================');
  
  const API_KEY = process.env.GEMINI_API_KEY;
  const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${API_KEY}`;

  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);
    const data = await response.json();
    
    // Filter for models that support generateContent
    availableModels = (data.models || [])
      .filter(m => m.supportedGenerationMethods.includes('generateContent'))
      .filter(m => !m.name.toLowerCase().includes('embedding'))
      .filter(m => !m.name.toLowerCase().includes('aqa'))
      .map(m => ({
        name: m.name.replace('models/', ''),
        displayName: m.displayName
      }));

    availableModels.forEach(m => {
      console.log(`[Gemini] Found model: ${m.name}`);
      console.log(`[Gemini] Supports generateContent: true`);
    });

    // Select best working model from our priority list
    for (const name of FALLBACK_ORDER) {
      if (availableModels.find(m => m.name === name)) {
        activeModelName = name;
        break;
      }
    }

    if (!activeModelName && availableModels.length > 0) {
      activeModelName = availableModels[0].name;
    }

    if (activeModelName) {
      console.log(`[Gemini] PRIMARY MODEL SELECTED: ${activeModelName}`);
    } else {
      console.warn('[Gemini] No suitable models found. Using fallback hardcoded name.');
      activeModelName = 'gemini-1.5-flash';
    }
  } catch (err) {
    console.error('[Gemini Discovery Error]', err.message);
    activeModelName = 'gemini-1.5-flash'; // Last resort
  }
  console.log('===============================\n');
}

/**
 * Generate content with automatic fallback across models
 */
async function generateWithFallback(prompt, history = [], options = {}) {
  let lastError = null;
  
  // Start from active or fallback order
  const modelsToTry = [activeModelName, ...FALLBACK_ORDER].filter((v, i, a) => a.indexOf(v) === i);

  for (const modelName of modelsToTry) {
    try {
      console.log(`[AI] Attempting with model: ${modelName}`);
      const model = genAI.getGenerativeModel({ model: modelName });
      
      let result;
      if (history && history.length > 0) {
        // Chat mode
        const chat = model.startChat({
          history: history,
          generationConfig: { 
            maxOutputTokens: options.maxTokens || 2048, 
            temperature: options.temperature || 0.7 
          }
        });
        result = await chat.sendMessage(prompt);
      } else {
        // Single prompt mode
        result = await model.generateContent(prompt);
      }

      const text = result.response.text();
      if (text) {
        // Update active model if a fallback worked
        if (modelName !== activeModelName) {
          console.log(`[AI] Fallback SUCCESS. Switching primary model to: ${modelName}`);
          activeModelName = modelName;
        }
        return { text, modelUsed: modelName };
      }
    } catch (err) {
      lastError = err;
      console.error(`[AI] Model ${modelName} FAILED: ${err.message}`);
      
      // Don't retry if it's a prompt issue (blocked, etc.)
      if (err.message.includes('Safety') || err.message.includes('blocked')) {
        throw err;
      }
      // Continue to next fallback if it's a 404, 429, 500, etc.
    }
  }

  throw new Error(`AI Request Failed after all fallbacks. Last error: ${lastError?.message}`);
}

function getActiveModelInfo() {
  return {
    activeModel: activeModelName,
    availableModels,
    fallbackOrder: FALLBACK_ORDER
  };
}

function getModel() {
  return genAI.getGenerativeModel({ model: activeModelName || 'gemini-1.5-flash' });
}

module.exports = {
  discoverModels,
  generateWithFallback,
  getActiveModelInfo,
  getModel
};
