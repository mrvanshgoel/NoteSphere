package com.notesphere.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.notesphere.app.R;
import com.notesphere.app.activities.MainActivity;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentHomeBinding;
import com.notesphere.app.models.Subject;
import com.notesphere.app.models.Syllabus;
import com.notesphere.app.utils.SharedPrefManager;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private Call<?> activeCall;
    private SharedPrefManager pref;

    // Day views held as fields so updateGoalUI can refresh them without rebuild
    private static final String[] DAY_LABELS   = { "M", "T", "W", "T", "F", "S", "S" };
    private static final int BAR_HEIGHT_DP = 80; // total bar track height in dp

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
        buildWeekCalendar();     // Build day columns into the container
        updateGoalUI();          // Fill data into the columns

        binding.swipeRefresh.setOnRefreshListener(this::fetchData);

        pref.updateStudyStreak();
        binding.tvStreakCount.setText(String.valueOf(pref.getStudyStreak()));

        fetchData();
        fetchIntelligence();
        return binding.getRoot();
    }

    private void fetchIntelligence() {
        ApiClient.getInstance().getStudyIntelligence().enqueue(new Callback<com.google.gson.JsonObject>() {
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
        setupAvatar(); // refresh avatar if updated in profile
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
        if (avatarUrl == null || avatarUrl.isEmpty() || binding == null) return;

        com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
            .circleCrop()
            .placeholder(R.drawable.ic_launcher_temp)
            .error(R.drawable.ic_launcher_temp)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
            .skipMemoryCache(true);

        Glide.with(this).load(avatarUrl).apply(options).into(binding.ivHomeAvatar);
    }

    private void setupQuickActions() {
        binding.cardQuickUpload.setOnClickListener(v -> navigateToTab(R.id.nav_subjects));
        binding.btnStartChat.setOnClickListener(v -> navigateToTab(R.id.nav_chat));
    }

    // ───────────────────────────────────────────────────────────────────────────
    // CALENDAR-STYLE WEEKLY STUDY GOAL
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Build the 7 day columns programmatically once and add them to the container.
     * Each column = label + fill bar track + day letter, all tappable.
     */
    private void buildWeekCalendar() {
        if (binding == null) return;
        LinearLayout container = binding.layoutWeeklyCalendar;
        container.removeAllViews();
        container.setOrientation(LinearLayout.HORIZONTAL);

        String[] dateKeys = pref.getCurrentWeekDateKeys();
        String todayKey   = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        int barTrackPx = dp(BAR_HEIGHT_DP);

        // Add Goal Chip to the ChipGroup
        binding.chipGroupGoals.removeAllViews();
        Chip setGoalChip = new Chip(requireContext());
        setGoalChip.setText("Set Goal");
        setGoalChip.setId(View.generateViewId());
        setGoalChip.setTextSize(11f);
        setGoalChip.setChipBackgroundColorResource(R.color.ns_surface);
        setGoalChip.setTextColor(getResources().getColor(R.color.ns_cyan, null));
        setGoalChip.setOnClickListener(v -> showEditGoalDialog());
        binding.chipGroupGoals.addView(setGoalChip);

        for (int i = 0; i < 7; i++) {
            final String dateKey = dateKeys[i];
            final boolean isToday = dateKey.equals(todayKey);

            // ── Column root ─────────────────────────────────
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            col.setLayoutParams(colParams);
            col.setTag(dateKey);  // store dateKey as single-arg tag
            col.setPadding(dp(3), 0, dp(3), 0);

            // ── Hour label on top ────────────────────────────
            TextView tvHours = new TextView(requireContext());
            tvHours.setId(View.generateViewId());
            tvHours.setText("0h");
            tvHours.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tvHours.setTextColor(getResources().getColor(R.color.ns_text_secondary, null));
            tvHours.setGravity(Gravity.CENTER);
            tvHours.setTag("tvHours"); // inner tag for retrieval
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvParams.setMargins(0, 0, 0, dp(4));
            col.addView(tvHours, tvParams);

            // ── Bar track (background) ───────────────────────
            FrameLayout track = new FrameLayout(requireContext());
            int trackWidthPx = dp(26);
            LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(trackWidthPx, barTrackPx);
            track.setLayoutParams(trackParams);
            track.setBackgroundColor(isToday
                    ? getResources().getColor(R.color.primary, null) & 0x22FFFFFF
                    : getResources().getColor(R.color.ns_text_secondary, null) & 0x22FFFFFF);

            // Rounded corners via outline
            float radius = dp(6);
            track.setClipToOutline(true);
            track.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });

            // ── Fill bar (grows from bottom) ─────────────────
            View fill = new View(requireContext());
            fill.setTag("fill");
            FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0);
            fillParams.gravity = Gravity.BOTTOM;
            fill.setLayoutParams(fillParams);
            // Weekend = purple/secondary, weekday = cyan
            int fillColor = (i >= 5)
                    ? getResources().getColor(R.color.secondary, null)
                    : getResources().getColor(R.color.ns_cyan, null);
            fill.setBackgroundColor(fillColor);
            fill.setAlpha(0.9f);
            track.addView(fill);

            col.addView(track, trackParams);

            // ── Today highlight ring ─────────────────────────
            if (isToday) {
                track.setBackgroundColor(getResources().getColor(R.color.ns_cyan, null) & 0x22FFFFFF);
            }

            // ── Day letter below ─────────────────────────────
            TextView tvDay = new TextView(requireContext());
            tvDay.setText(DAY_LABELS[i]);
            tvDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tvDay.setTextColor(isToday
                    ? getResources().getColor(R.color.ns_cyan, null)
                    : getResources().getColor(R.color.ns_text_secondary, null));
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTypeface(tvDay.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dayParams.setMargins(0, dp(4), 0, 0);
            col.addView(tvDay, dayParams);

            // ── Click → log hours dialog ─────────────────────
            col.setOnClickListener(v -> showLogHoursDialog(dateKey, fill, tvHours));
            col.setClickable(true);
            col.setFocusable(true);

            // Ripple effect
            TypedValue ripple = new TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            col.setBackgroundResource(ripple.resourceId);

            container.addView(col);
        }
    }

    /**
     * Refresh all day bars + labels + weekly total text from SharedPrefs.
     * Called on onResume and after logging hours.
     */
    private void updateGoalUI() {
        if (binding == null) return;

        LinearLayout container = binding.layoutWeeklyCalendar;
        String[] dateKeys = pref.getCurrentWeekDateKeys();
        int barTrackPx = dp(BAR_HEIGHT_DP);
        int goalHours  = pref.getStudyGoal();
        float maxDayHours = Math.max(goalHours / 7f, 2f); // scale relative to daily goal or min 2h

        float weeklyTotal = 0f;

        for (int i = 0; i < container.getChildCount() && i < 7; i++) {
            View child = container.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout col = (LinearLayout) child;

            String dateKey = (String) col.getTag();  // retrieve single-arg tag
            if (dateKey == null) continue;

            float hours = pref.getDayStudyHours(dateKey);
            weeklyTotal += hours;

            // Update hour label
            TextView tvHours = (TextView) col.findViewWithTag("tvHours");
            if (tvHours != null) {
                if (hours == 0f) tvHours.setText("0h");
                else if (hours == (int) hours) tvHours.setText((int) hours + "h");
                else tvHours.setText(String.format(Locale.getDefault(), "%.1fh", hours));
            }

            // Update fill bar height
            FrameLayout track = null;
            for (int j = 0; j < col.getChildCount(); j++) {
                if (col.getChildAt(j) instanceof FrameLayout) {
                    track = (FrameLayout) col.getChildAt(j);
                    break;
                }
            }
            if (track != null) {
                View fill = track.findViewWithTag("fill");
                if (fill != null) {
                    float ratio = Math.min(hours / maxDayHours, 1f);
                    int fillHeight = (int) (barTrackPx * ratio);
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                    lp.height = fillHeight;
                    fill.setLayoutParams(lp);
                    fill.setAlpha(hours > 0 ? 0.9f : 0f);
                }
            }
        }

        // Weekly total and progress
        float totalRounded = Math.round(weeklyTotal * 10) / 10f;
        String totalStr = (totalRounded == (int) totalRounded)
                ? (int) totalRounded + "h / " + goalHours + "h goal"
                : totalRounded + "h / " + goalHours + "h goal";
        binding.tvWeeklyTotal.setText("This Week: " + totalStr);
    }

    /** Dialog to log hours for a specific day */
    private void showLogHoursDialog(String dateKey, View fill, TextView tvHours) {
        // Parse "yyyy-MM-dd" to display name like "Monday, 26 May"
        String parsedDate = dateKey;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey);
            parsedDate = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(d);
        } catch (Exception ignored) {}
        
        final String displayDate = parsedDate; // effectively final for lambda

        float current = pref.getDayStudyHours(dateKey);
        String currentStr = current == 0f ? "" : (current == (int) current
                ? String.valueOf((int) current)
                : String.format(Locale.getDefault(), "%.1f", current));

        EditText et = new EditText(requireContext());
        et.setHint("Hours studied (e.g. 2.5)");
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setText(currentStr);
        et.setSelectAllOnFocus(true);
        int pad = dp(16);
        et.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(requireContext())
            .setTitle("Log hours for " + displayDate)
            .setMessage("How many hours did you study?")
            .setView(et)
            .setPositiveButton("Save", (dialog, which) -> {
                try {
                    float hours = Float.parseFloat(et.getText().toString().trim());
                    if (hours < 0 || hours > 24) {
                        Toast.makeText(requireContext(), "Enter a valid value (0–24)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pref.setDayStudyHours(dateKey, hours);
                    updateGoalUI();
                    Toast.makeText(requireContext(),
                            hours + "h logged for " + displayDate + " ✓",
                            Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Enter a valid number", Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton("Clear", (dialog, which) -> {
                pref.setDayStudyHours(dateKey, 0f);
                updateGoalUI();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showEditGoalDialog() {
        int current = pref.getStudyGoal();
        EditText et = new EditText(requireContext());
        et.setHint("Target hours (e.g. 14)");
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(current));
        et.setSelectAllOnFocus(true);
        int pad = dp(16);
        et.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(requireContext())
            .setTitle("Weekly Study Goal")
            .setMessage("How many total hours do you want to study this week?")
            .setView(et)
            .setPositiveButton("Save", (dialog, which) -> {
                try {
                    int hours = Integer.parseInt(et.getText().toString().trim());
                    if (hours < 1 || hours > 168) {
                        Toast.makeText(requireContext(), "Enter a realistic goal (1–168h)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pref.setStudyGoal(hours);
                    updateGoalUI();
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** dp → px helper */
    private int dp(float dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }

    // ───────────────────────────────────────────────────────────────────────────

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
        if (activeCall != null) activeCall.cancel();
        Call<List<Subject>> call = ApiClient.getInstance().getSubjects();
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

                    fetchSyllabusProgress();
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

    private void fetchSyllabusProgress() {
        // Syllabus Tracker removed as part of Phase 4
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
