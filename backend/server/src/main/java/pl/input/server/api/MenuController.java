package pl.input.server.api;

import org.json.JSONArray;
import org.json.JSONObject;
import pl.input.server.db.Database;
import pl.input.server.http.Request;
import pl.input.server.logic.OrderService;

/**
 * Menu – restauracje i dania, które klient widzi na ekranie głównym.
 */
public class MenuController {

    private final Database db;

    public MenuController(Database db) {
        this.db = db;
    }

    /**
     * GET /api/menu
     * Odpowiedź: {"categories": [...], "restaurants": [...], "products": [...], "delivery_fee_text": "4,99 zł"}
     *
     * <p>Wysyłamy całe menu naraz (to kilkanaście dań). Filtrowanie po kategorii i wyszukiwanie
     * robi już aplikacja – dzięki temu działa natychmiast, bez czekania na serwer.</p>
     */
    public JSONObject menu(Request request) {
        request.requireUser();

        JSONArray products = db.queryList(
                "SELECT p.id, p.name, p.description, p.emoji, p.price, p.recommended," +
                        " r.id AS restaurant_id, r.name AS restaurant_name, r.category" +
                        " FROM products p JOIN restaurants r ON r.id = p.restaurant_id" +
                        " ORDER BY r.category, r.name, p.id");
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.getJSONObject(i);
            product.put("price_text", OrderService.money(product.getInt("price")));
        }

        return new JSONObject()
                .put("categories", OrderService.categories())
                .put("restaurants", restaurants(db))
                .put("products", products)
                .put("delivery_fee", OrderService.DELIVERY_FEE)
                .put("delivery_fee_text", OrderService.money(OrderService.DELIVERY_FEE));
    }

    /** Lista restauracji (używa jej też zakładka "Mapa" kuriera). */
    static JSONArray restaurants(Database db) {
        return db.queryList(
                "SELECT id, name, category, emoji, address, latitude, longitude, prep_minutes" +
                        " FROM restaurants ORDER BY name");
    }
}
