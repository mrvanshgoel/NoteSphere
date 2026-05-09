package com.example.aistudyassistant.models;

public class ShareResponse {
    private String shareUrl;
    private String shareCode;
    private String name;
    private String expiresIn;

    public String getShareUrl() {
        return shareUrl;
    }

    public String getShareCode() {
        return shareCode;
    }

    public String getName() {
        return name;
    }

    public String getExpiresIn() {
        return expiresIn;
    }
}
