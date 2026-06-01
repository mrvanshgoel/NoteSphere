package com.notesphere.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.notesphere.app.R;
import com.notesphere.app.activities.MaterialDetailActivity;
import com.notesphere.app.adapters.FolderAdapter;
import com.notesphere.app.adapters.MaterialAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.models.Folder;
import com.notesphere.app.models.Material;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SubjectFilesFragment extends Fragment {
    
    private String subjectId;
    private String subjectName;
    
    private FolderAdapter folderAdapter;
    private List<Folder> folders = new ArrayList<>();
    private String currentFolderId = null;
    
    private MaterialAdapter adapter;
    private List<Material> materials = new ArrayList<>();
    
    // Breadcrumb Tracking
    private static class BreadcrumbItem {
        String id;
        String name;
        BreadcrumbItem(String id, String name) { this.id = id; this.name = name; }
    }
    private List<BreadcrumbItem> breadcrumbStack = new ArrayList<>();
    
    private Call<?> activeCall;
    
    public static SubjectFilesFragment newInstance(String subjectId, String subjectName) {
        SubjectFilesFragment fragment = new SubjectFilesFragment();
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
        View view = inflater.inflate(R.layout.fragment_subject_files, container, false);
        
        setupRecyclerView(view);
        
        // Initialize Breadcrumbs with Root
        breadcrumbStack.clear();
        breadcrumbStack.add(new BreadcrumbItem(null, subjectName));
        updateBreadcrumbUI(view);
        
        fetchFolders();
        fetchMaterials();
        
        view.findViewById(R.id.btnNewFolder).setOnClickListener(v -> showCreateFolderDialog());
        
        ExtendedFloatingActionButton fabUpload = view.findViewById(R.id.fabUpload);
        fabUpload.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Upload coming soon", Toast.LENGTH_SHORT).show();
        });
        
        return view;
    }
    
    private void setupRecyclerView(View view) {
        RecyclerView rvMaterials = view.findViewById(R.id.rvMaterials);
        adapter = new MaterialAdapter(materials, new MaterialAdapter.OnMaterialClickListener() {
            @Override
            public void onMaterialClick(Material material) {
                Intent intent = new Intent(getActivity(), MaterialDetailActivity.class);
                intent.putExtra("material_id", material.getId());
                intent.putExtra("material_title", material.getTitle());
                intent.putExtra("material_type", material.getFileType());
                intent.putExtra("material_date", material.getCreatedAt());
                startActivity(intent);
            }

            @Override
            public void onMaterialLongClick(Material material) {
                // showMaterialOptions(material);
            }
        });
        rvMaterials.setLayoutManager(new LinearLayoutManager(getContext()));
        rvMaterials.setAdapter(adapter);

        RecyclerView rvFolders = view.findViewById(R.id.rvFolders);
        folderAdapter = new FolderAdapter(folders, new FolderAdapter.OnFolderClickListener() {
            @Override
            public void onFolderClick(Folder folder) {
                currentFolderId = folder.getId();
                breadcrumbStack.add(new BreadcrumbItem(folder.getId(), folder.getName()));
                updateBreadcrumbUI(getView());
                fetchFolders();
                fetchMaterials();
            }

            @Override
            public void onFolderLongClick(Folder folder) {
                // showFolderOptions(folder);
            }
        });
        rvFolders.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFolders.setAdapter(folderAdapter);
    }
    
    private void updateBreadcrumbUI(View view) {
        if (view == null) return;
        LinearLayout llBreadcrumbs = view.findViewById(R.id.llBreadcrumbs);
        if (llBreadcrumbs == null) return;
        
        llBreadcrumbs.removeAllViews();
        for (int i = 0; i < breadcrumbStack.size(); i++) {
            BreadcrumbItem item = breadcrumbStack.get(i);
            
            TextView tv = new TextView(getContext());
            tv.setText(item.name);
            tv.setTextSize(16);
            tv.setPadding(8, 8, 8, 8);
            
            if (i == breadcrumbStack.size() - 1) {
                // Current directory
                tv.setTextColor(Color.parseColor("#6C63FF"));
                tv.setTypeface(null, Typeface.BOLD);
            } else {
                // Parent directory
                tv.setTextColor(Color.GRAY);
                tv.setClickable(true);
                tv.setFocusable(true);
                tv.setBackgroundResource(android.R.attr.selectableItemBackground);
                int indexToPopTo = i;
                tv.setOnClickListener(v -> {
                    // Pop stack up to this index
                    while (breadcrumbStack.size() > indexToPopTo + 1) {
                        breadcrumbStack.remove(breadcrumbStack.size() - 1);
                    }
                    currentFolderId = breadcrumbStack.get(breadcrumbStack.size() - 1).id;
                    updateBreadcrumbUI(getView());
                    fetchFolders();
                    fetchMaterials();
                });
            }
            llBreadcrumbs.addView(tv);
            
            if (i < breadcrumbStack.size() - 1) {
                TextView separator = new TextView(getContext());
                separator.setText(" > ");
                separator.setTextColor(Color.GRAY);
                llBreadcrumbs.addView(separator);
            }
        }
    }

    private void fetchFolders() {
        ApiClient.getInstance().getFolders(subjectId, currentFolderId).enqueue(new Callback<List<Folder>>() {
            @Override
            public void onResponse(Call<List<Folder>> call, Response<List<Folder>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    folders.clear();
                    folders.addAll(response.body());
                    folderAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void onFailure(Call<List<Folder>> call, Throwable t) {}
        });
    }

    private void fetchMaterials() {
        if (activeCall != null) activeCall.cancel();
        Call<List<Material>> call = ApiClient.getInstance().getMaterials(subjectId, currentFolderId);
        activeCall = call;

        call.enqueue(new Callback<List<Material>>() {
            @Override
            public void onResponse(Call<List<Material>> call, Response<List<Material>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    materials.clear();
                    materials.addAll(response.body());
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(Call<List<Material>> call, Throwable t) {
                if (call.isCanceled()) return;
            }
        });
    }
    
    private void showCreateFolderDialog() {
        android.widget.EditText et = new android.widget.EditText(getContext());
        et.setHint("Folder Name");
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("New Folder")
                .setView(et)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) createFolder(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void createFolder(String name) {
        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(currentFolderId);
        try {
            java.lang.reflect.Field field = Folder.class.getDeclaredField("subjectId");
            field.setAccessible(true);
            field.set(folder, subjectId);
        } catch (Exception e) {}

        ApiClient.getInstance().createFolder(folder).enqueue(new Callback<Folder>() {
            @Override
            public void onResponse(Call<Folder> call, Response<Folder> response) {
                if (response.isSuccessful()) fetchFolders();
            }
            @Override
            public void onFailure(Call<Folder> call, Throwable t) {}
        });
    }
}
