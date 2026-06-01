package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.notesphere.app.adapters.NotesAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityNotesListBinding;
import com.notesphere.app.models.Note;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotesListActivity extends AppCompatActivity {
    private ActivityNotesListBinding binding;
    private NotesAdapter adapter;
    private List<Note> allNotes = new ArrayList<>();
    private String subjectId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotesListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subjectId = getIntent().getStringExtra("subjectId");
        if (subjectId == null) {
            Toast.makeText(this, "Subject ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());
        
        adapter = new NotesAdapter(new ArrayList<>(), note -> {
            Intent intent = new Intent(this, NoteEditorActivity.class);
            intent.putExtra("subjectId", subjectId);
            intent.putExtra("noteId", note.getId());
            intent.putExtra("noteTitle", note.getTitle());
            intent.putExtra("noteContent", note.getContent());
            intent.putExtra("notePinned", note.isPinned());
            startActivity(intent);
        });

        binding.rvNotes.setLayoutManager(new LinearLayoutManager(this));
        binding.rvNotes.setAdapter(adapter);

        binding.fabAddNote.setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteEditorActivity.class);
            intent.putExtra("subjectId", subjectId);
            startActivity(intent);
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterNotes(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchNotes();
    }

    private void fetchNotes() {
        binding.loadingAnimation.setVisibility(View.VISIBLE);
        ApiClient.getInstance().getNotes(subjectId).enqueue(new Callback<List<Note>>() {
            @Override
            public void onResponse(Call<List<Note>> call, Response<List<Note>> response) {
                if (isFinishing() || isDestroyed()) return;
                binding.loadingAnimation.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    allNotes = response.body();
                    
                    // Sort pinned first
                    allNotes.sort((n1, n2) -> Boolean.compare(n2.isPinned(), n1.isPinned()));
                    
                    filterNotes(binding.etSearch.getText().toString());
                } else {
                    Toast.makeText(NotesListActivity.this, "Failed to load notes", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Note>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                binding.loadingAnimation.setVisibility(View.GONE);
                Toast.makeText(NotesListActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterNotes(String query) {
        if (query.isEmpty()) {
            adapter.updateList(allNotes);
            binding.layoutEmpty.setVisibility(allNotes.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        List<Note> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (Note note : allNotes) {
            if (note.getTitle().toLowerCase().contains(lowerQuery) || 
                (note.getContent() != null && note.getContent().toLowerCase().contains(lowerQuery))) {
                filtered.add(note);
            }
        }
        adapter.updateList(filtered);
        binding.layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
