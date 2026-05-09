package com.notesphere.app.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentUploadBinding;
import com.notesphere.app.models.Material;
import com.notesphere.app.models.Subject;
import com.notesphere.app.utils.SharedPrefManager;
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

public class UploadFragment extends Fragment {
    private FragmentUploadBinding binding;
    private Uri selectedFileUri;
    private List<Subject> subjectsList = new ArrayList<>();
    private String selectedSubjectId;
    private String selectedFileName;
    private Call<?> activeCall;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUploadBinding.inflate(inflater, container, false);

        binding.btnPickFile.setOnClickListener(v -> pickFile());
        binding.btnUpload.setOnClickListener(v -> uploadFile());

        fetchSubjects();

        return binding.getRoot();
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {
            "application/pdf", 
            "image/*", 
            "text/plain", 
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    extractFileName(selectedFileUri);
                }
            }
    );

    private void extractFileName(Uri uri) {
        Context context = getContext();
        if (context == null || uri == null) return;
        
        selectedFileName = "file_" + System.currentTimeMillis();
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    selectedFileName = cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("UPLOAD", "Error extracting filename", e);
        }
        if (binding != null) {
            binding.tvFileName.setText("Selected: " + selectedFileName);
        }
    }

    private void fetchSubjects() {
        Context context = getContext();
        if (context == null) return;

        String token = "Bearer " + SharedPrefManager.getInstance(context).getToken();
        
        if (activeCall != null) activeCall.cancel();
        
        Call<List<Subject>> call = ApiClient.getInstance().getSubjects(token);
        activeCall = call;
        
        call.enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                Context innerContext = getContext();
                if (!isAdded() || innerContext == null || binding == null) return;
                
                if (response.isSuccessful() && response.body() != null) {
                    binding.layoutOfflineBanner.setVisibility(View.GONE);
                    binding.btnUpload.setEnabled(true);
                    subjectsList = response.body();
                    List<String> names = new ArrayList<>();
                    for (Subject s : subjectsList) names.add(s.getName());
                    
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(innerContext, android.R.layout.simple_dropdown_item_1line, names);
                    binding.spinnerSubjects.setAdapter(adapter);
                    binding.spinnerSubjects.setOnItemClickListener((parent, view, position, id) -> {
                        selectedSubjectId = subjectsList.get(position).getId();
                    });
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {
                if (!isAdded() || getContext() == null || binding == null) return;
                if (call.isCanceled()) return;
                android.util.Log.e("UPLOAD", "Fetch subjects failed", t);
                binding.layoutOfflineBanner.setVisibility(View.VISIBLE);
                binding.btnUpload.setEnabled(false);
            }
        });
    }

    private void uploadFile() {
        Context context = getContext();
        if (context == null) return;

        if (selectedFileUri == null || selectedSubjectId == null) {
            Toast.makeText(context, "Please select a file and subject", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnUpload.setEnabled(false);

        try {
            File file = prepareFileForUpload(selectedFileUri, selectedFileName);
            if (file == null || !file.exists()) throw new Exception("Failed to prepare file");

            String mimeType = context.getContentResolver().getType(selectedFileUri);
            if (mimeType == null) mimeType = "application/octet-stream";

            RequestBody requestFile = RequestBody.create(MediaType.parse(mimeType), file);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", selectedFileName, requestFile);
            
            RequestBody subjectIdPart = RequestBody.create(MediaType.parse("text/plain"), selectedSubjectId);
            RequestBody namePart = RequestBody.create(MediaType.parse("text/plain"), selectedFileName);

            String token = "Bearer " + SharedPrefManager.getInstance(context).getToken();
            
            if (activeCall != null) activeCall.cancel();
            
            Call<Material> call = ApiClient.getInstance().uploadMaterial(token, subjectIdPart, namePart, filePart);
            activeCall = call;

            call.enqueue(new Callback<Material>() {
                @Override
                public void onResponse(Call<Material> call, Response<Material> response) {
                    Context innerContext = getContext();
                    if (!isAdded() || innerContext == null || binding == null) return;
                    
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnUpload.setEnabled(true);

                    if (response.isSuccessful()) {
                        Toast.makeText(innerContext, "Upload Successful!", Toast.LENGTH_SHORT).show();
                        
                        // Save copy to NoteSphereDocs for offline access
                        com.notesphere.app.utils.FileUtils.saveUriToLocal(innerContext, selectedFileUri, selectedFileName);
                        
                        binding.tvFileName.setText("Tap to select PDF, Image or Text");
                        selectedFileUri = null;
                        selectedFileName = null;
                        if (file.exists()) file.delete();
                    } else {
                        String errorMsg = parseError(response);
                        Toast.makeText(innerContext, "Upload Failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Material> call, Throwable t) {
                    Context innerContext = getContext();
                    if (!isAdded() || innerContext == null || binding == null) return;
                    if (call.isCanceled()) return;

                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnUpload.setEnabled(true);
                    Toast.makeText(innerContext, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnUpload.setEnabled(true);
            Toast.makeText(context, "File Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File prepareFileForUpload(Uri uri, String fileName) throws Exception {
        Context context = getContext();
        if (context == null) return null;

        InputStream is = context.getContentResolver().openInputStream(uri);
        File file = new File(context.getCacheDir(), fileName);
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
        fos.flush();
        fos.close();
        is.close();
        return file;
    }

    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorJson = response.errorBody().string();
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(errorJson).getAsJsonObject();
                if (obj.has("error")) return obj.get("error").getAsString();
            }
        } catch (Exception e) {
            android.util.Log.e("UPLOAD", "Error parsing error body", e);
        }
        return "Server error (" + response.code() + ")";
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
