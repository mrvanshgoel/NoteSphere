package com.notesphere.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.notesphere.app.adapters.FlashcardAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityFlashcardBinding;
import com.notesphere.app.utils.SharedPrefManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FlashcardActivity extends AppCompatActivity {
    private ActivityFlashcardBinding binding;
    private List<JsonObject> cards = new ArrayList<>();
    private FlashcardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String materialId = getIntent().getStringExtra("materialId");
        String subjectName = getIntent().getStringExtra("subjectName");
        if (subjectName != null) binding.tvSubjectName.setText(subjectName);

        adapter = new FlashcardAdapter(cards);
        binding.vpFlashcards.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSkip.setOnClickListener(v -> binding.vpFlashcards.setCurrentItem(binding.vpFlashcards.getCurrentItem() + 1));
        binding.btnMastered.setOnClickListener(v -> binding.vpFlashcards.setCurrentItem(binding.vpFlashcards.getCurrentItem() + 1));

        binding.vpFlashcards.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateProgress(position);
            }
        });

        if (materialId != null) fetchFlashcards(materialId);
    }

    private void fetchFlashcards(String materialId) {
        JsonObject body = new JsonObject();
        body.addProperty("materialId", materialId);

        ApiClient.getInstance().generateFlashcards(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        if (response.body().has("flashcards") && response.body().get("flashcards").isJsonArray()) {
                            JsonArray arr = response.body().getAsJsonArray("flashcards");
                            if (arr.size() == 0) {
                                Toast.makeText(FlashcardActivity.this, "No flashcards generated. Try again.", Toast.LENGTH_SHORT).show();
                            } else {
                                for (int i = 0; i < arr.size(); i++) {
                                    if (arr.get(i) != null && arr.get(i).isJsonObject()) {
                                        cards.add(arr.get(i).getAsJsonObject());
                                    }
                                }
                                adapter.notifyDataSetChanged();
                                updateProgress(0);
                            }
                        } else {
                            Toast.makeText(FlashcardActivity.this, "AI response format error.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(FlashcardActivity.this, "Error parsing flashcards.", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(FlashcardActivity.this, "Failed to generate cards", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(FlashcardActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateProgress(int position) {
        int total = cards.size();
        if (total == 0) return;
        binding.tvProgress.setText("Card " + (position + 1) + " of " + total);
        binding.sessionProgress.setProgress((int) (((float) (position + 1) / total) * 100));
    }
}
