package com.example.demoemptyactivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignUpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        Button registerBtn = findViewById(R.id.btn_register);
        Button backBtn = findViewById(R.id.btn_back);

        registerBtn.setOnClickListener(v -> openHome());

        backBtn.setOnClickListener(v -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void openHome() {
        String role = getIntent().getStringExtra(RoleSelectionActivity.EXTRA_ROLE);
        Class<?> target = RoleSelectionActivity.ROLE_COURIER.equals(role)
                ? CourierInterfaceActivity.class : UserInterfaceActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
