package com.example.aistudyassistant.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.aistudyassistant.adapters.SubjectAdapter;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.FragmentSubjectsBinding;
import com.example.aistudyassistant.models.Subject;
import com.example.aistudyassistant.utils.SharedPrefManager;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SubjectsFragment extends Fragment {
    private FragmentSubjectsBinding binding;
    private SubjectAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSubjectsBinding.inflate(inflater, container, false);

        binding.rvSubjects.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.fabAdd.setOnClickListener(v -> showAddSubjectDialog());

        fetchSubjects();

        return binding.getRoot();
    }

    private void fetchSubjects() {
        if (!isAdded()) return;
        String token = SharedPrefManager.getInstance(requireContext()).getToken();
        if (token == null) return;
        
        String authHeader = "Bearer " + token;
        ApiClient.getInstance().getSubjects(authHeader).enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    List<Subject> subjects = response.body();
                    if (subjects.isEmpty()) {
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmpty.setVisibility(View.GONE);
                        adapter = new SubjectAdapter(subjects, subject -> {
                            // Open Materials Activity logic
                        }, subject -> {
                            deleteSubject(subject.getId());
                        });
                        binding.rvSubjects.setAdapter(adapter);
                    }
                } else {
                    Toast.makeText(getContext(), "Error fetching subjects", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {
                if (isAdded()) Toast.makeText(getContext(), "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddSubjectDialog() {
        EditText et = new EditText(getContext());
        et.setHint("Subject Name (e.g. Mathematics)");
        et.setPadding(50, 50, 50, 50);
        
        new AlertDialog.Builder(requireContext())
                .setTitle("New Subject")
                .setMessage("Enter the name of the subject you want to track.")
                .setView(et)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        // Random nice color and icon for premium feel
                        String[] colors = {"#6C63FF", "#FF6584", "#4CAF50", "#2196F3", "#FF9800"};
                        String color = colors[(int)(Math.random() * colors.length)];
                        createSubject(name, color, "📚");
                    }
                    else Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createSubject(String name, String color, String icon) {
        String token = SharedPrefManager.getInstance(getContext()).getToken();
        
        Subject subject = new Subject();
        subject.setName(name);
        subject.setColor(color);
        subject.setIcon(icon);
        
        android.util.Log.d("SUBJECTS", "Calling POST /api/subjects with name: " + name);
        
        ApiClient.getInstance().createSubject("Bearer " + token, subject)
            .enqueue(new Callback<Subject>() {
                @Override
                public void onResponse(Call<Subject> call, Response<Subject> response) {
                    android.util.Log.d("SUBJECTS", "Response code: " + response.code());
                    if (response.isSuccessful() && response.body() != null) {
                        android.util.Log.d("SUBJECTS", "Subject created: " + response.body().getName());
                        Toast.makeText(getContext(), "Subject created!", Toast.LENGTH_SHORT).show();
                        fetchSubjects(); // refresh list
                    } else {
                        android.util.Log.d("SUBJECTS", "Error body: " + (response.errorBody() != null ? response.errorBody().toString() : "null"));
                        Toast.makeText(getContext(), "Failed to create subject", Toast.LENGTH_SHORT).show();
                    }
                }
                
                @Override
                public void onFailure(Call<Subject> call, Throwable t) {
                    android.util.Log.e("SUBJECTS", "Network error: " + t.getMessage());
                    Toast.makeText(getContext(), "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void deleteSubject(String id) {
        String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();
        ApiClient.getInstance().deleteSubject(token, id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) fetchSubjects();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
