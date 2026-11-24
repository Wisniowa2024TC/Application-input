package pl.input.server.http;

import com.sun.net.httpserver.Headers;
import org.json.JSONObject;

import java.util.Map;

/**
 * Jedno żądanie HTTP, które przyszło do serwera – w wygodnej postaci dla kontrolerów.
 *
 * <p>Zawiera:</p>
 * <ul>
 *     <li>parametry ze ścieżki – np. dla trasy {@code /api/orders/{id}} i adresu
 *         {@code /api/orders/1047} metoda {@code param("id")} zwróci {@code "1047"},</li>
 *     <li>treść żądania (JSON) – np. login i hasło przy logowaniu,</li>
 *     <li>zalogowanego użytkownika (jeżeli aplikacja wysłała poprawny token).</li>
 * </ul>
 */
public class Request {

    private final String method;
    private final String path;
    private final Map<String, String> pathParams;
    private final JSONObject body;
    private final Headers headers;
    private final JSONObject user;

    public Request(String method, String path, Map<String, String> pathParams,
                   JSONObject body, Headers headers, JSONObject user) {
        this.method = method;
        this.path = path;
        this.pathParams = pathParams;
        this.body = body;
        this.headers = headers;
        this.user = user;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /** Wartość parametru ze ścieżki, np. numer zamówienia z {@code /api/orders/{id}}. */
    public String param(String name) {
        return pathParams.get(name);
    }

    /** Cała treść żądania w formacie JSON (pusty obiekt, jeżeli nic nie wysłano). */
    public JSONObject body() {
        return body;
    }

    /** Wartość nagłówka HTTP, np. {@code X-Locker-Key}. Zwraca null, jeżeli go nie ma. */
    public String header(String name) {
        return headers.getFirst(name);
    }

    /**
     * Pole tekstowe z treści żądania, które MUSI być wypełnione.
     * Gdy go brakuje, od razu kończy obsługę żądania błędem 400 z podanym komunikatem.
     */
    public String requireText(String field, String errorMessage) {
        String value = body.optString(field, "").trim();
        if (value.isEmpty()) {
            throw new ApiException(400, errorMessage);
        }
        return value;
    }

    /**
     * Zalogowany użytkownik (wiersz z tabeli users: id, username, full_name, phone, role).
     * Jeżeli nikt nie jest zalogowany – błąd 401, aplikacja wróci wtedy do ekranu logowania.
     */
    public JSONObject requireUser() {
        if (user == null) {
            throw new ApiException(401, "Sesja wygasła – zaloguj się ponownie");
        }
        return user;
    }

    /**
     * Zalogowany użytkownik o konkretnej roli ("CLIENT" albo "COURIER").
     * Dzięki temu np. klient nie wywoła funkcji przeznaczonych tylko dla kuriera.
     */
    public JSONObject requireRole(String role) {
        JSONObject current = requireUser();
        if (!role.equals(current.getString("role"))) {
            String who = role.equals("COURIER") ? "kuriera" : "klienta";
            throw new ApiException(403, "Ta funkcja jest dostępna tylko dla konta " + who);
        }
        return current;
    }
}
