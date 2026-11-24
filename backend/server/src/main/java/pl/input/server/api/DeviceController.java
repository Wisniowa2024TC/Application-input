package pl.input.server.api;

import org.json.JSONArray;
import org.json.JSONObject;
import pl.input.server.Config;
import pl.input.server.db.Database;
import pl.input.server.http.ApiException;
import pl.input.server.http.Request;
import pl.input.server.logic.OrderService;

/**
 * API dla fizycznych paczkomatów (program na Raspberry Pi).
 *
 * <p>Paczkomat nie loguje się jak użytkownik – zamiast tokenu wysyła wspólny klucz
 * w nagłówku {@code X-Locker-Key} (patrz {@link Config#LOCKER_API_KEY}).</p>
 *
 * <p>Paczkomat robi dwie rzeczy:</p>
 * <ol>
 *     <li>co 2 sekundy pyta serwer "czy mam coś otworzyć?" ({@link #poll}) –
 *         tak działa otwieranie skrytek z aplikacji (kurier wkłada jedzenie, klient je wyjmuje),</li>
 *     <li>gdy klient wpisze kod odbioru na klawiaturze paczkomatu, sprawdza go na serwerze ({@link #enterCode}).</li>
 * </ol>
 * <p>Dzięki "odpytywaniu" (polling) Raspberry Pi nie musi mieć publicznego adresu IP
 * – to ono zawsze łączy się z serwerem, nigdy odwrotnie.</p>
 */
public class DeviceController {

    private final Database db;
    private final OrderService orders;

    public DeviceController(Database db, OrderService orders) {
        this.db = db;
        this.orders = orders;
    }

    /**
     * POST /api/device/{code}/poll
     * Odpowiedź: {"locker": {...}, "compartments": [...], "commands": [{"compartment_number": 3, "reason": "..."}]}
     *
     * <p>Przy okazji zapisujemy czas ostatniego kontaktu – aplikacja pokazuje wtedy paczkomat jako "online".</p>
     */
    public JSONObject poll(Request request) {
        JSONObject locker = authorizeLocker(request);
        long lockerId = locker.getLong("id");

        db.update("UPDATE lockers SET last_seen_ms = ? WHERE id = ?", System.currentTimeMillis(), lockerId);

        JSONArray commands = db.queryList(
                "SELECT id, compartment_number, reason FROM locker_commands" +
                        " WHERE locker_id = ? AND delivered = 0 ORDER BY id", lockerId);
        // Polecenie wysyłamy tylko raz – od razu oznaczamy je jako dostarczone.
        db.update("UPDATE locker_commands SET delivered = 1 WHERE locker_id = ? AND delivered = 0", lockerId);

        JSONArray compartments = db.queryList(
                "SELECT c.number, c.size," +
                        " EXISTS (SELECT 1 FROM orders o WHERE o.compartment_id = c.id) AS occupied" +
                        " FROM compartments c WHERE c.locker_id = ? ORDER BY c.number", lockerId);

        return new JSONObject()
                .put("locker", new JSONObject()
                        .put("code", locker.getString("code"))
                        .put("name", locker.getString("name"))
                        .put("address", locker.getString("address")))
                .put("compartments", compartments)
                .put("commands", commands);
    }

    /**
     * POST /api/device/{code}/enter-code
     * Treść: {"code": "123456"}
     * Odpowiedź: {"compartment_number": 3, "message": "..."}
     *
     * <p>Szukamy zamówienia, które czeka w TYM paczkomacie i ma taki kod odbioru.</p>
     */
    public JSONObject enterCode(Request request) {
        JSONObject locker = authorizeLocker(request);
        String code = request.requireText("code", "Wpisz kod odbioru");

        JSONArray found = orders.findAll("WHERE o.pickup_code = ? AND o.locker_id = ? AND o.status = ?",
                code, locker.getLong("id"), OrderService.READY_FOR_PICKUP);
        if (found.isEmpty()) {
            throw new ApiException(404, "Nieprawidłowy kod albo zamówienie czeka w innym paczkomacie");
        }

        JSONObject order = found.getJSONObject(0);
        int compartment = orders.pickUp(order, null);
        return new JSONObject()
                .put("compartment_number", compartment)
                .put("message", "Odbiór zamówienia #" + order.getLong("id")
                        + ". Wyjmij jedzenie ze skrytki nr " + compartment + " i zamknij drzwiczki. Smacznego!");
    }

    /** Sprawdza klucz paczkomatu i zwraca jego wiersz z bazy. */
    private JSONObject authorizeLocker(Request request) {
        if (!Config.LOCKER_API_KEY.equals(request.header("X-Locker-Key"))) {
            throw new ApiException(401, "Nieprawidłowy klucz paczkomatu");
        }
        JSONObject locker = db.queryOne("SELECT * FROM lockers WHERE code = ?", request.param("code"));
        if (locker == null) {
            throw new ApiException(404, "Nie ma paczkomatu o kodzie " + request.param("code"));
        }
        return locker;
    }
}
