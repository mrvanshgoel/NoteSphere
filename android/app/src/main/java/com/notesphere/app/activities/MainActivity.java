package com.notesphere.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
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

    // Fragment tag constants — used for FragmentManager caching
    private static final String TAG_HOME     = "fragment_home";
    private static final String TAG_SUBJECTS = "fragment_subjects";
    private static final String TAG_CHAT     = "fragment_chat";
    private static final String TAG_PROFILE  = "fragment_profile";

    // Cached fragment references (lazy-initialized on first use)
    private HomeFragment     homeFragment;
    private SubjectsFragment subjectsFragment;
    private ChatFragment     chatFragment;
    private ProfileFragment  profileFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Entering MainActivity");

        // Restore fragment instances if activity was recreated (e.g., rotation)
        FragmentManager fm = getSupportFragmentManager();
        homeFragment     = (HomeFragment)     fm.findFragmentByTag(TAG_HOME);
        subjectsFragment = (SubjectsFragment) fm.findFragmentByTag(TAG_SUBJECTS);
        chatFragment     = (ChatFragment)     fm.findFragmentByTag(TAG_CHAT);
        profileFragment  = (ProfileFragment)  fm.findFragmentByTag(TAG_PROFILE);

        refreshFirebaseToken();

        // Show the home fragment on first launch
        showFragment(R.id.nav_home);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == currentNavId) return false;
            showFragment(id);
            return true;
        });

        setupSidebar();
    }

    private void setupSidebar() {
        LinearLayout btnGitHub = findViewById(R.id.btnGitHub);
        if (btnGitHub != null) {
            btnGitHub.setOnClickListener(v -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mrvanshgoel/NoteSphere/"));
                startActivity(browserIntent);
            });
        }
        
        checkBackendStatus();
    }

    private void checkBackendStatus() {
        TextView tvStatus = findViewById(R.id.tvBackendStatus);
        ImageView ivStatus = findViewById(R.id.ivBackendStatus);
        if (tvStatus == null || ivStatus == null) return;
        
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(com.notesphere.app.utils.Constants.BASE_URL + "health");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.connect();
                boolean isUp = conn.getResponseCode() == 200;
                runOnUiThread(() -> {
                    tvStatus.setText(isUp ? "Backend Active" : "Backend Offline");
                    tvStatus.setTextColor(getResources().getColor(isUp ? android.R.color.holo_green_light : android.R.color.holo_red_light, null));
                    ivStatus.setColorFilter(getResources().getColor(isUp ? android.R.color.holo_green_light : android.R.color.holo_red_light, null));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvStatus.setText("Backend Offline");
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light, null));
                    ivStatus.setColorFilter(getResources().getColor(android.R.color.holo_red_light, null));
                });
            }
        }).start();
    }

    public void openDrawer() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null) {
            drawer.openDrawer(GravityCompat.START);
        }
    }

    private void showFragment(int navId) {
        FragmentManager fm = getSupportFragmentManager();
        var tx = fm.beginTransaction();

        hideAllFragments(fm, tx);

        Fragment target = getOrCreateFragment(navId);

        if (!target.isAdded()) {
            String tag = getTagForNavId(navId);
            tx.add(R.id.fragment_container, target, tag);
        } else {
            tx.show(target);
        }

        tx.commit();
        currentNavId = navId;
    }

    private Fragment getOrCreateFragment(int navId) {
        if (navId == R.id.nav_home) {
            if (homeFragment == null) homeFragment = new HomeFragment();
            return homeFragment;
        } else if (navId == R.id.nav_subjects) {
            if (subjectsFragment == null) subjectsFragment = new SubjectsFragment();
            return subjectsFragment;
        } else if (navId == R.id.nav_chat) {
            if (chatFragment == null) chatFragment = new ChatFragment();
            return chatFragment;
        } else if (navId == R.id.nav_profile) {
            if (profileFragment == null) profileFragment = new ProfileFragment();
            return profileFragment;
        }
        if (homeFragment == null) homeFragment = new HomeFragment();
        return homeFragment;
    }

    private void hideAllFragments(FragmentManager fm, androidx.fragment.app.FragmentTransaction tx) {
        Fragment f;
        if ((f = fm.findFragmentByTag(TAG_HOME))     != null && f.isVisible()) tx.hide(f);
        if ((f = fm.findFragmentByTag(TAG_SUBJECTS)) != null && f.isVisible()) tx.hide(f);
        if ((f = fm.findFragmentByTag(TAG_CHAT))     != null && f.isVisible()) tx.hide(f);
        if ((f = fm.findFragmentByTag(TAG_PROFILE))  != null && f.isVisible()) tx.hide(f);
    }

    private String getTagForNavId(int navId) {
        if (navId == R.id.nav_home)     return TAG_HOME;
        if (navId == R.id.nav_subjects) return TAG_SUBJECTS;
        if (navId == R.id.nav_chat)     return TAG_CHAT;
        if (navId == R.id.nav_profile)  return TAG_PROFILE;
        return TAG_HOME;
    }

    public void navigateToTab(int navId) {
        binding.bottomNavigation.setSelectedItemId(navId);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }
        if (currentNavId != R.id.nav_home) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    private void refreshFirebaseToken() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Refreshing Firebase token for UID: " + user.getUid());
            user.getIdToken(true).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String token = task.getResult().getToken();
                    SharedPrefManager.getInstance(this).saveToken(token);
                    android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Token Refresh SUCCESS");
                } else {
                    android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] Token Refresh FAILED: " + task.getException().getMessage());
                }
            });
        } else {
            android.util.Log.e("NOTESPHERE_DIAGNOSTIC", "[AUTH FLOW] No current Firebase user!");
        }
    }
}
