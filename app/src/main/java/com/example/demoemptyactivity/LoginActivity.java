package com.example.demoemptyactivity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        Button backBtn = findViewById(R.id.btn_back);

        backBtn.setOnClickListener(v ->{
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
        });

        MaterialButton clientBtn = findViewById(R.id.btn_client);
        MaterialButton courierBtn = findViewById(R.id.btn_courier);

        int selectedColor = Color.parseColor("#6479B3");
        int normalColor = Color.parseColor("#1E3A8A");

        int selectedStrokeColor = Color.parseColor("#1E3A8A");
        int normalStrokeColor = Color.parseColor("#1E3A8A");

        clientBtn.setOnClickListener(v -> {
            clientBtn.setStrokeColor(ColorStateList.valueOf(selectedStrokeColor));
            courierBtn.setStrokeColor(ColorStateList.valueOf(normalStrokeColor));
            clientBtn.setBackgroundTintList(ColorStateList.valueOf(selectedColor));
            courierBtn.setBackgroundTintList(ColorStateList.valueOf(normalColor));
        });

        courierBtn.setOnClickListener(v -> {
            courierBtn.setStrokeColor(ColorStateList.valueOf(selectedStrokeColor));
            clientBtn.setStrokeColor(ColorStateList.valueOf(normalStrokeColor));
            courierBtn.setBackgroundTintList(ColorStateList.valueOf(selectedColor));
            clientBtn.setBackgroundTintList(ColorStateList.valueOf(normalColor));
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
}