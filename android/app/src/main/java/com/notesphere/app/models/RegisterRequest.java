package com.notesphere.app.models;

public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private String uid;

    public RegisterRequest(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
