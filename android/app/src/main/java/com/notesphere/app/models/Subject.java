package com.notesphere.app.models;

public class Subject {
    private String id;
    private String name;
    private String icon;
    private String color;
    private String user_id;
    private int material_count;

    public Subject() {}

    public Subject(String id, String name, String icon, String color, String user_id, int material_count) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.user_id = user_id;
        this.material_count = material_count;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public String getUserId() { return user_id; }
    public void setUserId(String user_id) { this.user_id = user_id; }
    
    public int getMaterialCount() { return material_count; }
    public void setMaterialCount(int material_count) { this.material_count = material_count; }
}
