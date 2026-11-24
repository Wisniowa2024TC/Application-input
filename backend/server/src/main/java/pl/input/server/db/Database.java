package pl.input.server.db;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Dostęp do bazy danych SQLite (plik {@code input.db}).
 *
 * <p>Zamiast pisać za każdym razem pętlę po {@link ResultSet}, korzystamy z kilku metod pomocniczych.
 * Każdy wiersz z bazy od razu zamieniamy na {@link JSONObject}, którego klucze to nazwy kolumn
 * – dzięki temu wynik zapytania można prawie bez zmian odesłać do aplikacji.</p>
 *
 * <pre>
 *     JSONObject user  = db.queryOne("SELECT * FROM users WHERE id = ?", 5);
 *     JSONArray lockers = db.queryList("SELECT * FROM lockers");
 *     long id = db.insert("INSERT INTO lockers(code, name) VALUES (?, ?)", "A1", "Paczkomat A1");
 * </pre>
 *
 * <p>Znaki zapytania w SQL to parametry – wartości podajemy osobno, a sterownik sam je
 * bezpiecznie wstawia. Nigdy nie doklejamy danych od użytkownika do tekstu SQL (SQL injection!).</p>
 *
 * <p>Wyjątki {@link SQLException} zamieniamy na {@link RuntimeException}, żeby nie trzeba było
 * pisać {@code throws SQLException} w każdej metodzie serwera.</p>
 */
public class Database {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Wersja struktury bazy. Gdy zmieniamy tabele, zwiększamy tę liczbę – stary plik input.db
     * zostanie wtedy wyczyszczony i utworzony od nowa (z danymi przykładowymi).
     * W prawdziwym systemie robi się "migracje", ale w projekcie szkolnym to wystarczy.
     */
    private static final int SCHEMA_VERSION = 2;

    private final Connection connection;

