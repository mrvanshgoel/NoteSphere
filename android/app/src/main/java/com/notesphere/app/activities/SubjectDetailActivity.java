package com.notesphere.app.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.notesphere.app.adapters.MaterialAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivitySubjectDetailBinding;
import com.notesphere.app.models.Material;
import com.notesphere.app.utils.SharedPrefManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SubjectDetailActivity extends AppCompatActivity {
    private ActivitySubjectDetailBinding binding;
    private String subjectId;
    private String subjectName;
    private MaterialAdapter adapter;
    private List<Material> materials = new ArrayList<>();
    private Call<?> activeCall;
    private Uri selectedFileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubjectDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subjectId = getIntent().getStringExtra("subject_id");
        subjectName = getIntent().getStringExtra("subject_name");
        String subjectColor = getIntent().getStringExtra("subject_color");

        if (subjectId == null) {
            finish();
            return;
        }

        binding.tvSubjectName.setText(subjectName);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        if (subjectColor != null) {
            try {
                int color = android.graphics.Color.parseColor(subjectColor);
                binding.tvProgressPercent.setTextColor(color);
                binding.pbSyllabus.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
                binding.fabUpload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
            } catch (Exception e) {}
        }

        setupRecyclerView();
        fetchMaterials();

        binding.fabUpload.setOnClickListener(v -> pickFile());
        
        // Quick AI Actions
        binding.chipQuiz.setOnClickListener(v -> {
            if (materials.isEmpty()) {
                Toast.makeText(this, "Upload a material first to generate a quiz!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra("material_id", materials.get(0).getId()); // Use first material as default
            startActivity(intent);
        });

        binding.chipNotes.setOnClickListener(v -> {
            if (materials.isEmpty()) {
                Toast.makeText(this, "Upload a material first!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, MaterialDetailActivity.class);
            intent.putExtra("material_id", materials.get(0).getId());
            intent.putExtra("material_title", materials.get(0).getTitle());
            intent.putExtra("auto_action", "notes");
            startActivity(intent);
        });

        binding.chipSyllabus.setOnClickListener(v -> {
            Intent intent = new Intent(this, SyllabusDashboardActivity.class);
            startActivity(intent);
        });
        
        binding.cardSyllabus.setOnClickListener(v -> {
            Intent intent = new Intent(this, SyllabusDashboardActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        adapter = new MaterialAdapter(materials, material -> {
            Intent intent = new Intent(this, MaterialDetailActivity.class);
            intent.putExtra("material_id", material.getId());
            intent.putExtra("material_title", material.getTitle());
            intent.putExtra("material_type", material.getFileType());
            intent.putExtra("material_date", material.getCreatedAt());
            startActivity(intent);
        });
        binding.rvMaterials.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMaterials.setAdapter(adapter);
    }

    private void fetchMaterials() {
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        
        if (activeCall != null) activeCall.cancel();
        Call<List<Material>> call = ApiClient.getInstance().getMaterials(token, subjectId);
        activeCall = call;

        call.enqueue(new Callback<List<Material>>() {
            @Override
            public void onResponse(Call<List<Material>> call, Response<List<Material>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    materials.clear();
                    materials.addAll(response.body());
                    adapter.notifyDataSetChanged();
                    updateUIState();
                }
            }

            @Override
            public void onFailure(Call<List<Material>> call, Throwable t) {
                if (call.isCanceled()) return;
                android.util.Log.e("API_ERROR", "Fetch Materials Failed", t);
                Toast.makeText(SubjectDetailActivity.this, "Failed to load materials: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUIState() {
        binding.tvSubjectInfo.setText("Subject Workspace • " + materials.size() + " Materials");
        if (materials.isEmpty()) {
            // Show empty state if needed
        }
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"application/pdf", "image/*", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    if (selectedFileUri != null) {
                        uploadFile(selectedFileUri);
                    }
                }
            }
    );

    private void uploadFile(Uri uri) {
        String fileName = "upload_" + System.currentTimeMillis();
        // Extract real name if possible
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) fileName = cursor.getString(nameIndex);
            }
        } catch (Exception e) {}

        Toast.makeText(this, "Uploading " + fileName + "...", Toast.LENGTH_SHORT).show();

        try {
            File file = prepareFile(uri, fileName);
            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "application/octet-stream";
            
            RequestBody requestFile = RequestBody.create(MediaType.parse(mimeType), file);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", fileName, requestFile);
            RequestBody subjectIdPart = RequestBody.create(MediaType.parse("text/plain"), subjectId);
            RequestBody namePart = RequestBody.create(MediaType.parse("text/plain"), fileName);

            String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
            
            ApiClient.getInstance().uploadMaterial(token, subjectIdPart, namePart, filePart).enqueue(new Callback<Material>() {
                @Override
                public void onResponse(Call<Material> call, Response<Material> response) {
                    if (isFinishing() || isDestroyed()) return;
                    if (response.isSuccessful()) {
                        Toast.makeText(SubjectDetailActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                        fetchMaterials(); // Refresh list
                        if (file.exists()) file.delete();
                    } else {
                        Toast.makeText(SubjectDetailActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Material> call, Throwable t) {
                    if (isFinishing() || isDestroyed()) return;
                    String msg = t.getMessage() != null ? t.getMessage() : "Unknown network error";
                    Toast.makeText(SubjectDetailActivity.this, "Upload failed: " + msg, Toast.LENGTH_LONG).show();
                    android.util.Log.e("UPLOAD", "Error: " + msg, t);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error preparing file", Toast.LENGTH_SHORT).show();
        }
    }

    private File prepareFile(Uri uri, String name) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        if (is == null) throw new IOException("Unable to open input stream for URI: " + uri);
        
        File file = new File(getCacheDir(), name);
        android.util.Log.d("UPLOAD", "Preparing file: " + file.getAbsolutePath());
        
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
        fos.flush();
        fos.close();
        is.close();
        return file;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
