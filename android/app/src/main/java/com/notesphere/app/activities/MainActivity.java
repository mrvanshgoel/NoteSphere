package com.notesphere.app.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
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

/**
 * MainActivity with tag-based FragmentManager caching.
 *
 * Instead of creating new fragment instances on every tab switch (which triggers
 * onCreateView → API fetch every time), we create each fragment ONCE and cache it.
 * Subsequent tab switches use show()/hide() to toggle visibility — no re-creation,
 * no duplicate API calls.
 *
 * Result: One session → One fragment creation → One data fetch per tab.
 */
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
    }

    /**
     * Shows the fragment for the given nav item ID.
     * Creates the fragment on first use; reuses the cached instance thereafter.
     * Uses show()/hide() so onCreateView is only called once per session.
     */
    private void showFragment(int navId) {
        FragmentManager fm = getSupportFragmentManager();
        var tx = fm.beginTransaction();

        // Hide all currently added fragments
        hideAllFragments(fm, tx);

        // Get or create the target fragment
        Fragment target = getOrCreateFragment(navId);

        if (!target.isAdded()) {
            // First time: add with tag so it can be found after process recreation
            String tag = getTagForNavId(navId);
            tx.add(R.id.fragment_container, target, tag);
        } else {
            tx.show(target);
        }

        tx.commit();
        currentNavId = navId;
    }

    /** Returns the cached fragment for navId, creating it if it doesn't exist yet. */
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
        // Fallback to home
        if (homeFragment == null) homeFragment = new HomeFragment();
        return homeFragment;
    }

    /** Hides all currently visible fragments. */
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

    /** Called by child fragments (e.g. HomeFragment quick actions) to switch tab. */
    public void navigateToTab(int navId) {
        binding.bottomNavigation.setSelectedItemId(navId);
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
