package com.notesphere.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.notesphere.app.R;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.models.Note;
import io.noties.markwon.Markwon;
import java.util.ArrayList;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * NoteEditorActivity — Full Markdown note editor with:
 * • Auto-save (2 second debounce after keystroke)
 * • Markdown preview toggle (Edit / Preview)
 * • Pin / unpin via toolbar icon
 * • Real API calls to create and update notes
 * • Load existing note by noteId
 * • "Saved ✓" / "Saving..." status in toolbar subtitle
 */
public class NoteEditorActivity extends AppCompatActivity {

    private static final int AUTO_SAVE_DELAY_MS = 2000;

    private EditText etTitle, etContent;
    private TextView tvPreview, tvSaveStatus;
    private ScrollView scrollPreview;
    private Button btnEdit, btnPreview;
    private MaterialToolbar toolbar;

    private String subjectId;
    private String noteId;       // null = new note
    private boolean isPinned = false;
    private boolean isPreviewMode = false;
    private boolean isDirty = false;
    private boolean isSaving = false;

    private Markwon markwon;
    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSaveRunnable = this::performSave;

    // ─────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        subjectId = getIntent().getStringExtra("subjectId");
        noteId    = getIntent().getStringExtra("noteId");

        initViews();
        setupMarkdown();
        setupToolbar();
        setupTextWatcher();
        setupModeToggle();

        if (noteId != null) {
            loadExistingNote();
        } else {
            toolbar.setTitle("New Note");
            setSaveStatus("Unsaved");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Save immediately when leaving the screen
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        if (isDirty) performSave();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
    }

    // ─────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────

    private void initViews() {
        etTitle      = findViewById(R.id.etTitle);
        etContent    = findViewById(R.id.etContent);
        tvPreview    = findViewById(R.id.tvPreview);
        scrollPreview = findViewById(R.id.scrollPreview);
        btnEdit      = findViewById(R.id.btnEdit);
        btnPreview   = findViewById(R.id.btnPreview);
        toolbar      = findViewById(R.id.toolbar);
    }

    private void setupMarkdown() {
        markwon = Markwon.create(this);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            autoSaveHandler.removeCallbacks(autoSaveRunnable);
            if (isDirty) performSave();
            finish();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Pin action in toolbar
        menu.add(Menu.NONE, R.id.btnSave, 0, isPinned ? "Unpin" : "Pin")
            .setIcon(isPinned ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.btnSave) {
            isPinned = !isPinned;
            item.setTitle(isPinned ? "Unpin" : "Pin");
            item.setIcon(isPinned ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            isDirty = true;
            scheduleAutoSave();
            Toast.makeText(this, isPinned ? "📌 Note pinned" : "Note unpinned", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupTextWatcher() {
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                isDirty = true;
                setSaveStatus("Unsaved");
                scheduleAutoSave();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        };
        etTitle.addTextChangedListener(watcher);
        etContent.addTextChangedListener(watcher);
    }

    private void setupModeToggle() {
        btnEdit.setOnClickListener(v -> {
            isPreviewMode = false;
            etContent.setVisibility(View.VISIBLE);
            scrollPreview.setVisibility(View.GONE);
        });

        btnPreview.setOnClickListener(v -> {
            isPreviewMode = true;
            etContent.setVisibility(View.GONE);
            scrollPreview.setVisibility(View.VISIBLE);
            markwon.setMarkdown(tvPreview, etContent.getText().toString());
        });
    }

    // ─────────────────────────────────────────────────────────
    // Auto-Save
    // ─────────────────────────────────────────────────────────

    private void scheduleAutoSave() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS);
    }

    private void setSaveStatus(String status) {
        runOnUiThread(() -> toolbar.setSubtitle(status));
    }

    private void performSave() {
        String title   = etTitle.getText().toString().trim();
        String content = etContent.getText().toString();

        if (title.isEmpty() && content.isEmpty()) return; // nothing to save
        if (isSaving) return;

        if (title.isEmpty()) {
            // Auto-generate title from first line of content
            String firstLine = content.split("\n")[0].replaceAll("#", "").trim();
            title = firstLine.isEmpty() ? "Untitled Note" : firstLine;
        }

        isSaving = true;
        setSaveStatus("Saving...");

        Note note = new Note(title, content, isPinned, new ArrayList<>());

        if (noteId == null) {
            // CREATE
            Call<Note> call = ApiClient.getInstance().createNote(subjectId, note);
            call.enqueue(new Callback<Note>() {
                @Override
                public void onResponse(Call<Note> call, Response<Note> response) {
                    isSaving = false;
                    if (response.isSuccessful() && response.body() != null) {
                        noteId = response.body().getId();
                        isDirty = false;
                        setSaveStatus("Saved ✓");
                        if (getSupportActionBar() != null) {
                            runOnUiThread(() -> toolbar.setTitle("Edit Note"));
                        }
                    } else {
                        setSaveStatus("Save failed");
                    }
                }
                @Override
                public void onFailure(Call<Note> call, Throwable t) {
                    isSaving = false;
                    setSaveStatus("Offline — will retry");
                }
            });
        } else {
            // UPDATE
            Call<com.google.gson.JsonObject> call = ApiClient.getInstance().updateNote(subjectId, noteId, note);
            call.enqueue(new Callback<com.google.gson.JsonObject>() {
                @Override
                public void onResponse(Call<com.google.gson.JsonObject> call, Response<com.google.gson.JsonObject> response) {
                    isSaving = false;
                    if (response.isSuccessful()) {
                        isDirty = false;
                        setSaveStatus("Saved ✓");
                    } else {
                        setSaveStatus("Save failed");
                    }
                }
                @Override
                public void onFailure(Call<com.google.gson.JsonObject> call, Throwable t) {
                    isSaving = false;
                    setSaveStatus("Offline — will retry");
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────
    // Load Existing Note
    // ─────────────────────────────────────────────────────────

    private void loadExistingNote() {
        toolbar.setTitle("Loading...");
        setSaveStatus("");
        // Fetch all notes for subject, then find the matching one
        // (Backend has no GET single-note endpoint yet — we use the list)
        ApiClient.getInstance().getNotes(subjectId).enqueue(new Callback<java.util.List<Note>>() {
            @Override
            public void onResponse(Call<java.util.List<Note>> call, Response<java.util.List<Note>> response) {
                if (!isFinishing() && response.isSuccessful() && response.body() != null) {
                    for (Note n : response.body()) {
                        if (noteId.equals(n.getId())) {
                            populateNote(n);
                            return;
                        }
                    }
                }
                toolbar.setTitle("Edit Note");
            }
            @Override
            public void onFailure(Call<java.util.List<Note>> call, Throwable t) {
                toolbar.setTitle("Edit Note (offline)");
            }
        });
    }

    private void populateNote(Note note) {
        runOnUiThread(() -> {
            etTitle.setText(note.getTitle());
            etContent.setText(note.getContent());
            isPinned = note.isPinned();
            toolbar.setTitle("Edit Note");
            setSaveStatus("Saved ✓");
            isDirty = false;
            invalidateOptionsMenu(); // refresh pin icon
        });
    }
}
