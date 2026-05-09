package com.notesphere.app.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.databinding.ActivityShareBinding;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ShareActivity extends AppCompatActivity {
    private ActivityShareBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getIntent() == null) {
            finish();
            return;
        }

        String shareUrl = getIntent().getStringExtra("share_url");
        String shareCode = getIntent().getStringExtra("share_code");
        String fileName = getIntent().getStringExtra("file_name");

        if (shareUrl == null || shareCode == null) {
            Toast.makeText(this, "Sharing session invalid", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.tvFileName.setText(fileName != null ? fileName : "Shared File");
        binding.tvShareCode.setText(shareCode);

        // Generate QR Code
        try {
            Bitmap qrBitmap = generateQrCode(shareUrl);
            binding.ivQrCode.setImageBitmap(qrBitmap);
        } catch (WriterException e) {
            Toast.makeText(this, "QR Generation failed", Toast.LENGTH_SHORT).show();
        }

        binding.btnCopyLink.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Share Link", shareUrl);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        binding.btnClose.setOnClickListener(v -> finish());
    }

    private Bitmap generateQrCode(String text) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }
}
