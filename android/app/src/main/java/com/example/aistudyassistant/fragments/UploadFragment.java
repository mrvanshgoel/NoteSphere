package com.example.aistudyassistant.fragments;

import android.app.Activity;
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
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.FragmentUploadBinding;
import com.example.aistudyassistant.models.Material;
import com.example.aistudyassistant.models.Subject;
import com.example.aistudyassistant.utils.SharedPrefManager;
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
        String[] mimeTypes = {"application/pdf", "image/*", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedFileUri = result.getData().getData();
                    binding.tvFileName.setText("Selected: " + selectedFileUri.getLastPathSegment());
                }
            }
    );

    private void fetchSubjects() {
        String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();
        ApiClient.getInstance().getSubjects(token).enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    subjectsList = response.body();
                    List<String> names = new ArrayList<>();
                    for (Subject s : subjectsList) names.add(s.getName());
                    
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, names);
                    binding.spinnerSubjects.setAdapter(adapter);
                    binding.spinnerSubjects.setOnItemClickListener((parent, view, position, id) -> {
                        selectedSubjectId = subjectsList.get(position).getId();
                    });
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {}
        });
    }

    private void uploadFile() {
        if (selectedFileUri == null || selectedSubjectId == null) {
            Toast.makeText(getContext(), "Please select a file and subject", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnUpload.setEnabled(false);

        try {
            File file = getFileFromUri(selectedFileUri);
            RequestBody requestFile = RequestBody.create(MediaType.parse(getContext().getContentResolver().getType(selectedFileUri)), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
            RequestBody subjectIdPart = RequestBody.create(MediaType.parse("text/plain"), selectedSubjectId);

            String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();
            ApiClient.getInstance().uploadMaterial(token, subjectIdPart, body).enqueue(new Callback<Material>() {
                @Override
                public void onResponse(Call<Material> call, Response<Material> response) {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnUpload.setEnabled(true);
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Upload Successful!", Toast.LENGTH_SHORT).show();
                        binding.tvFileName.setText("Tap to select PDF, Image or Text");
                        selectedFileUri = null;
                    } else {
                        Toast.makeText(getContext(), "Upload Failed", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Material> call, Throwable t) {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.btnUpload.setEnabled(true);
                    Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnUpload.setEnabled(true);
            Toast.makeText(getContext(), "File error", Toast.LENGTH_SHORT).show();
        }
    }

    private File getFileFromUri(Uri uri) throws Exception {
        InputStream is = getContext().getContentResolver().openInputStream(uri);
        File file = new File(getContext().getCacheDir(), "upload_temp");
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
        fos.flush();
        fos.close();
        is.close();
        return file;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
