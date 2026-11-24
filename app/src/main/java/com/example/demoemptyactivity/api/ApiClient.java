package com.example.demoemptyactivity.api;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.example.demoemptyactivity.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Komunikacja aplikacji z serwerem INPUT (HTTP + JSON).
 *
 * <p>Używamy tylko klas wbudowanych w Androida ({@link HttpURLConnection} i {@link JSONObject}),
 * bez dodatkowych bibliotek. Przykład użycia w ekranie:</p>
 * <pre>
 *     ApiClient.get(this, "/api/orders", new ApiClient.Callback() {
 *         public void onSuccess(JSONObject response) { ...pokaż zamówienia... }
 *         public void onError(String message) { ...pokaż komunikat... }
 *     });
 * </pre>
 *
 * <h2>Dlaczego callback, a nie zwykłe "return"?</h2>
 * <p>Android nie pozwala łączyć się z internetem w głównym wątku (tym, który rysuje ekran)
 * – aplikacja by się "zawiesiła" na czas czekania. Dlatego żądanie wykonujemy w wątku w tle,
 * a wynik przekazujemy z powrotem do głównego wątku przez {@link Callback}.
 * Metody onSuccess/onError są zawsze wywoływane w głównym wątku, więc można w nich
 * spokojnie zmieniać widoki.</p>
 */
public final class ApiClient {

    /** Co zrobić z odpowiedzią serwera. */
    public interface Callback {
        /** Serwer odpowiedział poprawnie (kod 2xx). */
        void onSuccess(JSONObject response);

        /** Coś poszło nie tak – message to gotowy tekst do pokazania użytkownikowi. */
        void onError(String message);
    }

    /** Wątki w tle do wysyłania żądań. */
    private static final ExecutorService BACKGROUND = Executors.newFixedThreadPool(2);

    /** Pozwala wrócić z wątku w tle do głównego wątku (tego od ekranu). */
    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());

    private ApiClient() {
    }

    /** Żądanie GET – pobranie danych, np. menu albo listy zamówień. */
    public static void get(Context context, String path, Callback callback) {
        send(context, "GET", path, null, callback);
    }

    /** Żądanie POST – wysłanie danych / wykonanie akcji, np. logowanie, złożenie zamówienia. */
    public static void post(Context context, String path, JSONObject body, Callback callback) {
        send(context, "POST", path, body == null ? new JSONObject() : body, callback);
    }

    private static void send(Context context, String method, String path, JSONObject body, Callback callback) {
        // Adres i token odczytujemy od razu (w głównym wątku), zanim ktoś np. się wyloguje.
        final Context appContext = context.getApplicationContext();
        final String serverUrl = Session.getServerUrl(appContext);
        final String token = Session.getToken(appContext);

        BACKGROUND.execute(() -> {
            try {
                HttpResult result = execute(method, serverUrl + path, token, body);

                if (result.status < 400) {
                    MAIN_THREAD.post(() -> callback.onSuccess(result.json));
                    return;
                }

                String message = result.json.optString("error", "Błąd serwera (kod " + result.status + ")");

                if (result.status == 401 && token != null) {
                    // Token przestał być ważny (np. serwer ma nową bazę) – wracamy do ekranu startowego.
                    MAIN_THREAD.post(() -> handleExpiredSession(appContext, message));
                    return;
                }
                MAIN_THREAD.post(() -> callback.onError(message));

            } catch (IOException e) {
                MAIN_THREAD.post(() -> callback.onError(
                        "Brak połączenia z serwerem " + serverUrl + ". Sprawdź, czy serwer jest uruchomiony."));
            } catch (JSONException e) {
                MAIN_THREAD.post(() -> callback.onError("Serwer zwrócił niepoprawną odpowiedź"));
            }
        });
    }

    /** Wynik żądania: kod HTTP + treść JSON. */
    private static class HttpResult {
        final int status;
        final JSONObject json;

        HttpResult(int status, JSONObject json) {
            this.status = status;
            this.json = json;
        }
    }

    /** Właściwe wysłanie żądania HTTP. Wywoływane TYLKO w wątku w tle. */
    private static HttpResult execute(String method, String url, String token, JSONObject body)
            throws IOException, JSONException {

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = connection.getResponseCode();
            // Przy błędzie (kod 4xx/5xx) treść odpowiedzi jest w "error stream".
            InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String text = stream == null ? "" : readText(stream);

            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            return new HttpResult(status, json);

        } finally {
            connection.disconnect();
        }
    }

    /** Czyta cały strumień jako tekst UTF-8. */
    private static String readText(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        stream.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Sesja wygasła: czyścimy dane logowania i otwieramy ekran startowy. */
    private static void handleExpiredSession(Context appContext, String message) {
        Session.clear(appContext);
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(appContext, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        appContext.startActivity(intent);
    }
}
