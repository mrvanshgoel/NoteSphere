package com.example.aistudyassistant.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.aistudyassistant.databinding.ItemChatAiBinding;
import com.example.aistudyassistant.databinding.ItemChatUserBinding;
import com.example.aistudyassistant.models.ChatMessage;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<ChatMessage> messages;
    private static final int TYPE_USER = 1;
    private static final int TYPE_AI = 2;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            ItemChatUserBinding binding = ItemChatUserBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new UserViewHolder(binding);
        } else {
            ItemChatAiBinding binding = ItemChatAiBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new AiViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).binding.tvMessage.setText(message.getMessage());
        } else {
            ((AiViewHolder) holder).binding.tvMessage.setText(message.getMessage());
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        ItemChatUserBinding binding;
        public UserViewHolder(ItemChatUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class AiViewHolder extends RecyclerView.ViewHolder {
        ItemChatAiBinding binding;
        public AiViewHolder(ItemChatAiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
