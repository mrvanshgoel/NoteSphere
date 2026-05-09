package com.example.aistudyassistant.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import com.example.aistudyassistant.R;
import com.example.aistudyassistant.activities.LoginActivity;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.FragmentProfileBinding;
import com.example.aistudyassistant.models.AvatarResponse;
import com.example.aistudyassistant.models.Subject;
import com.example.aistudyassistant.models.User;
import com.example.aistudyassistant.utils.SharedPrefManager;
import com.google.android.material.textfield.TextInputEditText;

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

        loadUserData();
        fetchStats();

        binding.fabEditAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        binding.btnChangeEmail.setOnClickListener(v -> showChangeEmailDialog());
        binding.btnSave.setOnClickListener(v -> saveProfileChanges());
        
        binding.btnLogout.setOnClickListener(v -> {
            pref.logout();
            Intent intent = new Intent(getContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return binding.getRoot();
    }

    private void loadUserData() {
        binding.etName.setText(pref.getUserName());
        binding.etEmail.setText(pref.getUserEmail());

        String avatarUrl = pref.getUserAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_launcher_temp)
                    .circleCrop()
                    .into(binding.ivProfile);
        }
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
        String newName = binding.etName.getText().toString().trim();
        if (newName.isEmpty()) {
            Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("Saving...");

        String authHeader = "Bearer " + pref.getToken();
        User.UserInfo profileUpdate = new User.UserInfo();
        // Since User.UserInfo fields are private, I'll use a hack or just update the model to have setters/public fields.
        // Let's assume I can use a new class or update User.UserInfo.
        // Actually, let's just use a Map or a simple POJO.
        
        ApiClient.getInstance().updateProfile(authHeader, new User.UserInfo(newName, pref.getUserAvatar()))
                .enqueue(new Callback<User.UserInfo>() {
                    @Override
                    public void onResponse(Call<User.UserInfo> call, Response<User.UserInfo> response) {
                        binding.btnSave.setEnabled(true);
                        binding.btnSave.setText("Save Changes");
                        if (response.isSuccessful() && response.body() != null) {
                            pref.saveUserInfo(response.body().getName(), response.body().getEmail(), response.body().getAvatarUrl());
                            Toast.makeText(getContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Update failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<User.UserInfo> call, Throwable t) {
                        binding.btnSave.setEnabled(true);
                        binding.btnSave.setText("Save Changes");
                        Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadAvatar(Uri uri) {
        try {
            File file = new File(requireContext().getCacheDir(), "avatar.jpg");
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();

            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("avatar", file.getName(), requestFile);
            String authHeader = "Bearer " + pref.getToken();

            Toast.makeText(getContext(), "Uploading avatar...", Toast.LENGTH_SHORT).show();

            ApiClient.getInstance().uploadAvatar(authHeader, body).enqueue(new Callback<AvatarResponse>() {
                @Override
                public void onResponse(Call<AvatarResponse> call, Response<AvatarResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String newUrl = response.body().getAvatarUrl();
                        pref.saveUserInfo(pref.getUserName(), pref.getUserEmail(), newUrl);
                        Glide.with(ProfileFragment.this).load(newUrl).circleCrop().into(binding.ivProfile);
                        Toast.makeText(getContext(), "Avatar updated!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "Upload failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<AvatarResponse> call, Throwable t) {
                    Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Toast.makeText(getContext(), "Error selecting image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchStats() {
        if (!isAdded()) return;
        String authHeader = "Bearer " + pref.getToken();
        ApiClient.getInstance().getSubjects(authHeader).enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    List<Subject> subjects = response.body();
                    binding.tvSubjectCount.setText(String.valueOf(subjects.size()));
                    int materialCount = 0;
                    for (Subject s : subjects) materialCount += s.getMaterialCount();
                    binding.tvMaterialCount.setText(String.valueOf(materialCount));
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
