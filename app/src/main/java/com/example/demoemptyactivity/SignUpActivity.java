package com.example.demoemptyactivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    private AuthDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        database = new AuthDatabase(this);

        EditText usernameInput = findViewById(R.id.et_username);
        EditText passwordInput = findViewById(R.id.et_password);

        Button registerBtn = findViewById(R.id.btn_register);
        Button backBtn = findViewById(R.id.btn_back);

        registerBtn.setOnClickListener(v -> {

            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Wypełnij wszystkie pola",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (username.length() < 3) {
                Toast.makeText(this, "Nazwa musi mieć minimum 3 znaki",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 8) {
                Toast.makeText(this, "Hasło musi mieć minimum 8 znaków",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (database.register(username, password)) {

                Toast.makeText(this, "Konto zostało utworzone!",
                        Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(
                        SignUpActivity.this,
                        LoginActivity.class
                );

                startActivity(intent);
                finish();

            } else {
                Toast.makeText(this,
                        "Nie udało się utworzyć konta. Ta nazwa może być zajęta.",
                        Toast.LENGTH_LONG).show();
            }
        });

        backBtn.setOnClickListener(v -> finish());
    }
}