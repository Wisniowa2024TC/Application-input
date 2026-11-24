package pl.input.server.api;

import org.json.JSONObject;
import pl.input.server.http.ApiException;
import pl.input.server.http.Request;
import pl.input.server.logic.OrderService;

/**
 * Zamówienia klienta: składanie, lista "Moje zamówienia", szczegóły, odbiór i anulowanie.
 */
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    /**
     * GET /api/orders
     * Odpowiedź: {"orders": [...]}
     *
     * <p>Kolejność: najpierw jedzenie czekające w paczkomacie (to najważniejsze!),
     * potem zamówienia w realizacji, na końcu historia.</p>
     */
    public JSONObject myOrders(Request request) {
        JSONObject client = request.requireRole("CLIENT");
        return new JSONObject().put("orders", orders.toJsonList(orders.findAll(
                "WHERE o.client_id = ? ORDER BY CASE o.status" +
                        " WHEN 'READY_FOR_PICKUP' THEN 0 WHEN 'IN_DELIVERY' THEN 1 WHEN 'PLACED' THEN 1 ELSE 2 END," +
                        " o.id DESC", client.getLong("id")), client));
    }

    /**
     * POST /api/orders
     * Treść: {"locker_id": 1, "items": [{"product_id": 3, "quantity": 2}, ...]}
     * Odpowiedź: {"order": {...}, "message": "..."}
     */
    public JSONObject create(Request request) {
        JSONObject client = request.requireRole("CLIENT");
        long orderId = orders.create(client, request.body().optLong("locker_id"), request.body().optJSONArray("items"));
        JSONObject order = orders.toJson(orders.findById(orderId), client);
        return new JSONObject()
                .put("order", order)
                .put("message", "Zamówienie " + order.getString("number") + " przyjęte! Planowana dostawa ok. "
                        + order.getString("planned_time") + " do: " + order.getString("locker_name") + ".");
    }

    /**
     * GET /api/orders/{id}
     * Odpowiedź: {"order": {...}, "items": [...], "history": [...]}
     *
     * <p>Zamówienie może zobaczyć klient, który je złożył, oraz każdy kurier.</p>
     */
    public JSONObject details(Request request) {
        JSONObject user = request.requireUser();
        JSONObject order = orders.findById(parseId(request));
        boolean isOwner = order.getLong("client_id") == user.getLong("id");
        if (!isOwner && !"COURIER".equals(user.getString("role"))) {
            throw new ApiException(403, "To nie jest Twoje zamówienie");
        }
        long id = order.getLong("id");
        return new JSONObject()
                .put("order", orders.toJson(order, user))
                .put("items", orders.items(id))
                .put("history", orders.history(id));
    }

    /**
     * POST /api/orders/{id}/open
     * Klient stoi przy paczkomacie i otwiera skrytkę z aplikacji (zamiast wpisywać kod).
     */
    public JSONObject open(Request request) {
        JSONObject client = request.requireRole("CLIENT");
        JSONObject order = orders.findById(parseId(request));
        if (order.getLong("client_id") != client.getLong("id")) {
            throw new ApiException(403, "To nie jest Twoje zamówienie");
        }
        int compartment = orders.pickUp(order, client.getLong("id"));
        String message = orders.openRemotely(order.getLong("locker_id"), compartment,
                "Odbiór zamówienia #" + order.getLong("id"));
        return new JSONObject().put("message", message + "\nWyjmij jedzenie i zamknij drzwiczki. Smacznego!");
    }

    /** POST /api/orders/{id}/cancel */
    public JSONObject cancel(Request request) {
        JSONObject client = request.requireRole("CLIENT");
        JSONObject order = orders.findById(parseId(request));
        if (order.getLong("client_id") != client.getLong("id")) {
            throw new ApiException(403, "To nie jest Twoje zamówienie");
        }
        orders.cancel(order, client);
        return new JSONObject().put("message", "Zamówienie anulowane");
    }

    /** Numer zamówienia ze ścieżki. Akceptujemy też zapis z kratką, np. "#1047". */
    static long parseId(Request request) {
        String raw = request.param("id").replace("#", "").trim();
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new ApiException(404, "Nie ma zamówienia " + raw);
        }
    }
}
