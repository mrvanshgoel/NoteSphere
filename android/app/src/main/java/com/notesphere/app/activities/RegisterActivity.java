package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityRegisterBinding;
import com.notesphere.app.models.RegisterRequest;
import com.notesphere.app.models.User;
import com.notesphere.app.utils.SharedPrefManager;
import com.google.android.material.snackbar.Snackbar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Entering RegisterActivity");

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
        binding.btnRegister.setText("Registering...");

        android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Attempting Registration for email: " + email);

        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Registration SUCCESS for UID: " + user.getUid());
                        syncProfileWithBackend(user, name);
                    } else {
                        android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Registration SUCCESS but getCurrentUser is null!");
                    }
                } else {
                    android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Registration FAILED: " + task.getException().getMessage());
                    binding.btnRegister.setEnabled(true);
                    binding.btnRegister.setText("Register");
                    showError("Registration failed: " + task.getException().getMessage());
                }
            });
    }

    private void syncProfileWithBackend(FirebaseUser user, String name) {
        user.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String idToken = task.getResult().getToken();
                android.util.Log.d("AUTH_DIAGNOSTIC", "Token Source: FirebaseUser.getIdToken(true) [Register]");
                android.util.Log.d("AUTH_DIAGNOSTIC", "Token Prefix: " + idToken.substring(0, Math.min(20, idToken.length())));
                
                SharedPrefManager pref = SharedPrefManager.getInstance(RegisterActivity.this);
                pref.saveToken(idToken);

                // RegisterRequest now includes UID
                RegisterRequest request = new RegisterRequest(name, user.getEmail(), ""); 
                request.setUid(user.getUid());

                ApiClient.getInstance().register(request).enqueue(new Callback<User>() {
                    @Override
                    public void onResponse(Call<User> call, Response<User> response) {
                        if (response.isSuccessful()) {
                            pref.saveUserInfo(name, user.getEmail(), null);
                            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            showError("Sync failed");
                        }
                    }

                    @Override
                    public void onFailure(Call<User> call, Throwable t) {
                        showError("Network Error");
                    }
                });
            }
        });
    }

    private void showError(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }
}
