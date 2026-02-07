package com.example.rapidjava;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class Login extends AppCompatActivity {

    private EditText editTextEmail, editTextPassword;
    private Button loginButton, registerButton, btnBiometric;
    private TextView forgotPassword;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ProgressDialog progressDialog;
    private BiometricPrompt biometricPrompt;
    private Executor executor;

    // Loading overlay components
    private RelativeLayout loadingOverlay;
    private ProgressBar loadingProgress;
    private TextView loadingMessage;

    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);


        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();


        editTextEmail = findViewById(R.id.inputEmail);
        editTextPassword = findViewById(R.id.inputPassword);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        forgotPassword = findViewById(R.id.forgotPassword);
        btnBiometric = findViewById(R.id.biometricButton);

        // Initialize loading overlay
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingProgress = findViewById(R.id.loadingProgress);
        loadingMessage = findViewById(R.id.loadingMessage);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCancelable(false);

        executor = ContextCompat.getMainExecutor(this);
        setupBiometricPrompt();

        loginButton.setOnClickListener(view -> loginUser());
        registerButton.setOnClickListener(view -> navigateToRegister());
        forgotPassword.setOnClickListener(view -> showPasswordResetDialog());
        btnBiometric.setOnClickListener(view -> attemptBiometricLogin());

        updateBiometricButtonVisibility();
    }

    private void setupBiometricPrompt() {
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // Actually perform the login after successful biometric auth
                performBiometricLogin();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                runOnUiThread(() ->
                        Toast.makeText(Login.this, "Authentication failed", Toast.LENGTH_SHORT).show());
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Use your fingerprint to login")
                .setNegativeButtonText("Use Email/Password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
    }

    private void performBiometricLogin() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String savedEmail = prefs.getString("saved_email", "");
        String savedPassword = prefs.getString("saved_password", "");

        if (TextUtils.isEmpty(savedEmail) || TextUtils.isEmpty(savedPassword)) {
            runOnUiThread(() ->
                    Toast.makeText(Login.this, "No saved credentials, please login first!", Toast.LENGTH_SHORT).show());
            return;
        }

        loginWithCredentials(savedEmail, savedPassword);
    }


    private void updateBiometricButtonVisibility() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean biometricEnabled = prefs.getBoolean("biometric_enabled", false);
        String savedEmail = prefs.getString("saved_email", "");
        String savedPassword = prefs.getString("saved_password", "");

        if (biometricEnabled && !TextUtils.isEmpty(savedEmail) && !TextUtils.isEmpty(savedPassword)) {
            btnBiometric.setVisibility(View.VISIBLE);
        } else {
            btnBiometric.setVisibility(View.GONE);
        }
    }

    private void attemptBiometricLogin() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String savedEmail = prefs.getString("saved_email", "");
        String savedPassword = prefs.getString("saved_password", "");

        if (TextUtils.isEmpty(savedEmail) || TextUtils.isEmpty(savedPassword)) {
            Toast.makeText(this, "No saved credentials. Log in normally first.", Toast.LENGTH_LONG).show();
            return;
        }

        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo);
        } else if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            Toast.makeText(this, "No fingerprint enrolled", Toast.LENGTH_SHORT).show();
        } else if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            Toast.makeText(this, "Biometric hardware not available", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Biometric authentication not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void loginWithCredentials(String email, String password) {
        showLoadingDialog("Logging in...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                            checkUserRole(user.getUid());
                        } else {
                            dismissLoadingDialog();
                            Toast.makeText(Login.this, "Please verify your email first", Toast.LENGTH_LONG).show();
                            mAuth.signOut();
                        }
                    } else {
                        dismissLoadingDialog();
                        handleLoginError(task.getException());
                    }
                });
    }

    private void checkUserRole(String uid) {
        db.collection("users").document(uid).get().addOnCompleteListener(task -> {
            dismissLoadingDialog();
            if (task.isSuccessful()) {
                DocumentSnapshot doc = task.getResult();
                if (doc != null && doc.exists()) {
                    Boolean isAdmin = doc.getBoolean("isAdmin");

                    // Save biometric credentials AFTER successful login and role check
                    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                    if (prefs.getBoolean("biometric_enabled", false)) {
                        String email = editTextEmail.getText().toString().trim();
                        String password = editTextPassword.getText().toString().trim();
                        saveCredentialsForBiometric(email, password);
                    }

                    if (Boolean.TRUE.equals(isAdmin)) {
                        startActivity(new Intent(Login.this, AdminVoucherGeneratorActivity.class));
                    } else {
                        navigateToHome();
                    }
                } else {
                    Log.w(TAG, "User document doesn't exist for UID: " + uid);
                    createUserDocument(uid);
                }
            } else {
                Log.e(TAG, "Error checking user role: " + task.getException().getMessage(), task.getException());
                Toast.makeText(this, "Error checking user role: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                navigateToHome(); // Fallback
            }
        });
    }

    private void createUserDocument(String uid) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> userData = new HashMap<>();
        userData.put("email", user.getEmail());
        userData.put("balance", 0.0);
        userData.put("createdAt", FieldValue.serverTimestamp());
        userData.put("updatedAt", FieldValue.serverTimestamp());
        userData.put("isAdmin", false);

        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User document created successfully");
                    navigateToHome();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create user document", e);
                    navigateToHome(); // Still navigate as fallback
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
        runOnUiThread(() -> {
            loadingOverlay.setVisibility(View.GONE);
        });
    }

    private void saveCredentialsForBiometric(String email, String password) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("saved_email", email);
        editor.putString("saved_password", password);
        editor.apply();
    }

    private void loginUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Email is required");
            editTextEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Enter a valid email");
            editTextEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError("Password is required");
            editTextPassword.requestFocus();
            return;
        }

        loginWithCredentials(email, password);
    }

    private void handleLoginError(Exception exception) {
        try {
            throw exception;
        } catch (FirebaseAuthInvalidUserException e) {
            editTextEmail.setError("User does not exist or is invalid");
            editTextEmail.requestFocus();
        } catch (FirebaseAuthInvalidCredentialsException e) {
            editTextPassword.setError("Invalid password");
            editTextPassword.requestFocus();
        } catch (Exception e) {
            Toast.makeText(Login.this, "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPasswordResetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");
        builder.setMessage("Enter your email to receive a reset link");

        final EditText input = new EditText(this);
        input.setHint("Email");
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (!TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                sendPasswordResetEmail(email);
            } else {
                Toast.makeText(Login.this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendPasswordResetEmail(String email) {
        showLoadingDialog("Sending reset email...");

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    dismissLoadingDialog();
                    if (task.isSuccessful()) {
                        Toast.makeText(Login.this,
                                "Password reset email sent. Check your inbox.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(Login.this,
                                "Failed to send reset email",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToRegister() {
        Intent intent = new Intent(Login.this, Register.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void navigateToHome() {
        Intent intent = new Intent(Login.this, Home.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void clearBiometricCredentials() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("saved_email");
        editor.remove("saved_password");
        editor.apply();
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isEmailVerified()) {
                checkUserRole(currentUser.getUid());
            } else {
                Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show();
                mAuth.signOut();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBiometricButtonVisibility(); // Add here
    }

}