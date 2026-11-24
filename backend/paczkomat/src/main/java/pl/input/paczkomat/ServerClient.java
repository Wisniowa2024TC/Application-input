package pl.input.paczkomat;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Komunikacja paczkomatu z serwerem INPUT.
 *
 * <p>Każde żądanie zawiera nagłówek {@code X-Locker-Key} – to "hasło" paczkomatu,
 * takie samo jak w ustawieniach serwera (Config.LOCKER_API_KEY).</p>
 */
public class ServerClient {

    /** Musi być identyczny z Config.LOCKER_API_KEY na serwerze. */
    private static final String LOCKER_API_KEY = "paczkomat-tajny-klucz";

    private final String serverUrl;
    private final String lockerCode;

    /**
     * @param serverUrl  adres serwera, np. http://192.168.1.10:8080
     * @param lockerCode kod tego paczkomatu w bazie serwera, np. A1
     */
    public ServerClient(String serverUrl, String lockerCode) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.lockerCode = lockerCode;
    }

    /** Pyta serwer o polecenia otwarcia skrytek (i przy okazji zgłasza, że paczkomat działa). */
    public JSONObject poll() throws IOException {
        return post("/api/device/" + lockerCode + "/poll", new JSONObject());
    }

    /** Wysyła kod wpisany na klawiaturze. Odpowiedź zawiera numer skrytki do otwarcia. */
    public JSONObject enterCode(String code) throws IOException {
        return post("/api/device/" + lockerCode + "/enter-code", new JSONObject().put("code", code));
    }

    /**
     * Wysyła żądanie POST z treścią JSON i zwraca odpowiedź.
     * Jeżeli serwer odpowie błędem (np. zły kod), rzucamy wyjątek z komunikatem z pola "error".
     */
    private JSONObject post(String path, JSONObject body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(serverUrl + path).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("X-Locker-Key", LOCKER_API_KEY);

        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();

        JSONObject response = new JSONObject(text);
        if (status >= 400) {
            throw new ServerError(response.optString("error", "Błąd serwera (" + status + ")"));
        }
        return response;
    }

    /** Błąd zgłoszony przez serwer (np. "Nieprawidłowy kod") – w odróżnieniu od braku połączenia. */
    public static class ServerError extends IOException {
        public ServerError(String message) {
            super(message);
        }
    }
}
