package com.notesphere.app.models;

public class Syllabus {
    private String id;
    private String userId;
    private String subjectId;
    private String title;
    private List<Unit> contentTree;
    private int progressPercentage;
    private String createdAt;

    public static class Unit {
        private String unit;
        private List<Topic> topics;
        public String getUnitName() { return unit; }
        public List<Topic> getTopics() { return topics; }
    }

    public static class Topic {
        private String name;
        private boolean completed;
        public String getName() { return name; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSubjectId() { return subjectId; }
    public String getTitle() { return title; }
    public List<Unit> getContentTree() { return contentTree; }
    public int getProgressPercentage() { return progressPercentage; }

    public void setContentTree(List<Unit> contentTree) { this.contentTree = contentTree; }
    public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }
}
