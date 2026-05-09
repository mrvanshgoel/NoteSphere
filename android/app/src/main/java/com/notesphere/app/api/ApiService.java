package com.notesphere.app.api;

import com.notesphere.app.models.AiRequest;
import com.notesphere.app.models.AiResponse;
import com.notesphere.app.models.ChatRequest;
import com.notesphere.app.models.LoginRequest;
import com.notesphere.app.models.Material;
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

    // ─── Materials ───────────────────────────────────────────────────────
    @GET("api/materials/{subjectId}")
    Call<List<Material>> getMaterials(@Path("subjectId") String subjectId);

    @Multipart
    @POST("api/materials/upload")
    Call<Material> uploadMaterial(
            @Part("subjectId") RequestBody subjectId,
            @Part("name") RequestBody name,
            @Part MultipartBody.Part file
    );

    @DELETE("api/materials/{id}")
    Call<Void> deleteMaterial(@Path("id") String id);

    // ─── AI: Chat ────────────────────────────────────────────────────────
    @POST("api/ai/chat")
    Call<AiResponse> chat(@Body ChatRequest body);

    @GET("api/ai/chats")
    Call<List<ChatSession>> getChatSessions();

    @POST("api/ai/chats")
    Call<ChatSession> createChatSession(@Body ChatSession body);

    @GET("api/ai/chats/{chatId}")
    Call<ChatSession> getChatSession(@Path("chatId") String chatId);

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
