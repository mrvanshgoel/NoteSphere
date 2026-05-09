package com.notesphere.app.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.notesphere.app.R;
import com.notesphere.app.activities.MainActivity;
import com.notesphere.app.activities.SyllabusDashboardActivity;
import android.content.Intent;
import com.notesphere.app.models.Syllabus;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentHomeBinding;
import com.notesphere.app.models.Material;
import com.notesphere.app.models.Subject;
import com.notesphere.app.utils.SharedPrefManager;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private Call<?> activeCall;
    private SharedPrefManager pref;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        pref = SharedPrefManager.getInstance(requireContext());

        setupGreeting();
        setupAvatar();
        setupQuickActions();
        setupRecyclerView();

        binding.swipeRefresh.setColorSchemeColors(
            requireContext().getColor(R.color.ns_purple),
            requireContext().getColor(R.color.ns_blue));
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            requireContext().getColor(R.color.ns_surface));
        binding.swipeRefresh.setOnRefreshListener(this::fetchData);

        // Update streak on every visit
        pref.updateStudyStreak();
        binding.tvStreakCount.setText(String.valueOf(pref.getStudyStreak()));

        fetchData();
        return binding.getRoot();
    }

    // ─── Greeting ──────────────────────────────────────────────────────────

    private void setupGreeting() {
        String name = pref.getUserName();
        binding.tvWelcome.setText(name);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12)       greeting = getString(R.string.greeting_morning) + " 🌅";
        else if (hour >= 12 && hour < 17) greeting = getString(R.string.greeting_afternoon) + " ☀️";
        else if (hour >= 17 && hour < 21) greeting = getString(R.string.greeting_evening) + " 🌆";
        else                               greeting = getString(R.string.greeting_night) + " 🌙";

        binding.tvGreetingLabel.setText(greeting);
    }

    // ─── Avatar ────────────────────────────────────────────────────────────

    private void setupAvatar() {
        String avatarUrl = pref.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty() && binding != null && isAdded()) {
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_launcher_temp)
                .error(R.drawable.ic_launcher_temp)
                .into(binding.ivHomeAvatar);
        }
    }

    // ─── Quick Actions ─────────────────────────────────────────────────────

    private void setupQuickActions() {
        binding.cardQuickUpload.setOnClickListener(v -> navigateToTab(R.id.nav_upload));
        binding.btnStartChat.setOnClickListener(v -> navigateToTab(R.id.nav_chat));
        binding.cardSyllabusTracker.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), com.notesphere.app.activities.SyllabusDashboardActivity.class));
        });
    }

    private void navigateToTab(int navId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToTab(navId);
        }
    }

    // ─── RecyclerView ──────────────────────────────────────────────────────

    private void setupRecyclerView() {
        binding.rvRecent.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
    }

    // ─── Data Fetching ─────────────────────────────────────────────────────

    private void fetchData() {
        Context context = getContext();
        if (context == null) return;

        showShimmer(true);
        String token = "Bearer " + pref.getToken();

        if (activeCall != null) activeCall.cancel();
        Call<List<Subject>> call = ApiClient.getInstance().getSubjects(token);
        activeCall = call;

        call.enqueue(new Callback<List<Subject>>() {
            @Override
            public void onResponse(Call<List<Subject>> call, Response<List<Subject>> response) {
                if (!isAdded() || binding == null) return;
                binding.swipeRefresh.setRefreshing(false);
                showShimmer(false);

                if (response.isSuccessful() && response.body() != null) {
                    List<Subject> subjects = response.body();
                    binding.tvSubjectCount.setText(String.valueOf(subjects.size()));

                    int totalMaterials = 0;
                    for (Subject s : subjects) totalMaterials += s.getMaterialCount();
                    binding.tvMaterialCount.setText(String.valueOf(totalMaterials));

                    if (totalMaterials == 0) showEmptyState(true);
                    
                    fetchSyllabusProgress(token);
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                if (call.isCanceled()) return;
                binding.swipeRefresh.setRefreshing(false);
                showShimmer(false);
            }
        });
    }

    private void fetchSyllabusProgress(String token) {
        String userId = pref.getUserId();
        ApiClient.getInstance().getSyllabuses(token, userId).enqueue(new Callback<List<Syllabus>>() {
            @Override
            public void onResponse(Call<List<Syllabus>> call, Response<List<Syllabus>> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Syllabus s = response.body().get(0);
                    binding.tvOverallProgress.setText(s.getProgressPercentage() + "%");
                    binding.overallProgressBar.setProgress(s.getProgressPercentage());
                    binding.tvSyllabusStatus.setText("Tracking " + s.getTitle());
                }
            }

            @Override
            public void onFailure(Call<List<Syllabus>> call, Throwable t) {}
        });
    }

    private void showShimmer(boolean show) {
        if (binding == null) return;
        if (show) {
            binding.shimmerRecent.setVisibility(View.VISIBLE);
            binding.shimmerRecent.startShimmer();
            binding.rvRecent.setVisibility(View.GONE);
        } else {
            binding.shimmerRecent.stopShimmer();
            binding.shimmerRecent.setVisibility(View.GONE);
            binding.rvRecent.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState(boolean show) {
        if (binding == null) return;
        binding.layoutEmptyHome.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
