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
    private UnitAdapter adapter;
    private Syllabus currentSyllabus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySyllabusDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        binding.rvUnits.setLayoutManager(new LinearLayoutManager(this));
        showState(State.LOADING);
        loadSyllabus();

        binding.fabUploadSyllabus.setVisibility(View.GONE);
        binding.btnAiAnalyze.setVisibility(View.GONE);
    }

    private void loadSyllabus() {
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        String userId = SharedPrefManager.getInstance(this).getUserId();

        if (userId == null) {
            showState(State.EMPTY);
            return;
        }

        ApiClient.getInstance().getSyllabuses(token, userId).enqueue(new Callback<List<Syllabus>>() {
            @Override
            public void onResponse(Call<List<Syllabus>> call, Response<List<Syllabus>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    currentSyllabus = response.body().get(0);
                    displaySyllabus(currentSyllabus);
                    showState(State.CONTENT);
                } else {
                    showState(State.EMPTY);
                }
            }

            @Override
            public void onFailure(Call<List<Syllabus>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                showState(State.ERROR);
                Toast.makeText(SyllabusDashboardActivity.this,
                    "Network error. Check your connection.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displaySyllabus(Syllabus syllabus) {
        if (syllabus == null) { showState(State.EMPTY); return; }

        binding.tvSubjectName.setText(syllabus.getTitle() != null ? syllabus.getTitle() : "Syllabus");
        updateProgressUI(syllabus);

        // Null-safe content tree
        List<Syllabus.Unit> units = syllabus.getContentTree();
        if (units == null) units = new ArrayList<>();

        adapter = new UnitAdapter(units, (topic, isChecked) -> {
            topic.setCompleted(isChecked);
            if (currentSyllabus != null) updateSyllabusOnServer(currentSyllabus);
        });
        binding.rvUnits.setAdapter(adapter);
    }

    private void updateProgressUI(Syllabus syllabus) {
        if (syllabus == null) return;

        List<Syllabus.Unit> units = syllabus.getContentTree();
        if (units == null || units.isEmpty()) {
            binding.tvProgressPercent.setText("0%");
            binding.progressSyllabus.setProgress(0);
            binding.tvSyllabusStatus.setText("No topics added yet");
            return;
        }

        int totalTopics = 0;
        int completedTopics = 0;
        for (Syllabus.Unit unit : units) {
            if (unit.getTopics() == null) continue;
            totalTopics += unit.getTopics().size();
            for (Syllabus.Topic topic : unit.getTopics()) {
                if (topic != null && topic.isCompleted()) completedTopics++;
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
            public void onResponse(Call<Syllabus> call, Response<Syllabus> response) {}
            @Override
            public void onFailure(Call<Syllabus> call, Throwable t) {
                if (!isFinishing()) Toast.makeText(SyllabusDashboardActivity.this, "Sync failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private enum State { LOADING, CONTENT, EMPTY, ERROR }

    private void showState(State state) {
        // Null-safe view state management
        if (binding == null) return;
        binding.rvUnits.setVisibility(state == State.CONTENT ? View.VISIBLE : View.GONE);
        if (binding.layoutEmptySyllabus != null)
            binding.layoutEmptySyllabus.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
        if (binding.progressLoading != null)
            binding.progressLoading.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        if (binding.layoutErrorSyllabus != null)
            binding.layoutErrorSyllabus.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
    }
}
