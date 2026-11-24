package pl.input.server.api;

import org.json.JSONArray;
import org.json.JSONObject;
import pl.input.server.db.Database;
import pl.input.server.http.Request;
import pl.input.server.logic.OrderService;

/**
 * Lista paczkomatów – używana w zakładce "Paczkomat" i przy wyborze miejsca dostawy w koszyku.
 */
public class LockerController {

    private final Database db;

    public LockerController(Database db) {
        this.db = db;
    }

    /**
     * GET /api/lockers
     * Odpowiedź: {"lockers": [...]}
     */
    public JSONObject list(Request request) {
        request.requireUser();
        return new JSONObject().put("lockers", lockers(db));
    }

    /**
     * Paczkomaty z liczbą wolnych skrytek i informacją, czy Raspberry Pi jest online.
     * Metoda statyczna, bo korzysta z niej też zakładka "Mapa" kuriera.
     */
    static JSONArray lockers(Database db) {
        JSONArray lockers = db.queryList(
                "SELECT l.id, l.code, l.name, l.address, l.latitude, l.longitude, l.last_seen_ms," +
                        " (SELECT COUNT(*) FROM compartments c WHERE c.locker_id = l.id) AS total_compartments," +
                        " (SELECT COUNT(*) FROM compartments c WHERE c.locker_id = l.id" +
                        "    AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.compartment_id = c.id)) AS free_compartments" +
                        " FROM lockers l ORDER BY l.name");

        for (int i = 0; i < lockers.length(); i++) {
            JSONObject locker = lockers.getJSONObject(i);
            locker.put("online", OrderService.isOnline(locker.getLong("last_seen_ms")));
        }
        return lockers;
    }
}
