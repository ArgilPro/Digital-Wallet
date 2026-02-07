package com.example.rapidjava;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.rapidjava.adapter.ActivityLogAdapter;
import com.example.rapidjava.model.ActivityLog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class Meldingen extends AppCompatActivity implements ActivityLogAdapter.OnLogClickListener {

    private RecyclerView activityLogRecyclerView;
    private TextView emptyView;
    private ActivityLogAdapter adapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private View loadingOverlay;
    private TextView loadingMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meldingen);

        // Initialize views
        activityLogRecyclerView = findViewById(R.id.transactionsRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingMessage = findViewById(R.id.loadingMessage);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Show loading overlay
        showLoadingDialog("Loading transaction history...");

        // Set up back button
        findViewById(R.id.backButton).setOnClickListener(v -> navigateBack());

        // Set up RecyclerView
        setupRecyclerView();
        loadActivityLogs();
    }

    private void setupRecyclerView() {
        adapter = new ActivityLogAdapter(this);
        activityLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        activityLogRecyclerView.setAdapter(adapter);
    }

    private void loadActivityLogs() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            dismissLoadingDialog();
            Toast.makeText(this, "Please log in to view history", Toast.LENGTH_SHORT).show();
            navigateBack();
            return;
        }

        String currentUserId = user.getUid();

        db.collection("activity_logs")
                .whereEqualTo("userId", currentUserId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    dismissLoadingDialog();

                    if (error != null) {
                        Log.w("Meldingen", "Listen failed", error);
                        Toast.makeText(this, "Error loading history: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        List<ActivityLog> logs = new ArrayList<>();
                        for (QueryDocumentSnapshot document : snapshots) {
                            try {
                                ActivityLog log = document.toObject(ActivityLog.class);
                                log.setId(document.getId());
                                if (document.get("timestamp") instanceof com.google.firebase.Timestamp) {
                                    log.setTimestamp(document.getTimestamp("timestamp"));
                                }
                                logs.add(log);
                            } catch (Exception e) {
                                Log.e("Meldingen", "Error processing log: " + e.getMessage());
                            }
                        }
                        adapter.submitList(logs);
                        updateEmptyState(logs);
                    } else {
                        updateEmptyState(new ArrayList<>());
                    }
                });
    }

    private void showLoadingDialog(String message) {
        runOnUiThread(() -> {
            if (message != null) {
                loadingMessage.setText(message);
            }
            loadingOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void dismissLoadingDialog() {
        runOnUiThread(() -> loadingOverlay.setVisibility(View.GONE));
    }

    private void updateEmptyState(List<ActivityLog> logs) {
        runOnUiThread(() -> {
            if (logs.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
                activityLogRecyclerView.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                activityLogRecyclerView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void navigateBack() {
        Intent intent = new Intent(this, Home.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        navigateBack();
    }

    @Override
    public void onLogClick(ActivityLog log) {
        // Show details dialog (optional, for user to view full details)
        new android.app.AlertDialog.Builder(this)
                .setTitle(log.getAction())
                .setMessage(log.generateDisplayDetails() + "\n\nTime: " +
                        (log.getTimestamp() != null ? log.getTimestamp().toDate().toString() : "Unknown"))
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }
}