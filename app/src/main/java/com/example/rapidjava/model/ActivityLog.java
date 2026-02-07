package com.example.rapidjava.model;

import com.google.firebase.Timestamp;

public class ActivityLog {
    private String id;
    private String userId;
    private String action;
    private String details;
    private double amount;
    private boolean isCredit;
    private String counterpartyId;
    private String counterpartyEmail;
    private Timestamp timestamp;
    private String type;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }


    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public boolean isCredit() { return isCredit; }
    public void setCredit(boolean credit) { isCredit = credit; }

    public String getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(String counterpartyId) { this.counterpartyId = counterpartyId; }

    public String getCounterpartyEmail() { return counterpartyEmail; }
    public void setCounterpartyEmail(String counterpartyEmail) { this.counterpartyEmail = counterpartyEmail; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    // Helper method to generate human-readable details
    public String generateDisplayDetails() {
        if (details != null && !details.isEmpty()) {
            return details;
        }

        switch (type) {
            case "money_sent":
                return "Sent SRD " + amount + " to " + counterpartyEmail;
            case "money_received":
                return "Received SRD " + amount + " from " + counterpartyEmail;
            case "request_declined":
                return "Request for SRD " + amount + " declined by " + counterpartyEmail;
            case "request_sent":
                return "Requested SRD " + amount + " from " + counterpartyEmail;
            case "top_up":
                return "Topped up wallet with SRD " + amount;
            default:
                return action + " - SRD " + amount;
        }
    }
}