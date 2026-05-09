package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.R;
import com.notesphere.app.models.Syllabus;
import java.util.List;

public class UnitAdapter extends RecyclerView.Adapter<UnitAdapter.ViewHolder> {
    private List<Syllabus.Unit> units;
    private TopicAdapter.OnTopicStatusChangeListener topicListener;

    public UnitAdapter(List<Syllabus.Unit> units, TopicAdapter.OnTopicStatusChangeListener topicListener) {
        this.units = units;
        this.topicListener = topicListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_unit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Syllabus.Unit unit = units.get(position);
        holder.tvName.setText(unit.getUnitName());
        
        long completed = unit.getTopics().stream().filter(Syllabus.Topic::isCompleted).count();
        holder.tvProgress.setText(completed + "/" + unit.getTopics().size());

        TopicAdapter topicAdapter = new TopicAdapter(unit.getTopics(), (topic, isChecked) -> {
            long newCompleted = unit.getTopics().stream().filter(Syllabus.Topic::isCompleted).count();
            holder.tvProgress.setText(newCompleted + "/" + unit.getTopics().size());
            if (topicListener != null) topicListener.onStatusChanged(topic, isChecked);
        });
        
        holder.rvTopics.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.rvTopics.setAdapter(topicAdapter);

        holder.layoutHeader.setOnClickListener(v -> {
            boolean isVisible = holder.rvTopics.getVisibility() == View.VISIBLE;
            holder.rvTopics.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            holder.ivExpand.setRotation(isVisible ? 0 : 180);
        });
    }

    @Override
    public int getItemCount() {
        return units.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvProgress;
        ImageView ivExpand;
        RecyclerView rvTopics;
        View layoutHeader;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvUnitName);
            tvProgress = itemView.findViewById(R.id.tvUnitProgress);
            ivExpand = itemView.findViewById(R.id.ivExpand);
            rvTopics = itemView.findViewById(R.id.rvTopics);
            layoutHeader = itemView.findViewById(R.id.layoutUnitHeader);
        }
    }
}
