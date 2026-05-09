package com.notesphere.app.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.notesphere.app.api.ApiClient;
import com.notesphere.app.databinding.ActivityQuizBinding;
import com.notesphere.app.models.AiRequest;
import com.notesphere.app.models.QuizQuestion;
import com.notesphere.app.models.QuizResponse;
import com.notesphere.app.utils.SharedPrefManager;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QuizActivity extends AppCompatActivity {
    private ActivityQuizBinding binding;
    private List<QuizQuestion> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int score = 0;
    private String materialId;
    private Call<?> activeCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        materialId = getIntent().getStringExtra("material_id");
        
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnSubmit.setOnClickListener(v -> checkAnswer());
        binding.btnNext.setOnClickListener(v -> nextQuestion());
        binding.btnRetry.setOnClickListener(v -> loadQuiz());

        loadQuiz();
    }

    private void loadQuiz() {
        binding.layoutError.setVisibility(View.GONE);
        binding.layoutQuizContent.setVisibility(View.GONE);
        binding.layoutLoading.setVisibility(View.VISIBLE);
        binding.quizProgress.setIndeterminate(true);
        String token = "Bearer " + SharedPrefManager.getInstance(this).getToken();
        AiRequest request = new AiRequest(materialId);

        if (activeCall != null) activeCall.cancel();
        Call<QuizResponse> call = ApiClient.getInstance().generateQuiz(token, request);
        activeCall = call;

        call.enqueue(new Callback<QuizResponse>() {
            @Override
            public void onResponse(Call<QuizResponse> call, Response<QuizResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                binding.quizProgress.setIndeterminate(false);
                binding.layoutLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null
                        && response.body().getQuestions() != null
                        && !response.body().getQuestions().isEmpty()) {
                    questions = response.body().getQuestions();
                    binding.layoutQuizContent.setVisibility(View.VISIBLE);
                    displayQuestion();
                } else {
                    showError("No questions generated. The material might be too short or complex.");
                }
            }

            @Override
            public void onFailure(Call<QuizResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                binding.quizProgress.setIndeterminate(false);
                binding.layoutLoading.setVisibility(View.GONE);
                showError("Network Error: " + t.getMessage());
            }
        });
    }

    private void showError(String message) {
        binding.layoutError.setVisibility(View.VISIBLE);
        binding.tvErrorText.setText(message);
    }

    private void displayQuestion() {
        QuizQuestion q = questions.get(currentQuestionIndex);
        binding.tvProgress.setText("Question " + (currentQuestionIndex + 1) + "/" + questions.size());
        binding.quizProgress.setProgress((int) (((float)(currentQuestionIndex + 1) / questions.size()) * 100));
        
        binding.tvQuestion.setText(q.getQuestion());
        binding.optionsGroup.removeAllViews();
        binding.cardExplanation.setVisibility(View.GONE);
        binding.btnSubmit.setVisibility(View.VISIBLE);
        binding.btnNext.setVisibility(View.GONE);

        for (int i = 0; i < q.getOptions().size(); i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(q.getOptions().get(i));
            rb.setTextColor(Color.WHITE); // Assuming dark mode NotebookLM theme
            rb.setId(i);
            rb.setPadding(24, 32, 24, 32);
            rb.setTextSize(16f);
            
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, 
                RadioGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            rb.setLayoutParams(params);
            
            // Add a subtle pill background
            rb.setBackgroundResource(com.notesphere.app.R.drawable.bg_pill_surface);

            binding.optionsGroup.addView(rb);
        }
    }

    private void checkAnswer() {
        int selectedId = binding.optionsGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            return;
        }

        QuizQuestion q = questions.get(currentQuestionIndex);
        boolean isCorrect = (selectedId == q.getCorrectIndex());
        
        if (isCorrect) score++;

        // Visual feedback
        for (int i = 0; i < binding.optionsGroup.getChildCount(); i++) {
            RadioButton rb = (RadioButton) binding.optionsGroup.getChildAt(i);
            rb.setEnabled(false);
            if (i == q.getCorrectIndex()) {
                rb.setTextColor(Color.parseColor("#4CAF50")); // Green
            } else if (i == selectedId) {
                rb.setTextColor(Color.parseColor("#FF5252")); // Red
            }
        }

        binding.tvExplanation.setText(q.getExplanation());
        binding.cardExplanation.setVisibility(View.VISIBLE);
        binding.tvExplanationTitle.setTextColor(isCorrect ? Color.parseColor("#4CAF50") : Color.parseColor("#FF5252"));
        binding.tvExplanationTitle.setText(isCorrect ? "Correct!" : "Incorrect");

        binding.btnSubmit.setVisibility(View.GONE);
        binding.btnNext.setVisibility(View.VISIBLE);
        
        if (currentQuestionIndex == questions.size() - 1) {
            binding.btnNext.setText("Finish Quiz");
        }
    }

    private void nextQuestion() {
        if (currentQuestionIndex < questions.size() - 1) {
            currentQuestionIndex++;
            displayQuestion();
        } else {
            showResults();
        }
    }

    private void showResults() {
        Toast.makeText(this, "Quiz Finished! Score: " + score + "/" + questions.size(), Toast.LENGTH_LONG).show();
        finish();
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
