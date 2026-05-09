package com.notesphere.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.notesphere.app.adapters.UnitAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivitySyllabusDashboardBinding;
import com.notesphere.app.models.Syllabus;
import com.notesphere.app.utils.SharedPrefManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SyllabusDashboardActivity extends AppCompatActivity {
    private ActivitySyllabusDashboardBinding binding;
    private List<Syllabus> syllabusList = new ArrayList<>();
    private UnitAdapter adapter;
    private Syllabus currentSyllabus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySyllabusDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        loadSyllabus();

        binding.fabUploadSyllabus.setOnClickListener(v -> {
            Toast.makeText(this, "Upload Syllabus PDF to extract units (Phase 2 feature)", Toast.LENGTH_SHORT).show();
        });

        binding.btnAiAnalyze.setOnClickListener(v -> {
            Toast.makeText(this, "AI analyzing your progress and recommending study plans...", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRecyclerView() {
        binding.rvUnits.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadSyllabus() {
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        String userId = SharedPrefManager.getInstance(this).getUserId();

        ApiClient.getInstance().getSyllabuses(token, userId).enqueue(new Callback<List<Syllabus>>() {
            @Override
            public void onResponse(Call<List<Syllabus>> call, Response<List<Syllabus>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    currentSyllabus = response.body().get(0); // For demo, use first syllabus
                    displaySyllabus(currentSyllabus);
                } else {
                    Toast.makeText(SyllabusDashboardActivity.this, "No syllabus found. Upload one to get started!", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<Syllabus>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(SyllabusDashboardActivity.this, "Error loading syllabus", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displaySyllabus(Syllabus syllabus) {
        binding.tvSubjectName.setText(syllabus.getTitle());
        updateProgressUI(syllabus);

        adapter = new UnitAdapter(syllabus.getContentTree(), (topic, isChecked) -> {
            updateSyllabusOnServer(syllabus);
        });
        binding.rvUnits.setAdapter(adapter);
    }

    private void updateProgressUI(Syllabus syllabus) {
        int totalTopics = 0;
        int completedTopics = 0;
        
        for (Syllabus.Unit unit : syllabus.getContentTree()) {
            totalTopics += unit.getTopics().size();
            for (Syllabus.Topic topic : unit.getTopics()) {
                if (topic.isCompleted()) completedTopics++;
            }
        }

        int progress = (totalTopics > 0) ? (completedTopics * 100 / totalTopics) : 0;
        binding.tvProgressPercent.setText(progress + "%");
        binding.progressSyllabus.setProgress(progress);
        binding.tvSyllabusStatus.setText(completedTopics + " of " + totalTopics + " topics completed");
    }

    private void updateSyllabusOnServer(Syllabus syllabus) {
        updateProgressUI(syllabus);
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        
        ApiClient.getInstance().updateSyllabus(token, syllabus.getId(), syllabus).enqueue(new Callback<Syllabus>() {
            @Override
            public void onResponse(Call<Syllabus> call, Response<Syllabus> response) {
                // Silently updated
            }

            @Override
            public void onFailure(Call<Syllabus> call, Throwable t) {
                Toast.makeText(SyllabusDashboardActivity.this, "Failed to sync progress", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
