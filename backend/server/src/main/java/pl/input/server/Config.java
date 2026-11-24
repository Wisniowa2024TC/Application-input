package pl.input.server;

/**
 * Ustawienia serwera INPUT zebrane w jednym miejscu.
 *
 * <p>Każdą wartość można nadpisać zmienną środowiskową (np. {@code INPUT_PORT=9000}),
 * ale do zajęć wystarczą wartości domyślne.</p>
 */
public final class Config {

    /** Port HTTP, na którym serwer nasłuchuje (aplikacja łączy się na http://ADRES:8080). */
    public static final int PORT = readInt("INPUT_PORT", 8080);

    /** Plik bazy SQLite. Tworzy się sam przy pierwszym uruchomieniu – żeby wyczyścić dane, wystarczy go usunąć. */
    public static final String DATABASE_FILE = readString("INPUT_DB", "input.db");

    /**
     * Wspólny klucz, którym paczkomaty (Raspberry Pi) przedstawiają się serwerowi.
     * Dzięki temu zwykły użytkownik nie może udawać paczkomatu i otwierać skrytek.
     * Ta sama wartość musi być wpisana w programie paczkomatu.
     */
    public static final String LOCKER_API_KEY = readString("INPUT_LOCKER_KEY", "paczkomat-tajny-klucz");

    /**
     * Kod firmowy wymagany przy rejestracji konta kuriera.
     * Bez niego każdy mógłby założyć konto kuriera i wyjmować cudze zamówienia.
     */
    public static final String COURIER_SIGNUP_CODE = readString("INPUT_COURIER_CODE", "KURIER2026");

    /** Po ilu sekundach bez sygnału od Raspberry Pi uznajemy paczkomat za offline. */
    public static final int LOCKER_OFFLINE_AFTER_SECONDS = 30;

    private Config() {
        // Klasa tylko ze stałymi – nie tworzymy jej obiektów.
    }

    private static String readString(String envName, String defaultValue) {
        String value = System.getenv(envName);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static int readInt(String envName, int defaultValue) {
        try {
            return Integer.parseInt(readString(envName, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
