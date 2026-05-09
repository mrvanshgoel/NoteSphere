package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.R;
import com.notesphere.app.models.Syllabus;
import java.util.List;

public class TopicAdapter extends RecyclerView.Adapter<TopicAdapter.ViewHolder> {
    private List<Syllabus.Topic> topics;
    private OnTopicStatusChangeListener listener;

    public interface OnTopicStatusChangeListener {
        void onStatusChanged(Syllabus.Topic topic, boolean completed);
    }

    public TopicAdapter(List<Syllabus.Topic> topics, OnTopicStatusChangeListener listener) {
        this.topics = topics;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Syllabus.Topic topic = topics.get(position);
        holder.tvName.setText(topic.getName());
        holder.cbCompleted.setOnCheckedChangeListener(null);
        holder.cbCompleted.setChecked(topic.isCompleted());
        
        holder.cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            topic.setCompleted(isChecked);
            if (listener != null) listener.onStatusChanged(topic, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        CheckBox cbCompleted;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvTopicName);
            cbCompleted = itemView.findViewById(R.id.cbCompleted);
        }
    }
}
