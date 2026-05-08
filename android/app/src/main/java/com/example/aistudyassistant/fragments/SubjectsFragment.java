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
        String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();
        ApiClient.getInstance().getSubjects(token).enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Subject> subjects = response.body();
                    if (subjects.isEmpty()) {
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmpty.setVisibility(View.GONE);
                        adapter = new SubjectAdapter(subjects, subject -> {
                            // Open Materials Activity logic
                        }, subject -> {
                            // Delete subject logic
                            deleteSubject(subject.getId());
                        });
                        binding.rvSubjects.setAdapter(adapter);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {
                Toast.makeText(getContext(), "Failed to fetch subjects", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddSubjectDialog() {
        EditText et = new EditText(getContext());
        et.setHint("Subject Name");
        new AlertDialog.Builder(getContext())
                .setTitle("Add Subject")
                .setView(et)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) createSubject(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createSubject(String name) {
        String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();
        Subject subject = new Subject(null, name, "book", "#6C63FF", null, 0);
        ApiClient.getInstance().createSubject(token, subject).enqueue(new Callback<Subject>() {
            @Override
            public void onResponse(Call<Subject> call, Response<Subject> response) {
                if (response.isSuccessful()) {
                    fetchSubjects();
                }
            }

            @Override
            public void onFailure(Call<Subject> call, Throwable t) {}
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
