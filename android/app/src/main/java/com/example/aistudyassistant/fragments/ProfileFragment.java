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

        loadUserData();

        binding.btnLogout.setOnClickListener(v -> {
            SharedPrefManager.getInstance(requireContext()).logout();
            Intent intent = new Intent(getContext(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        binding.btnEditProfile.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Account Settings coming soon!", Toast.LENGTH_SHORT).show();
        });

        fetchStats();

        return binding.getRoot();
    }

    private void loadUserData() {
        SharedPrefManager pref = SharedPrefManager.getInstance(requireContext());
        binding.tvName.setText(pref.getUserName());
        binding.tvEmail.setText(pref.getUserEmail());

        String avatarUrl = pref.getUserAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_launcher_temp)
                    .circleCrop()
                    .into(binding.ivProfile);
        }
    }

    private void fetchStats() {
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
