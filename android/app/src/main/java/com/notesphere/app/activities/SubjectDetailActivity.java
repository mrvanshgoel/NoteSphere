package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivitySubjectDetailBinding;
import com.notesphere.app.models.Material;
import com.notesphere.app.activities.NoteEditorActivity;
import com.notesphere.app.activities.NotesListActivity;
import com.notesphere.app.activities.FlashcardActivity;
import com.notesphere.app.activities.QuizActivity;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SubjectDetailActivity extends AppCompatActivity {
    private ActivitySubjectDetailBinding binding;
    private String subjectId;
    private String subjectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubjectDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subjectId = getIntent().getStringExtra("subject_id");
        subjectName = getIntent().getStringExtra("subject_name");

        if (subjectId == null) {
            finish();
            return;
        }

        binding.tvSubjectName.setText(subjectName);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupCards();
        fetchData();
    }

    private void setupCards() {
        // Expand/Collapse logic can be added here if needed, but for now they are static hubs
        
        binding.btnViewMaterials.setOnClickListener(v -> {
            Intent intent = new Intent(this, FolderExplorerActivity.class);
            intent.putExtra("subjectId", subjectId);
            intent.putExtra("subjectName", subjectName);
            startActivity(intent);
        });

        binding.btnNewNote.setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteEditorActivity.class);
            intent.putExtra("subject_id", subjectId);
            startActivity(intent);
        });
        
        binding.btnViewNotes.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotesListActivity.class);
            intent.putExtra("subjectId", subjectId); // FIXED key mismatch
            startActivity(intent);
        });

        binding.btnAiChat.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("navigate_to", "chat");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });

        // Removed btnQuickContinue entirely (dead button)
        binding.btnQuickContinue.setVisibility(android.view.View.GONE);

        binding.btnFlashcards.setOnClickListener(v -> {
            Intent intent = new Intent(this, FlashcardActivity.class);
            intent.putExtra("subjectId", subjectId); // Now passes subjectId
            intent.putExtra("subjectName", subjectName);
            startActivity(intent);
        });

        binding.btnQuizzes.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra("subject_id", subjectId);
            startActivity(intent);
        });
        
        binding.btnQuickContinue.setOnClickListener(v -> {
            Toast.makeText(this, "Resuming last activity...", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchData() {
        // Fetch Materials count
        ApiClient.getInstance().getMaterials(subjectId, null).enqueue(new Callback<List<Material>>() {
            @Override
            public void onResponse(Call<List<Material>> call, Response<List<Material>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    int count = response.body().size();
                    binding.tvMaterialCount.setText(count + " items");
                    if (count > 0) {
                        binding.tvEmptyMaterials.setVisibility(View.GONE);
                    } else {
                        binding.tvEmptyMaterials.setVisibility(View.VISIBLE);
                    }
                }
            }
            @Override
            public void onFailure(Call<List<Material>> call, Throwable t) {}
        });

        // Add Notes fetch logic here later
        binding.tvNotesCount.setText("0 notes");
    }
}
