package com.notesphere.app.models;

import java.util.List;

/**
 * Response model for /api/ai/quiz
 * The backend returns: { "questions": [ { "question", "options", "correctIndex", "explanation" } ] }
 */
public class QuizResponse {
    private List<QuizQuestion> questions;

    public List<QuizQuestion> getQuestions() { return questions; }
    public void setQuestions(List<QuizQuestion> questions) { this.questions = questions; }
}
