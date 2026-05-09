package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.R;
import com.notesphere.app.models.ChatSession;
import java.util.List;

public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder> {
    private List<ChatSession> sessions;
    private OnSessionClickListener listener;
    private OnSessionDeleteListener deleteListener;

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public interface OnSessionDeleteListener {
        void onSessionDelete(ChatSession session);
    }

    public ChatHistoryAdapter(List<ChatSession> sessions, OnSessionClickListener listener, OnSessionDeleteListener deleteListener) {
        this.sessions = sessions;
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        holder.tvTitle.setText(session.getTitle());
        holder.tvDate.setText(session.getUpdatedAt() != null ? session.getUpdatedAt() : "Recently");
        
        holder.itemView.setOnClickListener(v -> listener.onSessionClick(session));
        holder.btnDelete.setOnClickListener(v -> deleteListener.onSessionDelete(session));
    }

    @Override
    public int getItemCount() { return sessions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate;
        View btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            btnDelete = itemView.findViewById(R.id.btnDeleteChat);
        }
    }
}
