/**
 * ai_model_manager.js — Model Discovery Bootstrap
 *
 * This module is now a thin compatibility shim.
 * All routing logic lives in ai_router.js (AIRouter).
 *
 * On startup it calls aiRouter.discoverModels() to populate
 * the confirmed-available model set used by routing tables.
 *
 * Legacy exports (generateWithFallback, getActiveModelInfo) are
 * preserved for backward compatibility but delegate to AIRouter.
 */

const { aiRouter } = require('./ai_router');

/**
 * Called once at server startup.
 * Populates aiRouter.availableModels from the live Gemini API.
 */
async function discoverModels() {
  await aiRouter.discoverModels();
}

/**
 * Legacy compatibility: generate with automatic fallback.
 * Delegates to AIRouter using AUTO task.
 *
 * @param {string} prompt
 * @param {Array}  history  — Gemini-format [{role, parts}]
 * @param {Object} options  — { task, maxTokens, temperature, modelMode }
 */
async function generateWithFallback(prompt, history = [], options = {}) {
  const result = await aiRouter.generate(prompt, history, {
    task: options.task || 'AUTO',
    modelMode: options.modelMode || 'auto',
    maxTokens: options.maxTokens,
    temperature: options.temperature,
    estimatedTokens: options.estimatedTokens,
    hasAttachment: options.hasAttachment,
  });
  return result;
}

/**
 * Legacy compatibility: get info about the current active model + router state.
 */
function getActiveModelInfo() {
  const info = aiRouter.getRouterInfo();
  // Return a shape compatible with old callers
  return {
    activeModel:    Array.from(aiRouter.availableModels)[0] || 'gemini-2.0-flash',
    availableModels: info.availableModels,
    fallbackOrder:  info.routingTables.CHAT,
    routerInfo:     info,
  };
}

/**
 * Legacy: get the active Gemini model instance directly.
 * Prefer using aiRouter.generate() instead.
 */
function getModel() {
  return aiRouter.genAI.getGenerativeModel({
    model: Array.from(aiRouter.availableModels)[0] || 'gemini-2.0-flash',
  });
}

module.exports = {
  discoverModels,
  generateWithFallback,
  getActiveModelInfo,
  getModel,
};
