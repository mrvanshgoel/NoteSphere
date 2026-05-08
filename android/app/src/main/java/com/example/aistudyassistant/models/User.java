package com.example.aistudyassistant.models;

public class User {
    private String id;
    private String name;
    private String email;
    private String token;

    public User(String id, String name, String email, String token) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.token = token;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getToken() { return token; }
}
