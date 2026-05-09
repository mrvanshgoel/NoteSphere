const { GoogleGenerativeAI } = require('@google/generative-ai');

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

/**
 * Get the appropriate Gemini model based on the task type.
 * 'flash': gemini-1.5-flash (fast responses, chat, quiz, short tasks)
 * 'pro': gemini-1.5-pro (long PDFs, syllabus extraction, deep analysis)
 */
function getModel(type = 'flash') {
  const modelName = type === 'pro' ? 'gemini-1.5-pro' : 'gemini-1.5-flash';
  console.log(`[AI] Using model: ${modelName} for task type: ${type}`);
  return genAI.getGenerativeModel({ model: modelName });
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
