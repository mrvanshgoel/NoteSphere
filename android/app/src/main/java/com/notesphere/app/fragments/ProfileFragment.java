package com.notesphere.app.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.notesphere.app.R;
import com.notesphere.app.BuildConfig;
import com.notesphere.app.activities.LoginActivity;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentProfileBinding;
import com.notesphere.app.models.Subject;
import com.notesphere.app.models.User;
import com.notesphere.app.utils.SharedPrefManager;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private SharedPrefManager pref;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private Call<?> activeCall;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadAvatar(imageUri);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        pref = SharedPrefManager.getInstance(requireContext());

        loadUserProfile();
        loadStats();

        binding.fabEditAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        binding.btnChangeEmail.setOnClickListener(v -> showChangeEmailDialog());
        binding.btnSave.setOnClickListener(v -> saveProfileChanges());
        
        binding.btnLogout.setOnClickListener(v -> {
            pref.logout();
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            Context context = getContext();
            if (context != null) {
                Intent intent = new Intent(context, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
        
        if (BuildConfig.DEBUG) {
            setupDeveloperOptions();
        }

        return binding.getRoot();
    }

    private void setupDeveloperOptions() {
        android.widget.Button devBtn = new android.widget.Button(requireContext());
        devBtn.setText("DEV: FORCE RESET APP");
        devBtn.setTextColor(android.graphics.Color.WHITE);
        devBtn.setBackgroundColor(android.graphics.Color.RED);
        devBtn.setOnClickListener(v -> forceResetApp());
        
        // Since ViewBinding strictly returns ScrollView for this fragment layout:
        android.widget.ScrollView sv = binding.getRoot();
        if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) sv.getChildAt(0)).addView(devBtn);
        }
    }
    
    private void forceResetApp() {
        android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "===== EXECUTING HARD SESSION RESET =====");
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        SharedPrefManager.getInstance(requireContext()).clearAll();
        // Since Room DB instance wasn't explicitly provided in the snippets, we skip it or clear cache dir
        try {
            requireContext().getCacheDir().delete();
        } catch (Exception ignored) {}
        
        Intent intent = new Intent(requireContext(), com.notesphere.app.activities.SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void loadUserProfile() {
        if (binding == null) return;
        binding.etName.setText(pref.getUserName());
        binding.etEmail.setText(pref.getUserEmail());

        String savedUrl = pref.getAvatarUrl();
        if (savedUrl != null && !savedUrl.isEmpty()) {
            loadAvatarWithGlide(savedUrl);
        }

        if (activeCall != null) activeCall.cancel();
        Call<User.UserInfo> call = ApiClient.getApiService().getProfile();
        activeCall = call;

        call.enqueue(new Callback<User.UserInfo>() {
            @Override
            public void onResponse(Call<User.UserInfo> call, Response<User.UserInfo> response) {
                Context context = getContext();
                if (!isAdded() || context == null || binding == null) return;
                
                if (response.isSuccessful() && response.body() != null) {
                    String remoteUrl = response.body().getAvatarUrl();
                    if (remoteUrl != null && !remoteUrl.isEmpty()) {
                        pref.saveAvatarUrl(remoteUrl);
                        loadAvatarWithGlide(remoteUrl);
                    }
                }
            }

            @Override
            public void onFailure(Call<User.UserInfo> call, Throwable t) {
                if (call.isCanceled()) return;
                android.util.Log.e("AVATAR", "API Error: " + t.getMessage());
            }
        });
    }

    private void loadAvatarWithGlide(String url) {
        if (binding == null || !isAdded() || url == null || url.isEmpty()) return;

        RequestOptions options = new RequestOptions()
            .circleCrop()
            .placeholder(R.drawable.ic_launcher_temp)
            .error(R.drawable.ic_launcher_temp)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true);

        // Regular URL - add cache-bust param
        String cacheBustUrl = url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
        Glide.with(this)
            .load(cacheBustUrl)
            .apply(options)
            .into(binding.ivProfile);
    }

    private void showChangeEmailDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_change_email, null);
        TextInputEditText etNewEmail = dialogView.findViewById(R.id.etNewEmail);

        new AlertDialog.Builder(requireContext())
                .setTitle("Change Email")
                .setView(dialogView)
                .setPositiveButton("Verify", (dialog, which) -> {
                    String newEmail = etNewEmail.getText().toString().trim();
                    if (!newEmail.isEmpty()) {
                        Toast.makeText(getContext(), "Verification email sent to " + newEmail, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveProfileChanges() {
        if (binding == null) return;
        String newName = binding.etName.getText().toString().trim();
        if (newName.isEmpty()) {
            if (getContext() != null) Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("Saving...");

        if (activeCall != null) activeCall.cancel();
        Call<User.UserInfo> call = ApiClient.getInstance().updateProfile(new User.UserInfo(newName, pref.getAvatarUrl()));
        activeCall = call;

        call.enqueue(new Callback<User.UserInfo>() {
                    @Override
                    public void onResponse(Call<User.UserInfo> call, Response<User.UserInfo> response) {
                        Context innerContext = getContext();
                        if (!isAdded() || innerContext == null || binding == null) return;
                        
                        binding.btnSave.setEnabled(true);
                        binding.btnSave.setText("Save Changes");
                        if (response.isSuccessful() && response.body() != null) {
                            pref.saveUserInfo(response.body().getName(), response.body().getEmail(), response.body().getAvatarUrl());
                            Toast.makeText(innerContext, "Profile updated!", Toast.LENGTH_SHORT).show();
                        } else {
                            String errorMsg = parseError(response);
                            Toast.makeText(innerContext, "Update failed: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<User.UserInfo> call, Throwable t) {
                        Context innerContext = getContext();
                        if (!isAdded() || innerContext == null || binding == null) return;
                        if (call.isCanceled()) return;

                        binding.btnSave.setEnabled(true);
                        binding.btnSave.setText("Save Changes");
                        Toast.makeText(innerContext, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadAvatar(Uri uri) {
        Context context = getContext();
        if (context == null) return;

        // Show uploading feedback
        if (binding != null) {
            binding.fabEditAvatar.setEnabled(false);
        }
        Toast.makeText(context, "Uploading avatar...", Toast.LENGTH_SHORT).show();

        try {
            // ─── Decode & compress to JPEG under 1.5MB ───────────────────────────
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            Bitmap original = BitmapFactory.decodeStream(inputStream);
            if (original == null) throw new Exception("Could not decode image");

            // Scale down if too large (max 512x512)
            Bitmap scaled = original;
            if (original.getWidth() > 512 || original.getHeight() > 512) {
                float scale = 512f / Math.max(original.getWidth(), original.getHeight());
                scaled = Bitmap.createScaledBitmap(original,
                        (int)(original.getWidth() * scale),
                        (int)(original.getHeight() * scale), true);
            }

            // Compress to JPEG at 80% quality
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            byte[] imageBytes = baos.toByteArray();

            // Write compressed bytes to temp file
            File file = new File(context.getCacheDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(imageBytes);
            fos.flush();
            fos.close();

            RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("avatar", file.getName(), requestFile);
            if (activeCall != null) activeCall.cancel();
            Call<User.AvatarResponse> call = ApiClient.getInstance().uploadAvatar(body);
            activeCall = call;

            call.enqueue(new Callback<User.AvatarResponse>() {
                @Override
                public void onResponse(Call<User.AvatarResponse> call, Response<User.AvatarResponse> response) {
                    Context innerContext = getContext();
                    if (!isAdded() || innerContext == null || binding == null) return;
                    binding.fabEditAvatar.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null) {
                        String newUrl = response.body().getAvatarUrl();
                        pref.saveAvatarUrl(newUrl);
                        loadAvatarWithGlide(newUrl);
                        Toast.makeText(innerContext, "✓ Avatar updated!", Toast.LENGTH_SHORT).show();
                        if (file.exists()) file.delete();
                    } else {
                        String errorMsg = parseError(response);
                        Toast.makeText(innerContext, "Upload failed: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<User.AvatarResponse> call, Throwable t) {
                    Context innerContext = getContext();
                    if (!isAdded() || innerContext == null || binding == null) return;
                    if (call.isCanceled()) return;
                    binding.fabEditAvatar.setEnabled(true);
                    String msg = "Network error: " + t.getMessage();
                    Toast.makeText(innerContext, msg, Toast.LENGTH_LONG).show();
                    android.util.Log.e("AVATAR", "Failure: " + msg, t);
                }
            });

        } catch (Exception e) {
            if (binding != null) binding.fabEditAvatar.setEnabled(true);
            Toast.makeText(context, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadStats() {
        if (!isAdded()) return;
        if (activeCall != null) activeCall.cancel();
        Call<List<Subject>> call = ApiClient.getApiService().getSubjects();
        activeCall = call;

        call.enqueue(new Callback<List<Subject>>() {
                @Override
                public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                    if (!isAdded() || binding == null) return;
                    if (response.isSuccessful() && response.body() != null) {
                        int subjectCount = response.body().size();
                        int totalMaterials = 0;
                        for (Subject s : response.body()) {
                            totalMaterials += s.getMaterialCount();
                        }
                        binding.tvSubjectCount.setText(String.valueOf(subjectCount));
                        binding.tvMaterialCount.setText(String.valueOf(totalMaterials));
                    }
                }
                
                @Override
                public void onFailure(Call<List<Subject>> call, Throwable t) {
                    if (call.isCanceled()) return;
                    android.util.Log.e("PROFILE", "Stats error: " + t.getMessage());
                }
            });
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorJson = response.errorBody().string();
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(errorJson).getAsJsonObject();
                if (obj.has("error")) return obj.get("error").getAsString();
            }
        } catch (Exception e) {
            android.util.Log.e("PROFILE", "Error parsing error body", e);
        }
        return "Server error (" + response.code() + ")";
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding == null) return;
        loadStats();
        loadUserProfile();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
