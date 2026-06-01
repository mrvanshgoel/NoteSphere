package com.notesphere.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.JsonObject;
import com.notesphere.app.R;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityNoteEditorBinding;
import com.notesphere.app.models.Note;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NoteEditorActivity extends AppCompatActivity {
    private ActivityNoteEditorBinding binding;
    private String subjectId;
    private String noteId;
    private boolean isPinned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNoteEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subjectId = getIntent().getStringExtra("subjectId");
        noteId = getIntent().getStringExtra("noteId");

        if (subjectId == null) {
            Toast.makeText(this, "Subject missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> saveNote());
        
        binding.btnPin.setOnClickListener(v -> {
            isPinned = !isPinned;
            updatePinIcon();
        });

        if (noteId != null) {
            binding.btnDelete.setVisibility(View.VISIBLE);
            binding.btnDelete.setOnClickListener(v -> deleteNote());
            
            String title = getIntent().getStringExtra("noteTitle");
            String content = getIntent().getStringExtra("noteContent");
            isPinned = getIntent().getBooleanExtra("notePinned", false);
            
            binding.etNoteTitle.setText(title);
            binding.etNoteContent.setText(content);
            updatePinIcon();
        }
    }

    private void updatePinIcon() {
        binding.btnPin.setImageResource(isPinned ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        binding.btnPin.setColorFilter(isPinned ? 0xFF6C63FF : 0xFF8E8E93);
    }

    private void saveNote() {
        String title = binding.etNoteTitle.getText().toString().trim();
        String content = binding.etNoteContent.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note(title, content, isPinned, null);
        binding.loadingAnimation.setVisibility(View.VISIBLE);
        binding.btnSave.setEnabled(false);

        if (noteId == null) {
            ApiClient.getInstance().createNote(subjectId, note).enqueue(new Callback<Note>() {
                @Override
                public void onResponse(Call<Note> call, Response<Note> response) {
                    binding.loadingAnimation.setVisibility(View.GONE);
                    binding.btnSave.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        Toast.makeText(NoteEditorActivity.this, "Note created", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(NoteEditorActivity.this, "Failed to create note", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<Note> call, Throwable t) {
                    binding.loadingAnimation.setVisibility(View.GONE);
                    binding.btnSave.setEnabled(true);
                    Toast.makeText(NoteEditorActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            ApiClient.getInstance().updateNote(subjectId, noteId, note).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    binding.loadingAnimation.setVisibility(View.GONE);
                    binding.btnSave.setEnabled(true);
                    if (response.isSuccessful()) {
                        Toast.makeText(NoteEditorActivity.this, "Note saved", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(NoteEditorActivity.this, "Failed to save note", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    binding.loadingAnimation.setVisibility(View.GONE);
                    binding.btnSave.setEnabled(true);
                    Toast.makeText(NoteEditorActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void deleteNote() {
        binding.loadingAnimation.setVisibility(View.VISIBLE);
        ApiClient.getInstance().deleteNote(subjectId, noteId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                binding.loadingAnimation.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(NoteEditorActivity.this, "Note deleted", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(NoteEditorActivity.this, "Failed to delete", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                binding.loadingAnimation.setVisibility(View.GONE);
                Toast.makeText(NoteEditorActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
