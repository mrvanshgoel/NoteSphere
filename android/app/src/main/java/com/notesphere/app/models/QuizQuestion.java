package com.notesphere.app.models;

import java.util.List;

public class QuizQuestion {
    private String question;
    private List<String> options;
    private int correctAnswerIndex;
    private String explanation;

    public String getQuestion() { return question; }
    public List<String> getOptions() { return options; }
    public int getCorrectAnswerIndex() { return correctAnswerIndex; }
    public String getExplanation() { return explanation; }
}
