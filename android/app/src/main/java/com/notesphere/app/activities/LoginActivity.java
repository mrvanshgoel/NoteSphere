package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityLoginBinding;
import com.notesphere.app.models.LoginRequest;
import com.notesphere.app.models.User;
import com.notesphere.app.utils.SharedPrefManager;
import com.google.android.material.snackbar.Snackbar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
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

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        fetchTokenAndProfile(user);
                    }
                } else {
                    binding.btnLogin.setEnabled(true);
                    binding.btnLogin.setText("Login");
                    showError("Login failed: " + task.getException().getMessage());
                }
            });
    }

    private void fetchTokenAndProfile(FirebaseUser user) {
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String idToken = task.getResult().getToken();
                SharedPrefManager pref = SharedPrefManager.getInstance(LoginActivity.this);
                pref.saveToken(idToken);

                // Fetch profile from backend to sync SharedPrefs
                ApiClient.getInstance().getProfile("Bearer " + idToken).enqueue(new Callback<User.UserInfo>() {
                    @Override
                    public void onResponse(Call<User.UserInfo> call, Response<User.UserInfo> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            User.UserInfo u = response.body();
                            pref.saveUserInfo(u.getName(), u.getEmail(), u.getAvatarUrl());
                        }
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }

                    @Override
                    public void onFailure(Call<User.UserInfo> call, Throwable t) {
                        // Even if profile fetch fails, we have the token, so proceed
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }
                });
            } else {
                showError("Failed to generate token");
            }
        });
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
