package com.example.rapidjava.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

@IgnoreExtraProperties
public class Transaction {
    // Transaction types
    public static final String TYPE_TOPUP = "topup";
    public static final String TYPE_PAYMENT_SENT = "payment_sent";
    public static final String TYPE_PAYMENT_RECEIVED = "payment_received";

    private String transactionId;
    private String type;
    private String description;
    private double amount;
    private String payerId;
    private String recipientId;
    private String currency;
    private String counterparty;
    private boolean isCredit;
    private Timestamp timestamp;
    private String formattedTimestamp; // For display purposes

    public Transaction() {}

    public Transaction(String type, String description, double amount, String payerId, String recipientId,
                       String currency, String counterparty, boolean isCredit,
                       Timestamp timestamp) {
        this.type = type;
        this.description = description;
        this.amount = amount;
        this.payerId = payerId;
        this.recipientId = recipientId;
        this.currency = currency;
        this.counterparty = counterparty;
        this.isCredit = isCredit;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getPayerId() { return payerId; }
    public void setPayerId(String payerId) { this.payerId = payerId; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String counterparty) { this.counterparty = counterparty; }

    @Exclude
    public boolean isCredit() { return isCredit; }
    public void setCredit(boolean credit) { isCredit = credit; }
    public boolean getIsCredit() { return isCredit; } // For Firestore

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getFormattedTimestamp() { return formattedTimestamp; }
    public void setFormattedTimestamp(String formattedTimestamp) {
        this.formattedTimestamp = formattedTimestamp;
    }

    @Exclude
    public Date getDate() {
        return timestamp != null ? timestamp.toDate() : null;
    }

    @Exclude
    public String getTypeDisplayName() {
        switch (type) {
            case TYPE_TOPUP: return "Top-up";
            case TYPE_PAYMENT_SENT: return "Payment Sent";
            case TYPE_PAYMENT_RECEIVED: return "Payment Received";
            default: return type;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Transaction that = (Transaction) o;

        return Double.compare(that.amount, amount) == 0 &&
                isCredit == that.isCredit &&
                Objects.equals(transactionId, that.transactionId) &&
                Objects.equals(type, that.type) &&
                Objects.equals(description, that.description) &&
                Objects.equals(currency, that.currency) &&
                Objects.equals(counterparty, that.counterparty) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(formattedTimestamp, that.formattedTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, type, description, amount, currency,
                counterparty, isCredit, timestamp, formattedTimestamp);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", type='" + type + '\'' +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", counterparty='" + counterparty + '\'' +
                ", isCredit=" + isCredit +
                ", timestamp=" + timestamp +
                ", formattedTimestamp='" + formattedTimestamp + '\'' +
                '}';
    }
}