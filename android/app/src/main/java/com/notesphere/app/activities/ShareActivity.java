package com.notesphere.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.databinding.ActivityShareBinding;
import com.notesphere.app.models.Material;

public class ShareActivity extends AppCompatActivity {
    private ActivityShareBinding binding;
    private String materialId;
    private String materialTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        materialId = getIntent().getStringExtra("material_id");
        materialTitle = getIntent().getStringExtra("material_title");

        binding.tvTitle.setText(materialTitle);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // For now, P2P is simulated via a deep link QR
        generateQRCode("https://notesphere.ai/share/" + materialId);

        binding.btnShareApps.setOnClickListener(v -> shareViaApps());
        binding.btnP2P.setOnClickListener(v -> startP2PSimulation());
    }

    private void generateQRCode(String content) {
        // In a real app, use ZXing or similar. For this demo, we'll show a "Generating..." state
        binding.ivQRCode.setAlpha(0.5f);
        Toast.makeText(this, "Generating secure P2P transfer code...", Toast.LENGTH_SHORT).show();
        
        // Mocking a QR code appearance
        binding.ivQRCode.animate().alpha(1f).setDuration(1000).start();
    }

    private void shareViaApps() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Sharing Study Material: " + materialTitle);
        intent.putExtra(Intent.EXTRA_TEXT, "Hey! Check out this study material on NoteSphere: https://notesphere.ai/share/" + materialId);
        startActivity(Intent.createChooser(intent, "Share via"));
    }

    private void startP2PSimulation() {
        binding.layoutP2PStatus.setVisibility(View.VISIBLE);
        binding.tvP2PStatus.setText("Searching for nearby devices...");
        
        binding.progressBarP2P.setIndeterminate(true);
        
        binding.getRoot().postDelayed(() -> {
            binding.tvP2PStatus.setText("Device Found: Vansh's iPhone\nTransferring...");
            binding.progressBarP2P.setIndeterminate(false);
            binding.progressBarP2P.setProgress(45);
        }, 2000);

        binding.getRoot().postDelayed(() -> {
            binding.progressBarP2P.setProgress(100);
            binding.tvP2PStatus.setText("Transfer Complete! ✅");
        }, 5000);
    }
}
