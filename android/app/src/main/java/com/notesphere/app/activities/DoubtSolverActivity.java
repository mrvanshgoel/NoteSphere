package com.notesphere.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.notesphere.app.adapters.ChatAdapter;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityDoubtSolverBinding;
import com.notesphere.app.models.AiResponse;
import com.notesphere.app.models.ChatMessage;
import com.notesphere.app.models.DoubtRequest;
import com.notesphere.app.utils.SharedPrefManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DoubtSolverActivity extends AppCompatActivity {
    private ActivityDoubtSolverBinding binding;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private String materialId;
    private String materialTitle;
    private Call<?> activeCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDoubtSolverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        materialId = getIntent().getStringExtra("material_id");
        materialTitle = getIntent().getStringExtra("material_title");

        binding.tvMaterialName.setText("Discussing: " + materialTitle);
        binding.btnBack.setOnClickListener(v -> finish());

        adapter = new ChatAdapter(messages);
        binding.rvChat.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChat.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendQuestion());
        
        // Initial AI message
        messages.add(new ChatMessage("Hi! I've analyzed your document. Ask me any doubts you have about it.", false));
        adapter.notifyItemInserted(0);
    }

    private void sendQuestion() {
        String question = binding.etMessage.getText().toString().trim();
        if (question.isEmpty()) return;

        // UI Update
        messages.add(new ChatMessage(question, true));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.rvChat.smoothScrollToPosition(messages.size() - 1);
        binding.etMessage.setText("");

        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        DoubtRequest request = new DoubtRequest(materialId, question);

        binding.loadingAnimation.setVisibility(View.VISIBLE);

        if (activeCall != null) activeCall.cancel();
        Call<AiResponse> call = ApiClient.getInstance().solveDoubt(token, request);
        activeCall = call;

        call.enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                binding.loadingAnimation.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().getContent();
                    messages.add(new ChatMessage(content, false));
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.rvChat.smoothScrollToPosition(messages.size() - 1);
                } else {
                    Toast.makeText(DoubtSolverActivity.this, "AI failed to respond", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AiResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                binding.loadingAnimation.setVisibility(View.GONE);
                Toast.makeText(DoubtSolverActivity.this, "Connection Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        binding = null;
    }
}
