package com.example.rapidjava;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AboutUs extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about_us);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Back button click listener
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            // Navigate to Mijn_profiel activity
            Intent intent = new Intent(AboutUs.this, Mijn_Profiel.class);
            startActivity(intent);
            finish(); // Optional: remove if you want to keep AboutUs in back stack
        });

        // Email click listener
        View emailLayout = findViewById(R.id.emailLayout); // You'll need to add this ID to your XML
        emailLayout.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:support@rapidpay.com")); // Replace with your support email
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "RapidPay Support");
            startActivity(Intent.createChooser(emailIntent, "Send email via"));
        });

        // Phone click listener
        View phoneLayout = findViewById(R.id.phoneLayout); // You'll need to add this ID to your XML
        phoneLayout.setOnClickListener(v -> {
            Intent phoneIntent = new Intent(Intent.ACTION_DIAL);
            phoneIntent.setData(Uri.parse("tel:+1234567890")); // Replace with your support number
            startActivity(phoneIntent);
        });

        // Website click listener
        View websiteLayout = findViewById(R.id.websiteLayout); // You'll need to add this ID to your XML
        websiteLayout.setOnClickListener(v -> {
            Intent webIntent = new Intent(Intent.ACTION_VIEW);
            webIntent.setData(Uri.parse("https://www.rapidpay.com")); // Replace with your website URL
            startActivity(webIntent);
        });
    }
}