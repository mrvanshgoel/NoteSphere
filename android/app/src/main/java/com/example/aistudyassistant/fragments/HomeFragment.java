package com.example.aistudyassistant.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.FragmentHomeBinding;
import com.example.aistudyassistant.models.Subject;
import com.example.aistudyassistant.utils.SharedPrefManager;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        String name = SharedPrefManager.getInstance(getContext()).getUserName();
        binding.tvWelcome.setText("Hello, " + name + "!");

        binding.swipeRefresh.setOnRefreshListener(this::fetchData);
        
        binding.btnQuickUpload.setOnClickListener(v -> {
            // Navigate to Upload tab logic (usually via Activity)
        });

        fetchData();

        return binding.getRoot();
    }

    private void fetchData() {
        binding.swipeRefresh.setRefreshing(true);
        String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();

        ApiClient.getInstance().getSubjects(token).enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                binding.swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    binding.tvSubjectCount.setText(String.valueOf(response.body().size()));
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {
                binding.swipeRefresh.setRefreshing(false);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
