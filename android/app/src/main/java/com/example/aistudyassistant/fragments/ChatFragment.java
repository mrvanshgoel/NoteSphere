package com.example.aistudyassistant.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.aistudyassistant.adapters.ChatAdapter;
import com.example.aistudyassistant.api.ApiClient;
import com.example.aistudyassistant.databinding.FragmentChatBinding;
import com.example.aistudyassistant.models.AiResponse;
import com.example.aistudyassistant.models.ApiMessage;
import com.example.aistudyassistant.models.ChatMessage;
import com.example.aistudyassistant.models.ChatRequest;
import com.example.aistudyassistant.utils.SharedPrefManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatFragment extends Fragment {
    private FragmentChatBinding binding;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private List<ApiMessage> apiHistory = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);

        adapter = new ChatAdapter(messages);
        binding.rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChat.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendMessage());
        
        // Setup long press to copy
        adapter.setOnMessageLongClickListener(text -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied Message", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Message copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        return binding.getRoot();
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        // UI Update
        messages.add(new ChatMessage(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvChat.smoothScrollToPosition(messages.size() - 1);
        binding.etMessage.setText("");

        // API Prep
        apiHistory.add(new ApiMessage("user", text));
        
        String token = SharedPrefManager.getInstance(requireContext()).getToken();
        if (token == null) {
            Toast.makeText(getContext(), "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String authHeader = "Bearer " + token;
        ChatRequest request = new ChatRequest(new ArrayList<>(apiHistory));

        // Show typing indicator
        binding.tvTyping.setVisibility(View.VISIBLE);

        ApiClient.getInstance().chat(authHeader, request).enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (!isAdded()) return;
                binding.tvTyping.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().getContent();
                    messages.add(new ChatMessage(content, false));
                    apiHistory.add(new ApiMessage("assistant", content));
                    
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.rvChat.smoothScrollToPosition(messages.size() - 1);
                } else {
                    String errorMsg = "AI Chat failed";
                    if (response.code() == 401) errorMsg = "Session expired. Please login again.";
                    Toast.makeText(getContext(), errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                if (!isAdded()) return;
                binding.tvTyping.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Connection Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
