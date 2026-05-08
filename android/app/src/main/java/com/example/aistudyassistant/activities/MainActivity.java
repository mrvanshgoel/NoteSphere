package com.example.aistudyassistant.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.aistudyassistant.R;
import com.example.aistudyassistant.databinding.ActivityMainBinding;
import com.example.aistudyassistant.fragments.ChatFragment;
import com.example.aistudyassistant.fragments.HomeFragment;
import com.example.aistudyassistant.fragments.ProfileFragment;
import com.example.aistudyassistant.fragments.SubjectsFragment;
import com.example.aistudyassistant.fragments.UploadFragment;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadFragment(new HomeFragment());

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_home) fragment = new HomeFragment();
            else if (id == R.id.nav_subjects) fragment = new SubjectsFragment();
            else if (id == R.id.nav_upload) fragment = new UploadFragment();
            else if (id == R.id.nav_chat) fragment = new ChatFragment();
            else if (id == R.id.nav_profile) fragment = new ProfileFragment();

            return loadFragment(fragment);
        });
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (binding.bottomNavigation.getSelectedItemId() != R.id.nav_home) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }
}
