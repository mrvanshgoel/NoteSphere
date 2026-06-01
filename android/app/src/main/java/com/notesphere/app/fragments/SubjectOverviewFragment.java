package com.notesphere.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.notesphere.app.R;

public class SubjectOverviewFragment extends Fragment {
    
    private String subjectId;
    private String subjectName;

    public static SubjectOverviewFragment newInstance(String subjectId, String subjectName) {
        SubjectOverviewFragment fragment = new SubjectOverviewFragment();
        Bundle args = new Bundle();
        args.putString("subjectId", subjectId);
        args.putString("subjectName", subjectName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            subjectId = getArguments().getString("subjectId");
            subjectName = getArguments().getString("subjectName");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_subject_overview, container, false);
        return view;
    }
}
