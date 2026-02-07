package com.example.rapidjava;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class Feedback extends AppCompatActivity {

    private RadioGroup radioFeedbackType;
    private TextInputEditText editEmail, editFeedback;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        // Initialize views
        radioFeedbackType = findViewById(R.id.radioFeedbackType);
        editEmail = findViewById(R.id.editEmail);
        editFeedback = findViewById(R.id.editFeedback);
        btnSubmit = findViewById(R.id.btnSubmit);

        ImageButton btnBack = findViewById(R.id.btnBack);

        // Set click listeners
        btnSubmit.setOnClickListener(v -> submitFeedback());
        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void submitFeedback() {
        // Get feedback type
        String feedbackType;
        int selectedId = radioFeedbackType.getCheckedRadioButtonId();

        if (selectedId == R.id.radioBug) {
            feedbackType = "Bug Report";
        } else if (selectedId == R.id.radioSuggestion) {
            feedbackType = "Suggestion";
        } else {
            feedbackType = "Other";
        }

        // Get email and feedback text
        String email = editEmail.getText().toString().trim();
        String feedbackText = editFeedback.getText().toString().trim();

        // Validate feedback
        if (feedbackText.isEmpty()) {
            editFeedback.setError("Please enter your feedback");
            editFeedback.requestFocus();
            return;
        }

        // Here you would typically send the feedback to your server/backend
        // For now, we'll just show a confirmation and close the activity

        // Prepare feedback data
        String feedbackData = "Type: " + feedbackType + "\n"
                + "Email: " + (email.isEmpty() ? "Not provided" : email) + "\n"
                + "Feedback: " + feedbackText;

        // In a real app, you would send this to your backend
        sendFeedbackToServer(feedbackData);

        // Show confirmation
        Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show();

        // Close the activity
        finish();
    }

    private void sendFeedbackToServer(String feedbackData) {
        // Implement your actual feedback submission logic here
        // This could be:
        // 1. Sending to a REST API
        // 2. Saving to a local database
        // 3. Sending via email intent

        // Example using email intent:
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("message/rfc822");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@yourapp.com"});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "App Feedback");
        emailIntent.putExtra(Intent.EXTRA_TEXT, feedbackData);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send feedback via..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}