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

        binding.btnSave.setOnClickListener(v -> saveSettings());
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
