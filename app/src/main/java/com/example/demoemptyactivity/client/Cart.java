package com.example.demoemptyactivity.client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Koszyk klienta.
 *
 * <p>Trzymamy go w pamięci (pola statyczne) – jest wspólny dla ekranu menu i ekranu koszyka.
 * Po zamknięciu aplikacji koszyk znika, tak jak w wielu aplikacjach z jedzeniem.
 * Ceny w koszyku służą tylko do podglądu – ostateczną kwotę i tak liczy serwer.</p>
 *
 * <p>Zasada jak w dużych aplikacjach z dostawą jedzenia: w koszyku mogą być dania
 * tylko z JEDNEJ restauracji (kurier jedzie po jedzenie w jedno miejsce).</p>
 */
public final class Cart {

    /** Jedna pozycja w koszyku, np. "2 × Burger Classic". */
    public static class Line {
        public final long productId;
        public final String name;
        public final String emoji;
        public final int price;   // cena za sztukę w groszach
        public int quantity;

        Line(long productId, String name, String emoji, int price) {
            this.productId = productId;
            this.name = name;
            this.emoji = emoji;
            this.price = price;
        }
    }

    /** Pozycje w kolejności dodawania (LinkedHashMap zachowuje kolejność). */
    private static final Map<Long, Line> lines = new LinkedHashMap<>();
    private static long restaurantId = -1;
    private static String restaurantName = "";

    private Cart() {
    }

    /** Czy to danie jest z innej restauracji niż to, co już leży w koszyku? */
    public static boolean isFromOtherRestaurant(JSONObject product) {
        return !lines.isEmpty() && product.optLong("restaurant_id") != restaurantId;
    }

    /** Dodaje jedną sztukę dania (obiekt z /api/menu). */
    public static void add(JSONObject product) {
        if (isFromOtherRestaurant(product)) {
            clear();
        }
        restaurantId = product.optLong("restaurant_id");
        restaurantName = product.optString("restaurant_name");

        long id = product.optLong("id");
        Line line = lines.get(id);
        if (line == null) {
            line = new Line(id, product.optString("name"), product.optString("emoji"), product.optInt("price"));
            lines.put(id, line);
        }
        line.quantity++;
    }

    /** Zmienia ilość o +1 albo -1. Gdy dojdzie do zera – pozycja znika. */
    public static void change(long productId, int delta) {
        Line line = lines.get(productId);
        if (line == null) {
            return;
        }
        line.quantity += delta;
        if (line.quantity <= 0) {
            lines.remove(productId);
        }
    }

    public static void clear() {
        lines.clear();
        restaurantId = -1;
        restaurantName = "";
    }

    public static boolean isEmpty() {
        return lines.isEmpty();
    }

    public static List<Line> lines() {
        return new ArrayList<>(lines.values());
    }

    public static String restaurantName() {
        return restaurantName;
    }

    /** Łączna liczba sztuk, np. 2 burgery + 1 frytki = 3. */
    public static int count() {
        int count = 0;
        for (Line line : lines.values()) {
            count += line.quantity;
        }
        return count;
    }

    /** Suma za jedzenie w groszach (bez dostawy). */
    public static int itemsTotal() {
        int total = 0;
        for (Line line : lines.values()) {
            total += line.price * line.quantity;
        }
        return total;
    }

    /** Pozycje w formacie, którego oczekuje serwer: [{"product_id": 3, "quantity": 2}, ...]. */
    public static JSONArray toJson() throws JSONException {
        JSONArray items = new JSONArray();
        for (Line line : lines.values()) {
            items.put(new JSONObject().put("product_id", line.productId).put("quantity", line.quantity));
        }
        return items;
    }
}
