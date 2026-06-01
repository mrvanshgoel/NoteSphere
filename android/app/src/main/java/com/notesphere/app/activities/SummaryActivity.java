package com.notesphere.app.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notesphere.app.R;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivitySummaryBinding;
import com.notesphere.app.models.AiRequest;
import com.notesphere.app.models.AiResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * AI Summary Activity — displays AI-generated summaries in four modes:
 * Summary | Study Notes | Key Concepts | Viva Prep
 */
public class SummaryActivity extends AppCompatActivity {

    private ActivitySummaryBinding binding;
    private String subjectId;
    private String subjectName;
    private String selectedMaterialId;
    private String currentMode = "summary";
    private Call<?> activeCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySummaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subjectId = getIntent().getStringExtra("subjectId");
        subjectName = getIntent().getStringExtra("subjectName");
        selectedMaterialId = getIntent().getStringExtra("materialId");

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        if (subjectName != null) {
            binding.tvSubjectBanner.setText(subjectName.toUpperCase());
        }

        // Chip mode selection
        binding.chipGroupMode.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipSummary) {
                currentMode = "summary";
                binding.tvModeBanner.setText("AI Summary");
            } else if (id == R.id.chipNotes) {
                currentMode = "notes";
                binding.tvModeBanner.setText("Study Notes");
            } else if (id == R.id.chipConcepts) {
                currentMode = "concepts";
                binding.tvModeBanner.setText("Key Concepts");
            } else if (id == R.id.chipViva) {
                currentMode = "viva";
                binding.tvModeBanner.setText("Viva Prep");
            }
            // Auto-generate if a material is already selected
            if (selectedMaterialId != null) {
                generateSummary();
            }
        });

        // Select material button — opens subject hub files tab
        binding.btnSelectMaterial.setOnClickListener(v -> {
            Toast.makeText(this, "Open a material from Files tab and use the AI tools from there.", Toast.LENGTH_LONG).show();
        });

        // Copy button
        binding.btnCopy.setOnClickListener(v -> {
            String content = binding.tvSummaryContent.getText().toString();
            if (!content.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("AI Summary", content);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // If materialId was passed directly (e.g., from MaterialDetailActivity)
        if (selectedMaterialId != null) {
            generateSummary();
        }
    }

    private void generateSummary() {
        if (selectedMaterialId == null) return;

        binding.layoutEmpty.setVisibility(View.GONE);
        binding.scrollContent.setVisibility(View.GONE);
        binding.layoutLoading.setVisibility(View.VISIBLE);

        AiRequest request = new AiRequest(selectedMaterialId, currentMode);

        if (activeCall != null) activeCall.cancel();
        Call<AiResponse> call = ApiClient.getInstance().summarize(request);
        activeCall = call;

        call.enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                binding.layoutLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().getContent();
                    if (content != null && !content.isEmpty()) {
                        binding.tvSummaryContent.setText(content);
                        binding.scrollContent.setVisibility(View.VISIBLE);
                    } else {
                        showEmpty("No content generated. Try a different material.");
                    }
                } else {
                    showEmpty("Failed to generate. Check your connection and try again.");
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                binding.layoutLoading.setVisibility(View.GONE);
                showEmpty("Network error: " + t.getMessage());
            }
        });
    }

    private void showEmpty(String message) {
        binding.layoutEmpty.setVisibility(View.VISIBLE);
        binding.scrollContent.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        binding = null;
    }
}
