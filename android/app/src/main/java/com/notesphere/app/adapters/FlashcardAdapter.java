package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonObject;
import com.notesphere.app.R;
import java.util.List;

public class FlashcardAdapter extends RecyclerView.Adapter<FlashcardAdapter.ViewHolder> {
    private List<JsonObject> cards;

    public FlashcardAdapter(List<JsonObject> cards) {
        this.cards = cards;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flashcard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject card = cards.get(position);
        String question = card.get("question").getAsString();
        String answer = card.get("answer").getAsString();

        holder.tvContent.setText(question);
        holder.tvType.setText("QUESTION");

        holder.itemView.setOnClickListener(v -> {
            boolean isQuestion = holder.tvType.getText().equals("QUESTION");
            holder.tvContent.setText(isQuestion ? answer : question);
            holder.tvType.setText(isQuestion ? "ANSWER" : "QUESTION");
            holder.tvType.setTextColor(isQuestion ? 0xFF00C853 : 0xFF6C63FF); // Green for answer, purple for question
        });
    }

    @Override
    public int getItemCount() { return cards.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvCardType);
            tvContent = itemView.findViewById(R.id.tvCardContent);
        }
    }
}
