package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.databinding.ActivitySplashBinding;
import com.notesphere.app.utils.SharedPrefManager;

public class SplashActivity extends AppCompatActivity {
    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Pulsing animation for logo
        AlphaAnimation pulse = new AlphaAnimation(0.4f, 1.0f);
        pulse.setDuration(1500);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        binding.ivLogo.startAnimation(pulse);

        // Subtle fade-in for developer branding
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1.0f);
        fadeIn.setDuration(1000);
        fadeIn.setStartOffset(500);
        binding.tvDeveloper.startAnimation(fadeIn);

        new Handler().postDelayed(() -> {
            if (SharedPrefManager.getInstance(this).isLoggedIn()) {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            }
            finish();
        }, 3000);
    }
}
