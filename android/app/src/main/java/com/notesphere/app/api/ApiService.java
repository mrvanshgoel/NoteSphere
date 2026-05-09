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
import com.notesphere.app.models.DoubtRequest;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // ─── Auth ────────────────────────────────────────────────────────────
    @POST("api/auth/login")
    Call<User> login(@Body LoginRequest body);

    @POST("api/auth/register")
    Call<User> register(@Body RegisterRequest body);

    @GET("api/auth/profile")
    Call<User.UserInfo> getProfile(@Header("Authorization") String token);

    @PUT("api/auth/profile")
    Call<User.UserInfo> updateProfile(@Header("Authorization") String token, @Body User.UserInfo profile);

    @Multipart
    @POST("api/auth/upload-avatar")
    Call<User.AvatarResponse> uploadAvatar(
            @Header("Authorization") String token,
            @Part MultipartBody.Part avatar
    );

    @DELETE("api/auth/avatar")
    Call<Void> deleteAvatar(@Header("Authorization") String token);

    // ─── Subjects ────────────────────────────────────────────────────────
    @GET("api/subjects")
    Call<List<Subject>> getSubjects(@Header("Authorization") String token);

    @POST("api/subjects")
    Call<Subject> createSubject(@Header("Authorization") String token, @Body Subject subject);

    @DELETE("api/subjects/{id}")
    Call<Void> deleteSubject(@Header("Authorization") String token, @Path("id") String id);

    // ─── Materials ───────────────────────────────────────────────────────
    @GET("api/materials/{subjectId}")
    Call<List<Material>> getMaterials(@Header("Authorization") String token, @Path("subjectId") String subjectId);

    @Multipart
    @POST("api/materials/upload")
    Call<Material> uploadMaterial(
            @Header("Authorization") String token,
            @Part("subjectId") RequestBody subjectId,
            @Part("name") RequestBody name,
            @Part MultipartBody.Part file
    );

    @DELETE("api/materials/{id}")
    Call<Void> deleteMaterial(@Header("Authorization") String token, @Path("id") String id);

    // ─── AI: Chat ────────────────────────────────────────────────────────
    @POST("api/ai/chat")
    Call<AiResponse> chat(@Header("Authorization") String token, @Body ChatRequest body);

    // ─── AI: Summarize (multi-mode: summary/notes/concepts/viva) ─────────
    @POST("api/ai/summarize")
    Call<AiResponse> summarize(@Header("Authorization") String token, @Body AiRequest body);

    // ─── AI: Quiz Generation ─────────────────────────────────────────────
    @POST("api/ai/quiz")
    Call<QuizResponse> generateQuiz(@Header("Authorization") String token, @Body AiRequest body);

    // ─── AI: Doubt Solver ────────────────────────────────────────────────
    @POST("api/ai/doubt")
    Call<AiResponse> solveDoubt(@Header("Authorization") String token, @Body DoubtRequest body);

    // ─── AI: Syllabus Extraction ─────────────────────────────────────────
    @POST("api/ai/syllabus")
    Call<Syllabus> extractSyllabus(@Header("Authorization") String token, @Body AiRequest body);

    // ─── Syllabus Tracker ────────────────────────────────────────────────
    @GET("api/syllabus/{userId}")
    Call<List<Syllabus>> getSyllabuses(@Header("Authorization") String token, @Path("userId") String userId);

    @POST("api/syllabus")
    Call<Syllabus> createSyllabus(@Header("Authorization") String token, @Body Syllabus syllabus);

    @PUT("api/syllabus/{syllabusId}")
    Call<Syllabus> updateSyllabus(@Header("Authorization") String token, @Path("syllabusId") String syllabusId, @Body Syllabus syllabus);

    // ─── Share ───────────────────────────────────────────────────────────
    @POST("api/share/generate")
    Call<ShareResponse> generateShareLink(@Header("Authorization") String token, @Body ShareRequest body);
}
