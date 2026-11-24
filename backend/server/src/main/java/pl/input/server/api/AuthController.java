package pl.input.server.api;

import org.json.JSONObject;
import pl.input.server.Config;
import pl.input.server.db.Database;
import pl.input.server.http.ApiException;
import pl.input.server.http.Request;
import pl.input.server.logic.PasswordHasher;

import java.util.UUID;

/**
 * Rejestracja, logowanie i wylogowanie.
 *
 * <p>Po udanym logowaniu serwer zwraca losowy <b>token</b> (np. "3f2a...").
 * Aplikacja zapisuje go w telefonie i dołącza do każdego kolejnego żądania w nagłówku
 * {@code Authorization: Bearer TOKEN}. Dzięki temu serwer wie, kto wysyła żądanie,
 * bez ponownego podawania hasła.</p>
 *
 * <p>Konto ma jedną rolę: CLIENT (klient) albo COURIER (kurier). Przy logowaniu aplikacja
 * podaje, kim użytkownik chce być – jeśli wybierze złą rolę, dostanie czytelny komunikat.</p>
 */
public class AuthController {

    /** Kolumny użytkownika, które wolno wysłać do aplikacji (bez soli i skrótu hasła!). */
    static final String USER_COLUMNS = "id, username, full_name, phone, role";

    private final Database db;

    public AuthController(Database db) {
        this.db = db;
    }

    /**
     * POST /api/auth/register
     * Treść: {"role": "CLIENT", "full_name": "...", "username": "...", "phone": "...", "password": "...",
     *         "courier_code": "..." (tylko dla kuriera)}
     * Odpowiedź: {"token": "...", "user": {...}}
     */
    public JSONObject register(Request request) {
        String role = parseRole(request.requireText("role", "Wybierz, czy jesteś klientem czy kurierem"));
        String fullName = request.requireText("full_name", "Podaj imię i nazwisko");
        String username = request.requireText("username", "Podaj nazwę użytkownika");
        String phone = normalizePhone(request.requireText("phone", "Podaj numer telefonu"));
        String password = request.body().optString("password", "");

        if (!username.matches("[A-Za-z0-9_.]{3,30}")) {
            throw new ApiException(400, "Nazwa użytkownika: 3–30 znaków, tylko litery, cyfry, _ i .");
        }
        if (password.length() < 8) {
            throw new ApiException(400, "Hasło musi mieć minimum 8 znaków");
        }
        if (role.equals("COURIER") && !Config.COURIER_SIGNUP_CODE.equals(request.body().optString("courier_code").trim())) {
            throw new ApiException(403, "Nieprawidłowy kod kuriera. Otrzymasz go od firmy INPUT.");
        }
        if (db.queryInt("SELECT COUNT(*) FROM users WHERE username = ?", username) > 0) {
            throw new ApiException(409, "Ta nazwa użytkownika jest już zajęta");
        }
        if (db.queryInt("SELECT COUNT(*) FROM users WHERE phone = ?", phone) > 0) {
            throw new ApiException(409, "Konto z tym numerem telefonu już istnieje");
        }

        long userId = createUser(db, username, password, fullName, phone, role);
        return startSession(userId);
    }

    /**
     * POST /api/auth/login
     * Treść: {"username": "...", "password": "...", "role": "CLIENT" albo "COURIER"}
     * Odpowiedź: {"token": "...", "user": {...}}
     */
    public JSONObject login(Request request) {
        String username = request.requireText("username", "Podaj nazwę użytkownika");
        String password = request.body().optString("password", "");
        String role = parseRole(request.requireText("role", "Wybierz, czy jesteś klientem czy kurierem"));

        JSONObject user = db.queryOne("SELECT * FROM users WHERE username = ?", username);

        // Ten sam komunikat dla złego loginu i złego hasła – nie zdradzamy, które konta istnieją.
        if (user == null || !PasswordHasher.matches(password, user.getString("salt"), user.getString("password_hash"))) {
            throw new ApiException(400, "Nieprawidłowa nazwa użytkownika lub hasło");
        }
        if (!role.equals(user.getString("role"))) {
            String real = user.getString("role").equals("COURIER") ? "Kurier" : "Klient";
            throw new ApiException(403, "To konto ma rolę „" + real + "” – wybierz ją powyżej.");
        }

        return startSession(user.getLong("id"));
    }

    /** POST /api/auth/logout – usuwa token, więc telefon przestaje być zalogowany. */
    public JSONObject logout(Request request) {
        String header = request.header("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            db.update("DELETE FROM sessions WHERE token = ?", header.substring("Bearer ".length()).trim());
        }
        return new JSONObject().put("message", "Wylogowano");
    }

    /** GET /api/me – dane zalogowanego użytkownika (aplikacja sprawdza nim, czy token jest nadal ważny). */
    public JSONObject me(Request request) {
        return new JSONObject().put("user", request.requireUser());
    }

    /**
     * Tworzy konto w bazie. Metoda statyczna, bo używa jej też {@code DemoData} przy zakładaniu kont testowych.
     */
    public static long createUser(Database db, String username, String password, String fullName,
                                  String phone, String role) {
        String salt = PasswordHasher.newSalt();
        return db.insert(
                "INSERT INTO users (username, full_name, phone, role, salt, password_hash, created_at)" +
                        " VALUES (?, ?, ?, ?, ?, ?, ?)",
                username, fullName, phone, role, salt, PasswordHasher.hash(password, salt), Database.now());
    }

    /** Tworzy nowy token logowania dla użytkownika i zwraca go razem z danymi konta. */
    private JSONObject startSession(long userId) {
        String token = UUID.randomUUID().toString();
        db.insert("INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)", token, userId, Database.now());

        JSONObject user = db.queryOne("SELECT " + USER_COLUMNS + " FROM users WHERE id = ?", userId);
        return new JSONObject().put("token", token).put("user", user);
    }

    /** Akceptujemy tylko dwie role. */
    private String parseRole(String role) {
        String upper = role.toUpperCase();
        if (!upper.equals("CLIENT") && !upper.equals("COURIER")) {
            throw new ApiException(400, "Nieznana rola: " + role);
        }
        return upper;
    }

    /**
     * Ujednolica numer telefonu: usuwa spacje, myślniki i "+48".
     * "+48 500-100-200" → "500100200". Numer musi mieć 9 cyfr.
     */
    private static String normalizePhone(String phone) {
        String digits = phone.replace(" ", "").replace("-", "");
        if (digits.startsWith("+48")) {
            digits = digits.substring(3);
        }
        if (!digits.matches("\\d{9}")) {
            throw new ApiException(400, "Numer telefonu musi mieć 9 cyfr");
        }
        return digits;
    }
}
