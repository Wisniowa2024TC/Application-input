package com.example.demoemptyactivity.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.demoemptyactivity.MainActivity;
import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Ekran logowania.
 *
 * <p>Użytkownik wybiera rolę (Klient / Kurier), wpisuje login i hasło.
 * Dane wysyłamy do serwera ({@code POST /api/auth/login}), a serwer sprawdza hasło
 * i czy konto ma wybraną rolę. Po sukcesie zapisujemy token w {@link Session}
 * i otwieramy panel klienta albo kuriera.</p>
 */
public class LoginActivity extends AppCompatActivity {

    private EditText usernameInput;
    private EditText passwordInput;
    private MaterialButtonToggleGroup roleGroup;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameInput = findViewById(R.id.et_username);
        passwordInput = findViewById(R.id.et_password);
        roleGroup = findViewById(R.id.role_group);
        loginButton = findViewById(R.id.btn_login);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_go_register).setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });
        loginButton.setOnClickListener(v -> login());
    }

    private void login() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String role = roleGroup.getCheckedButtonId() == R.id.btn_role_courier ? "COURIER" : "CLIENT";

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            body.put("role", role);
        } catch (JSONException e) {
            return;
        }

        setLoading(true);
        ApiClient.post(this, "/api/auth/login", body, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Odpowiedź: {"token": "...", "user": {...}}
                Session.save(LoginActivity.this, response.optString("token"), response.optJSONObject("user"));
                Toast.makeText(LoginActivity.this,
                        "Witaj, " + Session.getFirstName(LoginActivity.this) + "!", Toast.LENGTH_SHORT).show();
                MainActivity.openHomeScreen(LoginActivity.this);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Blokuje przycisk na czas czekania na serwer, żeby nie wysłać logowania dwa razy. */
    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Logowanie..." : "Zaloguj się");
    }
}
