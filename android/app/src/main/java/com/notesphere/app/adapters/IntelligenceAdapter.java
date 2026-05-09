package com.notesphere.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notesphere.app.R;
import com.google.gson.JsonObject;
import java.util.List;

public class IntelligenceAdapter extends RecyclerView.Adapter<IntelligenceAdapter.ViewHolder> {
    private List<JsonObject> suggestions;
    private OnActionClickListener listener;

    public interface OnActionClickListener {
        void onActionClick(JsonObject suggestion);
    }

    public IntelligenceAdapter(List<JsonObject> suggestions, OnActionClickListener listener) {
        this.suggestions = suggestions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_intelligence_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject suggestion = suggestions.get(position);
        
        String type = suggestion.get("type").getAsString();
        String message = suggestion.get("message").getAsString();
        
        holder.tvType.setText(type);
        holder.tvMessage.setText(message);
        
        if (type.equalsIgnoreCase("revision")) {
            holder.tvIcon.setText("⏳");
            holder.btnAction.setText("Revise Now");
        } else if (type.equalsIgnoreCase("engagement")) {
            holder.tvIcon.setText("🎯");
            holder.btnAction.setText("Take Quiz");
        } else {
            holder.tvIcon.setText("💡");
            holder.btnAction.setText("Open");
        }

        holder.btnAction.setOnClickListener(v -> listener.onActionClick(suggestion));
    }

    @Override
    public int getItemCount() { return suggestions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIcon, tvType, tvMessage;
        View btnAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIcon = itemView.findViewById(R.id.tvIntelligenceIcon);
            tvType = itemView.findViewById(R.id.tvIntelligenceType);
            tvMessage = itemView.findViewById(R.id.tvIntelligenceMessage);
            btnAction = itemView.findViewById(R.id.btnIntelligenceAction);
        }
    }
}
