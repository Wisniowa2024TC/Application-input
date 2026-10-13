package com.example.demoemptyactivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class UserInterfaceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.user_interface);

        open(R.id.btn1, OdebranieActivity.class);
        open(R.id.btn2, NadanieActivity.class);
        open(R.id.btn3, ZwracanieActivity.class);
        open(R.id.btn4, KartyPlatniczeActivity.class);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void open(int buttonId, Class<?> target) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> startActivity(new Intent(this, target)));
    }
}
