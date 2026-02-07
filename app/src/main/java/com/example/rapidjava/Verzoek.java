package com.example.rapidjava;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import android.app.ProgressDialog;

import org.json.JSONObject;

public class Verzoek extends AppCompatActivity {

    private EditText amountInput;
    private Button generateButton;
    private ImageView qrCodeImage;
    private TextView instructionText;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verzoek);

        amountInput = findViewById(R.id.amountInput);
        generateButton = findViewById(R.id.generateButton);
        qrCodeImage = findViewById(R.id.qrCodeImage);
        instructionText = findViewById(R.id.instructionText);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("QR-code genereren...");
        progressDialog.setCancelable(false);

        // Generate the KeyStore key BEFORE generating QR codes
        KeyStoreUtils.generateAESKeyIfNotExists();

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        generateButton.setOnClickListener(view -> {
            String amount = amountInput.getText().toString().trim();

            if (amount.isEmpty()) {
                showError("Voer een bedrag in");
                return;
            }

            try {
                double amountValue = Double.parseDouble(amount);
                if (amountValue <= 0) {
                    showError("Bedrag moet positief zijn");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Ongeldig bedrag formaat");
                return;
            }

            // Hide keyboard before generating QR
            hideKeyboard();
            generateQRCode(amount);
        });
    }

    private void generateQRCode(String amount) {
        generateButton.setEnabled(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null || user.getEmail() == null) {
                    runOnUiThread(() -> {
                        showError(user == null ? "Gebruiker niet ingelogd" : "Geen email gevonden");
                        progressDialog.dismiss();
                        generateButton.setEnabled(true);
                    });
                    return;
                }

                String qrData = createQRData(amount, user);

                if (qrData == null || qrData.isEmpty()) {
                    runOnUiThread(() -> {
                        showError("Ongeldige QR data: Kon geen QR genereren");
                        progressDialog.dismiss();
                        generateButton.setEnabled(true);
                    });
                    return;
                }

                Log.d("QRData", "QR string length: " + qrData.length());
                Log.d("QRData", "First 100 chars: " + qrData.substring(0, Math.min(100, qrData.length())));

                if (qrData.length() > 2500) {
                    runOnUiThread(() -> {
                        showError("QR data te groot, probeer een kleiner bedrag");
                        progressDialog.dismiss();
                        generateButton.setEnabled(true);
                    });
                    return;
                }

                try {
                    MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
                    // Adjust QR code size based on data length
                    int size = qrData.length() > 500 ? 1000 : 800;
                    BitMatrix bitMatrix = multiFormatWriter.encode(qrData, BarcodeFormat.QR_CODE, size, size);
                    BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                    final Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);

                    runOnUiThread(() -> {
                        qrCodeImage.setImageBitmap(bitmap);
                        qrCodeImage.setVisibility(View.VISIBLE);
                        instructionText.setText("Laat deze QR-code scannen om SRD " + amount + " te ontvangen");
                        instructionText.setTextColor(getResources().getColor(R.color.secondary_text));
                        progressDialog.dismiss();
                        generateButton.setEnabled(true);
                        Toast.makeText(Verzoek.this, "QR-code gegenereerd", Toast.LENGTH_SHORT).show();
                    });

                } catch (WriterException e) {
                    Log.e("QRGeneration", "WriterException: " + e.getMessage(), e);
                    runOnUiThread(() -> {
                        showError("QR data te complex: " + e.getMessage());
                        progressDialog.dismiss();
                        generateButton.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                Log.e("QRGeneration", "General error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    showError("Fout bij genereren: " + e.getMessage());
                    progressDialog.dismiss();
                    generateButton.setEnabled(true);
                });
            }
        }).start();
    }

    private String createQRData(String amount, FirebaseUser user) {
        try {
            Log.d("QRData", "Starting QR data creation...");

            // Create the payment request payload
            JSONObject payload = new JSONObject();
            payload.put("amount", Double.parseDouble(amount));
            payload.put("currency", "SRD");
            payload.put("recipientId", user.getUid());
            payload.put("recipientEmail", user.getEmail());
            payload.put("recipientName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
            payload.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            payload.put("uuid", UUID.randomUUID().toString());
            payload.put("expiresAt", System.currentTimeMillis() + (5 * 60 * 1000)); // 5 minutes expiration
            payload.put("type", "payment_request");

            Log.d("QRData", "Created payload successfully");

            // Use the SecureQRUtils to generate the encrypted QR data
            Log.d("QRData", "Calling SecureQRUtils.generateSecureQR()...");
            String qr = SecureQRUtils.generateSecureQR(payload);

            if (qr == null) {
                Log.e("QRData", "SecureQRUtils.generateSecureQR returned null");

                // Let's test if we can create a simple unencrypted QR as fallback
                Log.d("QRData", "Testing fallback: creating simple JSON payload");
                try {
                    String simpleJson = payload.toString();
                    Log.d("QRData", "Simple JSON length: " + simpleJson.length());
                    if (simpleJson.length() < 1000) {
                        Log.d("QRData", "Simple JSON: " + simpleJson);
                    }
                } catch (Exception e) {
                    Log.e("QRData", "Fallback test failed: " + e.getMessage());
                }

                return null;
            } else {
                Log.d("QRData", "QR generation successful! Length: " + qr.length());
                Log.d("QRData", "Generated QR string (first 100 chars): " +
                        (qr.length() > 100 ? qr.substring(0, 100) + "..." : qr));
            }

            return qr;

        } catch (Exception e) {
            Log.e("QRData", "QR generation failed with exception: " + e.getMessage(), e);
            return null;
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            instructionText.setText(message);
            instructionText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            qrCodeImage.setVisibility(View.INVISIBLE);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && amountInput.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(amountInput.getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        super.onDestroy();
    }
}