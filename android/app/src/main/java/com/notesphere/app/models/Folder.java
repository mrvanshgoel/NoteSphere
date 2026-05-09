package com.notesphere.app.models;

import com.google.gson.annotations.SerializedName;

public class Folder {
    private String id;
    private String name;
    private String subjectId;
    private String userId;
    private String createdAt;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSubjectId() { return subjectId; }
    public String getUserId() { return userId; }
    public String getCreatedAt() { return createdAt; }
    
    public void setName(String name) { this.name = name; }
}
