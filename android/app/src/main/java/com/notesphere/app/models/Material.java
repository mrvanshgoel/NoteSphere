package com.notesphere.app.models;

public class Material {
    private String id;
    private String title;
    private String fileUrl;
    private String fileType;
    private String subjectId;
    private String createdAt;
    private String localPath;
    private String folderId;
    private boolean isFavorite;
    private boolean isPinned;

    public Material(String id, String title, String fileUrl, String fileType, String subjectId, String createdAt) {
        this.id = id;
        this.title = title;
        this.fileUrl = fileUrl;
        this.fileType = fileType;
        this.subjectId = subjectId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getFileUrl() { return fileUrl; }
    public String getFileType() { return fileType; }
    public String getSubjectId() { return subjectId; }
    public String getCreatedAt() { return createdAt; }
    public String getLocalPath() { return localPath; }
    public String getFolderId() { return folderId; }
    public boolean isFavorite() { return isFavorite; }
    public boolean isPinned() { return isPinned; }

    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
    public void setTitle(String title) { this.title = title; }
}
