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
    private com.notesphere.app.adapters.FolderAdapter folderAdapter;
    private List<com.notesphere.app.models.Folder> folders = new ArrayList<>();
    private String currentFolderId = null;
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
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (currentFolderId != null) {
                currentFolderId = null;
                binding.tvSubjectName.setText(subjectName);
                fetchFolders();
                fetchMaterials();
            } else {
                finish();
            }
        });

        if (subjectColor != null) {
            try {
                int color = android.graphics.Color.parseColor(subjectColor);
                binding.tvStudyTime.setTextColor(color);
                binding.fabUpload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
                binding.btnNewFolder.setColorFilter(color);
            } catch (Exception e) {}
        }

        setupRecyclerView();
        fetchFolders();
        fetchMaterials();

        binding.fabUpload.setOnClickListener(v -> pickFile());
        binding.btnNewFolder.setOnClickListener(v -> showCreateFolderDialog());
        // Hub Actions
        binding.cardNotes.setOnClickListener(v -> {
            Toast.makeText(this, "Notes system coming soon (Phase 4.5)", Toast.LENGTH_SHORT).show();
        });

        binding.cardFlashcards.setOnClickListener(v -> {
            Intent intent = new Intent(this, FlashcardActivity.class);
            intent.putExtra("materialId", materials.isEmpty() ? "" : materials.get(0).getId());
            intent.putExtra("subjectName", subjectName);
            startActivity(intent);
        });

        binding.cardQuizzes.setOnClickListener(v -> {
            if (materials.isEmpty()) {
                Toast.makeText(this, "Upload a material first to generate a quiz!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, QuizActivity.class);
            intent.putExtra("material_id", materials.get(0).getId());
            startActivity(intent);
        });

        binding.cardVideos.setOnClickListener(v -> {
            Toast.makeText(this, "YouTube Learning System coming soon (Phase 8)", Toast.LENGTH_SHORT).show();
        });

        binding.cardAiWorkspace.setOnClickListener(v -> {
            Intent intent = new Intent(this, DoubtSolverActivity.class);
            intent.putExtra("material_id", materials.isEmpty() ? "" : materials.get(0).getId());
            intent.putExtra("material_title", subjectName + " Knowledge Base");
            startActivity(intent);
        });

        binding.cardActivity.setOnClickListener(v -> {
            Toast.makeText(this, "Activity Tracker coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRecyclerView() {
        adapter = new MaterialAdapter(materials, new MaterialAdapter.OnMaterialClickListener() {
            @Override
            public void onMaterialClick(Material material) {
                Intent intent = new Intent(SubjectDetailActivity.this, MaterialDetailActivity.class);
                intent.putExtra("material_id", material.getId());
                intent.putExtra("material_title", material.getTitle());
                intent.putExtra("material_type", material.getFileType());
                intent.putExtra("material_date", material.getCreatedAt());
                startActivity(intent);
            }

            @Override
            public void onMaterialLongClick(Material material) {
                showMaterialOptions(material);
            }
        });
        binding.rvMaterials.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMaterials.setAdapter(adapter);

        folderAdapter = new com.notesphere.app.adapters.FolderAdapter(folders, new com.notesphere.app.adapters.FolderAdapter.OnFolderClickListener() {
            @Override
            public void onFolderClick(com.notesphere.app.models.Folder folder) {
                currentFolderId = folder.getId();
                binding.tvSubjectName.setText(folder.getName());
                fetchFolders(); // Should be empty inside a folder for now
                fetchMaterials();
            }

            @Override
            public void onFolderLongClick(com.notesphere.app.models.Folder folder) {
                showFolderOptions(folder);
            }
        });
        binding.rvFolders.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFolders.setAdapter(folderAdapter);
    }

    private void fetchFolders() {
        if (currentFolderId != null) {
            folders.clear();
            folderAdapter.notifyDataSetChanged();
            return;
        }
        ApiClient.getInstance().getFolders(subjectId).enqueue(new Callback<List<com.notesphere.app.models.Folder>>() {
            @Override
            public void onResponse(Call<List<com.notesphere.app.models.Folder>> call, Response<List<com.notesphere.app.models.Folder>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    folders.clear();
                    folders.addAll(response.body());
                    folderAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void onFailure(Call<List<com.notesphere.app.models.Folder>> call, Throwable t) {}
        });
    }

    private void fetchMaterials() {
        if (activeCall != null) activeCall.cancel();
        Call<List<Material>> call = ApiClient.getInstance().getMaterials(subjectId, currentFolderId);
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
                Toast.makeText(SubjectDetailActivity.this, "Failed to load materials", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showCreateFolderDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("Folder Name");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("New Folder")
                .setView(et)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) createFolder(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createFolder(String name) {
        com.notesphere.app.models.Folder folder = new com.notesphere.app.models.Folder();
        folder.setName(name);
        // Backend expects subjectId in body if we use the POST /api/folders
        // Wait, I need to make sure Folder model has subjectId. It does.
        // I need to set it.
        try {
            java.lang.reflect.Field field = com.notesphere.app.models.Folder.class.getDeclaredField("subjectId");
            field.setAccessible(true);
            field.set(folder, subjectId);
        } catch (Exception e) {}

        ApiClient.getInstance().createFolder(folder).enqueue(new Callback<com.notesphere.app.models.Folder>() {
            @Override
            public void onResponse(Call<com.notesphere.app.models.Folder> call, Response<com.notesphere.app.models.Folder> response) {
                if (response.isSuccessful()) {
                    fetchFolders();
                }
            }
            @Override
            public void onFailure(Call<com.notesphere.app.models.Folder> call, Throwable t) {}
        });
    }

    private void showMaterialOptions(Material material) {
        String[] options = {"Share / P2P", "Rename", "Delete"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(material.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, ShareActivity.class);
                        intent.putExtra("material_id", material.getId());
                        intent.putExtra("material_title", material.getTitle());
                        startActivity(intent);
                    } else if (which == 1) renameMaterial(material);
                    else deleteMaterial(material);
                })
                .show();
    }

    private void renameMaterial(Material material) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(material.getTitle());
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Rename Material")
                .setView(et)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newTitle = et.getText().toString().trim();
                    if (!newTitle.isEmpty()) {
                        material.setTitle(newTitle);
                        ApiClient.getInstance().updateMaterial(material.getId(), material).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                fetchMaterials();
                            }
                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {}
                        });
                    }
                })
                .show();
    }

    private void deleteMaterial(Material material) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Material")
                .setMessage("Are you sure you want to delete this material from Firebase?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ApiClient.getInstance().deleteMaterial(material.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            fetchMaterials();
                        }
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {}
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFolderOptions(com.notesphere.app.models.Folder folder) {
        String[] options = {"Delete"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(folder.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) deleteFolder(folder);
                })
                .show();
    }

    private void deleteFolder(com.notesphere.app.models.Folder folder) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Folder")
                .setMessage("Delete folder and move its materials to root?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ApiClient.getInstance().deleteFolder(folder.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            fetchFolders();
                            fetchMaterials();
                        }
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {}
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateUIState() {
        binding.tvSubjectInfo.setText("Subject Workspace • " + materials.size() + " Items");
        binding.tvMaterialCount.setText(String.valueOf(materials.size()));
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
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) fileName = cursor.getString(nameIndex);
            }
        } catch (Exception e) {}

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();

        try {
            File file = prepareFile(uri, fileName);
            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "application/octet-stream";
            
            RequestBody requestFile = RequestBody.create(MediaType.parse(mimeType), file);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", fileName, requestFile);
            RequestBody subjectIdPart = RequestBody.create(MediaType.parse("text/plain"), subjectId);
            RequestBody namePart = RequestBody.create(MediaType.parse("text/plain"), fileName);
            
            // Handle folderId if currently inside a folder
            RequestBody folderIdPart = currentFolderId != null ? 
                    RequestBody.create(MediaType.parse("text/plain"), currentFolderId) : null;

            // Update: ApiService uploadMaterial needs folderId too if we want to support it
            // I'll update ApiService again to include folderId in Multipart
            ApiClient.getInstance().uploadMaterialWithFolder(subjectIdPart, folderIdPart, namePart, filePart).enqueue(new Callback<Material>() {
                @Override
                public void onResponse(Call<Material> call, Response<Material> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(SubjectDetailActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                        fetchMaterials();
                        if (file.exists()) file.delete();
                    }
                }
                @Override
                public void onFailure(Call<Material> call, Throwable t) {}
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
