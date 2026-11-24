package pl.input.server.db;

import org.json.JSONArray;
import org.json.JSONObject;
import pl.input.server.api.AuthController;
import pl.input.server.logic.OrderService;

/**
 * Dane przykładowe wstawiane przy pierwszym uruchomieniu serwera (gdy baza jest pusta).
 *
 * <p>Konta testowe (hasło do wszystkich: <b>haslo123</b>):</p>
 * <ul>
 *     <li>jakub – klient, tel. 500100200,</li>
 *     <li>anna – klientka, tel. 500200300,</li>
 *     <li>kurier – kurier.</li>
 * </ul>
 *
 * <p>Zamówienia przykładowe są tworzone przez te same metody {@link OrderService}, których używa aplikacja
 * (zamów → kurier odbiera z restauracji → wkłada do paczkomatu → klient odbiera),
 * więc mają prawdziwą historię i poprawnie zajęte skrytki.</p>
 *
 * <p>Paczkomat A1 to ten fizyczny – na Raspberry Pi (6 skrytek). B2 i C3 są "wirtualne",
 * ale można je również uruchomić programem paczkomatu na komputerze.</p>
 */
public final class DemoData {

    public static final String DEMO_PASSWORD = "haslo123";

    private DemoData() {
    }

    /** Wstawia dane, jeżeli w bazie nie ma jeszcze żadnego paczkomatu. */
    public static void insertIfEmpty(Database db, OrderService orders) {
        if (db.queryInt("SELECT COUNT(*) FROM lockers") > 0) {
            return;
        }
        System.out.println("Pusta baza – wstawiam dane przykładowe...");

        db.inTransaction(() -> {
            // --- Konta ---
            long jakubId = AuthController.createUser(db, "jakub", DEMO_PASSWORD, "Jakub Kowalski", "500100200", "CLIENT");
            long annaId = AuthController.createUser(db, "anna", DEMO_PASSWORD, "Anna Nowak", "500200300", "CLIENT");
            long courierId = AuthController.createUser(db, "kurier", DEMO_PASSWORD, "Marek Wiśniewski", "500900900", "COURIER");

            JSONObject jakub = user(db, jakubId);
            JSONObject anna = user(db, annaId);
            JSONObject courier = user(db, courierId);

            // --- Paczkomaty (współrzędne przykładowe – można zmienić na okolice swojej szkoły) ---
            long a1 = addLocker(db, "A1", "Paczkomat A1", "ul. Szkolna 1 (przy wejściu do szkoły)", 52.2297, 21.0122, "AABBCC");
            long b2 = addLocker(db, "B2", "Paczkomat B2", "ul. Marszałkowska 10", 52.2319, 21.0067, "AAABBBCC");
            long c3 = addLocker(db, "C3", "Paczkomat C3", "ul. Puławska 25", 52.2051, 21.0213, "AAABBBCC");

            // --- Restauracje i menu (ceny w groszach) ---
            long pizzeria = addRestaurant(db, "Pizzeria Bella Napoli", "PIZZA", "🍕", "ul. Nowy Świat 15", 52.2330, 21.0180, 15);
            long margherita = addProduct(db, pizzeria, "Pizza Margherita", "Sos pomidorowy, mozzarella, świeża bazylia", "🍕", 2999, true);
            addProduct(db, pizzeria, "Pizza Pepperoni", "Mozzarella i pikantne salami pepperoni", "🍕", 3499, false);
            long capricciosa = addProduct(db, pizzeria, "Pizza Capricciosa", "Szynka, pieczarki, mozzarella", "🍕", 3699, false);
            addProduct(db, pizzeria, "Pizza Wegetariana", "Papryka, cukinia, oliwki, kukurydza", "🍕", 3299, false);

            long burgers = addRestaurant(db, "Burger Bros", "BURGER", "🍔", "ul. Chmielna 8", 52.2310, 21.0140, 10);
            long classic = addProduct(db, burgers, "Burger Classic", "Wołowina 150 g, sałata, pomidor, sos domowy", "🍔", 2799, true);
            long cheeseburger = addProduct(db, burgers, "Cheeseburger", "Wołowina 150 g, podwójny cheddar", "🍔", 2999, false);
            long fries = addProduct(db, burgers, "Frytki", "Chrupiące frytki z solą morską", "🍟", 999, false);
            addProduct(db, burgers, "Nuggetsy", "6 sztuk z sosem BBQ", "🍗", 1499, false);

            long sushi = addRestaurant(db, "Sushi Master", "SUSHI", "🍣", "ul. Mokotowska 40", 52.2230, 21.0160, 20);
            long california = addProduct(db, sushi, "Zestaw California", "12 sztuk: krab, awokado, ogórek", "🍣", 3999, true);
            long maki = addProduct(db, sushi, "Maki z łososiem", "8 sztuk", "🍣", 2499, false);
            addProduct(db, sushi, "Nigiri mix", "6 sztuk: łosoś i tuńczyk", "🍣", 2999, false);
            long miso = addProduct(db, sushi, "Zupa miso", "Tofu, wakame, szczypiorek", "🍜", 1299, false);

            long bowl = addRestaurant(db, "Zdrowa Miska", "INNE", "🥗", "ul. Hoża 12", 52.2260, 21.0130, 10);
            long caesar = addProduct(db, bowl, "Sałatka Cezar", "Kurczak, parmezan, grzanki, sos cezar", "🥗", 2499, true);
            long wrap = addProduct(db, bowl, "Wrap z kurczakiem", "Kurczak, warzywa, sos jogurtowy", "🌯", 2199, false);
            addProduct(db, bowl, "Sernik", "Kawałek domowego sernika", "🍰", 1299, false);
            long lemonade = addProduct(db, bowl, "Lemoniada", "Domowa, 0,5 l", "🥤", 899, false);

            // Numery zamówień zaczynamy od 1041 – jak na makiecie aplikacji (#1047, #1048...).
            db.update("INSERT INTO sqlite_sequence (name, seq) VALUES ('orders', 1040)");

            // --- Zamówienia w różnych etapach, żeby od razu było co testować ---

            // #1041 Jakub – odebrane (historia)
            long o1 = orders.create(jakub, a1, items(margherita, 2));
            collect(orders, o1, courier);
            deliver(orders, o1, courier);
            orders.pickUp(orders.findById(o1), jakubId);

            // #1042 Anna – odebrane (historia)
            long o2 = orders.create(anna, b2, items(maki, 2, miso, 1));
            collect(orders, o2, courier);
            deliver(orders, o2, courier);
            orders.pickUp(orders.findById(o2), annaId);

            // #1043 Jakub – anulowane
            long o3 = orders.create(jakub, a1, items(wrap, 1));
            orders.cancel(orders.findById(o3), jakub);

            // #1044 Anna – odebrane (historia)
            long o4 = orders.create(anna, c3, items(cheeseburger, 1, fries, 1));
            collect(orders, o4, courier);
            deliver(orders, o4, courier);
            orders.pickUp(orders.findById(o4), annaId);

            // #1045 Jakub – CZEKA w paczkomacie A1 (Jakub ma kod odbioru)
            long o5 = orders.create(jakub, a1, items(classic, 2, fries, 1));
            collect(orders, o5, courier);
            deliver(orders, o5, courier);

            // #1046 Anna – czeka w paczkomacie B2
            long o6 = orders.create(anna, b2, items(capricciosa, 1, margherita, 1));
            collect(orders, o6, courier);
            deliver(orders, o6, courier);

            // #1047 Jakub – kurier wiezie je do A1 ("W drodze")
            long o7 = orders.create(jakub, a1, items(california, 1, miso, 2));
            collect(orders, o7, courier);

            // #1048 Anna i #1049 Jakub – restauracje przygotowują ("Oczekuje" u kuriera)
            orders.create(anna, b2, items(caesar, 1, lemonade, 2));
            orders.create(jakub, a1, items(margherita, 1));

            // Polecenia "otwórz skrytkę" z tworzenia danych przykładowych nie są potrzebne.
            db.update("DELETE FROM locker_commands");
            return null;
        });
    }

