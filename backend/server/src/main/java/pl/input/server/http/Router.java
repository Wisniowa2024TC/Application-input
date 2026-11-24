package pl.input.server.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;
import pl.input.server.db.Database;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prosty "rozdzielacz" żądań HTTP – decyduje, która metoda kontrolera obsłuży dany adres.
 *
 * <p>Trasy rejestruje się w {@code Main} w czytelny sposób:</p>
 * <pre>
 *     router.post("/api/auth/login", auth::login);
 *     router.get("/api/orders/{id}", orders::details);
 * </pre>
 * <p>Fragment w nawiasach klamrowych, np. {@code {number}}, to parametr – jego wartość
 * odczytuje się w kontrolerze przez {@code request.param("number")}.</p>
 *
 * <p>Dodatkowo Router za każdym razem:</p>
 * <ol>
 *     <li>odczytuje treść żądania jako JSON,</li>
 *     <li>sprawdza token z nagłówka {@code Authorization: Bearer ...} i ustala, kto jest zalogowany,</li>
 *     <li>wykonuje kontroler w jednej transakcji bazy danych – jeśli coś pójdzie nie tak,
 *         żadna częściowa zmiana nie zostanie zapisana,</li>
 *     <li>zamienia wyjątki na odpowiedź {@code {"error": "..."}} i wypisuje log w konsoli.</li>
 * </ol>
 */
public class Router implements HttpHandler {

    /** Metoda kontrolera, która obsługuje jedną trasę i zwraca odpowiedź w formacie JSON. */
    public interface Handler {
        JSONObject handle(Request request);
    }

    /** Jedna zarejestrowana trasa, np. POST /api/orders/{id}/open. */
    private static class Route {
        final String method;
        final String[] parts;
        final Handler handler;

        Route(String method, String pattern, Handler handler) {
            this.method = method;
            this.parts = pattern.split("/");
            this.handler = handler;
        }
    }

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<Route> routes = new ArrayList<>();
    private final Database db;

    public Router(Database db) {
        this.db = db;
    }

    /** Rejestruje trasę dla zapytań GET (odczyt danych). */
    public void get(String pattern, Handler handler) {
        routes.add(new Route("GET", pattern, handler));
    }

    /** Rejestruje trasę dla zapytań POST (zmiana danych). */
    public void post(String pattern, Handler handler) {
        routes.add(new Route("POST", pattern, handler));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        int status = 200;
        JSONObject response;

        try {
            Map<String, String> params = new HashMap<>();
            Route route = findRoute(method, path, params);
            if (route == null) {
                throw new ApiException(404, "Nie ma takiego adresu w API: " + method + " " + path);
            }

            JSONObject body = readBody(exchange);
            JSONObject user = findUserByToken(exchange.getRequestHeaders().getFirst("Authorization"));
            Request request = new Request(method, path, params, body, exchange.getRequestHeaders(), user);

            // Cała obsługa żądania jako jedna transakcja: albo zapisze się wszystko, albo nic.
            response = db.inTransaction(() -> route.handler.handle(request));

        } catch (ApiException e) {
            status = e.getStatus();
            response = new JSONObject().put("error", e.getMessage());

        } catch (Exception e) {
            // Nieprzewidziany błąd w kodzie serwera – szczegóły w konsoli, krótki komunikat dla aplikacji.
            e.printStackTrace();
            status = 500;
            response = new JSONObject().put("error", "Błąd serwera: " + e.getMessage());
        }

        System.out.println(LocalTime.now().format(LOG_TIME) + "  " + method + " " + path + "  ->  " + status);
        sendJson(exchange, status, response);
    }

    /** Szuka trasy pasującej do metody i adresu. Wartości parametrów {..} wpisuje do mapy params. */
    private Route findRoute(String method, String path, Map<String, String> params) {
        String[] pathParts = path.split("/");

        for (Route route : routes) {
            if (!route.method.equals(method) || route.parts.length != pathParts.length) {
                continue;
            }

            Map<String, String> found = new HashMap<>();
            boolean matches = true;

            for (int i = 0; i < pathParts.length; i++) {
                String expected = route.parts[i];
                if (expected.startsWith("{") && expected.endsWith("}")) {
                    found.put(expected.substring(1, expected.length() - 1), pathParts[i]);
                } else if (!expected.equals(pathParts[i])) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                params.putAll(found);
                return route;
            }
        }
        return null;
    }

    /** Odczytuje treść żądania jako JSON. Puste żądanie (np. GET) daje pusty obiekt. */
    private JSONObject readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new ApiException(400, "Treść żądania nie jest poprawnym JSON-em");
        }
    }

    /**
     * Na podstawie nagłówka "Authorization: Bearer TOKEN" znajduje zalogowanego użytkownika.
     * Token dostaje aplikacja przy logowaniu (tabela sessions). Brak/zły token = null.
     */
    private JSONObject findUserByToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return db.queryOne(
                "SELECT u.id, u.username, u.full_name, u.phone, u.role " +
                        "FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?",
                token);
    }

    /** Wysyła odpowiedź JSON w kodowaniu UTF-8 (żeby polskie znaki dotarły poprawnie). */
    private void sendJson(HttpExchange exchange, int status, JSONObject response) throws IOException {
        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
