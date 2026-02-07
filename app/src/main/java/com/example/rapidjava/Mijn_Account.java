package com.example.rapidjava;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class Mijn_Account extends AppCompatActivity {

    private EditText editName, editPhoneNumber, editEmail;
    private Button btnSave, btnLogout, btnBiometric;
    private FirebaseAuth auth;
    private FirebaseUser user;
    private FirebaseFirestore db;
    private StorageReference storageRef;
    private ProgressDialog progressDialog;
    private Uri imageUri;
    private static final int PICK_IMAGE_REQUEST = 101;
    private static final int BIOMETRIC_ENROLL_REQUEST = 1001;

    // Biometric authentication variables
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private static final int PROFILE_COMPLETE_REQUEST = 1002;
    private static final String PROFILE_INCOMPLETE = "profile_incomplete";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mijn_account);

        // Initialize views
        editName = findViewById(R.id.editName);
        editPhoneNumber = findViewById(R.id.editPhoneNumber);
        editEmail = findViewById(R.id.editEmail);
        btnSave = findViewById(R.id.btnSave);
        btnLogout = findViewById(R.id.btnLogout);
        btnBiometric = findViewById(R.id.btnBiometric);
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            finish(); // This will close the current activity and go back
        });

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");

        // Initialize ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please wait");
        progressDialog.setCancelable(false);

        // Initialize Biometric Authentication
        executor = ContextCompat.getMainExecutor(this);
        setupBiometricPrompt();

        if (user == null) {
            redirectToLogin();
        } else {
            checkProfileCompletion();
        }

        // Set click listeners
        btnSave.setOnClickListener(v -> updateUserDetails());
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
        btnBiometric.setOnClickListener(v -> checkBiometricSupport());
    }

    private void clearBiometricCredentials() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove("saved_email");
        editor.remove("saved_password");
        editor.apply();
    }

    private void checkProfileCompletion() {
        progressDialog.setMessage("Loading profile...");
        progressDialog.show();

        db.collection("users").document(user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Check if required fields exist (not just profileComplete flag)
                            boolean hasRequiredFields = document.contains("name") &&
                                    document.contains("phone") &&
                                    document.getString("name") != null &&
                                    document.getString("phone") != null;

                            if (!hasRequiredFields) {
                                showProfileCompletionDialog();
                            } else {
                                loadUserData(); // Load existing data
                            }
                        } else {
                            // Document doesn't exist - create it with default values
                            createInitialUserDocument();
                            showProfileCompletionDialog();
                        }
                    } else {
                        Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showProfileCompletionDialog() {
        progressDialog.dismiss();

        new AlertDialog.Builder(this)
                .setTitle("Complete Your Profile")
                .setMessage("Please complete your profile information to continue")
                .setPositiveButton("Continue", (dialog, which) -> {
                    loadUserData();
                    markProfileFieldsRequired();
                })
                .setCancelable(false)
                .show();
    }

    // Add this method to highlight required fields
    private void markProfileFieldsRequired() {
        editName.setHint("Name (required)");
        editPhoneNumber.setHint("Phone (required)");
        editName.setError(null);
        editPhoneNumber.setError(null);
    }

    private void setupBiometricPrompt() {
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                runOnUiThread(() -> {
                    Toast.makeText(Mijn_Account.this, "Biometric login enabled!", Toast.LENGTH_SHORT).show();
                    saveBiometricPreference(true);

                    showPasswordDialogForBiometric();
                });
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                runOnUiThread(() ->
                        Toast.makeText(Mijn_Account.this, "Authentication failed", Toast.LENGTH_SHORT).show());
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable Biometric Login")
                .setSubtitle("Authenticate with your fingerprint or face")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
    }

    private void checkBiometricSupport() {
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                // Device supports biometric auth and user has credentials enrolled
                biometricPrompt.authenticate(promptInfo);
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                Toast.makeText(this, "No biometric sensors available", Toast.LENGTH_SHORT).show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                Toast.makeText(this, "Biometric features are currently unavailable", Toast.LENGTH_SHORT).show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                // Prompt user to enroll biometrics
                Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                        BiometricManager.Authenticators.BIOMETRIC_STRONG);
                startActivityForResult(enrollIntent, BIOMETRIC_ENROLL_REQUEST);
                break;
        }
    }

    // In your Settings / Mijn_Account.java
    private void saveBiometricPreference(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("biometric_enabled", enabled);
        editor.apply();
    }

    private void showPasswordDialogForBiometric() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Verify Password");
        builder.setMessage("Enter your current password to enable biometric login");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String password = input.getText().toString().trim();
            if (!TextUtils.isEmpty(password)) {
                SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("saved_email", user.getEmail());
                editor.putString("saved_password", password);
                editor.putBoolean("biometric_enabled", true);   // <-- now it is really enabled
                editor.apply();

                Toast.makeText(this, "Biometric login enabled!", Toast.LENGTH_SHORT).show();
//                checkBiometricVisibilityInLogin();   // refresh Login button
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

//    private void checkBiometricVisibilityInLogin() {
//        Login.refreshBiometricButton(this);
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case BIOMETRIC_ENROLL_REQUEST:
                    // Check if biometric authentication is now available after enrollment
                    BiometricManager biometricManager = BiometricManager.from(this);
                    int canAuthenticate = biometricManager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    );

                    if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                        // Show biometric prompt if enrollment was successful
                        biometricPrompt.authenticate(promptInfo);
                    } else {
                        // Handle cases where enrollment wasn't completed
                        String errorMessage;
                        switch (canAuthenticate) {
                            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                                errorMessage = "Biometric not enrolled";
                                break;
                            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                                errorMessage = "Biometric hardware unavailable";
                                break;
                            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                                errorMessage = "No biometric hardware available";
                                break;
                            default:
                                errorMessage = "Unknown biometric error";
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        } else if (resultCode == RESULT_CANCELED) {
            if (requestCode == PICK_IMAGE_REQUEST) {
                Toast.makeText(this, "Image selection cancelled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadUserData() {
//        progressDialog.setMessage("Loading your data...");
        progressDialog.show();

        db.collection("users").document(user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Set name - fallback to empty string if null
                            editName.setText(document.getString("name") != null ?
                                    document.getString("name") : "");

                            // Set phone - fallback to empty string if null
                            editPhoneNumber.setText(document.getString("phone") != null ?
                                    document.getString("phone") : "");

                            // Email comes from FirebaseUser
                            editEmail.setText(user.getEmail());
                            editEmail.setEnabled(false);

                            // Profile image logic removed
                        } else {
                            // Document doesn't exist - create basic structure
                            createInitialUserDocument();
                        }
                    } else {
                        Toast.makeText(this, "Error loading data: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e("Mijn_Account", "Error loading user data", task.getException());
                    }
                });
    }


    private void createInitialUserDocument() {
        // Get the current FirebaseUser
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", "");
        userData.put("phone", "");
        userData.put("email", firebaseUser.getEmail());
        userData.put("profileComplete", false);
        // Removed profileImageUrl from the document

        db.collection("users").document(firebaseUser.getUid())
                .set(userData)
                .addOnSuccessListener(aVoid -> loadUserData()) // Retry loading
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to initialize profile", Toast.LENGTH_SHORT).show();
                    Log.e("Mijn_Account", "Error creating user document", e);
                });
    }

    private void updateUserDetails() {
        String name = editName.getText().toString().trim();
        String phone = editPhoneNumber.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            editName.setError("Name is required");
            editName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone) || phone.replaceAll("[^0-9]", "").length() < 7) {
            editPhoneNumber.setError("Valid phone number required");
            editPhoneNumber.requestFocus();
            return;
        }

        progressDialog.setMessage("Updating details...");
        progressDialog.show();

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        updates.put("profileComplete", true); // Mark profile as complete

        db.collection("users").document(user.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();

                    // If coming from registration, set result for Home activity
                    if (getIntent().getBooleanExtra(PROFILE_INCOMPLETE, false)) {
                        setResult(RESULT_OK);
                    }
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> logoutUser())
                .setNegativeButton("No", null)
                .show();
    }

    private void logoutUser() {
        // ONLY clear auth-related data
//        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
//        prefs.edit()
//                .remove("saved_email")
//                .remove("saved_password")
//                .remove("biometric_enabled")
//                .apply();
//
//        clearBiometricCredentials();
        auth.signOut();  // Ends Firebase session
        redirectToLogin(); // Your existing method
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, Login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}