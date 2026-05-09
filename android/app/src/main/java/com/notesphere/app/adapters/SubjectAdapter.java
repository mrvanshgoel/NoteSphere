package com.notesphere.app.adapters;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.databinding.ItemSubjectBinding;
import com.notesphere.app.models.Subject;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {
    private List<Subject> subjects;
    private OnSubjectClickListener listener;
    private OnSubjectLongClickListener longListener;

    public interface OnSubjectClickListener {
        void onSubjectClick(Subject subject);
    }

    public interface OnSubjectLongClickListener {
        void onSubjectLongClick(Subject subject);
    }

    public SubjectAdapter(List<Subject> subjects, OnSubjectClickListener listener, OnSubjectLongClickListener longListener) {
        this.subjects = subjects;
        this.listener = listener;
        this.longListener = longListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubjectBinding binding = ItemSubjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Subject subject = subjects.get(position);
        holder.binding.tvName.setText(subject.getName());
        holder.binding.tvCount.setText(subject.getMaterialCount() + " Materials");
        if (subject.getIcon() != null && !subject.getIcon().isEmpty()) {
            holder.binding.tvIcon.setText(subject.getIcon());
        }
        
        try {
            GradientDrawable gd = (GradientDrawable) holder.binding.viewColor.getBackground();
            gd.setColor(Color.parseColor(subject.getColor()));
        } catch (Exception e) {
            // Default color if parse fails
        }

        holder.itemView.setOnClickListener(v -> listener.onSubjectClick(subject));
        holder.itemView.setOnLongClickListener(v -> {
            longListener.onSubjectLongClick(subject);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return subjects.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemSubjectBinding binding;
        public ViewHolder(ItemSubjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
