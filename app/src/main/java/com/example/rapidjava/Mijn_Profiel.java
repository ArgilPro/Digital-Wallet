package com.example.rapidjava;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class Mijn_Profiel extends AppCompatActivity {
    private static final String TAG = "MijnProfiel";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Activity created");

        try {
            setContentView(R.layout.activity_mijn_profiel);
            initializeViews();
        } catch (Exception e) {
            Log.e(TAG, "Initialization error", e);
            showErrorAndFinish();
        }
    }

    private void initializeViews() {
        try {
            // Initialize all clickable views
            View accountOption = findViewById(R.id.optionMyAccount);
            View feedbackOption = findViewById(R.id.optionFeedback);
            View aboutOption = findViewById(R.id.optionAbout);
            ImageButton backButton = findViewById(R.id.btnBack);

            // Set click listeners with null checks
            setClickListener(accountOption, Mijn_Account.class);
            setClickListener(feedbackOption, Feedback.class);
            setClickListener(aboutOption, AboutUs.class);

            if (backButton != null) {
                backButton.setOnClickListener(v -> navigateToHome());
            }
        } catch (Exception e) {
            Log.e(TAG, "View initialization failed", e);
            throw e;
        }
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, Home.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void setClickListener(View view, Class<?> targetActivity) {
        if (view != null) {
            view.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(this, targetActivity);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                } catch (Exception e) {
                    Log.e(TAG, "Navigation failed to " + targetActivity.getSimpleName(), e);
                    Toast.makeText(this, "Cannot open this screen", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        navigateToHome();
    }

    @Override
    public void finish() {
        super.finish();
        try {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        } catch (Exception e) {
            Log.e(TAG, "Transition animation failed", e);
        }
    }

    private void showErrorAndFinish() {
        Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show();
        navigateToHome();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed");
    }
}