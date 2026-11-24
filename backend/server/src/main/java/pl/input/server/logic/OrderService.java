package pl.input.server.logic;

import org.json.JSONArray;
import org.json.JSONObject;
import pl.input.server.Config;
import pl.input.server.db.Database;
import pl.input.server.http.ApiException;

import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cała logika zamówień jedzenia do paczkomatu – w jednym miejscu.
 *
 * <h2>Jak działa zamówienie (tak jak w dużych aplikacjach z dostawą jedzenia)</h2>
 * <pre>
 *   PLACED  ──kurier odbiera jedzenie z restauracji──▶  IN_DELIVERY
 *   ("Przyjęte")                                         ("W drodze")
 *                                                             │
 *                                 kurier wkłada jedzenie do skrytki (otwiera ją z aplikacji)
 *                                                             ▼
 *   PICKED_UP  ◀──klient wpisuje kod albo otwiera skrytkę z aplikacji──  READY_FOR_PICKUP
 *   ("Odebrane")                                                         ("Do odbioru")
 *
 *   PLACED ──klient anuluje (dopóki kurier nie odebrał jedzenia)──▶ CANCELLED
 * </pre>
 *
 * <p>Zasady:</p>
 * <ul>
 *     <li>jedno zamówienie = jedna restauracja (kurier jedzie po jedzenie tylko w jedno miejsce),</li>
 *     <li>ceny liczy SERWER na podstawie menu w bazie – nigdy nie ufamy cenie przysłanej z telefonu,</li>
 *     <li>kod odbioru (6 cyfr) powstaje dopiero, gdy jedzenie leży w skrytce,</li>
 *     <li>rozmiar skrytki zależy od liczby dań: do 2 – mała (A), do 5 – średnia (B), więcej – duża (C).</li>
 * </ul>
 */
public class OrderService {

    // --- Statusy zamówienia (zapisywane w kolumnie orders.status) ---
    public static final String PLACED = "PLACED";
    public static final String IN_DELIVERY = "IN_DELIVERY";
    public static final String READY_FOR_PICKUP = "READY_FOR_PICKUP";
    public static final String PICKED_UP = "PICKED_UP";
    public static final String CANCELLED = "CANCELLED";

    /** Opłata za dostawę do paczkomatu: 4,99 zł (w groszach). */
    public static final int DELIVERY_FEE = 499;

    /** Ile sztuk jednego dania można zamówić naraz. */
    private static final int MAX_QUANTITY = 10;

    /** Kategorie menu – te same, co przyciski na ekranie głównym aplikacji. */
    public static JSONArray categories() {
        return new JSONArray()
                .put(category("PIZZA", "Pizza", "🍕"))
                .put(category("BURGER", "Burgery", "🍔"))
                .put(category("SUSHI", "Sushi", "🍣"))
                .put(category("INNE", "Inne", "🥗"));
    }

    private static JSONObject category(String code, String name, String emoji) {
        return new JSONObject().put("code", code).put("name", name).put("emoji", emoji);
    }

    /**
     * Wspólne zapytanie: zamówienie + nazwy restauracji, paczkomatu, numer skrytki, klient i kurier.
     * {@code items_summary} to krótki opis zawartości, np. "2× Burger Classic, 1× Frytki".
     */
    private static final String ORDER_SELECT =
            "SELECT o.*," +
                    " r.name AS restaurant_name, r.emoji AS restaurant_emoji, r.address AS restaurant_address," +
                    " r.latitude AS restaurant_latitude, r.longitude AS restaurant_longitude," +
                    " l.code AS locker_code, l.name AS locker_name, l.address AS locker_address," +
                    " l.latitude AS locker_latitude, l.longitude AS locker_longitude," +
                    " c.number AS compartment_number," +
                    " cl.full_name AS client_name, cl.phone AS client_phone," +
                    " co.full_name AS courier_name," +
                    " (SELECT GROUP_CONCAT(i.quantity || '× ' || i.name, ', ')" +
                    "    FROM order_items i WHERE i.order_id = o.id) AS items_summary," +
                    " (SELECT SUM(i.quantity) FROM order_items i WHERE i.order_id = o.id) AS items_count" +
                    " FROM orders o" +
                    " JOIN restaurants r ON r.id = o.restaurant_id" +
                    " JOIN lockers l ON l.id = o.locker_id" +
                    " LEFT JOIN compartments c ON c.id = o.compartment_id" +
                    " JOIN users cl ON cl.id = o.client_id" +
                    " LEFT JOIN users co ON co.id = o.courier_id ";

    private final Database db;
    private final SecureRandom random = new SecureRandom();

    public OrderService(Database db) {
        this.db = db;
    }

    // =====================================================================
    //  Proste funkcje pomocnicze
    // =====================================================================

