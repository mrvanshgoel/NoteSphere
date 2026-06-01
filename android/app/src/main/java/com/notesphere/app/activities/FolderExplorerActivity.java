package com.notesphere.app.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.notesphere.app.R;
import com.notesphere.app.adapters.FolderAdapter;
import com.notesphere.app.adapters.MaterialAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.models.Folder;
import com.notesphere.app.models.Material;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FolderExplorerActivity extends AppCompatActivity {

    private String subjectId;
    private String subjectName;

    private FolderAdapter folderAdapter;
    private List<Folder> folders = new ArrayList<>();
    private String currentFolderId = null;

    private MaterialAdapter materialAdapter;
    private List<Material> materials = new ArrayList<>();

    private static class BreadcrumbItem {
        String id;
        String name;
        BreadcrumbItem(String id, String name) { this.id = id; this.name = name; }
    }
    private List<BreadcrumbItem> breadcrumbStack = new ArrayList<>();

    private Call<?> activeCall;

    // Upload
    private Uri selectedFileUri;
    private String selectedFileName;
    private ProgressBar uploadProgress;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    extractFileNameAndUpload(selectedFileUri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_explorer);

        subjectId = getIntent().getStringExtra("subjectId");
        subjectName = getIntent().getStringExtra("subjectName");

        uploadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        uploadProgress.setVisibility(View.GONE);

        setupRecyclerViews();

        breadcrumbStack.clear();
        breadcrumbStack.add(new BreadcrumbItem(null, subjectName != null ? subjectName : "Files"));
        updateBreadcrumbUI();

        fetchFolders();
        fetchMaterials();

        findViewById(R.id.btnNewFolder).setOnClickListener(v -> showCreateFolderDialog());

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ExtendedFloatingActionButton fabUpload = findViewById(R.id.fabUpload);
        fabUpload.setOnClickListener(v -> launchFilePicker());
    }

    // ─── Upload ──────────────────────────────────────────────

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
            "application/pdf",
            "image/*",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void extractFileNameAndUpload(Uri uri) {
        selectedFileName = "file_" + System.currentTimeMillis();
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx != -1) selectedFileName = cursor.getString(idx);
            }
        } catch (Exception e) {
            android.util.Log.e("EXPLORER_UPLOAD", "Filename extraction failed", e);
        }
        Toast.makeText(this, "Uploading: " + selectedFileName, Toast.LENGTH_SHORT).show();
        performUpload(0);
    }

    private void performUpload(int retryCount) {
        if (selectedFileUri == null || subjectId == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(selectedFileUri);
            File file = new File(getCacheDir(), selectedFileName);
            FileOutputStream fos = new FileOutputStream(file);
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) fos.write(buf, 0, read);
            fos.flush(); fos.close(); is.close();

            String mime = getContentResolver().getType(selectedFileUri);
            if (mime == null) mime = "application/octet-stream";

            RequestBody requestFile = RequestBody.create(MediaType.parse(mime), file);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", selectedFileName, requestFile);
            RequestBody subjectPart = RequestBody.create(MediaType.parse("text/plain"), subjectId);
            RequestBody namePart = RequestBody.create(MediaType.parse("text/plain"), selectedFileName);
            RequestBody folderPart = currentFolderId != null
                    ? RequestBody.create(MediaType.parse("text/plain"), currentFolderId)
                    : null;

            Call<Material> call = folderPart != null
                    ? ApiClient.getInstance().uploadMaterialWithFolder(subjectPart, folderPart, namePart, filePart)
                    : ApiClient.getInstance().uploadMaterial(subjectPart, namePart, filePart);

            call.enqueue(new Callback<Material>() {
                @Override public void onResponse(Call<Material> call, Response<Material> response) {
                    if (isDestroyed()) return;
                    if (response.isSuccessful()) {
                        Toast.makeText(FolderExplorerActivity.this, "✓ Uploaded: " + selectedFileName, Toast.LENGTH_SHORT).show();
                        fetchMaterials();
                        if (file.exists()) file.delete();
                    } else {
                        if (response.code() >= 500 && retryCount < 2) { performUpload(retryCount + 1); return; }
                        Toast.makeText(FolderExplorerActivity.this, "Upload failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<Material> call, Throwable t) {
                    if (isDestroyed()) return;
                    if (retryCount < 2) { performUpload(retryCount + 1); return; }
                    Toast.makeText(FolderExplorerActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "File error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ─── RecyclerViews ───────────────────────────────────────

    private void setupRecyclerViews() {
        RecyclerView rvMaterials = findViewById(R.id.rvMaterials);
        materialAdapter = new MaterialAdapter(materials, new MaterialAdapter.OnMaterialClickListener() {
            @Override
            public void onMaterialClick(Material material) {
                Intent intent = new Intent(FolderExplorerActivity.this, MaterialDetailActivity.class);
                intent.putExtra("material_id", material.getId());
                intent.putExtra("material_title", material.getTitle());
                intent.putExtra("material_type", material.getFileType());
                intent.putExtra("material_date", material.getCreatedAt());
                startActivity(intent);
            }

            @Override
            public void onMaterialLongClick(Material material) {
                showMaterialOptionsSheet(material);
            }
        });
        rvMaterials.setLayoutManager(new LinearLayoutManager(this));
        rvMaterials.setAdapter(materialAdapter);

        RecyclerView rvFolders = findViewById(R.id.rvFolders);
        folderAdapter = new FolderAdapter(folders, new FolderAdapter.OnFolderClickListener() {
            @Override
            public void onFolderClick(Folder folder) {
                currentFolderId = folder.getId();
                breadcrumbStack.add(new BreadcrumbItem(folder.getId(), folder.getName()));
                updateBreadcrumbUI();
                fetchFolders();
                fetchMaterials();
            }

            @Override
            public void onFolderLongClick(Folder folder) {
                showFolderOptionsSheet(folder);
            }
        });
        rvFolders.setLayoutManager(new LinearLayoutManager(this));
        rvFolders.setAdapter(folderAdapter);
    }

    // ─── Breadcrumb ──────────────────────────────────────────

    private void updateBreadcrumbUI() {
        LinearLayout llBreadcrumbs = findViewById(R.id.llBreadcrumbs);
        if (llBreadcrumbs == null) return;

        llBreadcrumbs.removeAllViews();

        // Resolve selectableItemBackground the correct way using TypedValue
        TypedValue selectableAttr = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableAttr, true);
        int selectableResId = selectableAttr.resourceId;

        for (int i = 0; i < breadcrumbStack.size(); i++) {
            BreadcrumbItem item = breadcrumbStack.get(i);

            TextView tv = new TextView(this);
            tv.setText(item.name);
            tv.setTextSize(14);
            tv.setPadding(8, 8, 8, 8);

            if (i == breadcrumbStack.size() - 1) {
                tv.setTextColor(Color.parseColor("#6C63FF"));
                tv.setTypeface(null, Typeface.BOLD);
            } else {
                tv.setTextColor(Color.GRAY);
                tv.setClickable(true);
                tv.setFocusable(true);
                if (selectableResId != 0) {
                    tv.setBackgroundResource(selectableResId);
                }
                int indexToPopTo = i;
                tv.setOnClickListener(v -> {
                    while (breadcrumbStack.size() > indexToPopTo + 1) {
                        breadcrumbStack.remove(breadcrumbStack.size() - 1);
                    }
                    currentFolderId = breadcrumbStack.get(breadcrumbStack.size() - 1).id;
                    updateBreadcrumbUI();
                    fetchFolders();
                    fetchMaterials();
                });
            }
            llBreadcrumbs.addView(tv);

            if (i < breadcrumbStack.size() - 1) {
                TextView sep = new TextView(this);
                sep.setText(" › ");
                sep.setTextColor(Color.GRAY);
                llBreadcrumbs.addView(sep);
            }
        }
    }

    // ─── Fetch ───────────────────────────────────────────────

    private void fetchFolders() {
        ApiClient.getInstance().getFolders(subjectId, currentFolderId).enqueue(new Callback<List<Folder>>() {
            @Override
            public void onResponse(Call<List<Folder>> call, Response<List<Folder>> response) {
                if (isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    folders.clear();
                    folders.addAll(response.body());
                    folderAdapter.notifyDataSetChanged();
                    android.util.Log.d("EXPLORER", "[Folders] Loaded " + folders.size() + " folders for folderId=" + currentFolderId);
                }
            }
            @Override
            public void onFailure(Call<List<Folder>> call, Throwable t) {
                android.util.Log.e("EXPLORER", "[Folders] Fetch failed: " + t.getMessage());
            }
        });
    }

    private void fetchMaterials() {
        if (activeCall != null) activeCall.cancel();
        Call<List<Material>> call = ApiClient.getInstance().getMaterials(subjectId, currentFolderId);
        activeCall = call;

        call.enqueue(new Callback<List<Material>>() {
            @Override
            public void onResponse(Call<List<Material>> call, Response<List<Material>> response) {
                if (isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null) {
                    materials.clear();
                    materials.addAll(response.body());
                    materialAdapter.notifyDataSetChanged();
                    android.util.Log.d("EXPLORER", "[Materials] Loaded " + materials.size() + " materials for folderId=" + currentFolderId);
                }
            }
            @Override
            public void onFailure(Call<List<Material>> call, Throwable t) {
                android.util.Log.e("EXPLORER", "[Materials] Fetch failed: " + t.getMessage());
            }
        });
    }

    // ─── Dialogs ─────────────────────────────────────────────

    private void showCreateFolderDialog() {
        EditText et = new EditText(this);
        et.setHint("Folder Name");
        new AlertDialog.Builder(this)
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
        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(currentFolderId);
        try {
            java.lang.reflect.Field field = Folder.class.getDeclaredField("subjectId");
            field.setAccessible(true);
            field.set(folder, subjectId);
        } catch (Exception e) {
            android.util.Log.e("EXPLORER", "Reflection error setting subjectId", e);
        }

        ApiClient.getInstance().createFolder(folder).enqueue(new Callback<Folder>() {
            @Override
            public void onResponse(Call<Folder> call, Response<Folder> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(FolderExplorerActivity.this, "Folder created", Toast.LENGTH_SHORT).show();
                    fetchFolders();
                } else {
                    Toast.makeText(FolderExplorerActivity.this, "Failed to create folder", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Folder> call, Throwable t) {
                Toast.makeText(FolderExplorerActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFolderOptionsSheet(Folder folder) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);

        // Build a simple vertical list via AlertDialog for now (avoids custom layout dep)
        String[] options = {"Rename", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(folder.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showRenameFolderDialog(folder);
                    else if (which == 1) confirmDeleteFolder(folder);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameFolderDialog(Folder folder) {
        EditText et = new EditText(this);
        et.setText(folder.getName());
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Rename Folder")
                .setView(et)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = et.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(folder.getName())) {
                        renameFolder(folder, newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renameFolder(Folder folder, String newName) {
        folder.setName(newName);
        ApiClient.getInstance().updateFolder(folder.getId(), folder).enqueue(new Callback<Folder>() {
            @Override
            public void onResponse(Call<Folder> call, Response<Folder> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(FolderExplorerActivity.this, "Folder renamed", Toast.LENGTH_SHORT).show();
                    fetchFolders();
                } else {
                    Toast.makeText(FolderExplorerActivity.this, "Rename failed", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Folder> call, Throwable t) {
                Toast.makeText(FolderExplorerActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteFolder(Folder folder) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Folder")
                .setMessage("Delete \"" + folder.getName() + "\"? Materials inside will be moved to root.")
                .setPositiveButton("Delete", (d, w) -> deleteFolder(folder))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFolder(Folder folder) {
        ApiClient.getInstance().deleteFolder(folder.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(FolderExplorerActivity.this, "Folder deleted", Toast.LENGTH_SHORT).show();
                    fetchFolders();
                    fetchMaterials();
                } else {
                    Toast.makeText(FolderExplorerActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(FolderExplorerActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMaterialOptionsSheet(Material material) {
        String[] options = {"Open", "Rename", "Delete", "Share"};
        new AlertDialog.Builder(this)
                .setTitle(material.getTitle())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: openMaterial(material); break;
                        case 1: showRenameMaterialDialog(material); break;
                        case 2: confirmDeleteMaterial(material); break;
                        case 3: shareMaterial(material); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openMaterial(Material material) {
        Intent intent = new Intent(this, MaterialDetailActivity.class);
        intent.putExtra("material_id", material.getId());
        intent.putExtra("material_title", material.getTitle());
        intent.putExtra("material_type", material.getFileType());
        intent.putExtra("material_date", material.getCreatedAt());
        startActivity(intent);
    }

    private void showRenameMaterialDialog(Material material) {
        EditText et = new EditText(this);
        et.setText(material.getTitle());
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Rename Material")
                .setView(et)
                .setPositiveButton("Rename", (dialog, which) -> {
                    String newName = et.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        material.setTitle(newName);
                        ApiClient.getInstance().updateMaterial(material.getId(), material).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                if (response.isSuccessful()) {
                                    Toast.makeText(FolderExplorerActivity.this, "Renamed", Toast.LENGTH_SHORT).show();
                                    fetchMaterials();
                                }
                            }
                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {}
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteMaterial(Material material) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Material")
                .setMessage("Permanently delete \"" + material.getTitle() + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    ApiClient.getInstance().deleteMaterial(material.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(FolderExplorerActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                                fetchMaterials();
                            }
                        }
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {}
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareMaterial(Material material) {
        String fileUrl = material.getFileUrl();
        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(this, "No file URL available to share", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, material.getTitle());
        shareIntent.putExtra(Intent.EXTRA_TEXT, material.getTitle() + "\n" + fileUrl);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }
}
