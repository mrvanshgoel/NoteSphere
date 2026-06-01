package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.R;
import com.notesphere.app.models.ChatSession;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ChatHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<Object> items = new ArrayList<>();
    private OnSessionClickListener listener;
    private OnSessionLongClickListener longClickListener;

    public interface OnSessionClickListener {
        void onSessionClick(ChatSession session);
    }

    public interface OnSessionLongClickListener {
        void onSessionLongClick(ChatSession session);
    }

    public ChatHistoryAdapter(List<ChatSession> sessions, OnSessionClickListener listener, OnSessionLongClickListener longClickListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
        groupSessions(sessions);
    }

    private void groupSessions(List<ChatSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return;

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        Calendar thisWeek = Calendar.getInstance();
        thisWeek.add(Calendar.DATE, -7);

        List<ChatSession> todayList = new ArrayList<>();
        List<ChatSession> yesterdayList = new ArrayList<>();
        List<ChatSession> thisWeekList = new ArrayList<>();
        List<ChatSession> olderList = new ArrayList<>();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));

        for (ChatSession session : sessions) {
            try {
                Date date = sdf.parse(session.getUpdatedAt());
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);

                if (isSameDay(cal, today)) todayList.add(session);
                else if (isSameDay(cal, yesterday)) yesterdayList.add(session);
                else if (cal.after(thisWeek)) thisWeekList.add(session);
                else olderList.add(session);
            } catch (Exception e) {
                olderList.add(session);
            }
        }

        if (!todayList.isEmpty()) {
            items.add("Today");
            items.addAll(todayList);
        }
        if (!yesterdayList.isEmpty()) {
            items.add("Yesterday");
            items.addAll(yesterdayList);
        }
        if (!thisWeekList.isEmpty()) {
            items.add("This Week");
            items.addAll(thisWeekList);
        }
        if (!olderList.isEmpty()) {
            items.add("Older");
            items.addAll(olderList);
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_history_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_history, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvTitle.setText((String) items.get(position));
        } else {
            ChatSession session = (ChatSession) items.get(position);
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            itemHolder.tvTitle.setText(session.getTitle());
            itemHolder.tvPreview.setText(session.getPreview() != null ? session.getPreview() : "Empty conversation");
            
            String dateStr = session.getUpdatedAt();
            if (dateStr != null) {
                itemHolder.tvDate.setText(formatDate(dateStr));
            } else {
                itemHolder.tvDate.setText("Now");
            }
            
            holder.itemView.setOnClickListener(v -> listener.onSessionClick(session));
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onSessionLongClick(session);
                    return true;
                }
                return false;
            });
        }
    }

    private String formatDate(String isoDate) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(isoDate);
            
            long diff = System.currentTimeMillis() - date.getTime();
            long minutes = diff / (60 * 1000);
            long hours = minutes / 60;

            if (minutes < 1) return "Just now";
            if (minutes < 60) return minutes + "m ago";
            if (hours < 24) return hours + "h ago";
            
            return new java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(date);
        } catch (Exception e) {
            return "Recently";
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvPreview;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvHistoryTitle);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvPreview = itemView.findViewById(R.id.tvHistoryPreview);
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = (TextView) itemView;
        }
    }

    public ChatSession getSessionAt(int position) {
        if (position < 0 || position >= items.size()) return null;
        Object item = items.get(position);
        return item instanceof ChatSession ? (ChatSession) item : null;
    }
}
