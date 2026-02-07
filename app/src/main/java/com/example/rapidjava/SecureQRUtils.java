package com.example.rapidjava;

import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

public class SecureQRUtils {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final String VERSION = "v1";

    // Generate QR: returns header|version|iv|ciphertext (base64)
    public static String generateSecureQR(JSONObject payload) {
        try {
            Log.d("SecureQRUtils", "=== STARTING QR GENERATION ===");

            // Ensure we have a key
            Log.d("SecureQRUtils", "1. Generating/checking AES key...");
            KeyStoreUtils.generateAESKeyIfNotExists();
            SecretKey key = KeyStoreUtils.getSecretKey();
            if (key == null) {
                Log.e("SecureQRUtils", "ERROR: KeyStoreUtils.getSecretKey() returned null!");
                return null;
            }

            Log.d("SecureQRUtils", "2. Key retrieved successfully");
            Log.d("SecureQRUtils", "   - Algorithm: " + key.getAlgorithm());

            // compress payload
            Log.d("SecureQRUtils", "3. Compressing payload...");
            byte[] plain = gzip(payload.toString().getBytes(StandardCharsets.UTF_8));
            Log.d("SecureQRUtils", "   - Compressed size: " + plain.length + " bytes");

            Log.d("SecureQRUtils", "4. Initializing cipher...");
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);

            Log.d("SecureQRUtils", "5. Starting encryption (Android KeyStore will auto-generate IV)...");

            // IMPORTANT: For Android KeyStore with GCM, DO NOT provide IV!
            // The KeyStore will automatically generate and handle it.
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] cipherBytes = cipher.doFinal(plain);

            // Get the IV that Android KeyStore generated
            byte[] iv = cipher.getIV();
            if (iv == null) {
                Log.e("SecureQRUtils", "ERROR: Cipher.getIV() returned null!");
                return null;
            }

            Log.d("SecureQRUtils", "6. Encryption successful!");
            Log.d("SecureQRUtils", "   - Ciphertext length: " + cipherBytes.length + " bytes");
            Log.d("SecureQRUtils", "   - Auto-generated IV length: " + iv.length + " bytes");

            String ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP);
            String ctB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP);

            Log.d("SecureQRUtils", "7. Base64 encoding complete");
            Log.d("SecureQRUtils", "   - IV (Base64) length: " + ivB64.length());
            Log.d("SecureQRUtils", "   - Ciphertext (Base64) length: " + ctB64.length());

            String qrString = "RapidPay|PaymentRequest|" + VERSION + "|" + ivB64 + "|" + ctB64;
            Log.d("SecureQRUtils", "8. Final QR string created");
            Log.d("SecureQRUtils", "   - Total length: " + qrString.length());

            Log.d("SecureQRUtils", "=== QR GENERATION COMPLETE ===");

            return qrString;

        } catch (Exception e) {
            Log.e("SecureQRUtils", "=== ENCRYPTION FAILED ===");
            Log.e("SecureQRUtils", "Error: " + e.getMessage(), e);
            return null;
        }
    }

    // Decrypt: expects iv and ciphertext base64; returns JSONObject or null
    public static JSONObject decryptSecureQR(String ivB64, String ctB64) {
        try {
            Log.d("SecureQRUtils", "=== STARTING QR DECRYPTION ===");

            // Ensure we have a key
            KeyStoreUtils.generateAESKeyIfNotExists();
            SecretKey key = KeyStoreUtils.getSecretKey();
            if (key == null) {
                Log.e("SecureQRUtils", "No AES key available for decryption");
                return null;
            }

            if (ivB64 == null || ctB64 == null) {
                Log.e("SecureQRUtils", "Null iv or ciphertext");
                return null;
            }

            byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
            byte[] ct = Base64.decode(ctB64, Base64.NO_WRAP);

            Log.d("SecureQRUtils", "IV length: " + iv.length + " bytes");
            Log.d("SecureQRUtils", "Ciphertext length: " + ct.length + " bytes");

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);

            // IMPORTANT: For decryption, you MUST provide the IV that was used during encryption
            // This is different from encryption where Android KeyStore auto-generates it
            java.security.spec.AlgorithmParameterSpec gcmSpec =
                    new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            byte[] decrypted = cipher.doFinal(ct);

            Log.d("SecureQRUtils", "Decryption successful. Decrypted length: " + decrypted.length);

            byte[] uncompressed = ungzip(decrypted);
            String json = new String(uncompressed, StandardCharsets.UTF_8);
            Log.d("SecureQRUtils", "JSON extracted successfully");

            return new JSONObject(json);
        } catch (Exception e) {
            Log.e("SecureQRUtils", "Decrypt failed: " + e.getMessage(), e);
            return null;
        }
    }

    // GZIP helper
    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(data);
        gzip.close();
        return bos.toByteArray();
    }

    private static byte[] ungzip(byte[] data) throws Exception {
        InputStream is = new GZIPInputStream(new ByteArrayInputStream(data));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        is.close();
        return bos.toByteArray();
    }
}