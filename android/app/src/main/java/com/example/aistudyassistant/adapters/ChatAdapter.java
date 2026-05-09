package com.example.aistudyassistant.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.aistudyassistant.databinding.ItemChatAiBinding;
import com.example.aistudyassistant.databinding.ItemChatUserBinding;
import com.example.aistudyassistant.models.ChatMessage;
import io.noties.markwon.Markwon;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<ChatMessage> messages;
    private Markwon markwon;
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
            String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            userHolder.binding.tvTime.setText(time);
            userHolder.binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(message.getMessage());
                }
                return true;
            });
        } else {
            AiViewHolder aiHolder = (AiViewHolder) holder;
            if (markwon == null) {
                markwon = Markwon.create(aiHolder.binding.getRoot().getContext());
            }
            markwon.setMarkdown(aiHolder.binding.tvMessage, message.getMessage());
            
            String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            aiHolder.binding.tvTime.setText(time);

            aiHolder.binding.btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("AI Response", message.getMessage());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(v.getContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show();
            });

            aiHolder.binding.btnPdf.setOnClickListener(v -> {
                saveChatAsPdf(v.getContext(), message.getMessage());
            });

            aiHolder.binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(message.getMessage());
                }
                return true;
            });
        }
    }

    private void saveChatAsPdf(Context context, String content) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(14f);
        paint.setFakeBoldText(true);

        int x = 40, y = 50;
        canvas.drawText("AI Study Assistant - Notes", x, y, paint);
        y += 40;

        paint.setFakeBoldText(false);
        paint.setTextSize(10f);
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (y > 800) {
                document.finishPage(page);
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = 50;
            }
            canvas.drawText(line, x, y, paint);
            y += 15;
        }

        document.finishPage(page);

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String fileName = "AI_Notes_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
        File file = new File(downloadsDir, fileName);

        try {
            document.writeTo(new FileOutputStream(file));
            Toast.makeText(context, "Saved to Downloads: " + fileName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        document.close();
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
