package com.example.aistudyassistant.api;

import com.example.aistudyassistant.models.AiRequest;
import com.example.aistudyassistant.models.AiResponse;
import com.example.aistudyassistant.models.ChatRequest;
import com.example.aistudyassistant.models.LoginRequest;
import com.example.aistudyassistant.models.Material;
import com.example.aistudyassistant.models.RegisterRequest;
import com.example.aistudyassistant.models.Subject;
import com.example.aistudyassistant.models.User;
import com.example.aistudyassistant.models.AvatarResponse;
import com.example.aistudyassistant.models.ShareRequest;
import com.example.aistudyassistant.models.ShareResponse;

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

public interface ApiService {

    // Auth
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
    Call<AvatarResponse> uploadAvatar(
            @Header("Authorization") String token,
            @Part MultipartBody.Part avatar
    );

    // Subjects
    @GET("api/subjects")
    Call<List<Subject>> getSubjects(@Header("Authorization") String token);

    @POST("api/subjects")
    Call<Subject> createSubject(@Header("Authorization") String token, @Body Subject subject);

    @DELETE("api/subjects/{id}")
    Call<Void> deleteSubject(@Header("Authorization") String token, @Path("id") String id);

    // Materials
    @GET("api/materials/{subjectId}")
    Call<List<Material>> getMaterials(@Header("Authorization") String token, @Path("subjectId") String subjectId);

    @Multipart
    @POST("api/materials/upload")
    Call<Material> uploadMaterial(
            @Header("Authorization") String token,
            @Part("subjectId") RequestBody subjectId,
            @Part MultipartBody.Part file
    );

    @DELETE("api/materials/{id}")
    Call<Void> deleteMaterial(@Header("Authorization") String token, @Path("id") String id);

    // AI Features
    @POST("api/ai/summary")
    Call<AiResponse> getSummary(@Header("Authorization") String token, @Body AiRequest body);

    @POST("api/ai/notes")
    Call<AiResponse> getNotes(@Header("Authorization") String token, @Body AiRequest body);

    @POST("api/ai/questions")
    Call<AiResponse> getQuestions(@Header("Authorization") String token, @Body AiRequest body);

    @POST("api/ai/chat")
    Call<AiResponse> chat(@Header("Authorization") String token, @Body ChatRequest body);

    @POST("api/share/generate")
    Call<ShareResponse> generateShareLink(@Header("Authorization") String token, @Body ShareRequest body);
}
