package com.notesphere.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.airbnb.lottie.LottieAnimationView;
import com.notesphere.app.R;
import com.notesphere.app.databinding.ActivityOnboardingBinding;
import com.notesphere.app.utils.SharedPrefManager;

public class OnboardingActivity extends AppCompatActivity {

    private ActivityOnboardingBinding binding;

    private static final int[] LOTTIE_ANIMS = {
        R.raw.anim_onboarding_upload,
        R.raw.anim_onboarding_ai,
        R.raw.anim_onboarding_progress
    };
    private static final int[] TITLES = {
        R.string.onboarding_title_1,
        R.string.onboarding_title_2,
        R.string.onboarding_title_3
    };
    private static final int[] DESCS = {
        R.string.onboarding_desc_1,
        R.string.onboarding_desc_2,
        R.string.onboarding_desc_3
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViewPager();
        setupDots(0);

        binding.btnOnboardingSkip.setOnClickListener(v -> finishOnboarding());
        binding.btnOnboardingNext.setOnClickListener(v -> {
            int current = binding.viewPagerOnboarding.getCurrentItem();
            if (current < TITLES.length - 1) {
                binding.viewPagerOnboarding.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });

        binding.viewPagerOnboarding.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setupDots(position);
                if (position == TITLES.length - 1) {
                    binding.btnOnboardingNext.setText(getString(R.string.onboarding_get_started));
                    binding.btnOnboardingSkip.setVisibility(View.GONE);
                } else {
                    binding.btnOnboardingNext.setText(getString(R.string.onboarding_next));
                    binding.btnOnboardingSkip.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void setupViewPager() {
        binding.viewPagerOnboarding.setAdapter(new OnboardingAdapter());
        binding.viewPagerOnboarding.setPageTransformer((page, position) -> {
            float absPos = Math.abs(position);
            page.setAlpha(1 - absPos * 0.4f);
            page.setTranslationX(-position * page.getWidth() * 0.1f);
        });
    }

    private void setupDots(int selected) {
        binding.layoutDots.removeAllViews();
        for (int i = 0; i < TITLES.length; i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                i == selected ? 24 : 8, 8);
            params.setMargins(6, 0, 6, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == selected
                ? R.drawable.dot_active
                : R.drawable.dot_inactive);
            binding.layoutDots.addView(dot);
        }
    }

    private void finishOnboarding() {
        SharedPrefManager.getInstance(this).setOnboardingShown();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
        overridePendingTransition(R.anim.fragment_slide_in, R.anim.fragment_slide_out);
    }

    // ─── Inner Adapter ──────────────────────────────────────────────────────
    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_page, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.lottie.setAnimation(LOTTIE_ANIMS[position]);
            holder.lottie.playAnimation();
            holder.title.setText(getString(TITLES[position]));
            holder.desc.setText(getString(DESCS[position]));
        }

        @Override
        public int getItemCount() { return TITLES.length; }

        class ViewHolder extends RecyclerView.ViewHolder {
            LottieAnimationView lottie;
            TextView title, desc;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                lottie = itemView.findViewById(R.id.lottieOnboarding);
                title = itemView.findViewById(R.id.tvOnboardingTitle);
                desc = itemView.findViewById(R.id.tvOnboardingDesc);
            }
        }
    }
}