    /** Zamienia grosze na tekst: 2999 → "29,99 zł". */
    public static String money(int grosze) {
        return String.format(Locale.ROOT, "%d,%02d zł", grosze / 100, grosze % 100);
    }

    /** Opis statusu, który widzi użytkownik w aplikacji. */
    public static String statusLabel(String status) {
        switch (status) {
            case PLACED:
                return "Przyjęte";
            case IN_DELIVERY:
                return "W drodze";
            case READY_FOR_PICKUP:
                return "Do odbioru";
            case PICKED_UP:
                return "Odebrane";
            case CANCELLED:
                return "Anulowane";
            default:
                return status;
        }
    }

    /** Rozmiar skrytki potrzebny na daną liczbę dań. */
    public static String sizeFor(int itemCount) {
        if (itemCount <= 2) {
            return "A";
        }
        return itemCount <= 5 ? "B" : "C";
    }

    /** Czy Raspberry Pi paczkomatu odezwało się niedawno (czyli jest "online"). */
    public static boolean isOnline(long lastSeenMs) {
        return System.currentTimeMillis() - lastSeenMs < Config.LOCKER_OFFLINE_AFTER_SECONDS * 1000L;
    }

    // =====================================================================
    //  Odczyt zamówień
    // =====================================================================

    /** Jedno zamówienie po numerze (np. 1047). Gdy go nie ma – błąd 404. */
    public JSONObject findById(long id) {
        JSONObject order = db.queryOne(ORDER_SELECT + "WHERE o.id = ?", id);
        if (order == null) {
            throw new ApiException(404, "Nie ma zamówienia #" + id);
        }
        return order;
    }

    /** Lista zamówień spełniających warunek, np. findAll("WHERE o.client_id = ?", 5). */
    public JSONArray findAll(String whereAndOrder, Object... params) {
        return db.queryList(ORDER_SELECT + whereAndOrder, params);
    }

