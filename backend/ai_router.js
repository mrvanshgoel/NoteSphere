/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           NOTESPHERE AI ORCHESTRATION LAYER                  ║
 * ║           ai_router.js — Centralized AI Router               ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * ALL AI requests pass through this module.
 * Responsibilities:
 *  • Task-type-based model routing (CHAT / DOCUMENT / OCR / AUTO)
 *  • Per-model health tracking (latency, failures, demotion)
 *  • Automatic fallback chaining across models
 *  • Recovery of demoted models after cooldown
 *  • Structured observability logs for every request
 *  • User-facing mode mapping (auto / fast / balanced / deep / document / experimental)
 */

const { GoogleGenerativeAI } = require('@google/generative-ai');
const dotenv = require('dotenv');
dotenv.config();

// ─────────────────────────────────────────────────────────────
// CONSTANTS
// ─────────────────────────────────────────────────────────────

const DEMOTION_THRESHOLD   = 3;     // consecutive failures before demotion
const RECOVERY_COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes
const DEFAULT_TIMEOUT_MS   = 30000; // 30s per model attempt

/**
 * Task routing tables.
 * Priority order: first confirmed-available + healthy model is chosen.
 *
 * IMPORTANT: Model IDs here are candidates — the discovery system filters
 * them against the live API on startup. Only confirmed models are used.
 * Never add speculative or deprecated model names here.
 *
 * Confirmed working as of 2025-06 (from /v1beta/models discovery):
 *   gemini-2.5-pro-preview-*   — deep reasoning, long context
 *   gemini-2.5-flash-preview-* — fast, capable, good for most tasks
 *   gemini-2.0-flash-lite      — deprecated, removed
 *   gemini-2.0-flash           — deprecated, removed
 */
const ROUTING_TABLES = {
  /**
   * CHAT — conversational AI, Q&A, doubt solving, follow-ups
   * Goal: ultra-fast, low-latency, good comprehension
   */
  CHAT: [
    'gemini-3.1-flash-lite',
    'gemini-3-flash-preview',
    'gemini-2.5-flash',
    'gemini-flash-latest',
  ],

  /**
   * DOCUMENT — PDF summaries, quiz gen, flashcards, concept extraction
   * Goal: deep reasoning, long context, structured JSON output
   */
  DOCUMENT: [
    'gemini-3.1-pro-preview',
    'gemini-3-pro-preview',
    'gemini-2.5-flash',
  ],

  /**
   * OCR — handwritten notes, diagrams, scanned images, screenshots
   * Goal: vision-capable model with high OCR accuracy
   * Resolved dynamically from discovered models with vision support
   */
  OCR: [
    'gemini-3.1-flash-image-preview',
    'gemini-2.5-flash-image',
  ],

  /**
   * AUTO — backend decides based on context signals (token count, attachments)
   * Resolved dynamically in _selectCandidates()
   */
  AUTO: [],
};

/**
 * User-facing mode → internal task mapping.
 * REMOVED all forceModel overrides — they bypass discovery and risk sending
 * requests to deprecated models. Let the routing table + health system decide.
 */
const MODE_MAP = {
  auto:         { task: 'AUTO'     },
  fast:         { task: 'CHAT'     },
  balanced:     { task: 'CHAT'     },   // was: forceModel: 'gemini-2.0-flash' (DEPRECATED — removed)
  deep:         { task: 'DOCUMENT' },
  document:     { task: 'DOCUMENT' },
  experimental: { task: 'DOCUMENT' },
};


// ─────────────────────────────────────────────────────────────
// AI ROUTER CLASS
// ─────────────────────────────────────────────────────────────

class AIRouter {
  constructor() {
    this.genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

    /**
     * Health registry: modelId → { failures, consecutiveFailures, lastFailure, totalLatency, callCount, demoted }
     */
    this.health = {};

    /**
     * Confirmed-available model IDs (populated by discoverModels()).
     * If empty (e.g. discovery failed), routing uses all table entries as candidates.
     */
    this.availableModels = new Set();

    this._initHealthRegistry();
  }

  /** Pre-populate health entries for all known model IDs. */
  _initHealthRegistry() {
    const allModels = new Set([
      ...ROUTING_TABLES.CHAT,
      ...ROUTING_TABLES.DOCUMENT,
      ...ROUTING_TABLES.OCR,
    ]);
    for (const id of allModels) {
      this.health[id] = {
        failures:            0,
        consecutiveFailures: 0,
        lastFailure:         null,
        totalLatency:        0,
        callCount:           0,
        demoted:             false,
      };
    }
  }

