package com.notesphere.app.models;

import com.google.gson.annotations.SerializedName;

public class ChatMessage {
    @SerializedName("content")
    private String message;
    
    @SerializedName("role")
    private String role;
    
    private boolean isUser;

    public ChatMessage(String message, boolean isUser) {
        this.message = message;
        this.isUser = isUser;
        this.role = isUser ? "user" : "assistant";
    }

    public String getMessage() { 
        return message; 
    }
    
    public boolean isUser() { 
        if (role != null) {
            return "user".equals(role);
        }
        return isUser; 
    }
    
    public String getRole() { return role; }
    public String getContent() { return message; }
}
