package com.notesphere.app.models;

public class DoubtRequest {
    private String materialId;
    private String question;

    public DoubtRequest(String materialId, String question) {
        this.materialId = materialId;
        this.question = question;
    }
}