    /** Lista dań do zamówienia: items(idDania, ilość, idDania, ilość, ...). */
    private static JSONArray items(long... productAndQuantity) {
        JSONArray items = new JSONArray();
        for (int i = 0; i < productAndQuantity.length; i += 2) {
            items.put(new JSONObject()
                    .put("product_id", productAndQuantity[i])
                    .put("quantity", productAndQuantity[i + 1]));
        }
        return items;
    }

    private static void collect(OrderService orders, long orderId, JSONObject courier) {
        orders.collect(orders.findById(orderId), courier);
    }

    private static void deliver(OrderService orders, long orderId, JSONObject courier) {
        orders.deliver(orders.findById(orderId), courier);
    }

    private static JSONObject user(Database db, long id) {
        return db.queryOne("SELECT id, username, full_name, phone, role FROM users WHERE id = ?", id);
    }

    private static long addRestaurant(Database db, String name, String category, String emoji, String address,
                                      double latitude, double longitude, int prepMinutes) {
        return db.insert("INSERT INTO restaurants (name, category, emoji, address, latitude, longitude, prep_minutes)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?)", name, category, emoji, address, latitude, longitude, prepMinutes);
    }

    private static long addProduct(Database db, long restaurantId, String name, String description, String emoji,
                                   int price, boolean recommended) {
        return db.insert("INSERT INTO products (restaurant_id, name, description, emoji, price, recommended)" +
                " VALUES (?, ?, ?, ?, ?, ?)", restaurantId, name, description, emoji, price, recommended ? 1 : 0);
    }

    /**
     * Dodaje paczkomat i jego skrytki.
     *
     * @param compartmentSizes rozmiary kolejnych skrytek, np. "AABBCC" = skrytki 1–2 małe, 3–4 średnie, 5–6 duże
     */
    private static long addLocker(Database db, String code, String name, String address,
                                  double latitude, double longitude, String compartmentSizes) {
        long lockerId = db.insert(
                "INSERT INTO lockers (code, name, address, latitude, longitude) VALUES (?, ?, ?, ?, ?)",
                code, name, address, latitude, longitude);

        for (int i = 0; i < compartmentSizes.length(); i++) {
            db.insert("INSERT INTO compartments (locker_id, number, size) VALUES (?, ?, ?)",
                    lockerId, i + 1, String.valueOf(compartmentSizes.charAt(i)));
        }
        return lockerId;
    }
}
