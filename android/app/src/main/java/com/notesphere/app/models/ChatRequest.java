package com.notesphere.app.models;

import java.util.List;

public class ChatRequest {
    private String message;
    private List<ApiMessage> history;
    private String chatId;
    private String materialId;

    public ChatRequest(String message, List<ApiMessage> history) {
        this.message = message;
        this.history = history;
    }

    public String getMessage() { return message; }
    public List<ApiMessage> getHistory() { return history; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getMaterialId() { return materialId; }
    public void setMaterialId(String materialId) { this.materialId = materialId; }
}
