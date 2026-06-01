package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.databinding.ActivitySplashBinding;
import com.notesphere.app.utils.SharedPrefManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.util.Log;

public class SplashActivity extends AppCompatActivity {
    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Entering SplashActivity");
        printStartupReport();

        startSplashSequence();
    }

    private void printStartupReport() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        SharedPrefManager pref = SharedPrefManager.getInstance(this);
        Log.e("NOTESPHERE_DIAGNOSTIC", "==================================================");
        Log.e("NOTESPHERE_DIAGNOSTIC", "STARTUP REPORT");
        Log.e("NOTESPHERE_DIAGNOSTIC", "SharedPref isLoggedIn: " + pref.isLoggedIn());
        if (user != null) {
            Log.e("NOTESPHERE_DIAGNOSTIC", "FirebaseUser != null");
            Log.e("NOTESPHERE_DIAGNOSTIC", "UID: " + user.getUid());
            Log.e("NOTESPHERE_DIAGNOSTIC", "Email: " + user.getEmail());
            Log.e("NOTESPHERE_DIAGNOSTIC", "Provider: " + user.getProviderId());
            Log.e("NOTESPHERE_DIAGNOSTIC", "FirebaseUser == null");
        }
        
        try {
            String projectId = getString(getResources().getIdentifier("project_id", "string", getPackageName()));
            String storageBucket = getString(getResources().getIdentifier("google_storage_bucket", "string", getPackageName()));
            String appId = getString(getResources().getIdentifier("google_app_id", "string", getPackageName()));
            String backendUrl = com.notesphere.app.api.ApiClient.BASE_URL;
            
            Log.e("NOTESPHERE_DIAGNOSTIC", "Android project_id: " + projectId);
            Log.e("NOTESPHERE_DIAGNOSTIC", "Android storage_bucket: " + storageBucket);
            Log.e("NOTESPHERE_DIAGNOSTIC", "Android mobilesdk_app_id: " + appId);
            Log.e("NOTESPHERE_DIAGNOSTIC", "Backend URL: " + backendUrl);
            
            // Expected Backend Matches:
            Log.e("NOTESPHERE_DIAGNOSTIC", "Expected Backend FIREBASE_PROJECT_ID: vansh-notesphere");
            Log.e("NOTESPHERE_DIAGNOSTIC", "Expected Backend FIREBASE_STORAGE_BUCKET: " + storageBucket); // Usually .appspot.com
        } catch (Exception e) {
            Log.e("NOTESPHERE_DIAGNOSTIC", "Failed to load google-services config strings", e);
        }
        Log.e("NOTESPHERE_DIAGNOSTIC", "==================================================");
    }

    private void startSplashSequence() {
        // 1. Initial Diamond Reveal
        binding.viewDiamond.setAlpha(0f);
        binding.viewDiamond.setScaleX(0.5f);
        binding.viewDiamond.setScaleY(0.5f);
        binding.viewDiamond.setVisibility(View.VISIBLE);

        binding.viewDiamond.animate()
                .alpha(1f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> {
                    // 2. Rotate to Diamond
                    binding.viewDiamond.animate()
                            .rotation(45f)
                            .scaleX(1.5f)
                            .scaleY(1.5f)
                            .setDuration(600)
                            .setInterpolator(new AccelerateDecelerateInterpolator())
                            .withEndAction(() -> {
                                // 3. Condense to Dot
                                binding.viewDiamond.animate()
                                        .scaleX(0.2f)
                                        .scaleY(0.2f)
                                        .rotation(90f)
                                        .setDuration(500)
                                        .setInterpolator(new AnticipateInterpolator())
                                        .withEndAction(() -> {
                                            binding.viewDiamond.setVisibility(View.GONE);
                                            revealLogo();
                                        })
                                        .start();
                            })
                            .start();
                })
                .start();
    }

    private void revealLogo() {
        binding.layoutLogo.setAlpha(0f);
        binding.layoutLogo.setScaleX(0.8f);
        binding.layoutLogo.setVisibility(View.VISIBLE);

        binding.layoutLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> {
                    // 5. Gradient Wipe Transition
                    performWipeAndExit();
                })
                .start();
    }

    private void performWipeAndExit() {
        binding.viewWipe.setVisibility(View.VISIBLE);
        binding.viewWipe.setTranslationY(binding.getRoot().getHeight());

        binding.viewWipe.animate()
                .translationY(0)
                .setDuration(700)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setStartDelay(500)
                .withEndAction(() -> {
                    navigateToNext();
                })
                .start();
    }

    private void navigateToNext() {
        boolean loggedIn = SharedPrefManager.getInstance(this).isLoggedIn();
        Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] navigateToNext() -> SharedPref isLoggedIn: " + loggedIn);
        
        if (loggedIn) {
            Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Routing to MainActivity");
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        } else {
            Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Routing to LoginActivity");
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
        }
        overridePendingTransition(0, 0); // Disable default animation to keep the wipe look
        finish();
    }
}
