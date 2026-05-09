package com.notesphere.app.fragments;

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
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.notesphere.app.R;
import com.notesphere.app.adapters.ChatAdapter;
import com.notesphere.app.adapters.ChatHistoryAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentChatBinding;
import com.notesphere.app.models.AiResponse;
import com.notesphere.app.models.ApiMessage;
import com.notesphere.app.models.ChatMessage;
import com.notesphere.app.models.ChatRequest;
import com.notesphere.app.models.ChatSession;
import com.notesphere.app.models.Material;
import com.notesphere.app.utils.SharedPrefManager;
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
    private Call<?> activeCall;
    private SharedPrefManager pref;
    
    private String currentChatId = null;
    private String attachedMaterialId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        pref = SharedPrefManager.getInstance(requireContext());

        adapter = new ChatAdapter(messages);
        binding.rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvChat.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.btnNewChat.setOnClickListener(v -> startNewChat());
        binding.btnChatHistory.setOnClickListener(v -> showChatHistory());
        binding.btnAttachFile.setOnClickListener(v -> showMaterialPicker());

        // Initial setup
        startNewChat();
        
        return binding.getRoot();
    }

    private void startNewChat() {
        currentChatId = null;
        attachedMaterialId = null;
        messages.clear();
        apiHistory.clear();
        adapter.notifyDataSetChanged();
        binding.tvChatTitle.setText("New Chat");
        binding.btnAttachFile.setColorFilter(null);
        
        // Create a placeholder session on the backend
        ChatSession newSession = new ChatSession("New Chat");
        ApiClient.getInstance().createChatSession(newSession).enqueue(new Callback<ChatSession>() {
            @Override
            public void onResponse(Call<ChatSession> call, Response<ChatSession> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentChatId = response.body().getId();
                    android.util.Log.d("CHAT", "New Chat Session Created: " + currentChatId);
                }
            }
            @Override
            public void onFailure(Call<ChatSession> call, Throwable t) {}
        });
    }

    private void showChatHistory() {
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_history, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        dialog.setContentView(sheetView);

        RecyclerView rv = sheetView.findViewById(R.id.rvChatHistory);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        ApiClient.getInstance().getChatSessions().enqueue(new Callback<List<ChatSession>>() {
            @Override
            public void onResponse(Call<List<ChatSession>> call, Response<List<ChatSession>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ChatHistoryAdapter historyAdapter = new ChatHistoryAdapter(response.body(), session -> {
                        loadChat(session);
                        dialog.dismiss();
                    }, session -> {
                        deleteChat(session, dialog);
                    });
                    rv.setAdapter(historyAdapter);
                }
            }
            @Override
            public void onFailure(Call<List<ChatSession>> call, Throwable t) {}
        });

        sheetView.findViewById(R.id.btnNewChatSheet).setOnClickListener(v -> {
            startNewChat();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void loadChat(ChatSession session) {
        currentChatId = session.getId();
        binding.tvChatTitle.setText(session.getTitle());
        messages.clear();
        apiHistory.clear();
        
        if (session.getMessages() != null) {
            for (ChatMessage msg : session.getMessages()) {
                messages.add(msg);
                apiHistory.add(new ApiMessage(msg.isUser() ? "user" : "assistant", msg.getMessage()));
            }
        }
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) binding.rvChat.smoothScrollToPosition(messages.size() - 1);
    }

    private void deleteChat(ChatSession session, BottomSheetDialog dialog) {
        ApiClient.getInstance().deleteChatSession(session.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    if (session.getId().equals(currentChatId)) startNewChat();
                    showChatHistory(); // refresh
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    private void showMaterialPicker() {
        // For now, let's just show a simple toast. In a real app, this would show a list of materials.
        Toast.makeText(getContext(), "Attachment feature enabled. Please select material from 'Study Material' section for now.", Toast.LENGTH_LONG).show();
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        messages.add(new ChatMessage(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvChat.smoothScrollToPosition(messages.size() - 1);
        binding.etMessage.setText("");

        ChatRequest request = new ChatRequest(text, new ArrayList<>(apiHistory));
        request.setChatId(currentChatId);
        request.setMaterialId(attachedMaterialId);

        binding.loadingAnimation.setVisibility(View.VISIBLE);
        binding.viewStatusDot.setBackgroundResource(R.drawable.bg_circle_green);

        ApiClient.getInstance().chat(request).enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (!isAdded() || binding == null) return;
                binding.loadingAnimation.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().getContent();
                    String modelUsed = response.body().getModel();
                    
                    messages.add(new ChatMessage(content, false));
                    apiHistory.add(new ApiMessage("assistant", content));
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.rvChat.smoothScrollToPosition(messages.size() - 1);
                    
                    binding.tvActiveModel.setText(modelUsed != null ? modelUsed : "Gemini");
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_circle_green);
                } else {
                    binding.viewStatusDot.setBackgroundResource(R.drawable.bg_circle_red);
                    Toast.makeText(getContext(), "AI limited or Quota Exceeded", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                if (!isAdded() || binding == null) return;
                binding.loadingAnimation.setVisibility(View.GONE);
                binding.viewStatusDot.setBackgroundResource(R.drawable.bg_circle_red);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (activeCall != null) activeCall.cancel();
        binding = null;
    }
}
