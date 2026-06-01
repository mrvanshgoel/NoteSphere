package com.notesphere.app.activities;

import android.os.Bundle;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NoteEditorActivity extends AppCompatActivity {

    private EditText etTitle, etContent;
    private TextView tvPreview;
    private ScrollView scrollPreview;
    private Button btnEdit, btnPreview, btnSave;
    private MaterialToolbar toolbar;

    private String subjectId;
    private String noteId;

    private Markwon markwon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        subjectId = getIntent().getStringExtra("subjectId");
        noteId = getIntent().getStringExtra("noteId");

        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        tvPreview = findViewById(R.id.tvPreview);
        scrollPreview = findViewById(R.id.scrollPreview);
        btnEdit = findViewById(R.id.btnEdit);
        btnPreview = findViewById(R.id.btnPreview);
        btnSave = findViewById(R.id.btnSave);
        toolbar = findViewById(R.id.toolbar);

        toolbar.setNavigationOnClickListener(v -> finish());
        
        markwon = Markwon.create(this);

        btnEdit.setOnClickListener(v -> {
            etContent.setVisibility(View.VISIBLE);
            scrollPreview.setVisibility(View.GONE);
        });

        btnPreview.setOnClickListener(v -> {
            etContent.setVisibility(View.GONE);
            scrollPreview.setVisibility(View.VISIBLE);
            markwon.setMarkdown(tvPreview, etContent.getText().toString());
        });

        btnSave.setOnClickListener(v -> saveNote());

        if (noteId != null) {
            // TODO: Fetch existing note
            toolbar.setTitle("Edit Note");
        } else {
            toolbar.setTitle("New Note");
        }
    }

    private void saveNote() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString();

        if (title.isEmpty()) {
            Toast.makeText(this, "Title required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mock saving for now - backend implementation is needed for POST /api/study/subjects/:subjectId/notes
        Toast.makeText(this, "Note saved locally (Sync coming soon)", Toast.LENGTH_SHORT).show();
        finish();
    }
}
