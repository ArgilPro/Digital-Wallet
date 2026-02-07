package com.example.rapidjava;

import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Register extends AppCompatActivity {

    private EditText inputEmail, inputName, inputPhone, inputPassword, inputConfirmPassword;
    private Button registerButton;
    private TextView errorMessage, loginLink;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Loading overlay components
    private RelativeLayout loadingOverlay;
    private ProgressBar loadingProgress;
    private TextView loadingMessage;

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(getApplicationContext(), Home.class));
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        // Initialize views
        inputEmail = findViewById(R.id.inputEmail);
        inputName = findViewById(R.id.inputName);
        inputPhone = findViewById(R.id.inputPhone);
        inputPassword = findViewById(R.id.inputPassword);
        inputConfirmPassword = findViewById(R.id.inputConfirmPassword);
        registerButton = findViewById(R.id.registerButton);
        errorMessage = findViewById(R.id.errorMessage);
        loginLink = findViewById(R.id.loginLink);

        // Initialize loading overlay
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingProgress = findViewById(R.id.loadingProgress);
        loadingMessage = findViewById(R.id.loadingMessage);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        registerButton.setOnClickListener(v -> {
            String email = inputEmail.getText().toString().trim();
            String name = inputName.getText().toString().trim();
            String phone = inputPhone.getText().toString().trim();
            String password = inputPassword.getText().toString().trim();
            String confirmPassword = inputConfirmPassword.getText().toString().trim();

            errorMessage.setText("");

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(name) || TextUtils.isEmpty(phone) ||
                    TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
                errorMessage.setText("All fields are required.");
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                errorMessage.setText("Enter a valid email address.");
            } else if (phone.replaceAll("[^0-9]", "").length() < 7) {
                errorMessage.setText("Enter a valid phone number (at least 7 digits).");
            } else if (password.length() < 6) {
                errorMessage.setText("Password must be at least 6 characters.");
            } else if (!password.equals(confirmPassword)) {
                errorMessage.setText("Passwords do not match.");
            } else {
                registerUser(email, name, phone, password);
            }
        });

        loginLink.setOnClickListener(v -> navigateToLogin());
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

    private void registerUser(String email, String name, String phone, String password) {
        // Use the centralized showLoadingDialog function
        showLoadingDialog("Creating your account...");

        // Start registration process
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Update loading message for email verification
                            showLoadingDialog("Sending verification email...");

                            user.sendEmailVerification()
                                    .addOnCompleteListener(verificationTask -> {
                                        if (verificationTask.isSuccessful()) {
                                            // Update loading message for final setup
                                            showLoadingDialog("Setting up your profile...");
                                            saveUserDetails(user.getUid(), name, email, phone);
                                        } else {
                                            dismissLoadingDialog();
                                            errorMessage.setText("Failed to send verification email.");
                                            Log.e(TAG, "Email verification failed", verificationTask.getException());
                                        }
                                    });
                        }
                    } else {
                        dismissLoadingDialog();
                        String error;
                        try {
                            throw task.getException();
                        } catch (FirebaseAuthWeakPasswordException e) {
                            error = "Password is too weak. Use at least 6 characters.";
                        } catch (FirebaseAuthInvalidCredentialsException e) {
                            error = "Invalid email format.";
                        } catch (FirebaseAuthUserCollisionException e) {
                            error = "Email already in use.";
                        } catch (Exception e) {
                            error = "Registration failed. Please try again.";
                            Log.e(TAG, "Registration error", e);
                        }
                        errorMessage.setText(error);
                    }
                });
    }

    private void saveUserDetails(String userId, String name, String email, String phone) {
        // Update loading message
        showLoadingDialog("Almost done...");

        Map<String, Object> user = new HashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("phone", phone);
        user.put("createdAt", FieldValue.serverTimestamp());
        user.put("updatedAt", FieldValue.serverTimestamp());
        user.put("emailVerified", false);
        user.put("balance", 0.0);
        user.put("profileComplete", true);

        db.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    dismissLoadingDialog();
                    Toast.makeText(Register.this,
                            "Registration successful! Please check your email for verification.",
                            Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(Register.this, Home.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    dismissLoadingDialog();
                    errorMessage.setText("Failed to save user data. Please try again.");
                    Log.e(TAG, "Error saving user data", e);
                });
    }

    private void navigateToLogin() {
        startActivity(new Intent(Register.this, Login.class));
        finish();
    }
}