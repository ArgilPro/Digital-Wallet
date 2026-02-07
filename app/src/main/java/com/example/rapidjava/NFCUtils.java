//package com.example.rapidjava;
//
//import android.app.PendingIntent;
//import android.content.Intent;
//import android.content.IntentFilter;
//import android.nfc.NfcAdapter;
//import android.nfc.Tag;
//import android.nfc.tech.IsoDep;
//import android.nfc.tech.NfcA;
//import android.nfc.tech.NfcF;
//import android.util.Log;
//import androidx.appcompat.app.AppCompatActivity;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.security.SecureRandom;
//import javax.crypto.Cipher;
//import javax.crypto.spec.IvParameterSpec;
//import javax.crypto.spec.SecretKeySpec;
//
//public class NFCUtils {
//    private static final String TAG = "NFCUtils";
//    private static final byte[] AID = {(byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x42, (byte)0x40, (byte)0x10}; // Replace with your custom AID
//
//    private NfcAdapter nfcAdapter;
//    private PendingIntent pendingIntent;
//    private IntentFilter[] intentFilters;
//    private String[][] techLists;
//    private NFCPaymentCallback paymentCallback;
//
//    public interface NFCPaymentCallback {
//        void onNFCTagDetected(Tag tag);
//        void onNFCError(String error);
//    }
//
//    public NFCUtils(AppCompatActivity activity, NFCPaymentCallback callback) {
//        this.paymentCallback = callback;
//        nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
//
//        if (nfcAdapter == null) {
//            callback.onNFCError("NFC not supported on this device");
//            return;
//        }
//
//        // Create PendingIntent
//        Intent intent = new Intent(activity, activity.getClass());
//        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
//        pendingIntent = PendingIntent.getActivity(activity, 0, intent,
//                PendingIntent.FLAG_MUTABLE);
//
//        // Setup intent filters
//        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
//        try {
//            ndef.addDataType("*/*");
//        } catch (IntentFilter.MalformedMimeTypeException e) {
//            throw new RuntimeException("Failed to add MIME type", e);
//        }
//        intentFilters = new IntentFilter[] { ndef };
//
//        // Setup tech lists
//        techLists = new String[][] {
//                { IsoDep.class.getName() },
//                { NfcA.class.getName() },
//                { NfcF.class.getName() }
//        };
//
//        // Generate RSA key pair for NFC encryption if not exists
//        KeyStoreUtils.generateKeyPairIfNotExists();
//    }
//
//    public void enableForegroundDispatch(AppCompatActivity activity) {
//        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
//            try {
//                nfcAdapter.enableForegroundDispatch(activity, pendingIntent, intentFilters, techLists);
//            } catch (SecurityException e) {
//                Log.e(TAG, "Security exception enabling NFC foreground dispatch", e);
//                if (paymentCallback != null) {
//                    paymentCallback.onNFCError("NFC permission denied");
//                }
//            }
//        }
//    }
//
//    public void disableForegroundDispatch(AppCompatActivity activity) {
//        if (nfcAdapter != null) {
//            try {
//                nfcAdapter.disableForegroundDispatch(activity);
//            } catch (SecurityException e) {
//                Log.e(TAG, "Security exception disabling NFC foreground dispatch", e);
//            }
//        }
//    }
//
//    public boolean isNFCSupported() {
//        return nfcAdapter != null;
//    }
//
//    public boolean isNFCEnabled() {
//        return nfcAdapter != null && nfcAdapter.isEnabled();
//    }
//
//    public void onNFCTagDetected(Tag tag) {
//        if (paymentCallback != null) {
//            paymentCallback.onNFCTagDetected(tag);
//        }
//    }
//
//    // Production-ready: Read encrypted data via APDU
//    public static byte[] readNFCTagData(Tag tag) {
//        IsoDep isoDep = IsoDep.get(tag);
//        if (isoDep == null) {
//            Log.e(TAG, "Tag does not support IsoDep");
//            return null;
//        }
//
//        try {
//            isoDep.connect();
//            isoDep.setTimeout(5000); // 5s timeout
//
//            // APDU: Select AID
//            byte[] selectApdu = new byte[] {
//                    (byte)0x00, (byte)0xA4, (byte)0x04, (byte)0x00, // CLA INS P1 P2
//                    (byte)AID.length // Lc
//            };
//            byte[] selectCommand = new byte[selectApdu.length + AID.length];
//            System.arraycopy(selectApdu, 0, selectCommand, 0, selectApdu.length);
//            System.arraycopy(AID, 0, selectCommand, selectApdu.length, AID.length);
//
//            byte[] selectResponse = isoDep.transceive(selectCommand);
//            if (selectResponse[selectResponse.length - 2] != (byte)0x90 || selectResponse[selectResponse.length - 1] != (byte)0x00) {
//                Log.e(TAG, "AID selection failed");
//                isoDep.close();
//                return null;
//            }
//
//            // APDU: Read encrypted data (assume tag stores 256 bytes; adjust as needed)
//            byte[] readApdu = {(byte)0x00, (byte)0xB0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x10}; // Read 16 bytes (example)
//            byte[] dataResponse = isoDep.transceive(readApdu);
//
//            // Check SW1/SW2 for success
//            if (dataResponse[dataResponse.length - 2] != (byte)0x90 || dataResponse[dataResponse.length - 1] != (byte)0x00) {
//                Log.e(TAG, "Data read failed");
//                isoDep.close();
//                return null;
//            }
//
//            // Extract actual data (exclude SW)
//            byte[] encryptedData = new byte[dataResponse.length - 2];
//            System.arraycopy(dataResponse, 0, encryptedData, 0, encryptedData.length);
//            isoDep.close();
//
//            return encryptedData;
//        } catch (IOException e) {
//            Log.e(TAG, "Error reading NFC tag", e);
//            return null;
//        }
//    }
//
//    // Decrypt and parse (use in handleNFCPayment)
//    public static String parsePaymentData(byte[] encryptedData) {
//        if (encryptedData == null) return null;
//
//        try {
//            // Assume first 16 bytes: IV, rest: encrypted payload (AES)
//            byte[] iv = new byte[16];
//            System.arraycopy(encryptedData, 0, iv, 0, 16);
//            byte[] ciphertext = new byte[encryptedData.length - 16];
//            System.arraycopy(encryptedData, 16, ciphertext, 0, ciphertext.length);
//
//            // Use the same AES key as in SecureQRUtils for consistency
//            byte[] aesKey = "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8); // Same key as SecureQRUtils
//            String decrypted = AESUtils.decrypt(ciphertext, aesKey, iv);
//
//            if (decrypted != null && decrypted.startsWith("RapidPay|NFC|")) {
//                // Validate nonce/timestamp for anti-replay
//                String[] parts = decrypted.split("\\|");
//                if (parts.length >= 5) {
//                    long nonce = Long.parseLong(parts[4]);
//                    if (System.currentTimeMillis() - nonce > 300000) { // 5min expiry
//                        Log.e(TAG, "Replay attack detected");
//                        return null;
//                    }
//                }
//                return decrypted;
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error parsing NFC data", e);
//        }
//        return null;
//    }
//
//    // Helper for AES
//    public static class AESUtils {
//        private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
//
//        public static String decrypt(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {
//            try {
//                // Initialize AES key and IV
//                SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
//                IvParameterSpec ivSpec = new IvParameterSpec(iv);
//
//                // Initialize Cipher for decryption
//                Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
//                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
//
//                // Decrypt the ciphertext
//                byte[] decryptedBytes = cipher.doFinal(ciphertext);
//
//                // Convert decrypted bytes to string
//                return new String(decryptedBytes, StandardCharsets.UTF_8);
//            } catch (Exception e) {
//                Log.e(TAG, "AES decryption failed", e);
//                throw e; // Rethrow to allow caller to handle
//            }
//        }
//    }
//
//    public void onNFCError(String error) {
//        if (paymentCallback != null) {
//            paymentCallback.onNFCError(error);
//        }
//    }
//}