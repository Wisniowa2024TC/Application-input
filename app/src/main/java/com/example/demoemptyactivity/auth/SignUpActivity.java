package com.example.demoemptyactivity.auth;

import android.os.Bundle;
import android.view.View;
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
 * Ekran rejestracji nowego konta.
 *
 * <p>Najpierw sprawdzamy dane w telefonie (puste pola, długość hasła, powtórzone hasło),
 * żeby od razu pokazać błąd. Potem wysyłamy je do serwera ({@code POST /api/auth/register}),
 * który sprawdza resztę (np. czy login nie jest zajęty, czy kod kuriera jest poprawny).
 * Po udanej rejestracji użytkownik jest od razu zalogowany.</p>
 */
public class SignUpActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup roleGroup;
    private EditText fullNameInput;
    private EditText usernameInput;
    private EditText phoneInput;
    private EditText passwordInput;
    private EditText passwordRepeatInput;
    private EditText courierCodeInput;
    private View courierCodeLayout;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        roleGroup = findViewById(R.id.role_group);
        fullNameInput = findViewById(R.id.et_full_name);
        usernameInput = findViewById(R.id.et_username);
        phoneInput = findViewById(R.id.et_phone);
        passwordInput = findViewById(R.id.et_password);
        passwordRepeatInput = findViewById(R.id.et_password_repeat);
        courierCodeInput = findViewById(R.id.et_courier_code);
        courierCodeLayout = findViewById(R.id.layout_courier_code);
        registerButton = findViewById(R.id.btn_register);

        // Pole "Kod kuriera" pokazujemy tylko wtedy, gdy wybrano rolę Kurier.
        roleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            boolean courier = group.getCheckedButtonId() == R.id.btn_role_courier;
            courierCodeLayout.setVisibility(courier ? View.VISIBLE : View.GONE);
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        registerButton.setOnClickListener(v -> register());
    }

    private void register() {
        boolean courier = roleGroup.getCheckedButtonId() == R.id.btn_role_courier;
        String fullName = fullNameInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String passwordRepeat = passwordRepeatInput.getText().toString();
        String courierCode = courierCodeInput.getText().toString().trim();

        // --- Proste sprawdzenie w telefonie (serwer i tak sprawdzi wszystko jeszcze raz) ---
        if (fullName.isEmpty() || username.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            showMessage("Wypełnij wszystkie pola");
            return;
        }
        if (username.length() < 3) {
            showMessage("Nazwa użytkownika musi mieć minimum 3 znaki");
            return;
        }
        if (password.length() < 8) {
            showMessage("Hasło musi mieć minimum 8 znaków");
            return;
        }
        if (!password.equals(passwordRepeat)) {
            showMessage("Hasła nie są takie same");
            return;
        }
        if (courier && courierCode.isEmpty()) {
            showMessage("Podaj kod kuriera");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("role", courier ? "COURIER" : "CLIENT");
            body.put("full_name", fullName);
            body.put("username", username);
            body.put("phone", phone);
            body.put("password", password);
            body.put("courier_code", courierCode);
        } catch (JSONException e) {
            return;
        }

        registerButton.setEnabled(false);
        ApiClient.post(this, "/api/auth/register", body, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Session.save(SignUpActivity.this, response.optString("token"), response.optJSONObject("user"));
                showMessage("Konto zostało utworzone!");
                MainActivity.openHomeScreen(SignUpActivity.this);
            }

            @Override
            public void onError(String message) {
                registerButton.setEnabled(true);
                showMessage(message);
            }
        });
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
