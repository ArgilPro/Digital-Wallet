package com.example.rapidjava;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AdminVoucherGeneratorActivity extends AppCompatActivity {

    private EditText amountEditText;
    private Button generateButton;
    private TextView resultTextView;
    private FirebaseFirestore db;
    private boolean isAdmin = false;
    private static final String TAG = "VoucherGenerator";
    private static final int MIN_AMOUNT = 10;
    private static final int MAX_AMOUNT = 1000;
    private static final int CODE_LENGTH = 8;
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_voucher_generator);

        db = FirebaseFirestore.getInstance();

        amountEditText = findViewById(R.id.amountEditText);
        generateButton = findViewById(R.id.generateButton);
        resultTextView = findViewById(R.id.resultTextView);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Check admin privileges
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && Boolean.TRUE.equals(documentSnapshot.getBoolean("isAdmin"))) {
                        isAdmin = true;
                        generateButton.setEnabled(true);
                        resultTextView.setText("Admin privileges verified. Enter amount between SRD " + MIN_AMOUNT + "-" + MAX_AMOUNT + ".");
                    } else {
                        Toast.makeText(this, "Access denied. Admin only.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error checking admin status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });

        generateButton.setEnabled(false);

        generateButton.setOnClickListener(v -> {
            if (!isAdmin) {
                Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
                return;
            }

            String amountText = amountEditText.getText().toString().trim();

            if (amountText.isEmpty()) {
                resultTextView.setText("Please enter an amount.");
                return;
            }

            try {
                double amount = Double.parseDouble(amountText);
                if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
                    resultTextView.setText("Amount must be between " + MIN_AMOUNT + " and " + MAX_AMOUNT);
                    return;
                }
                generateVoucher(amount);
            } catch (NumberFormatException e) {
                resultTextView.setText("Invalid amount format");
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // In generateVoucher method, update the voucher creation to match TopUpActivity expectations:
    private void generateVoucher(double amount) {
        try {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
                return;
            }

            String voucherCode = generateUniqueVoucherCode();

            // Store voucher - IMPORTANT: Match the field names expected by TopUpActivity
            Map<String, Object> voucher = new HashMap<>();
            // DON'T include "code" as a field since the document ID is the code
            voucher.put("amount", amount);
            voucher.put("currency", "SRD");
            voucher.put("used", false);
            voucher.put("createdAt", new Date());
            voucher.put("createdBy", currentUser.getUid());
            voucher.put("createdByEmail", currentUser.getEmail());
            // TopUpActivity expects "expiresAt" as Long timestamp, not Date
            voucher.put("expiresAt", System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)); // 30 days
            // Add these fields for tracking
            voucher.put("used", false);
            voucher.put("usedBy", null);
            voucher.put("usedAt", null);

            db.collection("vouchers").document(voucherCode).set(voucher)
                    .addOnSuccessListener(aVoid -> {
                        logAdminAction("voucher_generated", amount, voucherCode);
                        resultTextView.setText("Voucher generated successfully!\n\n" +
                                "Amount: SRD " + String.format(Locale.getDefault(), "%.2f", amount) + "\n" +
                                "Voucher Code: " + voucherCode + "\n\n" +
                                "This voucher expires in 30 days. Share securely.");
                        Toast.makeText(this, "Voucher created successfully", Toast.LENGTH_SHORT).show();

                        Log.d(TAG, "Voucher created: " + voucherCode);

                        // Log the exact structure for debugging
                        Log.d(TAG, "Voucher structure: " + voucher.toString());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to create voucher: " + e.getMessage(), e);
                        resultTextView.setText("Error: " + e.getMessage());
                        if (e.getMessage().contains("permission")) {
                            Toast.makeText(this, "Permission denied. Check Firestore rules.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Failed to create voucher: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error generating voucher: " + e.getMessage(), e);
            resultTextView.setText("Error: " + e.getMessage());
            Toast.makeText(this, "Error generating voucher", Toast.LENGTH_SHORT).show();
        }
    }

    private String generateUniqueVoucherCode() {
        SecureRandom random = new SecureRandom();
        String code;
        int retries = 0;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
            }
            code = sb.toString();
            retries++;
            if (retries > 10) { // Prevent infinite loop
                throw new RuntimeException("Unable to generate unique code after 10 tries");
            }
        } while (codeExists(code)); // Check uniqueness
        return code;
    }

    private boolean codeExists(String code) {
        // Async check would be better, but for simplicity, assume low collision; in prod, query Firestore
        // For now, use a sync get (fire-and-forget in prod)
        return false; // Placeholder - implement db.collection("vouchers").document(code).get().continueWith(task -> task.getResult().exists())
    }

    private void logAdminAction(String action, double amount, String code) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("action", action);
        log.put("amount", amount);
        log.put("code", code);
        log.put("adminId", user.getUid());
        log.put("timestamp", new Date());

        db.collection("admin_actions").add(log);
    }
}