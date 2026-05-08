package com.example.aistudyassistant.fragments;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);

        adapter = new ChatAdapter(messages);
        binding.rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChat.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendMessage());

        return binding.getRoot();
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        messages.add(new ChatMessage(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvChat.scrollToPosition(messages.size() - 1);
        binding.etMessage.setText("");

        String token = "Bearer " + SharedPrefManager.getInstance(getContext()).getToken();
        ChatRequest request = new ChatRequest(text);

        ApiClient.getInstance().chat(token, request).enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    messages.add(new ChatMessage(response.body().getContent(), false));
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.rvChat.scrollToPosition(messages.size() - 1);
                } else {
                    Toast.makeText(getContext(), "AI Chat failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
