package com.example.aistudyassistant.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.ActivityRegisterBinding;
import com.example.aistudyassistant.models.RegisterRequest;
import com.example.aistudyassistant.models.User;
import com.example.aistudyassistant.utils.SharedPrefManager;
import com.google.android.material.snackbar.Snackbar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnRegister.setOnClickListener(v -> registerUser());
        binding.tvLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Please fill all fields");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        binding.btnRegister.setEnabled(false);
        binding.btnRegister.setText("Creating Account...");

        RegisterRequest request = new RegisterRequest(name, email, password);
        ApiClient.getInstance().register(request).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                binding.btnRegister.setEnabled(true);
                binding.btnRegister.setText("Register");

                if (response.isSuccessful() && response.body() != null) {
                    User user = response.body();
                    SharedPrefManager pref = SharedPrefManager.getInstance(RegisterActivity.this);
                    pref.saveToken(user.getToken());
                    
                    String userName = user.getName();
                    String userEmail = user.getEmail();
                    String avatar = user.getAvatarUrl();
                    
                    pref.saveUserInfo(userName, userEmail, avatar);

                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    showError("Registration failed. Email might be taken.");
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                binding.btnRegister.setEnabled(true);
                binding.btnRegister.setText("Register");
                showError("Network Error");
            }
        });
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
