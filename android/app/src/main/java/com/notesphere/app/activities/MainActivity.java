package com.notesphere.app.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.notesphere.app.R;
import com.notesphere.app.databinding.ActivityMainBinding;
import com.notesphere.app.fragments.ChatFragment;
import com.notesphere.app.fragments.HomeFragment;
import com.notesphere.app.fragments.ProfileFragment;
import com.notesphere.app.fragments.SubjectsFragment;
import com.notesphere.app.utils.SharedPrefManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private int currentNavId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Entering MainActivity");

        refreshFirebaseToken();

        loadFragment(new HomeFragment(), false);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentNavId) return false;

            Fragment fragment = null;
            if      (id == R.id.nav_home)     fragment = new HomeFragment();
            else if (id == R.id.nav_subjects) fragment = new SubjectsFragment();
            else if (id == R.id.nav_chat)     fragment = new ChatFragment();
            else if (id == R.id.nav_profile)  fragment = new ProfileFragment();

            currentNavId = id;
            return loadFragment(fragment, true);
        });
    }

    /** Called by child fragments (e.g. HomeFragment quick actions) to switch tab */
    public void navigateToTab(int navId) {
        binding.bottomNavigation.setSelectedItemId(navId);
    }

    private boolean loadFragment(Fragment fragment, boolean animate) {
        if (fragment == null) return false;

        var tx = getSupportFragmentManager()
            .beginTransaction();

        if (animate) {
            tx.setCustomAnimations(
                R.anim.fragment_slide_in,
                R.anim.fragment_slide_out);
        }

        tx.replace(R.id.fragment_container, fragment)
          .commit();

        return true;
    }

    @Override
    public void onBackPressed() {
        if (currentNavId != R.id.nav_home) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    private void refreshFirebaseToken() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Token Refresh: Attempting to refresh token for " + user.getUid());
            user.getIdToken(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String token = task.getResult().getToken();
                    SharedPrefManager.getInstance(this).saveToken(token);
                    android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Token Refresh SUCCESS. Prefix: " + token.substring(0, Math.min(10, token.length())));
                } else {
                    android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Token Refresh FAILED: " + task.getException().getMessage());
                    // Note: AuthInterceptor will catch API 401s if this fails but SharedPref is still set.
                }
            });
        } else {
            android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Token Refresh: No current Firebase user!");
        }
    }
}
