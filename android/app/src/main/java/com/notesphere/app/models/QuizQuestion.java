package com.notesphere.app.models;

import java.util.List;

/**
 * Maps to the /api/ai/quiz question object:
 * { "question", "options", "correctIndex", "explanation" }
 */
public class QuizQuestion {
    private String question;
    private List<String> options;
    private int correctIndex;
    private String explanation;

    public QuizQuestion() {}

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }

    public int getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
