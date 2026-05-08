package com.example.aistudyassistant.models;

public class User {
    private String id;
    private String email;
    private UserData user;
    private Session session;

    public static class UserData {
        private String id;
        private String email;
        private UserMetadata user_metadata;

        public String getId() { return id; }
        public String getEmail() { return email; }
        public UserMetadata getUserMetadata() { return user_metadata; }
    }

    public static class UserMetadata {
        private String full_name;
        private String avatar_url;

        public String getFullName() { return full_name; }
        public String getAvatarUrl() { return avatar_url; }
    }

    public static class Session {
        private String access_token;
        public String getAccessToken() { return access_token; }
    }

    public UserData getUser() { return user; }
    public Session getSession() { return session; }
    
    // Legacy support for LoginActivity
    public String getToken() { return session != null ? session.getAccessToken() : null; }
    public String getName() { 
        if (user != null && user.getUserMetadata() != null) {
            return user.getUserMetadata().getFullName();
        }
        return "Student";
    }
    public String getEmail() { return user != null ? user.getEmail() : email; }
}
