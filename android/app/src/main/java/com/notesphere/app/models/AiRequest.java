package com.notesphere.app.models;

/**
 * Unified AI request body used across all /api/ai/* endpoints.
 * Fields are serialized by Gson — unused fields are ignored by the backend.
 */
public class AiRequest {
    private String materialId;
    private String text;
    private String mode;        // for /api/ai/summarize: "summary"|"notes"|"concepts"|"viva"
    private String subjectName; // for /api/ai/syllabus
    private int count = 10;     // for /api/ai/quiz: number of questions
    private String difficulty = "medium"; // for /api/ai/quiz

    // Constructors
    public AiRequest() {}

    public AiRequest(String materialId) {
        this.materialId = materialId;
    }

    public AiRequest(String materialId, String mode) {
        this.materialId = materialId;
        this.mode = mode;
    }

    // Getters / Setters
    public String getMaterialId() { return materialId; }
    public void setMaterialId(String materialId) { this.materialId = materialId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
}
