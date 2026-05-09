
const pdfParse = require('pdf-parse');
const { getModel, generateWithFallback } = require('./ai_model_manager');

/**
 * Extracts text from a file buffer (supports PDF, Images, Text).
 */
async function extractTextFromBuffer(buffer, mimeType) {
    if (mimeType === 'application/pdf') {
        try {
            console.log(`[AI] Extracting text from PDF using Gemini`);
            const base64Pdf = buffer.toString('base64');
            const prompt = "Extract all text from this PDF document as accurately as possible. If it's a study material, maintain the structure and hierarchy. Return ONLY the extracted text.";
            
            const model = getModel(); 
            const result = await model.generateContent([
                prompt,
                { inlineData: { data: base64Pdf, mimeType: 'application/pdf' } }
            ]);
            
            const text = result.response.text();
            if (!text || text.length < 10) {
                // Fallback to pdf-parse ONLY if Gemini returns nothing (rare)
                console.warn("[AI] Gemini extraction yielded little text, attempting pdf-parse fallback");
                const parse = typeof pdfParse === 'function' ? pdfParse : pdfParse.default;
                if (typeof parse === 'function') {
                    const data = await parse(buffer);
                    return data.text;
                }
            }
            return text;
        } catch (error) {
            console.error("PDF Extraction Error:", error.message);
            // Emergency fallback to pdf-parse if Gemini fails (e.g. quota)
            try {
                const parse = typeof pdfParse === 'function' ? pdfParse : pdfParse.default;
                if (typeof parse === 'function') {
                    const data = await parse(buffer);
                    return data.text;
                }
            } catch (e) {
                console.error("Critical: PDF fallback also failed:", e.message);
            }
            throw new Error("Failed to extract text from PDF: " + error.message);
        }
    } else if (mimeType.startsWith('image/')) {
        try {
            console.log(`[OCR] Extracting text from image: ${mimeType}`);
            // For images, we use Gemini's vision capabilities
            const base64Image = buffer.toString('base64');
            const prompt = "Extract all text from this image as accurately as possible. If it's a study material, maintain the structure. Return ONLY the extracted text.";
            
            const model = getModel(); // get the active model (must be a vision capable model like flash)
            const result = await model.generateContent([
                prompt,
                { inlineData: { data: base64Image, mimeType } }
            ]);
            
            return result.response.text();
        } catch (error) {
            console.error("Image OCR Error:", error.message);
            throw new Error("Failed to extract text from image: " + error.message);
        }
    } else if (mimeType.startsWith('text/')) {
        return buffer.toString('utf-8');
    }
    
    throw new Error(`Text extraction not supported for ${mimeType}`);
}

/**
 * Splits text into overlapping chunks.
 */
function chunkText(text, maxChunkSize = 1000, overlap = 200) {
    if (!text || text.trim().length === 0) return [];

    // Clean text: remove excessive newlines and spaces
    const cleanText = text.replace(/\s+/g, ' ').trim();

    const chunks = [];
    let startIndex = 0;

    while (startIndex < cleanText.length) {
        let endIndex = startIndex + maxChunkSize;

        // Don't cut off words - find nearest space
        if (endIndex < cleanText.length) {
            const lastSpaceIndex = cleanText.lastIndexOf(' ', endIndex);
            if (lastSpaceIndex > startIndex + overlap) { // ensure we make progress
                endIndex = lastSpaceIndex;
            }
        }

        chunks.push(cleanText.substring(startIndex, endIndex).trim());

        // Move start index, but overlap with previous chunk
        startIndex = endIndex - overlap;
    }

    return chunks;
}

/**
 * Generates an embedding for a piece of text using Gemini's text-embedding-004 model.
 */
async function generateEmbedding(text) {
    try {
        const model = genAI.getGenerativeModel({ model: "text-embedding-004" });
        const result = await model.embedContent(text);
        return result.embedding.values;
    } catch (error) {
        console.error("Embedding Error:", error);
        throw new Error("Failed to generate embedding");
    }
}

module.exports = {
    extractTextFromBuffer,
    chunkText,
    generateEmbedding
};
