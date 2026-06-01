package com.notesphere.app.api;

import com.notesphere.app.models.AiRequest;
import com.notesphere.app.models.AiResponse;
import com.notesphere.app.models.ChatRequest;
import com.notesphere.app.models.LoginRequest;
import com.notesphere.app.models.Material;
import com.notesphere.app.models.Note;
import com.notesphere.app.models.QuizResponse;
import com.notesphere.app.models.RegisterRequest;
import com.notesphere.app.models.Subject;
import com.notesphere.app.models.User;
import com.notesphere.app.models.ShareRequest;
import com.notesphere.app.models.ShareResponse;
import com.notesphere.app.models.Syllabus;
import com.notesphere.app.models.ChatSession;
import com.notesphere.app.models.DoubtRequest;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface ApiService {

    // ─── Auth ────────────────────────────────────────────────────────────
    @POST("api/auth/login")
    Call<User> login(@Body LoginRequest body);

    @POST("api/auth/register")
    Call<User> register(@Body RegisterRequest body);

    @GET("api/auth/profile")
    Call<User.UserInfo> getProfile();

    @PUT("api/auth/profile")
    Call<User.UserInfo> updateProfile(@Body User.UserInfo profile);

    @Multipart
    @POST("api/auth/upload-avatar")
    Call<User.AvatarResponse> uploadAvatar(
            @Part MultipartBody.Part avatar
    );

    @DELETE("api/auth/avatar")
    Call<Void> deleteAvatar();

    // ─── Subjects ────────────────────────────────────────────────────────
    @GET("api/subjects")
    Call<List<Subject>> getSubjects();

    @POST("api/subjects")
    Call<Subject> createSubject(@Body Subject subject);

    @DELETE("api/subjects/{id}")
    Call<Void> deleteSubject(@Path("id") String id);

    // ─── Folders ────────────────────────────────────────────────────────
    @GET("api/folders/{subjectId}")
    Call<List<com.notesphere.app.models.Folder>> getFolders(@Path("subjectId") String subjectId);

    @POST("api/folders")
    Call<com.notesphere.app.models.Folder> createFolder(@Body com.notesphere.app.models.Folder folder);

    @DELETE("api/folders/{id}")
    Call<Void> deleteFolder(@Path("id") String id);

    // ─── Materials (Extended) ──────────────────────────────────────────
    @Multipart
    @POST("api/materials/upload")
    Call<Material> uploadMaterialWithFolder(
            @Part("subjectId") RequestBody subjectId,
            @Part("folderId") RequestBody folderId,
            @Part("name") RequestBody name,
            @Part MultipartBody.Part file
    );

    @Multipart
    @POST("api/materials/upload")
    Call<Material> uploadMaterial(
            @Part("subjectId") RequestBody subjectId,
            @Part("name") RequestBody name,
            @Part MultipartBody.Part file
    );

    @GET("api/materials/{subjectId}")
    Call<List<Material>> getMaterials(
            @Path("subjectId") String subjectId,
            @retrofit2.http.Query("folderId") String folderId
    );

    @PUT("api/materials/{id}")
    Call<Void> updateMaterial(@Path("id") String id, @Body Material material);

    @DELETE("api/materials/{id}")
    Call<Void> deleteMaterial(@Path("id") String id);

    // ─── Subjects (Extended) ───────────────────────────────────────────
    @PUT("api/subjects/{id}")
    Call<Void> updateSubject(@Path("id") String id, @Body Subject subject);

    // ─── AI: Chat ────────────────────────────────────────────────────────
    @POST("api/ai/chat")
    Call<AiResponse> chat(@Body ChatRequest body);

    @GET("api/ai/chats")
    Call<List<ChatSession>> getChatSessions();

    @POST("api/ai/chats")
    Call<ChatSession> createChatSession(@Body ChatSession body);

    @PUT("api/ai/chats/{id}")
    Call<Void> updateChatSession(@Path("id") String id, @Body ChatSession body);

    @DELETE("api/ai/chats/{chatId}")
    Call<Void> deleteChatSession(@Path("chatId") String chatId);

    // ─── AI: Summarize (multi-mode: summary/notes/concepts/viva) ─────────
    @POST("api/ai/summarize")
    Call<AiResponse> summarize(@Body AiRequest body);

    // ─── AI: Quiz Generation ─────────────────────────────────────────────
    @POST("api/ai/quiz")
    Call<QuizResponse> generateQuiz(@Body AiRequest body);

    // ─── AI: Doubt Solver ────────────────────────────────────────────────
    @POST("api/ai/doubt")
    Call<AiResponse> solveDoubt(@Body DoubtRequest body);

    // ─── AI: Syllabus Extraction ─────────────────────────────────────────
    @POST("api/ai/syllabus")
    Call<Syllabus> extractSyllabus(@Body AiRequest body);

    // ─── Notes System (Phase 4.5) ─────────────────────────────────────────
    @GET("api/study/subjects/{subjectId}/notes")
    Call<List<Note>> getNotes(@Path("subjectId") String subjectId);

    @POST("api/study/subjects/{subjectId}/notes")
    Call<Note> createNote(@Path("subjectId") String subjectId, @Body Note note);

    @PUT("api/study/subjects/{subjectId}/notes/{noteId}")
    Call<JsonObject> updateNote(@Path("subjectId") String subjectId, @Path("noteId") String noteId, @Body Note note);

    @DELETE("api/study/subjects/{subjectId}/notes/{noteId}")
    Call<JsonObject> deleteNote(@Path("subjectId") String subjectId, @Path("noteId") String noteId);

    // ─── Syllabus Tracker ────────────────────────────────────────────────
    @GET("api/syllabus/{userId}")
    Call<List<Syllabus>> getSyllabuses(@Path("userId") String userId);

    @POST("api/syllabus")
    Call<Syllabus> createSyllabus(@Body Syllabus syllabus);

    @PUT("api/syllabus/{syllabusId}")
    Call<Syllabus> updateSyllabus(@Path("syllabusId") String syllabusId, @Body Syllabus syllabus);

    // ─── Share ───────────────────────────────────────────────────────────
    @POST("api/share/generate")
    Call<ShareResponse> generateShareLink(@Body ShareRequest body);

    // ─── Study Intelligence ──────────────────────────────────────────────
    @GET("api/study/intelligence")
    Call<com.google.gson.JsonObject> getStudyIntelligence();

    @POST("api/study/track-quiz")
    Call<Void> trackQuizResult(@Body com.google.gson.JsonObject body);

    @POST("api/ai/flashcards")
    Call<com.google.gson.JsonObject> generateFlashcards(@Body com.google.gson.JsonObject body);
}
