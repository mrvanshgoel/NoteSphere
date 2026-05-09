package com.notesphere.app.fragments;

import android.content.Context;
import android.content.Intent;
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
import com.notesphere.app.adapters.SubjectAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentSubjectsBinding;
import com.notesphere.app.models.Subject;
import com.notesphere.app.utils.SharedPrefManager;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SubjectsFragment extends Fragment {
    private FragmentSubjectsBinding binding;
    private SubjectAdapter adapter;
    private Call<?> activeCall;

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
        Context context = getContext();
        if (context == null) return;
        
        showShimmer(true);
        if (activeCall != null) activeCall.cancel();
        
        Call<List<Subject>> call = ApiClient.getInstance().getSubjects();
        activeCall = call;
        
        call.enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                if (!isAdded() || binding == null) return;
                showShimmer(false);
                if (response.isSuccessful() && response.body() != null) {
                    binding.layoutOfflineBanner.setVisibility(View.GONE);
                    List<Subject> subjects = response.body();
                    if (subjects.isEmpty()) {
                        binding.layoutEmptySubjects.setVisibility(View.VISIBLE);
                    } else {
                        binding.layoutEmptySubjects.setVisibility(View.GONE);
                        adapter = new SubjectAdapter(subjects, subject -> {
                            Intent intent = new Intent(getContext(), com.notesphere.app.activities.SubjectDetailActivity.class);
                            intent.putExtra("subject_id", subject.getId());
                            intent.putExtra("subject_name", subject.getName());
                            intent.putExtra("subject_color", subject.getColor());
                            startActivity(intent);
                        }, subject -> {
                            deleteSubject(subject.getId());
                        });
                        binding.rvSubjects.setAdapter(adapter);
                    }
                } else {
                    Context innerContext = getContext();
                    if (innerContext != null) Toast.makeText(innerContext, "Error fetching subjects", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {
                if (!isAdded() || getContext() == null) return;
                if (call.isCanceled()) return;
                showShimmer(false);
                binding.layoutOfflineBanner.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showShimmer(boolean show) {
        if (binding == null) return;
        if (show) {
            binding.shimmerSubjects.setVisibility(View.VISIBLE);
            binding.shimmerSubjects.startShimmer();
            binding.rvSubjects.setVisibility(View.GONE);
            binding.layoutEmptySubjects.setVisibility(View.GONE);
        } else {
            binding.shimmerSubjects.stopShimmer();
            binding.shimmerSubjects.setVisibility(View.GONE);
            binding.rvSubjects.setVisibility(View.VISIBLE);
        }
    }

    private void showAddSubjectDialog() {
        Context context = getContext();
        if (context == null) return;
        
        EditText et = new EditText(context);
        et.setHint("Subject Name (e.g. Mathematics)");
        et.setPadding(50, 50, 50, 50);
        
        new AlertDialog.Builder(context)
                .setTitle("New Subject")
                .setMessage("Enter the name of the subject you want to track.")
                .setView(et)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        String[] colors = {"#6C63FF", "#FF6584", "#4CAF50", "#2196F3", "#FF9800"};
                        String color = colors[(int)(Math.random() * colors.length)];
                        createSubject(name, color, "📚");
                    }
                    else {
                        if (getContext() != null) Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createSubject(String name, String color, String icon) {
        Context context = getContext();
        if (context == null) return;
        
        Subject subject = new Subject();
        subject.setName(name);
        subject.setColor(color);
        subject.setIcon(icon);
        
        if (activeCall != null) activeCall.cancel();
        
        Call<Subject> call = ApiClient.getInstance().createSubject(subject);
        activeCall = call;
        
        call.enqueue(new Callback<Subject>() {
            @Override
            public void onResponse(Call<Subject> call, Response<Subject> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful()) {
                    if (getContext() != null) Toast.makeText(getContext(), "Subject created!", Toast.LENGTH_SHORT).show();
                    fetchSubjects();
                } else {
                    if (getContext() != null) Toast.makeText(getContext(), "Failed to create subject", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onFailure(Call<Subject> call, Throwable t) {
                if (!isAdded() || getContext() == null) return;
                if (call.isCanceled()) return;
                Toast.makeText(getContext(), "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteSubject(String id) {
        Context context = getContext();
        if (context == null) return;
        
        if (activeCall != null) activeCall.cancel();
        
        Call<Void> call = ApiClient.getInstance().deleteSubject(id);
        activeCall = call;
        
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful()) fetchSubjects();
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (!isAdded() || getContext() == null) return;
                if (call.isCanceled()) return;
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
