package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.R;
import com.notesphere.app.models.Note;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {
    private List<Note> notes;
    private OnNoteClickListener listener;

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public NotesAdapter(List<Note> notes, OnNoteClickListener listener) {
        this.notes = notes;
        this.listener = listener;
    }

    public void updateList(List<Note> newNotes) {
        this.notes = newNotes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.tvTitle.setText(note.getTitle());
        holder.tvPreview.setText(note.getContent() != null ? note.getContent().replaceAll("(?s)<[^>]*>(\\s*<[^>]*>)*", " ") : "");
        holder.ivPinned.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);

        if (note.getUpdatedAt() instanceof String) {
            holder.tvDate.setText(formatDate((String) note.getUpdatedAt()));
        } else {
            holder.tvDate.setText("Just now");
        }

        holder.itemView.setOnClickListener(v -> listener.onNoteClick(note));
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    private String formatDate(String isoDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(isoDate);
            return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date);
        } catch (Exception e) {
            return "Recently";
        }
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvPreview, tvDate;
        ImageView ivPinned;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNoteTitle);
            tvPreview = itemView.findViewById(R.id.tvNotePreview);
            tvDate = itemView.findViewById(R.id.tvNoteDate);
            ivPinned = itemView.findViewById(R.id.ivPinned);
        }
    }
}
