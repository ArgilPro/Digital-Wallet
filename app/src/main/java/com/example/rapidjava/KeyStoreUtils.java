package com.example.rapidjava;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class KeyStoreUtils {

    private static final String AES_KEY_ALIAS = "RapidPayAES";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final int AES_KEY_SIZE = 256;

    public static void generateAESKeyIfNotExists() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(AES_KEY_ALIAS)) {
                Log.d("KeyStoreUtils", "Generating new AES key...");

                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                );

                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        AES_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
                )
                        .setKeySize(AES_KEY_SIZE)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)  // Use GCM
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)  // No padding for GCM
                        .build();

                keyGenerator.init(spec);
                keyGenerator.generateKey();
                Log.d("KeyStoreUtils", "AES key generated successfully in Android KeyStore");
            } else {
                Log.d("KeyStoreUtils", "AES key already exists in Android KeyStore");
            }
        } catch (Exception e) {
            Log.e("KeyStoreUtils", "Error generating AES key: " + e.getMessage(), e);
        }
    }

    public static SecretKey getSecretKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);

            if (!keyStore.containsAlias(AES_KEY_ALIAS)) {
                Log.e("KeyStoreUtils", "Key with alias '" + AES_KEY_ALIAS + "' not found");
                return null;
            }

            KeyStore.Entry entry = keyStore.getEntry(AES_KEY_ALIAS, null);
            if (entry == null) {
                Log.e("KeyStoreUtils", "Key entry is null for alias: " + AES_KEY_ALIAS);
                return null;
            }

            if (!(entry instanceof KeyStore.SecretKeyEntry)) {
                Log.e("KeyStoreUtils", "Entry is not a SecretKeyEntry");
                return null;
            }

            SecretKey secretKey = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            if (secretKey == null) {
                Log.e("KeyStoreUtils", "Retrieved secret key is null");
                return null;
            }

            Log.d("KeyStoreUtils", "Successfully retrieved AES key. Algorithm: " + secretKey.getAlgorithm());
            return secretKey;
        } catch (Exception e) {
            Log.e("KeyStoreUtils", "Error retrieving AES key: " + e.getMessage(), e);
            return null;
        }
    }
}