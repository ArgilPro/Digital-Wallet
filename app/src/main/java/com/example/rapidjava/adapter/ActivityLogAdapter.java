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
import com.example.rapidjava.model.ActivityLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ActivityLogAdapter extends ListAdapter<ActivityLog, ActivityLogAdapter.LogViewHolder> {

    public interface OnLogClickListener {
        void onLogClick(ActivityLog log);
    }

    private final OnLogClickListener listener;

    public ActivityLogAdapter(OnLogClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<ActivityLog> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ActivityLog>() {
                @Override
                public boolean areItemsTheSame(@NonNull ActivityLog oldItem, @NonNull ActivityLog newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ActivityLog oldItem, @NonNull ActivityLog newItem) {
                    return oldItem.getDetails().equals(newItem.getDetails())
                            && oldItem.getAmount() == newItem.getAmount()
                            && oldItem.getAction().equals(newItem.getAction());
                }
            };

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        ActivityLog log = getItem(position);
        holder.bind(log, listener);

        // Use generated details if available
        String displayDetails = log.generateDisplayDetails();
        holder.detailsText.setText(displayDetails);

        // Rest of your binding code...
        holder.amountText.setText(String.format("SRD %.2f", log.getAmount()));
        holder.amountText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                log.isCredit() ? R.color.black : R.color.red));

        if (log.getTimestamp() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            holder.timestampText.setText(sdf.format(log.getTimestamp().toDate()));
        }
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView detailsText;
        private final TextView amountText;
        private final TextView timestampText;

        LogViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.log_title);
            detailsText = itemView.findViewById(R.id.log_details);
            amountText = itemView.findViewById(R.id.log_amount);
            timestampText = itemView.findViewById(R.id.log_timestamp);
        }

        void bind(ActivityLog log, OnLogClickListener listener) {
            titleText.setText(log.getAction());
            detailsText.setText(log.getDetails());
            amountText.setText(String.format(Locale.getDefault(), "SRD %.2f", log.getAmount()));

            if (log.getTimestamp() != null) {
                Date date = log.getTimestamp().toDate();
                String formatted = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(date);
                timestampText.setText(formatted);
            } else {
                timestampText.setText("Onbekende datum");
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onLogClick(log);
            });
        }
    }
}
