package com.notesphere.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.notesphere.app.R;
import com.notesphere.app.activities.DoubtSolverActivity;
import com.notesphere.app.activities.FlashcardActivity;
import com.notesphere.app.activities.QuizActivity;
import com.notesphere.app.activities.SummaryActivity;

public class SubjectAiHubFragment extends Fragment {
    
    private String subjectId;
    private String subjectName;

    public static SubjectAiHubFragment newInstance(String subjectId, String subjectName) {
        SubjectAiHubFragment fragment = new SubjectAiHubFragment();
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
        View view = inflater.inflate(R.layout.fragment_subject_ai_hub, container, false);
        
        // Flashcards — pass subjectId + subjectName
        view.findViewById(R.id.cardFlashcards).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), FlashcardActivity.class);
            intent.putExtra("subjectName", subjectName);
            intent.putExtra("subjectId", subjectId);
            startActivity(intent);
        });
        
        // Quizzes — pass subjectId
        view.findViewById(R.id.cardQuizzes).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), QuizActivity.class);
            intent.putExtra("subjectId", subjectId);
            intent.putExtra("subjectName", subjectName);
            startActivity(intent);
        });

        // Ask AI — Doubt Solver (subject-level context)
        view.findViewById(R.id.cardAskAi).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), DoubtSolverActivity.class);
            intent.putExtra("material_title", subjectName + " Knowledge Base");
            intent.putExtra("subjectId", subjectId);
            startActivity(intent);
        });

        // Summary — fully wired to SummaryActivity
        view.findViewById(R.id.cardSummary).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), SummaryActivity.class);
            intent.putExtra("subjectId", subjectId);
            intent.putExtra("subjectName", subjectName);
            startActivity(intent);
        });
        
        return view;
    }
}
