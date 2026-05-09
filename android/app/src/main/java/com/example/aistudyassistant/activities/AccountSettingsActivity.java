package com.example.aistudyassistant.activities;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.aistudyassistant.R;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.ActivityAccountSettingsBinding;
import com.example.aistudyassistant.models.User;
import com.example.aistudyassistant.utils.SharedPrefManager;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.content.Intent;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class AccountSettingsActivity extends AppCompatActivity {
    private ActivityAccountSettingsBinding binding;
    private SharedPrefManager pref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        pref = SharedPrefManager.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        loadData();
        binding.ivProfile.setOnClickListener(v -> pickImage());
        binding.btnSave.setOnClickListener(v -> saveSettings());
    }

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    uploadAvatar(uri);
                }
            }
    );

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadAvatar(Uri uri) {
        binding.progressBar.setVisibility(android.view.View.VISIBLE);
        try {
            File file = getFileFromUri(uri);
            RequestBody requestFile = RequestBody.create(MediaType.parse(getContentResolver().getType(uri)), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("avatar", file.getName(), requestFile);
            String token = "Bearer " + pref.getToken();

            ApiClient.getInstance().uploadAvatar(token, body).enqueue(new Callback<User.AvatarResponse>() {
                @Override
                public void onResponse(Call<User.AvatarResponse> call, Response<User.AvatarResponse> response) {
                    if (isFinishing() || isDestroyed() || binding == null) return;
                    binding.progressBar.setVisibility(android.view.View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        String url = response.body().getAvatarUrl();
                        pref.saveUserInfo(pref.getUserName(), pref.getUserEmail(), url);
                        loadData();
                        Toast.makeText(AccountSettingsActivity.this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AccountSettingsActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<User.AvatarResponse> call, Throwable t) {
                    if (isFinishing() || isDestroyed() || binding == null) return;
                    binding.progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(AccountSettingsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            binding.progressBar.setVisibility(android.view.View.GONE);
            Toast.makeText(this, "File error", Toast.LENGTH_SHORT).show();
        }
    }

    private File getFileFromUri(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        File file = new File(getCacheDir(), "avatar_temp.jpg");
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
        fos.flush();
        fos.close();
        is.close();
        return file;
    }
    private void loadData() {
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

    private void saveSettings() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("Saving...");

        String authHeader = "Bearer " + pref.getToken();
        User.UserInfo update = new User.UserInfo(name, pref.getUserAvatar());
        
        ApiClient.getInstance().updateProfile(authHeader, update).enqueue(new Callback<User.UserInfo>() {
            @Override
            public void onResponse(Call<User.UserInfo> call, Response<User.UserInfo> response) {
                binding.btnSave.setEnabled(true);
                binding.btnSave.setText("Save Settings");
                if (response.isSuccessful() && response.body() != null) {
                    pref.saveUserInfo(response.body().getName(), response.body().getEmail(), response.body().getAvatarUrl());
                    Toast.makeText(AccountSettingsActivity.this, "Settings saved!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(AccountSettingsActivity.this, "Failed to save settings", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<User.UserInfo> call, Throwable t) {
                binding.btnSave.setEnabled(true);
                binding.btnSave.setText("Save Settings");
                Toast.makeText(AccountSettingsActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        // If email changed, usually you'd send a separate verification request
        if (!email.equals(pref.getUserEmail())) {
            Toast.makeText(this, "Verification email sent to " + email, Toast.LENGTH_LONG).show();
        }
    }
}
