package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.databinding.ItemMaterialBinding;
import com.notesphere.app.models.Material;
import java.util.List;

public class MaterialAdapter extends RecyclerView.Adapter<MaterialAdapter.ViewHolder> {
    private List<Material> materials;
    private OnMaterialClickListener listener;

    public interface OnMaterialClickListener {
        void onMaterialClick(Material material);
    }

    public MaterialAdapter(List<Material> materials, OnMaterialClickListener listener) {
        this.materials = materials;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMaterialBinding binding = ItemMaterialBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Material material = materials.get(position);
        holder.binding.tvTitle.setText(material.getTitle());
        holder.binding.tvDate.setText("Uploaded on " + material.getCreatedAt());
        
        String type = material.getFileType();
        if (type != null) {
            if (type.contains("pdf")) holder.binding.tvTypeIcon.setText("📕");
            else if (type.contains("image")) holder.binding.tvTypeIcon.setText("🖼️");
            else holder.binding.tvTypeIcon.setText("📄");
        }

        holder.itemView.setOnClickListener(v -> listener.onMaterialClick(material));
    }

    @Override
    public int getItemCount() {
        return materials.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemMaterialBinding binding;
        public ViewHolder(ItemMaterialBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