    /** Otwiera (albo tworzy) plik bazy i zakłada tabele, jeżeli jeszcze ich nie ma. */
    public Database(String file) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + file);
            if (queryInt("PRAGMA user_version") != SCHEMA_VERSION) {
                dropAllTables();
            }
            try (Statement statement = connection.createStatement()) {
                // SQLite domyślnie NIE sprawdza kluczy obcych – trzeba to włączyć ręcznie.
                statement.execute("PRAGMA foreign_keys = ON");
            }
            createTables();
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Nie udało się otworzyć bazy " + file, e);
        }
    }

    /** Usuwa wszystkie tabele starej wersji bazy. */
    private void dropAllTables() throws SQLException {
        JSONArray tables = queryList("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'");
        if (tables.isEmpty()) {
            return;
        }
        System.out.println("Stara wersja bazy – tworzę ją od nowa...");
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            for (int i = 0; i < tables.length(); i++) {
                statement.execute("DROP TABLE IF EXISTS " + tables.getJSONObject(i).getString("name"));
            }
        }
    }

    /**
     * Struktura bazy danych. Opis tabel:
     * <ul>
     *     <li>users – konta klientów i kurierów (rola CLIENT / COURIER),</li>
     *     <li>sessions – tokeny zalogowanych urządzeń (jeden użytkownik może mieć kilka telefonów),</li>
     *     <li>lockers – paczkomaty; kolumna code (np. "A1") łączy paczkomat z programem na Raspberry Pi,</li>
     *     <li>compartments – skrytki w paczkomatach z rozmiarem A/B/C,</li>
     *     <li>restaurants – restauracje, z których kurier odbiera jedzenie,</li>
     *     <li>products – dania w menu (cena w groszach, żeby uniknąć błędów zaokrągleń),</li>
     *     <li>orders – zamówienia; compartment_id jest wypełnione TYLKO gdy jedzenie fizycznie leży w skrytce,</li>
     *     <li>order_items – pozycje zamówienia; nazwę i cenę KOPIUJEMY z menu, bo menu może się później zmienić,
     *         a stare zamówienie musi pokazywać cenę, którą klient faktycznie zapłacił,</li>
     *     <li>order_events – historia zamówienia (to, co widać w "Historii" w aplikacji),</li>
     *     <li>locker_commands – kolejka poleceń "otwórz skrytkę" czekających na odebranie przez Raspberry Pi.</li>
     * </ul>
     */
    private void createTables() throws SQLException {
        String[] tables = {
                "CREATE TABLE IF NOT EXISTS users (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " username TEXT NOT NULL UNIQUE COLLATE NOCASE," +
                        " full_name TEXT NOT NULL," +
                        " phone TEXT NOT NULL UNIQUE," +
                        " role TEXT NOT NULL," +              // CLIENT albo COURIER
                        " salt TEXT NOT NULL," +              // losowa sól hasła (Base64)
                        " password_hash TEXT NOT NULL," +     // skrót hasła PBKDF2 (Base64)
                        " created_at TEXT NOT NULL)",

                "CREATE TABLE IF NOT EXISTS sessions (" +
                        " token TEXT PRIMARY KEY," +
                        " user_id INTEGER NOT NULL REFERENCES users(id)," +
                        " created_at TEXT NOT NULL)",

                "CREATE TABLE IF NOT EXISTS lockers (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " code TEXT NOT NULL UNIQUE," +
                        " name TEXT NOT NULL," +
                        " address TEXT NOT NULL," +
                        " latitude REAL NOT NULL," +
                        " longitude REAL NOT NULL," +
                        " last_seen_ms INTEGER NOT NULL DEFAULT 0)",   // kiedy Raspberry Pi ostatnio się odezwało

                "CREATE TABLE IF NOT EXISTS compartments (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " locker_id INTEGER NOT NULL REFERENCES lockers(id)," +
                        " number INTEGER NOT NULL," +
                        " size TEXT NOT NULL," +              // A – mała, B – średnia, C – duża
                        " UNIQUE (locker_id, number))",

                "CREATE TABLE IF NOT EXISTS restaurants (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " name TEXT NOT NULL," +
                        " category TEXT NOT NULL," +          // PIZZA, BURGER, SUSHI albo INNE
                        " emoji TEXT NOT NULL," +
                        " address TEXT NOT NULL," +
                        " latitude REAL NOT NULL," +
                        " longitude REAL NOT NULL," +
                        " prep_minutes INTEGER NOT NULL)",    // ile mniej więcej trwa przygotowanie

                "CREATE TABLE IF NOT EXISTS products (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " restaurant_id INTEGER NOT NULL REFERENCES restaurants(id)," +
                        " name TEXT NOT NULL," +
                        " description TEXT NOT NULL," +
                        " emoji TEXT NOT NULL," +
                        " price INTEGER NOT NULL," +          // w groszach: 2999 = 29,99 zł
                        " recommended INTEGER NOT NULL DEFAULT 0)",   // 1 = pokazuj w sekcji "Polecane"

                "CREATE TABLE IF NOT EXISTS orders (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " client_id INTEGER NOT NULL REFERENCES users(id)," +
                        " restaurant_id INTEGER NOT NULL REFERENCES restaurants(id)," +
                        " locker_id INTEGER NOT NULL REFERENCES lockers(id)," +
                        " compartment_id INTEGER REFERENCES compartments(id)," +
                        " courier_id INTEGER REFERENCES users(id)," +
                        " status TEXT NOT NULL," +
                        " size TEXT NOT NULL," +              // potrzebny rozmiar skrytki A/B/C
                        " items_total INTEGER NOT NULL," +    // suma za jedzenie (grosze)
                        " delivery_fee INTEGER NOT NULL," +   // opłata za dostawę (grosze)
                        " total INTEGER NOT NULL," +          // razem do zapłaty (grosze)
                        " pickup_code TEXT," +
                        " planned_time TEXT NOT NULL," +      // planowana godzina dostawy, np. "12:30"
                        " created_at TEXT NOT NULL," +
                        " updated_at TEXT NOT NULL)",

                "CREATE TABLE IF NOT EXISTS order_items (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " order_id INTEGER NOT NULL REFERENCES orders(id)," +
                        " product_id INTEGER NOT NULL REFERENCES products(id)," +
                        " name TEXT NOT NULL," +
                        " emoji TEXT NOT NULL," +
                        " price INTEGER NOT NULL," +
                        " quantity INTEGER NOT NULL)",

                "CREATE TABLE IF NOT EXISTS order_events (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " order_id INTEGER NOT NULL REFERENCES orders(id)," +
                        " user_id INTEGER REFERENCES users(id)," +   // kto to zrobił (null = paczkomat)
                        " status TEXT NOT NULL," +
                        " description TEXT NOT NULL," +
                        " created_at TEXT NOT NULL)",

                "CREATE TABLE IF NOT EXISTS locker_commands (" +
                        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        " locker_id INTEGER NOT NULL REFERENCES lockers(id)," +
                        " compartment_number INTEGER NOT NULL," +
                        " reason TEXT NOT NULL," +
                        " created_at TEXT NOT NULL," +
                        " delivered INTEGER NOT NULL DEFAULT 0)"   // 1 = Raspberry Pi już pobrało polecenie
        };

        try (Statement statement = connection.createStatement()) {
            for (String sql : tables) {
                statement.execute(sql);
            }
        }
    }

    /** Aktualna data i godzina w formacie, który od razu ładnie wygląda w aplikacji: "2026-11-24 12:30". */
    public static String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    /** Zwraca pierwszy wiersz wyniku albo null, gdy nic nie znaleziono. */
    public JSONObject queryOne(String sql, Object... params) {
        JSONArray rows = queryList(sql, params);
        return rows.isEmpty() ? null : rows.getJSONObject(0);
    }

    /** Zwraca wszystkie wiersze wyniku jako tablicę JSON. */
    public JSONArray queryList(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet result = statement.executeQuery()) {

            ResultSetMetaData meta = result.getMetaData();
            JSONArray rows = new JSONArray();

            while (result.next()) {
                JSONObject row = new JSONObject();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    Object value = result.getObject(i);
                    // JSONObject pomija klucze z wartością null, więc wstawiamy specjalne JSONObject.NULL.
                    row.put(meta.getColumnLabel(i), value == null ? JSONObject.NULL : value);
                }
                rows.put(row);
            }
            return rows;

        } catch (SQLException e) {
            throw new RuntimeException("Błąd zapytania: " + sql, e);
        }
    }

    /** Wygodny skrót dla zapytań typu "SELECT COUNT(*) ..." – zwraca liczbę z pierwszej kolumny. */
    public int queryInt(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Błąd zapytania: " + sql, e);
        }
    }

    /** Wykonuje UPDATE albo DELETE. Zwraca liczbę zmienionych wierszy. */
    public int update(String sql, Object... params) {
        try (PreparedStatement statement = prepare(sql, params)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Błąd zapytania: " + sql, e);
        }
    }

    /** Wykonuje INSERT i zwraca id nowo dodanego wiersza. */
    public long insert(String sql, Object... params) {
        update(sql, params);
        // last_insert_rowid() to funkcja SQLite zwracająca id ostatnio dodanego wiersza w tym połączeniu.
        return queryInt("SELECT last_insert_rowid()");
    }

    /**
     * Wykonuje podany kod w jednej transakcji.
     * Jeżeli w środku poleci wyjątek – wszystkie zmiany zostają wycofane (rollback).
     *
     * <p>Przykład: kurier wkłada jedzenie do paczkomatu – zmieniamy status zamówienia, przypisujemy skrytkę,
     * dopisujemy historię i polecenie otwarcia. Gdyby coś się wysypało w połowie, baza zostałaby
     * w niespójnym stanie. Transakcja tego pilnuje.</p>
     */
    public <T> T inTransaction(Supplier<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Błąd transakcji", e);
        }
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }
}
