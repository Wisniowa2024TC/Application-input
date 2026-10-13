package com.example.demoemptyactivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RoleSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_ROLE = "role";
    public static final String ROLE_USER = "user";
    public static final String ROLE_COURIER = "courier";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_role_selection);

        Button userBtn = findViewById(R.id.btn_user);
        Button courierBtn = findViewById(R.id.btn_courier);

        userBtn.setOnClickListener(v -> openMenu(ROLE_USER));
        courierBtn.setOnClickListener(v -> openMenu(ROLE_COURIER));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void openMenu(String role) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_ROLE, role);
        startActivity(intent);
    }
}
