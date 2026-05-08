package com.example.aistudyassistant.models;

public class Subject {
    private String id;
    private String name;
    private String icon;
    private String color;
    private String userId;
    private int materialCount;

    public Subject(String id, String name, String icon, String color, String userId, int materialCount) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.userId = userId;
        this.materialCount = materialCount;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public String getUserId() { return userId; }
    public int getMaterialCount() { return materialCount; }
}
