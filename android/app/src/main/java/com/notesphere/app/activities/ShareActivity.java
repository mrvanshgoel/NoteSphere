package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.notesphere.app.databinding.ActivityShareBinding;

public class ShareActivity extends AppCompatActivity {
    private ActivityShareBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String shareUrl = getIntent().getStringExtra("share_url");
        String shareCode = getIntent().getStringExtra("share_code");
        String fileName = getIntent().getStringExtra("file_name");

        if (shareCode != null) {
            binding.tvShareCode.setText("CODE: " + shareCode);
        }

        if (shareUrl != null) {
            // Use QR Server API to generate QR code for the share URL
            String qrApi = "https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=" + shareUrl;
            Glide.with(this).load(qrApi).into(binding.ivQrCode);
        }

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnSocialShare.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Sharing study material: " + fileName);
            intent.putExtra(Intent.EXTRA_TEXT, "Download " + fileName + " from NoteSphere AI: " + shareUrl);
            startActivity(Intent.createChooser(intent, "Share via"));
        });
    }
}