    /** Dania w zamówieniu – z ceną za sztukę i wartością pozycji. */
    public JSONArray items(long orderId) {
        JSONArray items = db.queryList(
                "SELECT name, emoji, price, quantity FROM order_items WHERE order_id = ? ORDER BY id", orderId);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            item.put("price_text", money(item.getInt("price")));
            item.put("line_total_text", money(item.getInt("price") * item.getInt("quantity")));
        }
        return items;
    }

    /** Historia zamówienia od najnowszego wpisu. */
    public JSONArray history(long orderId) {
        JSONArray events = db.queryList(
                "SELECT status, description, created_at FROM order_events WHERE order_id = ? ORDER BY id DESC", orderId);
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            event.put("status_label", statusLabel(event.getString("status")));
        }
        return events;
    }

    /**
     * Przygotowuje zamówienie do wysłania do aplikacji.
     *
     * <p>Dodaje teksty (numer "#1047", status, kwoty) i informacje, co oglądająca osoba może zrobić
     * (pola can_...). Dzięki temu aplikacja nie musi sama znać zasad – pokazuje przyciski,
     * na które pozwala serwer. Kod odbioru widzi TYLKO klient, który złożył zamówienie.</p>
     */
    public JSONObject toJson(JSONObject order, JSONObject viewer) {
        String status = order.getString("status");
        long viewerId = viewer.getLong("id");
        boolean isOwner = order.getLong("client_id") == viewerId;
        boolean isCourier = "COURIER".equals(viewer.getString("role"));
        boolean isMyDelivery = !order.isNull("courier_id") && order.getLong("courier_id") == viewerId;

        order.put("number", "#" + order.getLong("id"));
        order.put("status_label", statusLabel(status));
        order.put("items_total_text", money(order.getInt("items_total")));
        order.put("delivery_fee_text", money(order.getInt("delivery_fee")));
        order.put("total_text", money(order.getInt("total")));

        if (!(isOwner && READY_FOR_PICKUP.equals(status))) {
            order.remove("pickup_code");
        }

        order.put("can_cancel", isOwner && PLACED.equals(status));
        order.put("can_pick_up", isOwner && READY_FOR_PICKUP.equals(status));
        order.put("can_collect", isCourier && PLACED.equals(status));
        order.put("can_deliver", isCourier && isMyDelivery && IN_DELIVERY.equals(status));
        return order;
    }

    /** {@link #toJson} dla całej listy. */
    public JSONArray toJsonList(JSONArray orders, JSONObject viewer) {
        for (int i = 0; i < orders.length(); i++) {
            toJson(orders.getJSONObject(i), viewer);
        }
        return orders;
    }

    // =====================================================================
    //  Zmiany stanu zamówienia
    // =====================================================================

    /**
     * Nowe zamówienie (klient zapłacił w aplikacji – płatność jest symulowana).
     *
     * @param items lista {"product_id": 3, "quantity": 2}
     * @return numer nowego zamówienia
     */
    public long create(JSONObject client, long lockerId, JSONArray items) {
        if (items == null || items.isEmpty()) {
            throw new ApiException(400, "Koszyk jest pusty");
        }
        JSONObject locker = db.queryOne("SELECT * FROM lockers WHERE id = ?", lockerId);
        if (locker == null) {
            throw new ApiException(400, "Wybierz paczkomat, do którego mamy dostarczyć jedzenie");
        }

        // 1) Sprawdzamy dania i liczymy cenę na podstawie menu z bazy.
        long restaurantId = -1;
        int itemsTotal = 0;
        int itemCount = 0;
        JSONArray products = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            int quantity = item.optInt("quantity", 0);
            if (quantity < 1 || quantity > MAX_QUANTITY) {
                throw new ApiException(400, "Ilość jednego dania: od 1 do " + MAX_QUANTITY);
            }
            JSONObject product = db.queryOne("SELECT * FROM products WHERE id = ?", item.optLong("product_id"));
            if (product == null) {
                throw new ApiException(400, "Tego dania nie ma już w menu – odśwież koszyk");
            }
            if (restaurantId != -1 && restaurantId != product.getLong("restaurant_id")) {
                throw new ApiException(400, "Jedno zamówienie może zawierać dania tylko z jednej restauracji");
            }
            restaurantId = product.getLong("restaurant_id");
            itemsTotal += product.getInt("price") * quantity;
            itemCount += quantity;
            products.put(product.put("quantity", quantity));
        }

        // 2) Czy w paczkomacie jest w ogóle skrytka odpowiedniego rozmiaru?
        String size = sizeFor(itemCount);
        if (findFreeCompartment(lockerId, size) == null) {
            throw new ApiException(409, locker.getString("name") + " jest teraz pełny – wybierz inny paczkomat");
        }

        // 3) Planowana godzina dostawy = teraz + czas przygotowania + ok. 20 minut jazdy (zaokrąglone do 5 minut).
        JSONObject restaurant = db.queryOne("SELECT * FROM restaurants WHERE id = ?", restaurantId);
        LocalTime planned = LocalTime.now().plusMinutes(restaurant.getInt("prep_minutes") + 20L);
        planned = planned.plusMinutes((5 - planned.getMinute() % 5) % 5).withSecond(0).withNano(0);
        String plannedTime = planned.format(DateTimeFormatter.ofPattern("HH:mm"));

        // 4) Zapisujemy zamówienie i jego pozycje.
        String now = Database.now();
        long orderId = db.insert(
                "INSERT INTO orders (client_id, restaurant_id, locker_id, status, size, items_total, delivery_fee," +
                        " total, planned_time, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                client.getLong("id"), restaurantId, lockerId, PLACED, size, itemsTotal, DELIVERY_FEE,
                itemsTotal + DELIVERY_FEE, plannedTime, now, now);

        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.getJSONObject(i);
            db.insert("INSERT INTO order_items (order_id, product_id, name, emoji, price, quantity) VALUES (?, ?, ?, ?, ?, ?)",
                    orderId, product.getLong("id"), product.getString("name"), product.getString("emoji"),
                    product.getInt("price"), product.getInt("quantity"));
        }

        addEvent(orderId, PLACED, client.getLong("id"),
                "Zamówienie opłacone. " + restaurant.getString("name") + " przygotowuje jedzenie.");
        return orderId;
    }

    /** Kurier odebrał jedzenie z restauracji: PLACED → IN_DELIVERY. */
    public void collect(JSONObject order, JSONObject courier) {
        requireStatus(order, PLACED, "To zamówienie ma już kuriera albo zostało anulowane");
        db.update("UPDATE orders SET status = ?, courier_id = ?, updated_at = ? WHERE id = ?",
                IN_DELIVERY, courier.getLong("id"), Database.now(), order.getLong("id"));
        addEvent(order.getLong("id"), IN_DELIVERY, courier.getLong("id"),
                "Kurier " + courier.getString("full_name") + " odebrał jedzenie (" + order.getString("restaurant_name")
                        + ") i jedzie do: " + order.getString("locker_name") + ".");
    }

    /**
     * Kurier wkłada jedzenie do paczkomatu: IN_DELIVERY → READY_FOR_PICKUP.
     * Losujemy wolną skrytkę i kod odbioru dla klienta.
     *
     * @return numer skrytki, którą trzeba otworzyć
     */
    public int deliver(JSONObject order, JSONObject courier) {
        requireStatus(order, IN_DELIVERY, "To zamówienie nie jest teraz w drodze");
        if (order.getLong("courier_id") != courier.getLong("id")) {
            throw new ApiException(403, "To zamówienie wiezie inny kurier");
        }
        JSONObject compartment = findFreeCompartment(order.getLong("locker_id"), order.getString("size"));
        if (compartment == null) {
            throw new ApiException(409, order.getString("locker_name") + " nie ma teraz wolnej skrytki – spróbuj za chwilę");
        }
        int number = compartment.getInt("number");

        db.update("UPDATE orders SET status = ?, compartment_id = ?, pickup_code = ?, updated_at = ? WHERE id = ?",
                READY_FOR_PICKUP, compartment.getLong("id"), newUniqueCode(), Database.now(), order.getLong("id"));
        addEvent(order.getLong("id"), READY_FOR_PICKUP, courier.getLong("id"),
                "Jedzenie czeka: " + order.getString("locker_name") + ", skrytka nr " + number
                        + ". Klient otrzymał kod odbioru.");
        return number;
    }

    /**
     * Klient odbiera jedzenie: READY_FOR_PICKUP → PICKED_UP. Skrytka staje się wolna.
     *
     * @param userId kto odebrał (null = kod wpisany na klawiaturze paczkomatu)
     * @return numer skrytki, którą trzeba otworzyć
     */
    public int pickUp(JSONObject order, Long userId) {
        requireStatus(order, READY_FOR_PICKUP, "To zamówienie nie czeka na odbiór");
        int number = order.getInt("compartment_number");
        db.update("UPDATE orders SET status = ?, compartment_id = NULL, updated_at = ? WHERE id = ?",
                PICKED_UP, Database.now(), order.getLong("id"));
        addEvent(order.getLong("id"), PICKED_UP, userId,
                "Jedzenie odebrane (" + order.getString("locker_name") + "). Smacznego!");
        return number;
    }

    /** Klient anuluje zamówienie, dopóki kurier nie odebrał jedzenia. */
    public void cancel(JSONObject order, JSONObject client) {
        requireStatus(order, PLACED, "Kurier już wiezie to zamówienie – nie można go anulować");
        db.update("UPDATE orders SET status = ?, updated_at = ? WHERE id = ?", CANCELLED, Database.now(), order.getLong("id"));
        addEvent(order.getLong("id"), CANCELLED, client.getLong("id"),
                "Zamówienie anulowane. Pieniądze wrócą na konto (" + money(order.getInt("total")) + ").");
    }

    /**
     * Dodaje polecenie "otwórz skrytkę" do kolejki. Raspberry Pi pobierze je przy najbliższym odpytaniu
     * serwera (co 2 sekundy).
     *
     * @return komunikat dla użytkownika (inny, gdy paczkomat jest offline)
     */
    public String openRemotely(long lockerId, int compartmentNumber, String reason) {
        db.insert("INSERT INTO locker_commands (locker_id, compartment_number, reason, created_at) VALUES (?, ?, ?, ?)",
                lockerId, compartmentNumber, reason, Database.now());

        JSONObject locker = db.queryOne("SELECT name, last_seen_ms FROM lockers WHERE id = ?", lockerId);
        String start = locker.getString("name") + ": skrytka nr " + compartmentNumber;
        if (isOnline(locker.getLong("last_seen_ms"))) {
            return start + " otwiera się.";
        }
        return start + " otworzy się, gdy paczkomat połączy się z serwerem (teraz jest offline).";
    }

    // =====================================================================
    //  Funkcje wewnętrzne
    // =====================================================================

    /**
     * Najmniejsza wolna skrytka, do której zmieści się zamówienie (A < B < C – porównanie liter).
     * Skrytka jest zajęta, gdy jakieś zamówienie ma ją w kolumnie compartment_id.
     */
    private JSONObject findFreeCompartment(long lockerId, String size) {
        return db.queryOne(
                "SELECT c.* FROM compartments c WHERE c.locker_id = ? AND c.size >= ?" +
                        " AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.compartment_id = c.id)" +
                        " ORDER BY c.size, c.number LIMIT 1", lockerId, size);
    }

    private void addEvent(long orderId, String status, Long userId, String description) {
        db.insert("INSERT INTO order_events (order_id, user_id, status, description, created_at) VALUES (?, ?, ?, ?, ?)",
                orderId, userId, status, description, Database.now());
    }

    /** Przerywa akcję błędem 409, jeżeli zamówienie jest w innym stanie niż oczekiwany. */
    private static void requireStatus(JSONObject order, String expected, String message) {
        if (!expected.equals(order.getString("status"))) {
            throw new ApiException(409, message);
        }
    }

    /**
     * Losowy 6-cyfrowy kod odbioru. Sprawdzamy, czy nie używa go inne zamówienie czekające na odbiór,
     * bo wtedy paczkomat nie wiedziałby, którą skrytkę otworzyć.
     */
    private String newUniqueCode() {
        while (true) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            int used = db.queryInt("SELECT COUNT(*) FROM orders WHERE pickup_code = ? AND status = ?",
                    code, READY_FOR_PICKUP);
            if (used == 0) {
                return code;
            }
        }
    }
}
