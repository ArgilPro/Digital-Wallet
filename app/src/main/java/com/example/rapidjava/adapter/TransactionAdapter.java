package com.example.rapidjava.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.rapidjava.R;
import com.example.rapidjava.model.Transaction;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionAdapter extends ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder> {

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    private final OnTransactionClickListener listener;

    public TransactionAdapter(OnTransactionClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Transaction> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Transaction>() {
                @Override
                public boolean areItemsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
                    return oldItem.getTransactionId() != null &&
                            oldItem.getTransactionId().equals(newItem.getTransactionId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
                    return oldItem.equals(newItem);
                }
            };

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = getItem(position);
        holder.bind(transaction, listener);
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView descriptionText;
        private final TextView amountText;
        private final TextView timestampText;

        TransactionViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.transaction_title);
            descriptionText = itemView.findViewById(R.id.transaction_description);
            amountText = itemView.findViewById(R.id.transaction_amount);
            timestampText = itemView.findViewById(R.id.transaction_timestamp);
        }

        void bind(Transaction transaction, OnTransactionClickListener listener) {
            String title = transaction.getTypeDisplayName();
            String description = transaction.getDescription() != null
                    ? transaction.getDescription()
                    : transaction.getCounterparty();

            titleText.setText(title);
            descriptionText.setText(description != null ? description : "");

            // Format amount and color
            String formattedAmount = String.format(Locale.getDefault(), "%s %.2f",
                    transaction.getCurrency() != null ? transaction.getCurrency() : "SRD",
                    transaction.getAmount());
            amountText.setText(formattedAmount);
            int color = ContextCompat.getColor(itemView.getContext(),
                    transaction.isCredit() ? R.color.accentGreen : R.color.red);
            amountText.setTextColor(color);

            // Format timestamp
            Timestamp timestamp = transaction.getTimestamp();
            if (timestamp != null) {
                Date date = timestamp.toDate();
                String formattedDate = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                        .format(date);
                timestampText.setText(formattedDate);
            } else {
                timestampText.setText("Onbekende datum");
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTransactionClick(transaction);
            });
        }
    }
}
