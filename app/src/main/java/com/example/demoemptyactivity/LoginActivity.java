package com.example.demoemptyactivity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {

    private AuthDatabase database;
    private String selectedRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        database = new AuthDatabase(this);

        EditText usernameInput = findViewById(R.id.et_username);
        EditText passwordInput = findViewById(R.id.et_password);

        Button loginBtn = findViewById(R.id.btn_login);
        Button backBtn = findViewById(R.id.btn_back);

        MaterialButton clientBtn = findViewById(R.id.btn_client);
        MaterialButton courierBtn = findViewById(R.id.btn_courier);

        int selectedColor = Color.parseColor("#6479B3");
        int normalColor = Color.parseColor("#384E8C");

        int selectedStroke = Color.parseColor("#1E3A8A");
        int normalStroke = Color.parseColor("#CCCCCC");

        clientBtn.setOnClickListener(v -> {
            selectedRole = "Klient";

            clientBtn.setBackgroundTintList(
                    ColorStateList.valueOf(selectedColor));
            courierBtn.setBackgroundTintList(
                    ColorStateList.valueOf(normalColor));

            clientBtn.setStrokeColor(
                    ColorStateList.valueOf(selectedStroke));
            courierBtn.setStrokeColor(
                    ColorStateList.valueOf(normalStroke));
        });

        courierBtn.setOnClickListener(v -> {
            selectedRole = "Kurier";

            courierBtn.setBackgroundTintList(
                    ColorStateList.valueOf(selectedColor));
            clientBtn.setBackgroundTintList(
                    ColorStateList.valueOf(normalColor));

            courierBtn.setStrokeColor(
                    ColorStateList.valueOf(selectedStroke));
            clientBtn.setStrokeColor(
                    ColorStateList.valueOf(normalStroke));
        });

        loginBtn.setOnClickListener(v -> {

            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Wypełnij wszystkie pola",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedRole.isEmpty()) {
                Toast.makeText(this, "Wybierz Klient lub Kurier",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (database.login(username, password)) {

                Toast.makeText(this,
                        "Zalogowano jako " + selectedRole,
                        Toast.LENGTH_SHORT).show();

                if (selectedRole.equals("Klient")) {
                    Intent intent = new Intent(LoginActivity.this, ClientActivity.class);
                    startActivity(intent);
                } else if (selectedRole.equals("Kurier")) {
                    Intent intent = new Intent(LoginActivity.this, CourierActivity.class);
                    startActivity(intent);
                }

            } else {
                Toast.makeText(this,
                        "Nieprawidłowa nazwa użytkownika lub hasło",
                        Toast.LENGTH_SHORT).show();
            }
        });

        backBtn.setOnClickListener(v -> finish());
    }
}