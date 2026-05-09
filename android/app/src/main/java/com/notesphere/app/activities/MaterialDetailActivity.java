package com.notesphere.app.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityMaterialDetailBinding;
import com.notesphere.app.models.AiRequest;
import com.notesphere.app.models.AiResponse;
import com.notesphere.app.utils.SharedPrefManager;
import io.noties.markwon.Markwon;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MaterialDetailActivity extends AppCompatActivity {
    private ActivityMaterialDetailBinding binding;
    private String materialId;
    private String materialTitle;
    private Markwon markwon;
    private Call<?> activeCall;
    private String lastAction = null;

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
        binding.tvInfo.setText(type + " • " + date);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Analysis Group
        binding.btnSummary.setOnClickListener(v -> callAiApi("summary"));
        binding.btnConcepts.setOnClickListener(v -> callAiApi("concepts"));
        binding.btnNotes.setOnClickListener(v -> callAiApi("notes"));

        // Study Group
        binding.btnQuestions.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra("material_id", materialId);
            startActivity(intent);
        });
        binding.btnRevision.setOnClickListener(v -> callAiApi("viva")); // Reuse viva mode for revision points
        binding.btnFlashcards.setOnClickListener(v -> {
            Intent intent = new Intent(this, FlashcardActivity.class);
            intent.putExtra("materialId", materialId);
            intent.putExtra("subjectName", materialTitle);
            startActivity(intent);
        });

        // Interactive Group
        binding.btnDoubt.setOnClickListener(v -> openInteractiveChat());
        binding.btnChat.setOnClickListener(v -> openInteractiveChat());

        binding.btnRetry.setOnClickListener(v -> {
            if (lastAction != null) callAiApi(lastAction);
        });

        binding.btnP2PShare.setOnClickListener(v -> generateP2PShare());
        binding.btnCopy.setOnClickListener(v -> copyToClipboard());
        binding.btnSavePdf.setOnClickListener(v -> saveAsPdf());

        markwon = Markwon.create(this);
        
        // Handle auto-action from SubjectDetail (e.g. Generate Notes)
        String autoAction = getIntent().getStringExtra("auto_action");
        if (autoAction != null) {
            callAiApi(autoAction);
        }
    }

    private void openInteractiveChat() {
        Intent intent = new Intent(this, DoubtSolverActivity.class);
        intent.putExtra("material_id", materialId);
        intent.putExtra("material_title", materialTitle);
        startActivity(intent);
    }

    private void callAiApi(String action) {
        lastAction = action;

        binding.cardResult.setVisibility(View.VISIBLE);
        binding.layoutError.setVisibility(View.GONE);
        binding.pbLoading.setVisibility(View.VISIBLE);
        binding.tvLoadingText.setVisibility(View.VISIBLE);
        binding.layoutResultActions.setVisibility(View.GONE);
        binding.tvContent.setText("AI is analyzing " + materialTitle + "..."); 

        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        AiRequest request = new AiRequest(materialId, action);
        
        android.util.Log.d("AI_TRACE", "--- MATERIAL ANALYSIS REQUEST ---");
        android.util.Log.d("AI_TRACE", "Action: " + action);
        android.util.Log.d("AI_TRACE", "Material ID: " + materialId);
        android.util.Log.d("AI_TRACE", "Payload: " + new com.google.gson.Gson().toJson(request));

        if (activeCall != null) activeCall.cancel();
        
        Call<AiResponse> call = ApiClient.getInstance().summarize(token, request);
        
        // Update UI titles based on action
        switch (action) {
            case "summary": binding.tvResultTitle.setText("Summary"); break;
            case "notes": binding.tvResultTitle.setText("Smart Notes"); break;
            case "concepts": binding.tvResultTitle.setText("Key Concepts"); break;
            case "viva": binding.tvResultTitle.setText("Revision Points"); break;
        }

        activeCall = call;
        call.enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (isFinishing() || isDestroyed() || binding == null) return;
                binding.pbLoading.setVisibility(View.GONE);
                binding.tvLoadingText.setVisibility(View.GONE);
                
                android.util.Log.d("AI_TRACE", "--- MATERIAL ANALYSIS RESPONSE ---");
                android.util.Log.d("AI_TRACE", "Status Code: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    binding.layoutResultActions.setVisibility(View.VISIBLE);
                    markwon.setMarkdown(binding.tvContent, response.body().getContent());
                    
                    // Increment session for goal tracking
                    SharedPrefManager.getInstance(MaterialDetailActivity.this).incrementAiSessions();
                } else {
                    showError("AI was unable to process this request.");
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed() || binding == null) return;
                if (call.isCanceled()) return;
                binding.pbLoading.setVisibility(View.GONE);
                binding.tvLoadingText.setVisibility(View.GONE);
                android.util.Log.e("AI_ERROR", "Analysis Failed", t);
                showError("Connection failed: " + t.getMessage());
            }
        });
    }

    private void showError(String message) {
        binding.layoutResultActions.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);
        binding.tvErrorText.setText(message);
        binding.tvContent.setText("");
    }

    private void generateP2PShare() {
        binding.pbLoading.setVisibility(View.VISIBLE);
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        ApiClient.getInstance().generateShareLink(token, new com.notesphere.app.models.ShareRequest(materialId))
            .enqueue(new Callback<com.notesphere.app.models.ShareResponse>() {
                @Override
                public void onResponse(Call<com.notesphere.app.models.ShareResponse> call, Response<com.notesphere.app.models.ShareResponse> response) {
                    if (isFinishing() || isDestroyed() || binding == null) return;
                    binding.pbLoading.setVisibility(View.GONE);
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
                public void onFailure(Call<com.notesphere.app.models.ShareResponse> call, Throwable t) {
                    if (isFinishing() || isDestroyed() || binding == null) return;
                    binding.pbLoading.setVisibility(View.GONE);
                    Toast.makeText(MaterialDetailActivity.this, "Network error", Toast.LENGTH_SHORT).show();
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

    private void saveAsPdf() {
        String content = binding.tvContent.getText().toString();
        if (content.isEmpty()) return;

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(12f);

        int x = 40, y = 50;
        canvas.drawText(binding.tvResultTitle.getText().toString(), x, y, paint);
        y += 40;
        paint.setTextSize(10f);

        String[] lines = content.split("\n");
        for (String line : lines) {
            if (y > 800) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }
            canvas.drawText(line, x, y, paint);
            y += 15;
        }

        document.finishPage(page);
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String fileName = materialTitle.replaceAll("[^a-zA-Z0-9]", "_") + "_AI.pdf";
        File file = new File(downloadsDir, fileName);

        try {
            document.writeTo(new FileOutputStream(file));
            Toast.makeText(this, "Saved to Downloads", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving PDF", Toast.LENGTH_SHORT).show();
        }
        document.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
