package com.example.aistudyassistant.models;

public class ShareRequest {
    private String materialId;

    public ShareRequest(String materialId) {
        this.materialId = materialId;
    }

    public String getMaterialId() {
        return materialId;
    }
}
