package com.notesphere.app.models;

import com.google.gson.annotations.SerializedName;

public class Folder {
    private String id;
    private String name;
    private String subjectId;
    private String userId;
    private String createdAt;
    private String parentId;
    private boolean isFavorite;
    private boolean isPinned;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSubjectId() { return subjectId; }
    public String getUserId() { return userId; }
    public String getCreatedAt() { return createdAt; }
    public String getParentId() { return parentId; }
    
    public void setName(String name) { this.name = name; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public boolean isFavorite() { return isFavorite; }
    public boolean isPinned() { return isPinned; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
}
