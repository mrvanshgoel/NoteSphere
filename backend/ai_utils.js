
const pdfParse = require('pdf-parse');
const { GoogleGenerativeAI } = require('@google/generative-ai');
const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY);

/**
 * Extracts text from a file buffer (currently supports PDF).
 * Can be extended for PPT/DOCX later.
 */
async function extractTextFromBuffer(buffer, mimeType) {
    if (mimeType === 'application/pdf') {
        try {
            const data = await pdfParse(buffer);
            return data.text;
        } catch (error) {
            console.error("PDF Extraction Error:", error);
            throw new Error("Failed to extract text from PDF");
        }
    } else if (mimeType.startsWith('text/')) {
        return buffer.toString('utf-8');
    }
    // Fallback or throw error for unsupported types
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
