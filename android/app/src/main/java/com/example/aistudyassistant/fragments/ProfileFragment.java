package com.example.aistudyassistant.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.aistudyassistant.activities.LoginActivity;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.FragmentProfileBinding;
import com.example.aistudyassistant.models.Subject;
import com.example.aistudyassistant.utils.SharedPrefManager;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);

        SharedPrefManager pref = SharedPrefManager.getInstance(getContext());
        String name = pref.getUserName();
        binding.tvName.setText(name);
        binding.tvEmail.setText(pref.getUserEmail());
        
        if (name != null && name.length() >= 2) {
            binding.tvInitials.setText(name.substring(0, 2).toUpperCase());
        }

        binding.btnLogout.setOnClickListener(v -> {
            pref.logout();
            Intent intent = new Intent(getContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        fetchStats();

        return binding.getRoot();
    }

    private void fetchStats() {
        String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();
        ApiClient.getInstance().getSubjects(token).enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Subject> subjects = response.body();
                    binding.tvSubjectCount.setText(String.valueOf(subjects.size()));
                    int materialCount = 0;
                    for (Subject s : subjects) materialCount += s.getMaterialCount();
                    binding.tvMaterialCount.setText(String.valueOf(materialCount));
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
