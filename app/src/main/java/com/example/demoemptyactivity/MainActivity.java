package com.example.demoemptyactivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;
import com.example.demoemptyactivity.auth.LoginActivity;
import com.example.demoemptyactivity.auth.SignUpActivity;
import com.example.demoemptyactivity.client.ClientActivity;
import com.example.demoemptyactivity.common.Mascot;
import com.example.demoemptyactivity.courier.CourierActivity;

import org.json.JSONObject;

/**
 * Ekran startowy aplikacji INPUT.
 *
 * <ul>
 *     <li>Jeżeli użytkownik jest już zalogowany – od razu przechodzimy do jego panelu
 *         (klienta albo kuriera), bez pokazywania tego ekranu.</li>
 *     <li>W przeciwnym razie pokazujemy przyciski "Zaloguj się" i "Zarejestruj się".</li>
 *     <li>Na dole jest adres serwera – kliknięcie pozwala go zmienić (przydatne przy testach
 *         na prawdziwym telefonie, gdy serwer działa na laptopie).</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity {

    private TextView serverText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Session.isLoggedIn(this)) {
            openHomeScreen(this);
            return;
        }

        setContentView(R.layout.activity_main);

        Button loginButton = findViewById(R.id.btn_login);
        Button registerButton = findViewById(R.id.btn_register);
        TextView learnMore = findViewById(R.id.tv_learn_more);
        serverText = findViewById(R.id.tv_server);

        loginButton.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        registerButton.setOnClickListener(v -> startActivity(new Intent(this, SignUpActivity.class)));
        learnMore.setOnClickListener(v -> showAboutDialog(this));
        serverText.setOnClickListener(v -> showServerDialog());

        refreshServerText();
    }

    /**
     * Otwiera panel odpowiedni dla roli zalogowanego użytkownika i zamyka wszystkie
     * poprzednie ekrany (żeby przycisk "wstecz" nie wracał do logowania).
     */
    public static void openHomeScreen(Activity activity) {
        Class<?> screen = Session.isCourier(activity) ? CourierActivity.class : ClientActivity.class;
        Intent intent = new Intent(activity, screen);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    /** Okienko "Dowiedz się więcej" – maskotka krótko tłumaczy, jak działa INPUT. Używane też w menu paneli. */
    public static void showAboutDialog(Activity activity) {
        Mascot.dialog(activity, R.drawable.maskotka_czesc, "Jak działa INPUT?",
                        "INPUT dowozi jedzenie z restauracji do paczkomatu obok Ciebie.\n\n"
                                + "1. Zamów – wybierz dania z jednej restauracji i paczkomat, w którym je odbierzesz.\n\n"
                                + "2. Kurier – odbiera jedzenie z restauracji i wkłada je do skrytki.\n\n"
                                + "3. Odbierz – w aplikacji pojawi się kod odbioru. Wpisz go na klawiaturze "
                                + "paczkomatu albo otwórz skrytkę z telefonu. Smacznego!")
                .setPositiveButton("Rozumiem", null)
                .show();
    }

    private void refreshServerText() {
        serverText.setText("Serwer: " + Session.getServerUrl(this) + "  (zmień)");
    }

    /** Okienko zmiany adresu serwera + sprawdzenie, czy serwer odpowiada. */
    private void showServerDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(Session.getServerUrl(this));

        FrameLayout wrapper = new FrameLayout(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        wrapper.setPadding(padding, padding / 2, padding, 0);
        wrapper.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Adres serwera")
                .setMessage("Emulator: http://10.0.2.2:8080\n"
                        + "Telefon przez USB (adb reverse): http://127.0.0.1:8080\n"
                        + "Telefon w tej samej sieci Wi-Fi: http://IP_KOMPUTERA:8080")
                .setView(wrapper)
                .setPositiveButton("Zapisz", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "http://" + url;
                    }
                    Session.setServerUrl(this, url);
                    refreshServerText();
                    testConnection();
                })
                .setNeutralButton("Domyślny", (dialog, which) -> {
                    Session.setServerUrl(this, Session.DEFAULT_SERVER_URL);
                    refreshServerText();
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    /** Pyta serwer o /api/health i pokazuje wynik. */
    private void testConnection() {
        ApiClient.get(this, "/api/health", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                Toast.makeText(MainActivity.this, "Połączono z serwerem ✅", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
