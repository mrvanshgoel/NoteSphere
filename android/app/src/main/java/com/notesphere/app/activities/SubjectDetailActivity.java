package com.notesphere.app.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.tabs.TabLayoutMediator;
import com.notesphere.app.adapters.SubjectPagerAdapter;
import com.notesphere.app.databinding.ActivitySubjectDetailBinding;

public class SubjectDetailActivity extends AppCompatActivity {
    private ActivitySubjectDetailBinding binding;
    private String subjectId;
    private String subjectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubjectDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subjectId = getIntent().getStringExtra("subject_id");
        subjectName = getIntent().getStringExtra("subject_name");
        String subjectColor = getIntent().getStringExtra("subject_color");

        if (subjectId == null) {
            finish();
            return;
        }

        binding.tvSubjectName.setText(subjectName);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // Setup ViewPager and TabLayout
        SubjectPagerAdapter pagerAdapter = new SubjectPagerAdapter(this, subjectId, subjectName);
        binding.viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0: tab.setText("Overview"); break;
                        case 1: tab.setText("Files"); break;
                        case 2: tab.setText("Notes"); break;
                        case 3: tab.setText("AI Hub"); break;
                    }
                }
        ).attach();
    }
}
