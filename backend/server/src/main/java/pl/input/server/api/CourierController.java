package pl.input.server.api;

import org.json.JSONArray;
import org.json.JSONObject;
import pl.input.server.db.Database;
import pl.input.server.http.Request;
import pl.input.server.logic.OrderService;

/**
 * Panel dostawcy (kuriera).
 *
 * <p>Praca kuriera wygląda tak:</p>
 * <ol>
 *     <li>widzi zlecenia "Oczekuje" – jedzenie przygotowywane w restauracjach,</li>
 *     <li>jedzie do restauracji i klika "Odebrałem z restauracji" – zlecenie zmienia się na "W drodze",</li>
 *     <li>przy paczkomacie klika "Włóż do paczkomatu" – serwer wybiera wolną skrytkę i otwiera ją,</li>
 *     <li>klient dostaje kod odbioru.</li>
 * </ol>
 */
public class CourierController {

    private final Database db;
    private final OrderService orders;

    public CourierController(Database db, OrderService orders) {
        this.db = db;
        this.orders = orders;
    }

    /**
     * GET /api/courier/tasks
     * Odpowiedź: {"orders": [...], "active_count": 3, "in_vehicle": 1, "waiting": 2}
     *
     * <p>Najpierw zamówienia, które kurier już wiezie, potem oczekujące – od najstarszego.</p>
     */
    public JSONObject tasks(Request request) {
        JSONObject courier = request.requireRole("COURIER");
        long courierId = courier.getLong("id");

        JSONArray list = orders.findAll(
                "WHERE (o.status = ? AND o.courier_id = ?) OR o.status = ?" +
                        " ORDER BY CASE o.status WHEN 'IN_DELIVERY' THEN 0 ELSE 1 END, o.id",
                OrderService.IN_DELIVERY, courierId, OrderService.PLACED);
        orders.toJsonList(list, courier);

        int inVehicle = 0;
        for (int i = 0; i < list.length(); i++) {
            if (OrderService.IN_DELIVERY.equals(list.getJSONObject(i).getString("status"))) {
                inVehicle++;
            }
        }

        return new JSONObject()
                .put("orders", list)
                .put("active_count", list.length())
                .put("in_vehicle", inVehicle)
                .put("waiting", list.length() - inVehicle);
    }

    /** POST /api/courier/orders/{id}/collect – kurier odebrał jedzenie z restauracji. */
    public JSONObject collect(Request request) {
        JSONObject courier = request.requireRole("COURIER");
        JSONObject order = orders.findById(OrderController.parseId(request));
        orders.collect(order, courier);
        return new JSONObject().put("message", "Zamówienie #" + order.getLong("id") + " jest w Twoim aucie.\n"
                + "Jedź do: " + order.getString("locker_name") + " (" + order.getString("locker_address") + ").");
    }

    /** POST /api/courier/orders/{id}/deliver – kurier jest przy paczkomacie i wkłada jedzenie do skrytki. */
    public JSONObject deliver(Request request) {
        JSONObject courier = request.requireRole("COURIER");
        JSONObject order = orders.findById(OrderController.parseId(request));
        int compartment = orders.deliver(order, courier);
        String message = orders.openRemotely(order.getLong("locker_id"), compartment,
                "Dostawa zamówienia #" + order.getLong("id"));
        return new JSONObject().put("message", message + "\nWłóż jedzenie i zamknij drzwiczki.");
    }

    /**
     * GET /api/courier/map
     * Odpowiedź: {"restaurants": [...], "lockers": [...]} – miejsca, między którymi jeździ kurier.
     */
    public JSONObject map(Request request) {
        request.requireRole("COURIER");
        return new JSONObject()
                .put("restaurants", MenuController.restaurants(db))
                .put("lockers", LockerController.lockers(db));
    }

    /**
     * GET /api/courier/stats
     * Statystyki liczone z historii zamówień (tabela order_events).
     */
    public JSONObject stats(Request request) {
        JSONObject courier = request.requireRole("COURIER");
        long courierId = courier.getLong("id");
        String today = Database.now().substring(0, 10) + "%";   // np. "2026-11-24%"

        int deliveredToday = db.queryInt(
                "SELECT COUNT(*) FROM order_events WHERE user_id = ? AND status = ? AND created_at LIKE ?",
                courierId, OrderService.READY_FOR_PICKUP, today);
        int collectedToday = db.queryInt(
                "SELECT COUNT(*) FROM order_events WHERE user_id = ? AND status = ? AND created_at LIKE ?",
                courierId, OrderService.IN_DELIVERY, today);
        int deliveredTotal = db.queryInt(
                "SELECT COUNT(*) FROM order_events WHERE user_id = ? AND status = ?",
                courierId, OrderService.READY_FOR_PICKUP);
        int inVehicle = db.queryInt("SELECT COUNT(*) FROM orders WHERE courier_id = ? AND status = ?",
                courierId, OrderService.IN_DELIVERY);
        int waiting = db.queryInt("SELECT COUNT(*) FROM orders WHERE status = ?", OrderService.PLACED);

        // Kurier zarabia opłatę za dostawę każdego zamówienia, które włożył do paczkomatu.
        return new JSONObject()
                .put("delivered_today", deliveredToday)
                .put("collected_today", collectedToday)
                .put("delivered_total", deliveredTotal)
                .put("in_vehicle", inVehicle)
                .put("waiting", waiting)
                .put("earned_today_text", OrderService.money(deliveredToday * OrderService.DELIVERY_FEE));
    }
}
