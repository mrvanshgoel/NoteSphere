const { GoogleGenerativeAI } = require('@google/generative-ai');

const { GEMINI_MODEL } = require('./ai_config');
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

/**
 * Get the standardized Gemini model.
 * Standardized to: gemini-flash
 */
function getModel() {
  console.log(`[AI] Initializing model: ${GEMINI_MODEL}`);
  return genAI.getGenerativeModel({ model: GEMINI_MODEL });
}

/**
 * Timeout wrapper for AI calls.
 */
async function callAIWithTimeout(fn, timeoutMs = 30000) {
  return Promise.race([
    fn(),
    new Promise((_, reject) =>
      setTimeout(() => reject(new Error('AI request timed out')), timeoutMs)
    )
  ]);
}

module.exports = {
  getModel,
  callAIWithTimeout
};
