package com.notesphere.app.models;

import java.util.List;

public class ChatRequest {
    private String message;
    private List<ApiMessage> history;

    public ChatRequest(String message, List<ApiMessage> history) {
        this.message = message;
        this.history = history;
    }

    public String getMessage() { return message; }
    public List<ApiMessage> getHistory() { return history; }
}
