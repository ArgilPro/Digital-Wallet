package com.example.rapidjava;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;
import com.example.rapidjava.adapter.TransactionAdapter;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import android.util.Log;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.SharedPreferences;
import org.json.JSONObject;




public class Home extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final int TOP_UP_REQUEST = 1;
    private static final String USED_TOKENS_COLLECTION = "usedPaymentTokens";
    private BottomNavigationView bottomNavigationView;
    private TextView walletBalanceTextView;
    private double walletBalance = 0.00;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private View animationOverlay;
    private LottieAnimationView animationView;
    private Handler handler = new Handler();
    private SharedPreferences prefs;
    //private Vibrator vibrator;

    // Card display elements
    private TextView userNameTextView;
    private TextView userEmailTextView;
    private TextView statusIndicatorTextView;
    private TextView userIdDisplayTextView;
    private RecyclerView transactionsRecyclerView;
    private TransactionAdapter transactionAdapter;
    private List<com.example.rapidjava.model.Transaction> transactionList = new ArrayList<>();
    private TextView emptyTransactionsText;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);



        //setupNFC();
        userNameTextView = findViewById(R.id.user_greeting);
        userEmailTextView = findViewById(R.id.user_email); // Make sure this exists in your layout

        animationOverlay = findViewById(R.id.animationOverlay);
        animationView = findViewById(R.id.animationView);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        loadWalletBalance();
        updateCardDisplay();

        setupAccountIcon();
        KeyStoreUtils.generateAESKeyIfNotExists();



        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView);
        emptyTransactionsText = findViewById(R.id.empty_transactions_text);

        if (transactionsRecyclerView != null) {
            transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            transactionAdapter = new TransactionAdapter(transaction -> {
                // Klikgedrag voor transacties, bijvoorbeeld detailscherm
            });
            transactionsRecyclerView.setAdapter(transactionAdapter);
            loadRecentTransactions();
        } else {
            Log.e(TAG, "RecyclerView not found in layout");
        }




        prefs = getSharedPreferences("NFC_Prefs", MODE_PRIVATE);
        //nfcPaymentEnabled = prefs.getBoolean("nfc_enabled", false);
        //if (nfcToggle != null) {
        //    nfcToggle.setChecked(nfcPaymentEnabled);
        //}

        walletBalanceTextView = findViewById(R.id.wallet_balance_amount);
        updateWalletBalanceDisplay();

        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        if (bottomNavigationView != null) {
            bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.home) {
                    // Already on home
                    return true;
                } else if (itemId == R.id.scan) {
                    scanCode();
                    return true;
                } else if (itemId == R.id.request) {
                    startActivity(new Intent(this, Verzoek.class));
                    return true;
                } else if (itemId == R.id.topup) {
                    startActivity(new Intent(this, TopUpActivity.class));
                    return true;
                }
                return false;
            });
        }

        double topUpAmount = getIntent().getDoubleExtra("topUpAmount", 0.0);
        if (topUpAmount > 0) {
            topUpWallet(topUpAmount);
        }

        updateCardDisplay();
    }

    private void loadRecentTransactions() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("transactions")
                .whereEqualTo("payerId", user.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        emptyTransactionsText.setVisibility(View.VISIBLE);
                        transactionAdapter.submitList(null);
                    } else {
                        emptyTransactionsText.setVisibility(View.GONE);
                        List<com.example.rapidjava.model.Transaction> transactions =
                                querySnapshot.toObjects(com.example.rapidjava.model.Transaction.class);
                        transactionAdapter.submitList(transactions);

                    }
                })
                .addOnFailureListener(e -> {
                    emptyTransactionsText.setVisibility(View.VISIBLE);
                    emptyTransactionsText.setText("Nog geen transacties");
                });
    }



    private void updateCardDisplay() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && userNameTextView != null) { // Add null check
            // Update user name
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                userNameTextView.setText("Hoi, " + displayName);
            } else {
                String email = user.getEmail();
                if (email != null && email.contains("@")) {
                    // Extract username from email (part before @)
                    String username = email.substring(0, email.indexOf('@'));
                    // Capitalize first letter
                    username = username.substring(0, 1).toUpperCase() + username.substring(1);
                    userNameTextView.setText("Hoi, " + username);
                } else {
                    userNameTextView.setText("Hoi, Gebruiker");
                }
            }

            // Update email - add null check
            if (userEmailTextView != null && user.getEmail() != null) {
                userEmailTextView.setText(user.getEmail());
            }
        }
    }

    // Add this method to handle account icon click
    private void setupAccountIcon() {
        ImageView accountIcon = findViewById(R.id.account_icon);
        if (accountIcon != null) {
            accountIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    navigateToProfile();
                }
            });

            // Make sure the icon is clickable
            accountIcon.setClickable(true);
        }
    }

    private void navigateToProfile() {
        Intent intent = new Intent(Home.this, Mijn_Profiel.class);
        startActivity(intent);
    }


    // NFC lifecycle methods commented out
    @Override
    protected void onResume() {
        super.onResume();
        /*
        if (nfcUtils != null && nfcPaymentEnabled) {
            nfcUtils.enableForegroundDispatch(this);
        }
        */
        // Update card display when returning to the activity
        updateCardDisplay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        /*
        if (nfcUtils != null) {
            nfcUtils.disableForegroundDispatch(this);
        }
        */
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // QR code scanning intent handling (KEEP THIS - your existing functionality)
        if (intent.hasExtra("SCAN_RESULT")) {
            String scanResult = intent.getStringExtra("SCAN_RESULT");
            processPaymentRequest(scanResult);
        }
    }

    private void scanCode() {
        try {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan een voucher of betalingscode");
            options.setBeepEnabled(false);
            options.setOrientationLocked(true);
            options.setCaptureActivity(CaptureAct.class);
            barLauncher.launch(options);
        } catch (Exception e) {
            Log.e(TAG, "Error starting scanner", e);
            showErrorDialog("Scanner Error", "Could not start QR scanner: " + e.getMessage());
        }
    }

    private final ActivityResultLauncher<ScanOptions> barLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String scannedData = result.getContents().trim();
                    if (scannedData.startsWith("RapidPay|PaymentRequest|")) {
                        processPaymentRequest(scannedData);
                    } else {
                        showErrorDialog("Invalid QR Code", "Please scan a valid payment request QR code");
                    }
                }
            });

    private void isTokenUsed(String uuid, OnTokenCheckResult callback) {
        db.collection(USED_TOKENS_COLLECTION).document(uuid).get()
                .addOnSuccessListener(document -> {
                    callback.onCheck(document.exists());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Fout bij tokencheck", e);
                    callback.onCheck(true); // Veiligheid: behandel als gebruikt
                });
    }

    interface OnTokenCheckResult {
        void onCheck(boolean isUsed);
    }

    private void markTokenAsUsed(String uuid) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("usedBy", user.getUid());
        tokenData.put("usedAt", FieldValue.serverTimestamp());

        db.collection(USED_TOKENS_COLLECTION).document(uuid).set(tokenData)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Token gemarkeerd als gebruikt: " + uuid))
                .addOnFailureListener(e ->
                        Log.w(TAG, "Fout bij markeren token", e));
    }

    // Replace your current processPaymentRequest method with this improved version:
    private void processPaymentRequest(String qrData) {
        Log.d(TAG, "Starting QR processing with data: " + (qrData != null ? qrData.substring(0, Math.min(50, qrData.length())) + "..." : "null"));

        runOnUiThread(() -> {
            try {
                // Basic validation
                if (qrData == null || qrData.trim().isEmpty()) {
                    showErrorDialog("Invalid QR", "Scanned data is empty");
                    return;
                }

                // Check if user is logged in first
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser == null) {
                    showErrorDialog("Authentication Error", "Please log in again");
                    return;
                }
                String[] parts = qrData.split("\\|");
                Log.d(TAG, "QR parts length: " + parts.length);

                String ivB64;
                String encryptedB64;

                if (parts.length == 4) {
                    // Oud formaat: RapidPay|PaymentRequest|iv|ct
                    if (!"RapidPay".equals(parts[0]) || !"PaymentRequest".equals(parts[1])) {
                        showErrorDialog("Invalid QR Code", "Please scan a valid payment request QR code");
                        return;
                    }

                    Log.d(TAG, "Detected OLD QR format (CBC)");
                    ivB64 = parts[2];
                    encryptedB64 = parts[3];

                }
                else if (parts.length == 5) {
                    // Nieuw formaat: RapidPay|PaymentRequest|v1|iv|ct
                    if (!"RapidPay".equals(parts[0]) || !"PaymentRequest".equals(parts[1])) {
                        showErrorDialog("Invalid QR Code", "Please scan a valid payment request QR code");
                        return;
                    }

                    String version = parts[2];
                    Log.d(TAG, "Detected version: " + version);

                    if (!"v1".equals(version)) {
                        showErrorDialog("Invalid QR Version", "Unsupported QR version: " + version);
                        return;
                    }

                    ivB64 = parts[3];
                    encryptedB64 = parts[4];

                }
                else {
                    ivB64 = null;
                    encryptedB64 = null;
                    showErrorDialog("Invalid QR Code", "QR data format not recognized");
                    return;
                }


// Stap 4: validate data
                if (ivB64 == null || ivB64.isEmpty() || encryptedB64 == null || encryptedB64.isEmpty()) {
                    showErrorDialog("Invalid QR Code", "QR contains empty encryption parameters");
                    return;
                }


                // Show loading indicator
                showLoadingDialog("Processing QR code...");

                // Process decryption in background thread to avoid blocking UI
                new Thread(() -> {
                    try {
                        JSONObject decrypted = SecureQRUtils.decryptSecureQR(ivB64, encryptedB64);

                        runOnUiThread(() -> {
                            dismissLoadingDialog();

                            if (decrypted == null) {
                                Log.e(TAG, "Decryption failed - returned null");
                                showErrorDialog("Invalid QR Code", "Unable to decrypt payment request");
                                return;
                            }

                            Log.d(TAG, "Successfully decrypted QR data: " + decrypted.toString());
                            handleDecryptedQRData(decrypted, currentUser);
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Critical error in decryption thread: " + e.getMessage(), e);
                        runOnUiThread(() -> {
                            dismissLoadingDialog();
                            showErrorDialog("System Error", "Failed to process QR code: " + e.getMessage());
                        });
                    }
                }).start();

            } catch (Exception e) {
                Log.e(TAG, "Critical error processing payment request: " + e.getMessage(), e);
                showErrorDialog("System Error", "Unexpected error: " + e.getMessage());
            }
        });
    }

    private AlertDialog loadingDialog;

    private void showLoadingDialog(String message) {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message);
            builder.setCancelable(false);
            loadingDialog = builder.create();
            loadingDialog.show();
        });
    }

    private void dismissLoadingDialog() {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.dismiss();
            }
        });
    }

    private void handleDecryptedQRData(JSONObject decrypted, FirebaseUser currentUser) {
        try {
            // After successful decryption, check if it's a voucher
            if (decrypted.has("type") && "voucher".equals(decrypted.optString("type"))) {
                // Handle voucher redemption
                handleVoucherRedemption(decrypted, currentUser.getUid());
                return;
            }

            // Data uit JSON halen met safe methods
            double amount = decrypted.optDouble("amount", 0.0);
            if (amount <= 0) {
                showErrorDialog("Invalid Amount", "Payment amount is invalid: " + amount);
                return;
            }

            String currency = decrypted.optString("currency", "SRD");
            String date = decrypted.optString("date", "");
            String recipientId = decrypted.optString("recipientId", "");
            long expiresAt = decrypted.optLong("expiresAt", 0);
            String uuid = decrypted.optString("uuid", "");

            // Validate required fields
            if (recipientId.isEmpty()) {
                showErrorDialog("Invalid QR", "Missing recipient ID");
                return;
            }

            if (uuid.isEmpty()) {
                showErrorDialog("Invalid QR", "Missing transaction UUID");
                return;
            }

            // Check of QR is verlopen
            if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
                showErrorDialog("QR Expired", "This QR code has expired.");
                return;
            }

            // Check of betaling niet naar jezelf gaat
            if (currentUser.getUid().equals(recipientId)) {
                showErrorDialog("Error", "You cannot make a payment to yourself.");
                return;
            }

            // Check of token al gebruikt is
            isTokenUsed(uuid, isUsed -> {
                if (isUsed) {
                    showErrorDialog("QR Code Already Used", "This QR code has already been scanned and processed.");
                    return;
                }

                showPaymentConfirmationDialog(amount, currency, recipientId, uuid, date);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error handling decrypted QR data: " + e.getMessage(), e);
            showErrorDialog("Processing Error", "Failed to process payment data: " + e.getMessage());
        }
    }

    private void showPaymentConfirmationDialog(double amount, String currency, String recipientId, String uuid, String date) {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.custom_payment_dialog, null);

        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        LinearLayout confirmLayout = dialogView.findViewById(R.id.btn_confirm);
        LinearLayout cancelLayout = dialogView.findViewById(R.id.btn_cancel);

        // Set temporary message while fetching recipient name
        messageTextView.setText(String.format(Locale.getDefault(),
                "Betaal %s %.2f aan...", currency, amount));

        // Create the dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // Get recipient's name from Firestore
        fetchRecipientName(recipientId, new OnRecipientNameFetched() {
            @Override
            public void onNameFetched(String recipientName) {
                String displayName = recipientName != null ? recipientName : "gebruiker";
                String message = String.format(Locale.getDefault(),
                        "Weet je zeker dat je %s %.2f wilt betalen aan %s?",
                        currency, amount, displayName);

                messageTextView.setText(message);
            }

            @Override
            public void onError(String error) {
                String message = String.format(Locale.getDefault(),
                        "Weet je zeker dat je %s %.2f wilt betalen aan deze gebruiker?",
                        currency, amount);

                messageTextView.setText(message);
            }
        });

        // Set button listeners
        confirmLayout.setOnClickListener(v -> {
            markTokenAsUsed(uuid);
            proceedWithPayment(amount, uuid, date, recipientId, recipientId);
            dialog.dismiss();
        });

        cancelLayout.setOnClickListener(v -> dialog.dismiss());

        // Show the dialog
        dialog.show();

        // Customize dialog window
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    interface OnRecipientNameFetched {
        void onNameFetched(String recipientName);
        void onError(String error);
    }

    private void fetchRecipientName(String recipientId, OnRecipientNameFetched callback) {
        db.collection("users").document(recipientId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        if (name != null && !name.isEmpty()) {
                            callback.onNameFetched(name);
                        } else {
                            String email = documentSnapshot.getString("email");
                            callback.onNameFetched(email != null ? email : "gebruiker");
                        }
                    } else {
                        callback.onError("User not found");
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onError(e.getMessage());
                });
    }

    private void handleVoucherRedemption(JSONObject voucherData, String userId) {
        try {
            double amount = voucherData.getDouble("amount");
            String uuid = voucherData.getString("uuid");

            // Check if voucher already used
            db.collection("vouchers").document(uuid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && !documentSnapshot.getBoolean("used")) {
                            // Redeem voucher
                            redeemVoucher(amount, uuid, userId);
                        } else {
                            showErrorDialog("Voucher Used", "This voucher has already been redeemed.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        showErrorDialog("Error", "Could not verify voucher: " + e.getMessage());
                    });
        } catch (Exception e) {
            showErrorDialog("Invalid Voucher", "Voucher format is invalid.");
        }
    }

    private void redeemVoucher(double amount, String uuid, String userId) {
        // Update user balance
        db.collection("users").document(userId)
                .update("balance", FieldValue.increment(amount))
                .addOnSuccessListener(aVoid -> {
                    // Mark voucher as used
                    db.collection("vouchers").document(uuid)
                            .update("used", true, "redeemedBy", userId, "redeemedAt", new Date())
                            .addOnSuccessListener(aVoid2 -> {
                                logActivity("Voucher Redeemed", "Voucher of SRD " + amount + " redeemed", amount, true);
                                loadWalletBalance();
                            });
                })
                .addOnFailureListener(e -> {
                    showErrorDialog("Error", "Failed to redeem voucher: " + e.getMessage());
                });
    }

    public void proceedWithPayment(double amount, String transactionId, String timestamp,
                                   String recipientId, String recipientName) {
        FirebaseUser payer = mAuth.getCurrentUser();
        if (payer == null) {
            showErrorDialog("Error", "Not authenticated");
            return;
        }
        if (payer.getUid().equals(recipientId)) {
            showErrorDialog("Error", "Cannot pay yourself");
            return;
        }

        // First verify recipient exists
        db.collection("users").document(recipientId).get()
                .addOnSuccessListener(recipientSnapshot -> {
                    if (!recipientSnapshot.exists()) {
                        showErrorDialog("Error", "Recipient account not found");
                        return;
                    }

                    // Recipient exists, proceed with payment
                    processValidatedPayment(amount, transactionId, timestamp,
                            payer, recipientId, recipientName);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Recipient verification failed", e);
                    showErrorDialog("Error", "Could not verify recipient: " + e.getMessage());
                });
    }

    private void processValidatedPayment(double amount, String transactionId, String timestamp,
                                         FirebaseUser payer, String recipientId, String recipientName) {
        // Update local balance for UX
        walletBalance -= amount;
        updateWalletBalanceDisplay();

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentReference payerRef = db.collection("users").document(payer.getUid());
            DocumentSnapshot payerSnapshot = transaction.get(payerRef);

            // Verify payer document exists
            if (!payerSnapshot.exists()) {
                throw new RuntimeException("Payer account not found");
            }

            Double currentPayerBalance = payerSnapshot.getDouble("balance");
            if (currentPayerBalance == null || currentPayerBalance < amount) {
                throw new RuntimeException("Insufficient funds");
            }

            // Verify recipient document exists again (transaction context)
            DocumentReference recipientRef = db.collection("users").document(recipientId);
            DocumentSnapshot recipientSnapshot = transaction.get(recipientRef);
            if (!recipientSnapshot.exists()) {
                throw new RuntimeException("Recipient account not found");
            }

            // Update balances
            transaction.update(payerRef, "balance", FieldValue.increment(-amount));
            transaction.update(recipientRef, "balance", FieldValue.increment(amount));

            return null;
        }).addOnSuccessListener(aVoid -> {
            // Create transaction records (same as before)
            Map<String, Object> transactionData = createTransactionRecord(
                    amount, payer, recipientId, recipientName, transactionId, false);
            transactionData.put("date", timestamp);
            transactionData.put("uuid", transactionId);

            db.collection("transactions").add(transactionData)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Payment successful", Toast.LENGTH_SHORT).show();
                        logActivity("Payment Sent", "Paid SRD " + amount + " to " + recipientName, amount, false);
                        showSuccessAnimation();

                        // Na het succesvol loggen van de betaler
                        Map<String, Object> recipientTransaction = createTransactionRecord(
                                amount, payer, recipientId, recipientName, transactionId, true);
                        recipientTransaction.put("date", timestamp);
                        recipientTransaction.put("uuid", transactionId);

                        db.collection("transactions").add(recipientTransaction)
                                .addOnSuccessListener(docRef -> {
                                    Log.d(TAG, "Recipient transaction logged");
                                    // Hier ook logTransaction voor de ontvanger
                                    logActivity("Payment Received", "Received SRD " + amount + " from " + payer.getEmail(), amount, true);
                                })
                                .addOnFailureListener(e ->
                                        Log.w(TAG, "Error logging recipient transaction", e));

                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to create transaction record", e);
                        showErrorDialog("Warning",
                                "Payment completed but record failed. Contact support with code: " + transactionId);
                    });
        }).addOnFailureListener(e -> {
            // Rollback local balance
            walletBalance += amount;
            updateWalletBalanceDisplay();

            Log.e(TAG, "Payment failed: " + e.getMessage(), e);

            showFailureAnimation();

            if (e.getMessage().contains("Insufficient funds")) {
                showInsufficientBalanceDialog(amount);
            } else if (e.getMessage().contains("account not found")) {
                showErrorDialog("Error", "Account not found: " + e.getMessage());
            } else {
                showErrorDialog("Payment Failed",
                        "Could not complete payment: " + e.getMessage());
            }
        });
    }

    private void showSuccessAnimation() {
        runOnUiThread(() -> {
            animationOverlay.setVisibility(View.VISIBLE);
            animationView.setAnimation(R.raw.success);
            animationView.playAnimation();

            handler.postDelayed(() -> {
                animationOverlay.setVisibility(View.GONE);
                animationView.cancelAnimation();

                // Simply reload the wallet balance instead of restarting the activity
                loadWalletBalance();

            }, 3500); // Increased from 2000 to 3500 milliseconds (3.5 seconds)
        });
    }

    private void showFailureAnimation() {
        runOnUiThread(() -> {
            animationOverlay.setVisibility(View.VISIBLE);
            animationView.setAnimation(R.raw.payment_failed); // Make sure to add this JSON to raw folder
            animationView.playAnimation();

            handler.postDelayed(() -> {
                animationOverlay.setVisibility(View.GONE);
                animationView.cancelAnimation();
            }, 2000);
        });
    }


    private void showInsufficientBalanceDialog(double amount) {
        new AlertDialog.Builder(this)
                .setTitle("Insufficient Balance")
                .setMessage(String.format(Locale.getDefault(),
                        "You need SRD %.2f but only have SRD %.2f. Top up your wallet?",
                        amount, walletBalance))
                .setPositiveButton("Top Up", (dialog, which) -> {
                    startActivity(new Intent(this, TopUpActivity.class));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Map<String, Object> createTransactionRecord(double amount, FirebaseUser payer,
                                                        String recipientId, String recipientName,
                                                        String transactionId, boolean isRecipient) {
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("amount", amount);
        transaction.put("currency", "SRD");
        transaction.put("timestamp", FieldValue.serverTimestamp());
        transaction.put("transactionId", transactionId);

        if (isRecipient) {
            transaction.put("type", "received");
            transaction.put("fromUserId", payer.getUid());
            transaction.put("fromUserName", payer.getDisplayName() != null ? payer.getDisplayName() : payer.getEmail());
            transaction.put("toUserId", recipientId);
            transaction.put("toUserName", recipientName);
        } else {
            transaction.put("type", "sent");
            transaction.put("fromUserId", payer.getUid());
            transaction.put("fromUserName", payer.getDisplayName() != null ? payer.getDisplayName() : payer.getEmail());
            transaction.put("toUserId", recipientId);
            transaction.put("toUserName", recipientName);
        }

        return transaction;
    }

    private void logActivity(String title, String description, double amount, boolean isPositive) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> activity = new HashMap<>();
        activity.put("title", title);
        activity.put("description", description);
        activity.put("amount", amount);
        activity.put("isPositive", isPositive);
        activity.put("timestamp", FieldValue.serverTimestamp());
        activity.put("userId", user.getUid());

        db.collection("activity").add(activity)
                .addOnSuccessListener(documentReference ->
                        Log.d(TAG, "Activity logged: " + title))
                .addOnFailureListener(e ->
                        Log.w(TAG, "Error logging activity", e));
    }

    private void loadWalletBalance() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Double balance = documentSnapshot.getDouble("balance");
                            if (balance != null) {
                                walletBalance = balance;
                                updateWalletBalanceDisplay();
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading wallet balance", e);
                    });
        }
    }

    private void updateWalletBalanceDisplay() {
        if (walletBalanceTextView != null) {
            walletBalanceTextView.setText(String.format(Locale.getDefault(), "SRD %.2f", walletBalance));
        }
    }

    private void topUpWallet(double amount) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid())
                    .update("balance", FieldValue.increment(amount))
                    .addOnSuccessListener(aVoid -> {
                        walletBalance += amount;
                        updateWalletBalanceDisplay();
                        logActivity("Wallet Top-up", "Added SRD " + amount + " to wallet", amount, true);
                        Toast.makeText(this, "Successfully added SRD " + amount, Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error topping up wallet", e);
                        Toast.makeText(this, "Error topping up wallet", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void showErrorDialog(String title, String message) {
        runOnUiThread(() -> {
            try {
                // Dismiss loading dialog if shown
                dismissLoadingDialog();

                new AlertDialog.Builder(Home.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .setOnDismissListener(dialog -> {
                            // Optional: reload balance on error
                            loadWalletBalance();
                        })
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing error dialog", e);
                // Fallback: show toast if dialog fails
                Toast.makeText(Home.this, title + ": " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TOP_UP_REQUEST && resultCode == RESULT_OK && data != null) {
            double topUpAmount = data.getDoubleExtra("topUpAmount", 0.0);
            if (topUpAmount > 0) {
                topUpWallet(topUpAmount);
            }
        }
    }
}