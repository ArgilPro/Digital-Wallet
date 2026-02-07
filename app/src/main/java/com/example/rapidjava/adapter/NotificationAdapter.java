package com.example.rapidjava.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.rapidjava.R;
import com.example.rapidjava.model.Notification;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class NotificationAdapter extends ListAdapter<Notification, NotificationAdapter.NotificationViewHolder> {

    private final OnNotificationClickListener listener;

    public NotificationAdapter(OnNotificationClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }

    private static final DiffUtil.ItemCallback<Notification> DIFF_CALLBACK = new DiffUtil.ItemCallback<Notification>() {
        @Override
        public boolean areItemsTheSame(@NonNull Notification oldItem, @NonNull Notification newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Notification oldItem, @NonNull Notification newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = getItem(position);
        holder.bind(notification, listener);
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView messageText;
        private final TextView timeText;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.notificationTitle);
            messageText = itemView.findViewById(R.id.notificationMessage);
            timeText = itemView.findViewById(R.id.notificationTime);
        }

        public void bind(Notification notification, OnNotificationClickListener listener) {
            titleText.setText(notification.getTitle());
            messageText.setText(notification.getMessage());

            if (notification.getTimestamp() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                timeText.setText(sdf.format(notification.getTimestamp().toDate()));
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(notification);
                }
            });
        }
    }
}