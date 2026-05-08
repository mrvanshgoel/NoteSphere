package com.example.aistudyassistant.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.ActivityLoginBinding;
import com.example.aistudyassistant.models.LoginRequest;
import com.example.aistudyassistant.models.User;
import com.example.aistudyassistant.utils.SharedPrefManager;
import com.google.android.material.snackbar.Snackbar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> loginUser());
        binding.tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please fill all fields");
            return;
        }

        binding.btnLogin.setEnabled(false);
        binding.btnLogin.setText("Logging in...");

        LoginRequest request = new LoginRequest(email, password);
        ApiClient.getInstance().login(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                binding.btnLogin.setEnabled(true);
                binding.btnLogin.setText("Login");

                if (response.isSuccessful() && response.body() != null) {
                    User user = response.body();
                    SharedPrefManager pref = SharedPrefManager.getInstance(LoginActivity.this);
                    pref.saveToken(user.getToken());
                    
                    String name = user.getName();
                    String email = user.getEmail();
                    String avatar = (user.getUser() != null && user.getUser().getUserMetadata() != null) 
                            ? user.getUser().getUserMetadata().getAvatarUrl() : null;
                    
                    pref.saveUserInfo(name, email, avatar);

                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    showError("Login failed: Invalid credentials");
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                binding.btnLogin.setEnabled(true);
                binding.btnLogin.setText("Login");
                showError("Network Error: " + t.getMessage());
            }
        });
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
