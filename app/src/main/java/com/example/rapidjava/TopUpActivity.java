package com.example.rapidjava;

import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.google.android.material.progressindicator.CircularProgressIndicator; // For loading

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class TopUpActivity extends AppCompatActivity {

    private EditText voucherCodeEditText;
    private Button validateVoucherButton;
    private TextView resultTextView;
    private int attemptCount = 0;
    private static final int MAX_ATTEMPTS = 3; // Stricter
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private Dialog loadingDialog; // Non-dismissible loading

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topup);

        voucherCodeEditText = findViewById(R.id.voucherCodeEditText);
        validateVoucherButton = findViewById(R.id.validateVoucherButton);
        resultTextView = findViewById(R.id.resultTextView);
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Autofill from scan
        String scannedCode = getIntent().getStringExtra("VOUCHER_CODE");
        if (scannedCode != null) {
            voucherCodeEditText.setText(scannedCode);
            validateVoucher(scannedCode);
        }

        validateVoucherButton.setOnClickListener(v -> {
            String voucherCode = voucherCodeEditText.getText().toString().trim().toUpperCase();
            if (!isValidVoucherFormat(voucherCode)) {
                showErrorDialog("Invalid voucher format. Must be 8 characters.");
                return;
            }
            if (voucherCode.isEmpty()) {
                showErrorDialog("Please enter a voucher code.");
            } else if (attemptCount >= MAX_ATTEMPTS) {
                showErrorDialog("Too many attempts. Try again later.");
                validateVoucherButton.setEnabled(false);
            } else {
                attemptCount++;
                validateVoucher(voucherCode);
            }
        });
    }

    private boolean isValidVoucherFormat(String code) {
        return Pattern.matches("^[A-Z0-9]{8}$", code);
    }


    private void validateVoucher(String plainCode) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showErrorDialog("You need to be logged in.");
            return;
        }

        showLoadingDialog("Validating voucher...");

        // Simple validation first
        if (!isValidVoucherFormat(plainCode)) {
            dismissLoadingDialog();
            showErrorDialog("Invalid voucher format. Must be 8 characters (letters and numbers only).");
            return;
        }

        processVoucherTransaction(plainCode, user);
    }


    private void processVoucherTransaction(String voucherCode, FirebaseUser user) {
        DocumentReference voucherRef = db.collection("vouchers").document(voucherCode);
        DocumentReference userRef = db.collection("users").document(user.getUid());

        // Add logging before transaction
        Log.d("TopUpActivity", "Processing voucher: " + voucherCode);
        Log.d("TopUpActivity", "User ID: " + user.getUid());

        db.runTransaction(transaction -> {
            try {
                // Get voucher document
                DocumentSnapshot voucherSnapshot = transaction.get(voucherRef);

                Log.d("TopUpActivity", "Voucher exists: " + voucherSnapshot.exists());

                if (!voucherSnapshot.exists()) {
                    Log.d("TopUpActivity", "Voucher does not exist in database");
                    throw new Exception("Voucher does not exist");
                }

                // Log all fields for debugging
                Log.d("TopUpActivity", "Voucher data: " + voucherSnapshot.getData());

                // Check if voucher is used
                Boolean used = voucherSnapshot.getBoolean("used");
                if (used == null) {
                    Log.d("TopUpActivity", "Used field is null");
                    throw new Exception("Invalid voucher data - used field missing");
                }
                if (used) {
                    Log.d("TopUpActivity", "Voucher already used");
                    throw new Exception("Voucher has already been used");
                }

                // Check expiration
                Long expiresAt = voucherSnapshot.getLong("expiresAt");
                if (expiresAt == null) {
                    Log.d("TopUpActivity", "expiresAt field is null");
                    throw new Exception("Invalid voucher expiration");
                }

                Log.d("TopUpActivity", "Current time: " + System.currentTimeMillis());
                Log.d("TopUpActivity", "Expires at: " + expiresAt);

                if (System.currentTimeMillis() > expiresAt) {
                    Log.d("TopUpActivity", "Voucher expired");
                    throw new Exception("Voucher has expired");
                }

                // Get amount
                Double amount = voucherSnapshot.getDouble("amount");
                if (amount == null) {
                    Log.d("TopUpActivity", "Amount field is null");
                    throw new Exception("Invalid voucher amount");
                }

                Log.d("TopUpActivity", "Voucher amount: " + amount);

                if (amount <= 0 || amount > 1000) {
                    Log.d("TopUpActivity", "Amount out of range: " + amount);
                    throw new Exception("Invalid voucher amount");
                }

                // Get user document
                DocumentSnapshot userSnapshot = transaction.get(userRef);
                if (!userSnapshot.exists()) {
                    Log.d("TopUpActivity", "User document not found");
                    throw new Exception("User account not found");
                }

                // Get current balance
                Double currentBalance = userSnapshot.getDouble("balance");
                if (currentBalance == null) {
                    currentBalance = 0.0;
                    Log.d("TopUpActivity", "Setting initial balance to 0");
                }

                Log.d("TopUpActivity", "Current balance: " + currentBalance);
                Log.d("TopUpActivity", "New balance will be: " + (currentBalance + amount));

                // Update user balance
                transaction.update(userRef, "balance", currentBalance + amount);

                // Mark voucher as used
                Map<String, Object> voucherUpdates = new HashMap<>();
                voucherUpdates.put("used", true);
                voucherUpdates.put("usedBy", user.getUid());
                voucherUpdates.put("usedAt", FieldValue.serverTimestamp());
                transaction.update(voucherRef, voucherUpdates);

                Log.d("TopUpActivity", "Transaction successful, returning amount: " + amount);
                return amount;

            } catch (Exception e) {
                Log.e("TopUpActivity", "Transaction error: " + e.getMessage(), e);
                // Log the stack trace for better debugging
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).addOnSuccessListener(processedAmount -> {
            dismissLoadingDialog();
            Log.d("TopUpActivity", "Voucher redeemed successfully: " + processedAmount);

            showSuccessDialog("Voucher redeemed successfully!", processedAmount);
            logVoucherUse(voucherCode, processedAmount);

            Intent resultIntent = new Intent();
            resultIntent.putExtra("topUpAmount", processedAmount);
            setResult(RESULT_OK, resultIntent);

            // Also update the UI to show success
            resultTextView.setText("Success! Added SRD " + processedAmount + " to your wallet.");

            // You might want to refresh the user's balance display here
            refreshUserBalance();

        }).addOnFailureListener(e -> {
            dismissLoadingDialog();
            Log.e("TopUpActivity", "Voucher processing failed: " + e.getMessage(), e);

            // Log the full error for debugging
            if (e.getCause() != null) {
                Log.e("TopUpActivity", "Root cause: " + e.getCause().getMessage());
            }

            String errorMessage = "Failed to process voucher: ";
            String errorDetail = e.getMessage();

            if (errorDetail != null) {
                if (errorDetail.contains("does not exist")) {
                    errorMessage = "Voucher code not found";
                } else if (errorDetail.contains("already been used")) {
                    errorMessage = "Voucher has already been used";
                } else if (errorDetail.contains("expired")) {
                    errorMessage = "Voucher has expired";
                } else if (errorDetail.contains("Invalid voucher amount")) {
                    errorMessage = "Voucher amount is invalid";
                } else if (errorDetail.contains("User account not found")) {
                    errorMessage = "User account error - please log in again";
                } else {
                    errorMessage = "Error: " + errorDetail;
                }
            } else {
                errorMessage = "Network error. Please check your connection.";
            }

            showErrorDialog(errorMessage);
        });
    }

    // Add this method to refresh user balance
    private void refreshUserBalance() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Double balance = documentSnapshot.getDouble("balance");
                            if (balance != null) {
                                // Update your UI here if you have a balance display
                                Log.d("TopUpActivity", "Updated balance: " + balance);
                            }
                        }
                    });
        }
    }

    private void showLoadingDialog(String message) {
        loadingDialog = new Dialog(this);
        loadingDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        loadingDialog.setContentView(R.layout.custom_loading_dialog); // Create layout with CircularProgressIndicator and TextView
        loadingDialog.setCancelable(false);
        TextView msgText = loadingDialog.findViewById(R.id.loadingMessage);
        msgText.setText(message);
        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void logAMLActivity(String userId, String action, double amount) {
        Map<String, Object> log = new HashMap<>();
        log.put("userId", userId);
        log.put("action", action);
        log.put("amount", amount);
        log.put("timestamp", FieldValue.serverTimestamp());
        log.put("ip", "client-ip"); // Add geo if possible
        db.collection("aml_logs").add(log);
    }

    private void showSuccessDialog(String message, double amount) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_success);
        dialog.setCancelable(false);

        TextView successMessage = dialog.findViewById(R.id.successMessage);
        TextView amountText = dialog.findViewById(R.id.amountText);
        Button okButton = dialog.findViewById(R.id.okButton);

        successMessage.setText(message);
        amountText.setText("SRD " + amount + " added to your wallet");

        okButton.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        // Add animation
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        dialog.findViewById(R.id.successIcon).startAnimation(anim);

        dialog.show();
    }

    private void showErrorDialog(String message) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_error);
        dialog.setCancelable(true);

        TextView errorMessage = dialog.findViewById(R.id.errorMessage);
        Button okButton = dialog.findViewById(R.id.okButton);

        errorMessage.setText(message);

        okButton.setOnClickListener(v -> dialog.dismiss());

        // Add animation
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.shake);
        dialog.findViewById(R.id.errorIcon).startAnimation(anim);

        dialog.show();
    }

    private Task<Void> createUserDocument(DocumentReference userRef, String email) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("balance", 0.0);
        userData.put("createdAt", FieldValue.serverTimestamp());
        userData.put("updatedAt", FieldValue.serverTimestamp());

        return userRef.set(userData);
    }

    private void logVoucherUse(String voucherCode, double amount) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("voucherCode", voucherCode);
        log.put("amount", amount);
        log.put("userId", user.getUid());
        log.put("timestamp", new Date());
        log.put("device", Build.MODEL);

        FirebaseFirestore.getInstance().collection("voucherLogs").add(log);
    }
}