  // ───────────────────────────────────────────────────────────
  // MODEL DISCOVERY
  // ───────────────────────────────────────────────────────────

  /**
   * Discover which model IDs are live on the API.
   * Called once on server startup.
   */
  async discoverModels() {
    console.log('\n╔═══════════════════════════════════╗');
    console.log('║    NOTESPHERE AI ROUTER BOOT      ║');
    console.log('╚═══════════════════════════════════╝');

    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      console.warn('[AI Router] GEMINI_API_KEY not set — using all routing table entries as candidates.');
      return;
    }

    try {
      const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey}`;
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();

      const discovered = (data.models || [])
        .filter(m => m.supportedGenerationMethods?.includes('generateContent'))
        .filter(m => !m.name.toLowerCase().includes('embedding'))
        .filter(m => !m.name.toLowerCase().includes('aqa'))
        .map(m => m.name.replace('models/', ''));

      this.availableModels = new Set(discovered);

      console.log(`[AI Router] Discovered ${discovered.length} generateContent-capable models:`);
      discovered.forEach(id => console.log(`  ✓ ${id}`));

      // Log which routing table entries are confirmed available
      for (const [task, table] of Object.entries(ROUTING_TABLES)) {
        if (task === 'AUTO') continue;
        const available = table.filter(id => this.availableModels.has(id));
        console.log(`[AI Router] ${task} chain: [${available.join(' → ')}]`);
      }
    } catch (err) {
      console.warn(`[AI Router] Discovery failed (${err.message}) — using routing tables without availability filter.`);
    }

    console.log('═══════════════════════════════════════\n');
  }

  // ───────────────────────────────────────────────────────────
  // HEALTH TRACKING
  // ───────────────────────────────────────────────────────────

  _recordSuccess(modelId, latencyMs) {
    const h = this._getHealth(modelId);
    h.consecutiveFailures = 0;
    h.totalLatency        += latencyMs;
    h.callCount           += 1;
    h.demoted             = false; // auto-recover on success
  }

  _recordFailure(modelId, reason) {
    const h = this._getHealth(modelId);
    h.failures            += 1;
    h.consecutiveFailures += 1;
    h.lastFailure         = Date.now();

    if (h.consecutiveFailures >= DEMOTION_THRESHOLD && !h.demoted) {
      h.demoted = true;
      console.warn(`[AI Router] ⚠ Model DEMOTED after ${h.consecutiveFailures} consecutive failures: ${modelId}`);
    }
  }

  _getHealth(modelId) {
    if (!this.health[modelId]) {
      this.health[modelId] = {
        failures: 0, consecutiveFailures: 0, lastFailure: null,
        totalLatency: 0, callCount: 0, demoted: false,
      };
    }
    return this.health[modelId];
  }

  _isHealthy(modelId) {
    const h = this._getHealth(modelId);
    if (!h.demoted) return true;

    // Auto-recovery after cooldown
    if (h.lastFailure && Date.now() - h.lastFailure > RECOVERY_COOLDOWN_MS) {
      h.demoted             = false;
      h.consecutiveFailures = 0;
      console.log(`[AI Router] ✓ Model RECOVERED from demotion: ${modelId}`);
      return true;
    }
    return false;
  }

  _avgLatency(modelId) {
    const h = this._getHealth(modelId);
    return h.callCount > 0 ? Math.round(h.totalLatency / h.callCount) : 0;
  }

  // ───────────────────────────────────────────────────────────
  // MODEL SELECTION
  // ───────────────────────────────────────────────────────────

  /**
   * Resolve the ordered candidate list for a given task type and options.
   */
  _selectCandidates(task, options = {}) {
    // If forceModel is set (e.g. balanced mode), try it first
    if (options.forceModel) {
      const rest = (ROUTING_TABLES[task] || ROUTING_TABLES.CHAT)
        .filter(id => id !== options.forceModel);
      return [options.forceModel, ...rest];
    }

    if (task === 'AUTO') {
      // Auto mode: choose task type based on signals
      const estimatedTokens = options.estimatedTokens || 0;
      const hasAttachment   = options.hasAttachment   || false;
      const isOcr           = options.isOcr           || false;

      if (isOcr) return ROUTING_TABLES.OCR;
      if (hasAttachment || estimatedTokens > 3000) return ROUTING_TABLES.DOCUMENT;
      return ROUTING_TABLES.CHAT;
    }

    return ROUTING_TABLES[task] || ROUTING_TABLES.CHAT;
  }

  /**
   * Filter candidates to healthy + (optionally) confirmed-available models.
   * Always returns at least the raw candidate list as fallback so we never return empty.
   */
  _filterCandidates(candidates) {
    const healthy = candidates.filter(id => this._isHealthy(id));

    if (this.availableModels.size > 0) {
      const confirmed = healthy.filter(id => this.availableModels.has(id));
      if (confirmed.length > 0) return confirmed;
    }

    if (healthy.length > 0) return healthy;

    // Strict exclusion: do not fallback to dead models
    throw new Error('No healthy/available models found for this task. Please try again later.');
  }

  // ───────────────────────────────────────────────────────────
  // CORE GENERATE METHOD
  // ───────────────────────────────────────────────────────────

  /**
   * Generate AI content, routing by task type with full fallback + health tracking.
   *
   * @param {string} prompt         — The full prompt string
   * @param {Array}  history        — Gemini-format chat history [{role, parts}]
   * @param {Object} options        — { task, modelMode, maxTokens, temperature, estimatedTokens, hasAttachment, isOcr, forceModel }
   * @returns {{ text: string, modelUsed: string, latencyMs: number, fallbacksUsed: number, task: string }}
   */
  async generate(prompt, history = [], options = {}) {
    const startTime  = Date.now();
    const task       = options.task || 'AUTO';
    const modelMode  = options.modelMode || 'auto';
    const maxTokens  = options.maxTokens  || 2048;
    const temperature = options.temperature || 0.7;

    // Resolve effective task + override from user mode
    let effectiveTask    = task;
    let effectiveOptions = { ...options };
    if (MODE_MAP[modelMode]) {
      effectiveTask    = MODE_MAP[modelMode].task;
      if (MODE_MAP[modelMode].forceModel) {
        effectiveOptions.forceModel = MODE_MAP[modelMode].forceModel;
      }
    }

    const rawCandidates = this._selectCandidates(effectiveTask, effectiveOptions);
    const candidates    = this._filterCandidates(rawCandidates);

    let lastError     = null;
    let fallbacksUsed = 0;

    for (const modelId of candidates) {
      const attemptStart = Date.now();
      try {
        console.log(`[AI Router] Task: ${effectiveTask} | Mode: ${modelMode} | Trying: ${modelId}${fallbacksUsed > 0 ? ` (fallback #${fallbacksUsed})` : ''}`);

        const model = this.genAI.getGenerativeModel({ model: modelId });

        // Wrap in a timeout race
        const text = await this._callWithTimeout(
          model, prompt, history, { maxTokens, temperature }, DEFAULT_TIMEOUT_MS
        );

        if (!text || text.trim().length === 0) {
          throw new Error('Empty response from model');
        }

        const latencyMs = Date.now() - attemptStart;
        this._recordSuccess(modelId, latencyMs);

        const totalLatency = Date.now() - startTime;
        console.log(
          `[AI Router] Task: ${effectiveTask} | Mode: ${modelMode} | Model: ${modelId} | ` +
          `Latency: ${latencyMs}ms | Total: ${totalLatency}ms | Status: SUCCESS | Fallbacks: ${fallbacksUsed}`
        );

        return { text, modelUsed: modelId, latencyMs: totalLatency, fallbacksUsed, task: effectiveTask };

      } catch (err) {
        const latencyMs = Date.now() - attemptStart;
        lastError = err;

        // Safety/content blocks — do not retry, propagate immediately
        if (err.message?.includes('SAFETY') || err.message?.includes('blocked') || err.message?.includes('recitation')) {
          console.error(`[AI Router] Safety block on ${modelId} — not retrying.`);
          throw err;
        }

        this._recordFailure(modelId, err.message);
        console.warn(`[AI Router] ✗ ${modelId} FAILED (${latencyMs}ms): ${err.message.substring(0, 120)}`);

        if (candidates.indexOf(modelId) < candidates.length - 1) {
          fallbacksUsed++;
        }
      }
    }

    const totalLatency = Date.now() - startTime;
    console.error(
      `[AI Router] ALL MODELS FAILED | Task: ${effectiveTask} | Tried: ${candidates.length} models | ` +
      `Total: ${totalLatency}ms | Last error: ${lastError?.message}`
    );

    throw new Error(`AI request failed after ${candidates.length} model attempts. Last error: ${lastError?.message}`);
  }

  // ───────────────────────────────────────────────────────────
  // INTERNAL: CALL WITH TIMEOUT
  // ───────────────────────────────────────────────────────────

  async _callWithTimeout(model, prompt, history, genConfig, timeoutMs) {
    const aiCall = async () => {
      if (history && history.length > 0) {
        const chat = model.startChat({
          history,
          generationConfig: {
            maxOutputTokens: genConfig.maxTokens,
            temperature:     genConfig.temperature,
          },
        });
        const result = await chat.sendMessage(prompt);
        return result.response.text();
      } else {
        const result = await model.generateContent({
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig: {
            maxOutputTokens: genConfig.maxTokens,
            temperature:     genConfig.temperature,
          },
        });
        return result.response.text();
      }
    };

    return Promise.race([
      aiCall(),
      new Promise((_, reject) =>
        setTimeout(() => reject(new Error(`Model timeout after ${timeoutMs}ms`)), timeoutMs)
      ),
    ]);
  }

  // ───────────────────────────────────────────────────────────
  // VISION / OCR (Multimodal)
  // ───────────────────────────────────────────────────────────

  /**
   * Generate content with an inline file attachment (PDF, image).
   * Uses OCR routing table.
   */
  async generateWithFile(prompt, fileBuffer, mimeType, options = {}) {
    const startTime  = Date.now();
    const candidates = this._filterCandidates(ROUTING_TABLES.OCR);
    let   lastError  = null;
    let   fallbacksUsed = 0;

    for (const modelId of candidates) {
      const attemptStart = Date.now();
      try {
        console.log(`[AI Router] Task: OCR | MimeType: ${mimeType} | Trying: ${modelId}`);

        const model   = this.genAI.getGenerativeModel({ model: modelId });
        const base64  = fileBuffer.toString('base64');
        const result  = await Promise.race([
          model.generateContent([prompt, { inlineData: { data: base64, mimeType } }]),
          new Promise((_, reject) =>
            setTimeout(() => reject(new Error(`OCR timeout on ${modelId}`)), DEFAULT_TIMEOUT_MS)
          ),
        ]);

        const text      = result.response.text();
        const latencyMs = Date.now() - attemptStart;

        if (!text || text.trim().length < 5) throw new Error('Empty OCR response');

        this._recordSuccess(modelId, latencyMs);
        console.log(`[AI Router] Task: OCR | Model: ${modelId} | Latency: ${latencyMs}ms | Status: SUCCESS`);

        return { text, modelUsed: modelId, latencyMs: Date.now() - startTime, fallbacksUsed };

      } catch (err) {
        lastError = err;
        this._recordFailure(modelId, err.message);
        console.warn(`[AI Router] ✗ OCR ${modelId} FAILED: ${err.message.substring(0, 120)}`);
        fallbacksUsed++;
      }
    }

    throw new Error(`OCR failed on all models. Last: ${lastError?.message}`);
  }

  // ───────────────────────────────────────────────────────────
  // OBSERVABILITY / DEBUG
  // ───────────────────────────────────────────────────────────

  getHealthReport() {
    const report = {};
    for (const [id, h] of Object.entries(this.health)) {
      report[id] = {
        demoted:             h.demoted,
        failures:            h.failures,
        consecutiveFailures: h.consecutiveFailures,
        avgLatencyMs:        this._avgLatency(id),
        callCount:           h.callCount,
        lastFailure:         h.lastFailure ? new Date(h.lastFailure).toISOString() : null,
      };
    }
    return report;
  }

  getRouterInfo() {
    return {
      availableModels:  Array.from(this.availableModels),
      routingTables:    ROUTING_TABLES,
      modeMap:          MODE_MAP,
      healthReport:     this.getHealthReport(),
      demotionThreshold: DEMOTION_THRESHOLD,
      recoveryCooldownMs: RECOVERY_COOLDOWN_MS,
    };
  }
}

// Singleton instance
const aiRouter = new AIRouter();

module.exports = { aiRouter };
