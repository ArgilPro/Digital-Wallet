package com.example.rapidjava;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionUtils {

    public static String formatTimestamp(Date timestamp) {
        if (timestamp == null) return "Date not available";

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault());
            return sdf.format(timestamp);
        } catch (Exception e) {
            return "Invalid date";
        }
    }

    public static String formatAmount(double amount, boolean isCredit) {
        String sign = isCredit ? "+" : "-";
        return String.format(Locale.getDefault(), "%sSRD %.2f", sign, amount);
    }
}