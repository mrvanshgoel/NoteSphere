package com.notesphere.app.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.notesphere.app.fragments.SubjectAiHubFragment;
import com.notesphere.app.fragments.SubjectFilesFragment;
import com.notesphere.app.fragments.SubjectNotesFragment;
import com.notesphere.app.fragments.SubjectOverviewFragment;

public class SubjectPagerAdapter extends FragmentStateAdapter {
    
    private final String subjectId;
    private final String subjectName;
    
    public SubjectPagerAdapter(@NonNull FragmentActivity fragmentActivity, String subjectId, String subjectName) {
        super(fragmentActivity);
        this.subjectId = subjectId;
        this.subjectName = subjectName;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return SubjectOverviewFragment.newInstance(subjectId, subjectName);
            case 1: return SubjectFilesFragment.newInstance(subjectId, subjectName);
            case 2: return SubjectNotesFragment.newInstance(subjectId, subjectName);
            case 3: return SubjectAiHubFragment.newInstance(subjectId, subjectName);
            default: return SubjectOverviewFragment.newInstance(subjectId, subjectName);
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
