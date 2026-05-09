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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MaterialDetailActivity extends AppCompatActivity {
    private ActivityMaterialDetailBinding binding;
    private String materialId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMaterialDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        materialId = getIntent().getStringExtra("material_id");
        String title = getIntent().getStringExtra("material_title");
        String type = getIntent().getStringExtra("material_type");
        String date = getIntent().getStringExtra("material_date");

        binding.tvTitle.setText(title);
        binding.tvInfo.setText(type + " | " + date);

        binding.btnSummary.setOnClickListener(v -> callAiApi("summary"));
        binding.btnNotes.setOnClickListener(v -> callAiApi("notes"));
        binding.btnQuestions.setOnClickListener(v -> callAiApi("questions"));

        binding.btnP2PShare.setOnClickListener(v -> generateP2PShare());

        binding.btnCopy.setOnClickListener(v -> copyToClipboard());
        binding.btnShare.setOnClickListener(v -> shareResult());
    }

    private void generateP2PShare() {
        binding.progressBar.setVisibility(View.VISIBLE);
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        com.example.aistudyassistant.models.ShareRequest request = new com.example.aistudyassistant.models.ShareRequest(materialId);

        ApiClient.getInstance().generateShareLink(token, request).enqueue(new Callback<com.example.aistudyassistant.models.ShareResponse>() {
            @Override
            public void onResponse(Call<com.example.aistudyassistant.models.ShareResponse> call, Response<com.example.aistudyassistant.models.ShareResponse> response) {
                binding.progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    Intent intent = new Intent(MaterialDetailActivity.this, ShareActivity.class);
                    intent.putExtra("share_url", response.body().getShareUrl());
                    intent.putExtra("file_name", response.body().getName());
                    startActivity(intent);
                } else {
                    Toast.makeText(MaterialDetailActivity.this, "Sharing failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<com.example.aistudyassistant.models.ShareResponse> call, Throwable t) {
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
                binding.progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    binding.cardResult.setVisibility(View.VISIBLE);
                    binding.tvContent.setText(response.body().getContent());
                } else {
                    Toast.makeText(MaterialDetailActivity.this, "AI Action failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
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
}
