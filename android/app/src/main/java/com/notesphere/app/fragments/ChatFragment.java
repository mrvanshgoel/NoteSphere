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
        
        binding.btnSearchChat.setOnClickListener(v -> Toast.makeText(getContext(), "Search Chat feature coming soon", Toast.LENGTH_SHORT).show());
        binding.btnPinChat.setOnClickListener(v -> Toast.makeText(getContext(), "Pin Chat feature coming soon", Toast.LENGTH_SHORT).show());

        setupModelSpinner();

        // Initial setup
        startNewChat();
        
        return binding.getRoot();
    }

    private void setupModelSpinner() {
        String[] modes = {"Auto", "Fast", "Balanced", "Deep Reasoning", "Document Expert"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerModel.setAdapter(adapter);
    }

    private void startNewChat() {
        if (currentChatId != null && messages.isEmpty()) {
            // Delete empty orphaned chat session
            ApiClient.getInstance().deleteChatSession(currentChatId).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> res) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        }
        currentChatId = null;
        attachedMaterialId = null;
        messages.clear();
        apiHistory.clear();
        adapter.notifyDataSetChanged();
        binding.tvChatTitle.setText("Chat");
        binding.btnAttachFile.setColorFilter(null);
        
        // Premium Empty State: "Start a new study conversation"
        // (Handled by RecyclerView being empty)
    }

    private void showChatHistory() {
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_chat_history, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        dialog.setContentView(sheetView);

        RecyclerView rv = sheetView.findViewById(R.id.rvChatHistory);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        View layoutEmpty = sheetView.findViewById(R.id.layoutEmptyHistory); // Assuming we add this or handle it

        ApiClient.getInstance().getChatSessions().enqueue(new Callback<List<ChatSession>>() {
            @Override
            public void onResponse(Call<List<ChatSession>> call, Response<List<ChatSession>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ChatSession> sessions = response.body();
                    if (sessions.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        rv.setVisibility(View.GONE);
                        return;
                    }
                    layoutEmpty.setVisibility(View.GONE);
                    rv.setVisibility(View.VISIBLE);

                    ChatHistoryAdapter historyAdapter = new ChatHistoryAdapter(sessions, session -> {
                        loadChat(session);
                        dialog.dismiss();
                    }, session -> {
                        showRenameDialog(session, dialog);
                    });
                    rv.setAdapter(historyAdapter);
                    
                    // Swipe to Delete
                    new androidx.recyclerview.widget.ItemTouchHelper(new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                        @Override
                        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                            return false;
                        }

                        @Override
                        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                            int pos = viewHolder.getAbsoluteAdapterPosition();
                            ChatSession session = historyAdapter.getSessionAt(pos);
                            if (session != null) {
                                deleteChat(session, null); // Delete without closing dialog yet
                            }
                        }
                    }).attachToRecyclerView(rv);
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

    private void showRenameDialog(ChatSession session, BottomSheetDialog dialog) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Rename Chat");

        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setText(session.getTitle());
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("Rename", (d, which) -> {
            String newTitle = input.getText().toString().trim();
            if (!newTitle.isEmpty() && !newTitle.equals(session.getTitle())) {
                session.setTitle(newTitle);
                ApiClient.getInstance().updateChatSession(session.getId(), session).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(getContext(), "Chat renamed", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            // Refresh history sheet by dismissing and reopening, or update adapter. 
                            // Since we have an active dialog, simpler to just close and toast for now, or fetch again.
                            if (session.getId().equals(currentChatId)) {
                                binding.tvChatTitle.setText(newTitle);
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {}
                });
            }
        });
        builder.setNegativeButton("Cancel", (d, which) -> d.cancel());
        builder.show();
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

    private void deleteChat(ChatSession session, @Nullable BottomSheetDialog dialog) {
        ApiClient.getInstance().deleteChatSession(session.getId()).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    if (session.getId().equals(currentChatId)) startNewChat();
                    if (dialog != null) dialog.dismiss();
                    else Toast.makeText(getContext(), "Chat deleted", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    private void showMaterialPicker() {
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_material_picker, null);
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        dialog.setContentView(sheetView);

        RecyclerView rv = sheetView.findViewById(R.id.rvPickerMaterials);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        android.widget.ProgressBar pb = sheetView.findViewById(R.id.pbPickerLoading);
        android.widget.LinearLayout layoutEmpty = sheetView.findViewById(R.id.layoutPickerEmpty);
        android.widget.TextView tvEmpty = sheetView.findViewById(R.id.tvPickerEmpty);
        
        android.widget.LinearLayout layoutCurrent = sheetView.findViewById(R.id.layoutCurrentAttachment);
        android.widget.TextView tvCurrent = sheetView.findViewById(R.id.tvCurrentAttachment);
        android.widget.ImageButton btnRemove = sheetView.findViewById(R.id.btnRemoveAttachment);
        com.google.android.material.button.MaterialButton btnClear = sheetView.findViewById(R.id.btnClearAttachment);
        
        com.google.android.material.textfield.TextInputEditText etSearch = sheetView.findViewById(R.id.etSearchMaterial);
        com.google.android.material.chip.ChipGroup chipGroup = sheetView.findViewById(R.id.chipGroupSubjects);
        com.google.android.material.chip.Chip chipRecent = sheetView.findViewById(R.id.chipRecent);

        // State variables for the picker
        final List<Material> currentMaterialsList = new ArrayList<>();
        final com.notesphere.app.adapters.MaterialAdapter[] pickerAdapter = new com.notesphere.app.adapters.MaterialAdapter[1];
        
        if (attachedMaterialId != null) {
            layoutCurrent.setVisibility(View.VISIBLE);
            btnClear.setVisibility(View.VISIBLE);
            tvCurrent.setText("Attached: " + attachedMaterialId); // Optionally store actual title in a field
        }

        View.OnClickListener removeAttachmentListener = v -> {
            attachedMaterialId = null;
            layoutCurrent.setVisibility(View.GONE);
            btnClear.setVisibility(View.GONE);
            binding.btnAttachFile.setColorFilter(null);
            Toast.makeText(getContext(), "Attachment removed", Toast.LENGTH_SHORT).show();
        };

        btnRemove.setOnClickListener(removeAttachmentListener);
        btnClear.setOnClickListener(removeAttachmentListener);

        pickerAdapter[0] = new com.notesphere.app.adapters.MaterialAdapter(currentMaterialsList, new com.notesphere.app.adapters.MaterialAdapter.OnMaterialClickListener() {
            @Override
            public void onMaterialClick(Material material) {
                attachedMaterialId = material.getId();
                layoutCurrent.setVisibility(View.VISIBLE);
                btnClear.setVisibility(View.VISIBLE);
                tvCurrent.setText(material.getTitle());
                binding.btnAttachFile.setColorFilter(android.graphics.Color.parseColor("#8B5CF6")); // ns_purple
                Toast.makeText(getContext(), "Attached: " + material.getTitle(), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
            @Override
            public void onMaterialLongClick(Material material) {}
        });
        rv.setAdapter(pickerAdapter[0]);

        // Function to load materials based on selected chip / subject
        java.util.function.Consumer<String> loadMaterials = (subjectId) -> {
            pb.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            rv.setVisibility(View.GONE);
            
            Call<List<Material>> call;
            if (subjectId == null) {
                call = ApiClient.getInstance().getRecentMaterials();
            } else {
                call = ApiClient.getInstance().getMaterials(subjectId, null);
            }
            
            call.enqueue(new Callback<List<Material>>() {
                @Override
                public void onResponse(Call<List<Material>> call, Response<List<Material>> response) {
                    pb.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        currentMaterialsList.clear();
                        currentMaterialsList.addAll(response.body());
                        pickerAdapter[0].notifyDataSetChanged();
                        
                        if (currentMaterialsList.isEmpty()) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                        } else {
                            rv.setVisibility(View.VISIBLE);
                        }
                    } else {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText("Failed to load materials");
                    }
                }
                @Override
                public void onFailure(Call<List<Material>> call, Throwable t) {
                    pb.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("Network error");
                }
            });
        };

        // Load subjects and build chips
        ApiClient.getInstance().getSubjects().enqueue(new Callback<List<com.notesphere.app.models.Subject>>() {
            @Override
            public void onResponse(Call<List<com.notesphere.app.models.Subject>> call, Response<List<com.notesphere.app.models.Subject>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    for (com.notesphere.app.models.Subject s : response.body()) {
                        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(requireContext());
                        chip.setText(s.getName());
                        chip.setCheckable(true);
                        chip.setChipBackgroundColorResource(R.color.ns_surface_variant);
                        chip.setTextColor(getResources().getColor(R.color.ns_text_primary));
                        chip.setChipStrokeColorResource(R.color.ns_surface_variant);
                        chip.setChipStrokeWidth(getResources().getDisplayMetrics().density * 1);
                        chip.setCheckedIconVisible(false);
                        
                        chipGroup.addView(chip);
                        
                        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) loadMaterials.accept(s.getId());
                        });
                    }
                }
            }
            @Override
            public void onFailure(Call<List<com.notesphere.app.models.Subject>> call, Throwable t) {}
        });

        chipRecent.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) loadMaterials.accept(null);
        });

        // Search logic
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String query = s.toString().toLowerCase().trim();
                List<Material> filtered = new ArrayList<>();
                for (Material m : currentMaterialsList) {
                    if (m.getTitle().toLowerCase().contains(query)) {
                        filtered.add(m);
                    }
                }
                pickerAdapter[0].updateList(filtered);
                
                if (filtered.isEmpty() && !query.isEmpty()) {
                    rv.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("No results found");
                } else if (!currentMaterialsList.isEmpty()) {
                    rv.setVisibility(View.VISIBLE);
                    layoutEmpty.setVisibility(View.GONE);
                }
            }
        });

        // Initial load
        chipRecent.setChecked(true); // This will trigger loadMaterials.accept(null)
        
        dialog.show();
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

        String selectedMode = binding.spinnerModel.getSelectedItem().toString();
        switch (selectedMode) {
            case "Fast": request.setModelMode("fast"); break;
            case "Balanced": request.setModelMode("balanced"); break;
            case "Deep Reasoning": request.setModelMode("deep"); break;
            case "Document Expert": request.setModelMode("document"); break;
            default: request.setModelMode("auto"); break;
        }

        binding.loadingAnimation.setVisibility(View.VISIBLE);
        binding.viewStatusDot.setBackgroundResource(R.drawable.bg_circle_green);

        ApiClient.getInstance().chat(request).enqueue(new Callback<AiResponse>() {
            @Override
            public void onResponse(Call<AiResponse> call, Response<AiResponse> response) {
                if (!isAdded() || binding == null) return;
                binding.loadingAnimation.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    AiResponse aiResponse = response.body();
                    String content = aiResponse.getContent();
                    String modelUsed = aiResponse.getModel();
                    
                    // First message of a new chat? Backend returns new chatId
                    if (currentChatId == null && aiResponse.getChatId() != null) {
                        currentChatId = aiResponse.getChatId();
                        
                        // Auto-generate title from first prompt
                        String title = text.length() > 40 ? text.substring(0, 37) + "..." : text;
                        ChatSession session = new ChatSession();
                        session.setTitle(title);
                        ApiClient.getInstance().updateChatSession(currentChatId, session).enqueue(new Callback<Void>() {
                            @Override public void onResponse(Call<Void> call, Response<Void> res) {
                                if (res.isSuccessful()) binding.tvChatTitle.setText(title);
                            }
                            @Override public void onFailure(Call<Void> call, Throwable t) {}
                        });
                    }

                    messages.add(new ChatMessage(content, false));
                    apiHistory.add(new ApiMessage("assistant", content));
                    adapter.notifyItemInserted(messages.size() - 1);
                    binding.rvChat.smoothScrollToPosition(messages.size() - 1);
                    
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
