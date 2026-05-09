package com.notesphere.app.models;

public class ApiMessage {
    private String role;
    private String content;

    public ApiMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public String getContent() { return content; }
}
