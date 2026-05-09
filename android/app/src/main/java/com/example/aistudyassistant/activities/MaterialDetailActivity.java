package com.example.aistudyassistant.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.ActivityMaterialDetailBinding;
import com.example.aistudyassistant.models.AiRequest;
import com.example.aistudyassistant.models.AiResponse;
import com.example.aistudyassistant.utils.SharedPrefManager;
import io.noties.markwon.Markwon;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MaterialDetailActivity extends AppCompatActivity {
    private ActivityMaterialDetailBinding binding;
    private String materialId;
    private String materialTitle;
    private Markwon markwon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMaterialDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        materialId = getIntent().getStringExtra("material_id");
        materialTitle = getIntent().getStringExtra("material_title");
        String type = getIntent().getStringExtra("material_type");
        String date = getIntent().getStringExtra("material_date");

        binding.tvTitle.setText(materialTitle);
        binding.tvInfo.setText(type + " | " + date);

        binding.btnSummary.setOnClickListener(v -> callAiApi("summary"));
        binding.btnNotes.setOnClickListener(v -> callAiApi("notes"));
        binding.btnQuestions.setOnClickListener(v -> callAiApi("questions"));

        binding.btnP2PShare.setOnClickListener(v -> generateP2PShare());

        binding.btnCopy.setOnClickListener(v -> copyToClipboard());
        binding.btnShare.setOnClickListener(v -> shareResult());
        binding.btnSavePdf.setOnClickListener(v -> saveAsPdf());

        markwon = Markwon.create(this);
    }

    private void generateP2PShare() {
        binding.progressBar.setVisibility(View.VISIBLE);
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        com.example.aistudyassistant.models.ShareRequest request = new com.example.aistudyassistant.models.ShareRequest(materialId);

        ApiClient.getInstance().generateShareLink(token, request).enqueue(new Callback<com.example.aistudyassistant.models.ShareResponse>() {
            @Override
            public void onResponse(Call<com.example.aistudyassistant.models.ShareResponse> call, Response<com.example.aistudyassistant.models.ShareResponse> response) {
                if (isFinishing() || isDestroyed() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    Intent intent = new Intent(MaterialDetailActivity.this, ShareActivity.class);
                    intent.putExtra("share_url", response.body().getShareUrl());
                    intent.putExtra("share_code", response.body().getShareCode());
                    intent.putExtra("file_name", response.body().getName());
                    startActivity(intent);
                } else {
                    Toast.makeText(MaterialDetailActivity.this, "Sharing failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<com.example.aistudyassistant.models.ShareResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(MaterialDetailActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void callAiApi(String action) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.cardResult.setVisibility(View.GONE);

        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        AiRequest request = new AiRequest(materialId);
        Call<AiResponse> call;

        if (action.equals("summary")) {
            call = ApiClient.getInstance().getSummary(token, request);
            binding.tvResultTitle.setText("AI Summary");
        } else if (action.equals("notes")) {
            call = ApiClient.getInstance().getNotes(token, request);
            binding.tvResultTitle.setText("AI Notes");
        } else {
            call = ApiClient.getInstance().getQuestions(token, request);
            binding.tvResultTitle.setText("Practice Questions");
        }

        call.enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (isFinishing() || isDestroyed() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    binding.cardResult.setVisibility(View.VISIBLE);
                    markwon.setMarkdown(binding.tvContent, response.body().getContent());
                } else {
                    Toast.makeText(MaterialDetailActivity.this, "AI Action failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(MaterialDetailActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void copyToClipboard() {
        String text = binding.tvContent.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AI Result", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void shareResult() {
        String text = binding.tvContent.getText().toString();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "Share AI Result"));
    }

    private void saveAsPdf() {
        String content = binding.tvContent.getText().toString();
        if (content.isEmpty()) return;

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(16f);
        titlePaint.setFakeBoldText(true);

        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.BLACK);
        headerPaint.setTextSize(12f);
        headerPaint.setFakeBoldText(true);

        Paint contentPaint = new Paint();
        contentPaint.setColor(Color.BLACK);
        contentPaint.setTextSize(10f);

        int x = 40, y = 50;
        canvas.drawText(binding.tvResultTitle.getText().toString(), x, y, titlePaint);
        y += 40;

        String[] lines = content.split("\n");
        for (String line : lines) {
            if (y > 800) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }

            String processedLine = line.trim();
            if (processedLine.isEmpty()) {
                y += 10;
                continue;
            }

            Paint activePaint = contentPaint;

            // PDF Markdown Conversion Fix
            if (processedLine.startsWith("# ")) {
                processedLine = processedLine.replace("#", "").trim().toUpperCase();
                activePaint = titlePaint;
                y += 10;
            } else if (processedLine.startsWith("##")) {
                processedLine = "\n" + processedLine.replace("##", "").trim().toUpperCase();
                activePaint = headerPaint;
                y += 15;
            } else if (processedLine.startsWith("- ")) {
                processedLine = "• " + processedLine.substring(2).trim();
                activePaint = contentPaint;
            }

            // Remove bold asterisks but keep text
            if (processedLine.contains("**")) {
                processedLine = processedLine.replace("**", "");
                activePaint = headerPaint; // Treat bold lines as headers for emphasis
            }

            canvas.drawText(processedLine, x, y, activePaint);
            y += 20;
        }

        document.finishPage(page);
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String fileName = materialTitle.replaceAll("[^a-zA-Z0-9]", "_") + "_Notes.pdf";
        File file = new File(downloadsDir, fileName);

        try {
            document.writeTo(new FileOutputStream(file));
            Toast.makeText(this, "PDF saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        document.close();
    }
}
