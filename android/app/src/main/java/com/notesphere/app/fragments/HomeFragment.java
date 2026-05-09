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
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.notesphere.app.R;
import com.notesphere.app.activities.MainActivity;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentHomeBinding;
import com.notesphere.app.models.Subject;
import com.notesphere.app.models.Syllabus;
import com.notesphere.app.utils.SharedPrefManager;
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
        setupStudyGoal();

        binding.swipeRefresh.setOnRefreshListener(this::fetchData);
        
        pref.updateStudyStreak();
        binding.tvStreakCount.setText(String.valueOf(pref.getStudyStreak()));

        fetchData();
        fetchIntelligence();
        return binding.getRoot();
    }

    private void fetchIntelligence() {
        String token = "Bearer " + pref.getToken();
        ApiClient.getInstance().getStudyIntelligence(token).enqueue(new Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(Call<com.google.gson.JsonObject> call, Response<com.google.gson.JsonObject> response) {
                if (!isAdded() || binding == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    com.google.gson.JsonArray suggestionsArr = response.body().getAsJsonArray("suggestions");
                    java.util.List<com.google.gson.JsonObject> list = new java.util.ArrayList<>();
                    for (com.google.gson.JsonElement el : suggestionsArr) {
                        list.add(el.getAsJsonObject());
                    }
                    
                    com.notesphere.app.adapters.IntelligenceAdapter adapter = new com.notesphere.app.adapters.IntelligenceAdapter(list, s -> {
                        String action = s.get("action").getAsString();
                        if (action.equals("REVISE_TOPIC")) {
                            navigateToTab(R.id.nav_subjects);
                        } else if (action.equals("GENERATE_QUIZ")) {
                            navigateToTab(R.id.nav_subjects);
                        }
                    });
                    
                    binding.rvIntelligence.setAdapter(adapter);
                    binding.layoutIntelligence.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<com.google.gson.JsonObject> call, Throwable t) {
                if (binding != null) binding.layoutIntelligence.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGoalUI();
        binding.tvStreakCount.setText(String.valueOf(pref.getStudyStreak()));
    }

    private void setupGreeting() {
        String name = pref.getUserName();
        binding.tvWelcome.setText(name);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12)       greeting = "Good Morning 🌅";
        else if (hour >= 12 && hour < 17) greeting = "Good Afternoon ☀️";
        else if (hour >= 17 && hour < 21) greeting = "Good Evening 🌆";
        else                               greeting = "Good Night 🌙";

        binding.tvGreetingLabel.setText(greeting);
    }

    private void setupAvatar() {
        String avatarUrl = pref.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty() && binding != null) {
            Glide.with(this)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_launcher_temp)
                .into(binding.ivHomeAvatar);
        }
    }

    private void setupQuickActions() {
        binding.cardQuickUpload.setOnClickListener(v -> navigateToTab(R.id.nav_subjects));
        binding.btnStartChat.setOnClickListener(v -> navigateToTab(R.id.nav_chat));
        binding.cardSyllabusTracker.setOnClickListener(v -> {
            startActivity(new Intent(getContext(), com.notesphere.app.activities.SyllabusDashboardActivity.class));
        });
    }

    private void setupStudyGoal() {
        updateGoalUI();
        binding.tvGoalProgress.setOnClickListener(v -> showEditGoalDialog());
        binding.tvGoalDescription.setOnClickListener(v -> showEditGoalDialog());
    }

    private void updateGoalUI() {
        int goalHours = pref.getStudyGoal();
        int sessions = pref.getAiSessions();
        int targetSessions = goalHours * 2; // Assume 2 units per hour
        
        int progress = (targetSessions > 0) ? (sessions * 100) / targetSessions : 0;
        if (progress > 100) progress = 100;

        binding.tvGoalProgress.setText(sessions + "/" + targetSessions + " units");
        binding.pbStudyGoal.setProgress(progress);
        binding.tvGoalDescription.setText("Target: " + goalHours + " hrs/week (" + sessions + " units completed)");
    }

    private void showEditGoalDialog() {
        EditText et = new EditText(getContext());
        et.setHint("Hours per week (e.g. 10)");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Set Weekly Goal")
            .setMessage("How many hours do you want to study this week?")
            .setView(et)
            .setPositiveButton("Save", (dialog, which) -> {
                try {
                    int hours = Integer.parseInt(et.getText().toString());
                    pref.setStudyGoal(hours);
                    updateGoalUI();
                } catch (Exception e) {}
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void navigateToTab(int navId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToTab(navId);
        }
    }

    private void setupRecyclerView() {
        binding.rvRecent.setLayoutManager(
            new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
    }

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
                    binding.layoutOfflineBanner.setVisibility(View.GONE);
                    List<Subject> subjects = response.body();
                    binding.tvSubjectCount.setText(String.valueOf(subjects.size()));

                    int totalMaterials = 0;
                    for (Subject s : subjects) totalMaterials += s.getMaterialCount();
                    binding.tvMaterialCount.setText(String.valueOf(totalMaterials));

                    fetchSyllabusProgress(token);
                }
            }

            @Override
            public void onFailure(Call<List<Subject>> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                if (call.isCanceled()) return;
                binding.swipeRefresh.setRefreshing(false);
                showShimmer(false);
                binding.layoutOfflineBanner.setVisibility(View.VISIBLE);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
