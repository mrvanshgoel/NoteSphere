package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.databinding.ItemFolderBinding;
import com.notesphere.app.models.Folder;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.ViewHolder> {
    private List<Folder> folders;
    private OnFolderClickListener listener;

    public interface OnFolderClickListener {
        void onFolderClick(Folder folder);
        void onFolderLongClick(Folder folder);
    }

    public FolderAdapter(List<Folder> folders, OnFolderClickListener listener) {
        this.folders = folders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFolderBinding binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Folder folder = folders.get(position);
        holder.binding.tvFolderName.setText(folder.getName());
        holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onFolderLongClick(folder);
            return true;
        });
    }

    @Override
    public int getItemCount() { return folders.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemFolderBinding binding;
        public ViewHolder(ItemFolderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
