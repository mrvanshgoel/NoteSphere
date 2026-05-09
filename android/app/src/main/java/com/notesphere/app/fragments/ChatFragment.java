package com.notesphere.app.fragments;

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
import com.notesphere.app.adapters.ChatAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.FragmentChatBinding;
import com.notesphere.app.models.AiResponse;
import com.notesphere.app.models.ApiMessage;
import com.notesphere.app.models.ChatMessage;
import com.notesphere.app.models.ChatRequest;
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
            Context context = getContext();
            if (context != null) {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Copied Message", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        return binding.getRoot();
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        Context context = getContext();
        if (context == null) return;

        // UI Update
        messages.add(new ChatMessage(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvChat.smoothScrollToPosition(messages.size() - 1);
        binding.etMessage.setText("");

        // API Prep
        apiHistory.add(new ApiMessage("user", text));
        
        String token = SharedPrefManager.getInstance(context).getToken();
        if (token == null) {
            Toast.makeText(context, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String authHeader = "Bearer " + token;
        ChatRequest request = new ChatRequest(new ArrayList<>(apiHistory));

        // Show typing indicator
        binding.loadingAnimation.setVisibility(View.VISIBLE);

        if (activeCall != null) activeCall.cancel();
        
        Call<AiResponse> call = ApiClient.getInstance().chat(authHeader, request);
        activeCall = call;

        call.enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                Context innerContext = getContext();
                if (!isAdded() || innerContext == null || binding == null) return;
                
                binding.loadingAnimation.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().getContent();
                    messages.add(new ChatMessage(content, false));
                    apiHistory.add(new ApiMessage("assistant", content));
                    
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.rvChat.smoothScrollToPosition(messages.size() - 1);
                } else {
                    String errorMsg = "AI Chat failed";
                    try {
                        if (response.errorBody() != null) {
                            String errorJson = response.errorBody().string();
                            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(errorJson).getAsJsonObject();
                            if (obj.has("error")) errorMsg = obj.get("error").getAsString();
                        }
                    } catch (Exception e) {
                        if (response.code() == 401) errorMsg = "Session expired. Please login again.";
                        else errorMsg += ": " + response.code();
                    }
                    Toast.makeText(innerContext, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                Context innerContext = getContext();
                if (!isAdded() || innerContext == null || binding == null) return;
                if (call.isCanceled()) return;

                binding.loadingAnimation.setVisibility(View.GONE);
                Toast.makeText(innerContext, "Connection Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
