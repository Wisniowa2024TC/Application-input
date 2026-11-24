package pl.input.server;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import pl.input.server.api.AuthController;
import pl.input.server.api.CourierController;
import pl.input.server.api.DeviceController;
import pl.input.server.api.LockerController;
import pl.input.server.api.MenuController;
import pl.input.server.api.OrderController;
import pl.input.server.db.Database;
import pl.input.server.db.DemoData;
import pl.input.server.http.Router;
import pl.input.server.logic.OrderService;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Punkt startowy serwera INPUT.
 *
 * <h2>Jak to wszystko jest połączone</h2>
 * <pre>
 *   [Aplikacja Android]  ──HTTP/JSON──▶  [SERWER INPUT + baza SQLite]  ◀──HTTP/JSON──  [Paczkomat: Raspberry Pi]
 *    klient / kurier                       (ten program)                              klawiatura + zamki skrytek
 * </pre>
 *
 * <p>Serwer używa wyłącznie wbudowanego w Javę serwera HTTP ({@link HttpServer}) – bez Springa
 * i innych dużych bibliotek, żeby cały kod dało się przeczytać i zrozumieć.</p>
 *
 * <p>Uruchomienie: w IntelliJ zielona strzałka przy metodzie main, albo w terminalu w folderze backend:
 * {@code gradlew :server:run}</p>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Database db = new Database(Config.DATABASE_FILE);
        OrderService orderService = new OrderService(db);
        DemoData.insertIfEmpty(db, orderService);

        AuthController auth = new AuthController(db);
        LockerController lockers = new LockerController(db);
        MenuController menu = new MenuController(db);
        OrderController orders = new OrderController(orderService);
        CourierController courier = new CourierController(db, orderService);
        DeviceController device = new DeviceController(db, orderService);

        Router router = new Router(db);

        // --- Sprawdzenie, czy serwer działa (można otworzyć w przeglądarce) ---
        router.get("/api/health", request -> new JSONObject().put("status", "ok").put("name", "Serwer INPUT"));

        // --- Konta ---
        router.post("/api/auth/register", auth::register);
        router.post("/api/auth/login", auth::login);
        router.post("/api/auth/logout", auth::logout);
        router.get("/api/me", auth::me);

        // --- Menu i paczkomaty ---
        router.get("/api/menu", menu::menu);
        router.get("/api/lockers", lockers::list);

        // --- Klient: zamówienia ---
        router.get("/api/orders", orders::myOrders);
        router.post("/api/orders", orders::create);
        router.get("/api/orders/{id}", orders::details);
        router.post("/api/orders/{id}/open", orders::open);
        router.post("/api/orders/{id}/cancel", orders::cancel);

        // --- Kurier ---
        router.get("/api/courier/tasks", courier::tasks);
        router.get("/api/courier/stats", courier::stats);
        router.get("/api/courier/map", courier::map);
        router.post("/api/courier/orders/{id}/collect", courier::collect);
        router.post("/api/courier/orders/{id}/deliver", courier::deliver);

        // --- Fizyczny paczkomat (Raspberry Pi) ---
        router.post("/api/device/{code}/poll", device::poll);
        router.post("/api/device/{code}/enter-code", device::enterCode);

        // Wbudowany serwer HTTP. Bez własnego Executora obsługuje żądania po kolei (jedno na raz),
        // więc nie musimy martwić się o dwa żądania zmieniające bazę w tej samej chwili.
        HttpServer server = HttpServer.create(new InetSocketAddress(Config.PORT), 0);
        server.createContext("/", router);
        server.start();

        printBanner();
    }

    /** Wypisuje adresy, pod którymi aplikacja i paczkomat mogą znaleźć serwer. */
    private static void printBanner() throws Exception {
        System.out.println();
        System.out.println("==============================================");
        System.out.println("  Serwer INPUT działa na porcie " + Config.PORT);
        System.out.println("==============================================");
        System.out.println("  Emulator Androida:      http://10.0.2.2:" + Config.PORT);
        System.out.println("  Telefon przez USB:      http://127.0.0.1:" + Config.PORT
                + "  (po komendzie: adb reverse tcp:" + Config.PORT + " tcp:" + Config.PORT + ")");

        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback()) {
                continue;
            }
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
                if (address instanceof Inet4Address) {
                    System.out.println("  Ta sama sieć Wi-Fi:     http://" + address.getHostAddress() + ":" + Config.PORT);
                }
            }
        }
        System.out.println();
        System.out.println("  Konta testowe: jakub / anna (klienci), kurier (kurier), hasło: " + DemoData.DEMO_PASSWORD);
        System.out.println("  Kod rejestracji kuriera: " + Config.COURIER_SIGNUP_CODE);
        System.out.println("  Test w przeglądarce: http://localhost:" + Config.PORT + "/api/health");
        System.out.println("==============================================");
        System.out.println();
    }
}
