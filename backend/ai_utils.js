/**
 * ai_utils.js — File Extraction & Text Utilities
 *
 * Uses aiRouter.generateWithFile() for multimodal extraction (PDF, Images)
 * so all calls benefit from OCR routing table, health tracking, and fallbacks.
 */

const pdfParse = require('pdf-parse');
const { aiRouter } = require('./ai_router');

// ─────────────────────────────────────────────────────────────
// TEXT EXTRACTION
// ─────────────────────────────────────────────────────────────

/**
 * Extracts text from a file buffer.
 * Supports PDF (Gemini vision + pdf-parse fallback), Images (OCR), plain text.
 *
 * @param {Buffer} buffer
 * @param {string} mimeType
 * @returns {Promise<string>}
 */
async function extractTextFromBuffer(buffer, mimeType) {
  // ── PDF ──────────────────────────────────────────────────
  if (mimeType === 'application/pdf') {
    try {
      console.log('[AI Utils] Extracting text from PDF via Gemini OCR (OCR task)');
      const prompt = 'Extract all text from this PDF document as accurately as possible. Maintain the structure and hierarchy of the study material. Return ONLY the extracted text, no commentary.';
      const result = await aiRouter.generateWithFile(prompt, buffer, 'application/pdf');
      const text = result.text;

      if (!text || text.trim().length < 10) {
        throw new Error('Gemini PDF extraction yielded insufficient text');
      }

      console.log(`[AI Utils] PDF extraction complete. Model: ${result.modelUsed} | Chars: ${text.length}`);
      return text;

    } catch (error) {
      console.warn('[AI Utils] Gemini PDF extraction failed, attempting pdf-parse fallback:', error.message);
      try {
        const parse = typeof pdfParse === 'function' ? pdfParse : pdfParse.default;
        if (typeof parse === 'function') {
          const data = await parse(buffer);
          if (data.text && data.text.trim().length > 10) {
            console.log('[AI Utils] pdf-parse fallback succeeded.');
            return data.text;
          }
        }
      } catch (fallbackErr) {
        console.error('[AI Utils] Critical: pdf-parse fallback also failed:', fallbackErr.message);
      }
      throw new Error('Failed to extract text from PDF: ' + error.message);
    }
  }

  // ── IMAGES ───────────────────────────────────────────────
  if (mimeType.startsWith('image/')) {
    try {
      console.log(`[AI Utils] Running OCR on image: ${mimeType}`);
      const prompt = 'Extract all text from this image as accurately as possible. If it is a study material (notes, diagram labels, equations), maintain its structure. Return ONLY the extracted text.';
      const result = await aiRouter.generateWithFile(prompt, buffer, mimeType);

      console.log(`[AI Utils] Image OCR complete. Model: ${result.modelUsed} | Chars: ${result.text.length}`);
      return result.text;
    } catch (error) {
      console.error('[AI Utils] Image OCR failed:', error.message);
      throw new Error('Failed to extract text from image: ' + error.message);
    }
  }

  // ── PLAIN TEXT ───────────────────────────────────────────
  if (mimeType.startsWith('text/')) {
    return buffer.toString('utf-8');
  }

  throw new Error(`Text extraction not supported for MIME type: ${mimeType}`);
}

// ─────────────────────────────────────────────────────────────
// TEXT CHUNKING
// ─────────────────────────────────────────────────────────────

/**
 * Split text into overlapping chunks for RAG / context windows.
 *
 * @param {string} text
 * @param {number} maxChunkSize  — chars per chunk (default 1000)
 * @param {number} overlap       — overlap chars between chunks (default 200)
 * @returns {string[]}
 */
function chunkText(text, maxChunkSize = 1000, overlap = 200) {
  if (!text || text.trim().length === 0) return [];

  const cleanText = text.replace(/\s+/g, ' ').trim();
  const chunks = [];
  let startIndex = 0;

  while (startIndex < cleanText.length) {
    let endIndex = startIndex + maxChunkSize;

    if (endIndex < cleanText.length) {
      const lastSpace = cleanText.lastIndexOf(' ', endIndex);
      if (lastSpace > startIndex + overlap) endIndex = lastSpace;
    }

    chunks.push(cleanText.substring(startIndex, endIndex).trim());
    startIndex = endIndex - overlap;
  }

  return chunks;
}

// ─────────────────────────────────────────────────────────────
// TOKEN ESTIMATION
// ─────────────────────────────────────────────────────────────

/**
 * Rough token estimate from a string (1 token ≈ 4 chars).
 * Used by AIRouter AUTO mode to choose DOCUMENT vs CHAT routing.
 *
 * @param {string} text
 * @returns {number}
 */
function estimateTokens(text) {
  if (!text) return 0;
  return Math.ceil(text.length / 4);
}

module.exports = {
  extractTextFromBuffer,
  chunkText,
  estimateTokens,
};
