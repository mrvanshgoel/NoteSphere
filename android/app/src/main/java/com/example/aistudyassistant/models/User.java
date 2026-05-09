package com.example.aistudyassistant.models;

public class User {
    private String token;
    private UserInfo user;

    public static class UserInfo {
        private String id;
        private String name;
        private String email;
        private String avatar_url;

        public UserInfo() {}

        public UserInfo(String name, String avatar_url) {
            this.name = name;
            this.avatar_url = avatar_url;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getAvatarUrl() { return avatar_url; }

        public void setName(String name) { this.name = name; }
        public void setAvatarUrl(String avatar_url) { this.avatar_url = avatar_url; }
    }

    public String getToken() { return token; }
    public UserInfo getUser() { return user; }
    
    // Compatibility helpers for legacy code
    public String getName() { return user != null ? user.getName() : "Student"; }
    public String getEmail() { return user != null ? user.getEmail() : ""; }
    public String getAvatarUrl() { return user != null ? user.getAvatarUrl() : null; }
    public static class AvatarResponse {
        private String avatar_url;
        public String getAvatarUrl() { return avatar_url; }
    }
}
