package com.example.aistudyassistant.models;

import java.util.List;

public class ChatRequest {
    private List<ApiMessage> messages;
    private String systemPrompt;

    public ChatRequest(List<ApiMessage> messages) {
        this.messages = messages;
    }

    public ChatRequest(List<ApiMessage> messages, String systemPrompt) {
        this.messages = messages;
        this.systemPrompt = systemPrompt;
    }

    public List<ApiMessage> getMessages() { return messages; }
    public String getSystemPrompt() { return systemPrompt; }
}
