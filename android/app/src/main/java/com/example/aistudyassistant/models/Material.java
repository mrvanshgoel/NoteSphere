package com.example.aistudyassistant.models;

public class Material {
    private String id;
    private String title;
    private String fileUrl;
    private String fileType;
    private String subjectId;
    private String createdAt;

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
}
