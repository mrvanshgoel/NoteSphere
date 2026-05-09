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
    private OnMessageLongClickListener longClickListener;

    public interface OnMessageLongClickListener {
        void onLongClick(String text);
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }

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
            UserViewHolder userHolder = (UserViewHolder) holder;
            userHolder.binding.tvMessage.setText(message.getMessage());
            userHolder.binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(message.getMessage());
                }
                return true;
            });
        } else {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            aiHolder.binding.tvMessage.setText(message.getMessage());
            aiHolder.binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(message.getMessage());
                }
                return true;
            });
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        public ItemChatUserBinding binding;
        public UserViewHolder(ItemChatUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class AiViewHolder extends RecyclerView.ViewHolder {
        public ItemChatAiBinding binding;
        public AiViewHolder(ItemChatAiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
