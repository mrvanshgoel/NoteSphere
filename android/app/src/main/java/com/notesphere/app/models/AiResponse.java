package com.notesphere.app.models;

public class AiResponse {
    private String type;
    private String content;
    private String materialId;
    private String model;
    private String chatId;

    public AiResponse(String type, String content, String materialId) {
        this.type = type;
        this.content = content;
        this.materialId = materialId;
    }

    public String getType() { return type; }
    public String getContent() { return content; }
    public String getMaterialId() { return materialId; }
    public String getModel() { return model; }
    public String getChatId() { return chatId; }
}
