package com.example.demoemptyactivity.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pamięć aplikacji o zalogowanym użytkowniku (zapisywana w SharedPreferences telefonu).
 *
 * <p>Przechowujemy:</p>
 * <ul>
 *     <li>token – "przepustkę" z serwera, dołączaną do każdego żądania,</li>
 *     <li>dane użytkownika – imię, login, telefon i rolę (CLIENT albo COURIER),</li>
 *     <li>adres serwera – żeby dało się go zmienić bez ponownej kompilacji aplikacji.</li>
 * </ul>
 * <p>Dzięki temu po zamknięciu i ponownym otwarciu aplikacji nie trzeba się logować od nowa.</p>
 */
public final class Session {

    /**
     * Domyślny adres serwera. 10.0.2.2 to w emulatorze Androida specjalny adres
     * oznaczający "komputer, na którym działa emulator".
     */
    public static final String DEFAULT_SERVER_URL = "http://10.0.2.2:8080";

    private static final String PREFS_NAME = "input_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER = "user";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LAST_LOCKER = "last_locker_id";

    private Session() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Zapisuje token i dane użytkownika po udanym logowaniu lub rejestracji. */
    public static void save(Context context, String token, JSONObject user) {
        prefs(context).edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER, user.toString())
                .apply();
    }

    /** Usuwa dane logowania (wylogowanie). Adres serwera zostaje. */
    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER)
                .apply();
    }

    /** Token z serwera albo null, jeżeli nikt nie jest zalogowany. */
    public static String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, null);
    }

    public static boolean isLoggedIn(Context context) {
        return getToken(context) != null;
    }

    /** Dane zalogowanego użytkownika: id, username, full_name, phone, role. */
    public static JSONObject getUser(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_USER, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** Czy zalogowany jest kurier (a nie klient). */
    public static boolean isCourier(Context context) {
        return "COURIER".equals(getUser(context).optString("role"));
    }

    /** Samo imię – do powitania "Cześć, Jakub!". */
    public static String getFirstName(Context context) {
        String fullName = getUser(context).optString("full_name", "");
        return fullName.contains(" ") ? fullName.substring(0, fullName.indexOf(' ')) : fullName;
    }

    public static String getServerUrl(Context context) {
        return prefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    /** Zmienia adres serwera (usuwa ewentualny "/" na końcu). */
    public static void setServerUrl(Context context, String url) {
        String clean = url.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        prefs(context).edit().putString(KEY_SERVER_URL, clean).apply();
    }

    /** Paczkomat wybrany przy ostatnim zamówieniu – w koszyku podpowiadamy go jako pierwszy. */
    public static long getLastLockerId(Context context) {
        return prefs(context).getLong(KEY_LAST_LOCKER, -1);
    }

    public static void setLastLockerId(Context context, long lockerId) {
        prefs(context).edit().putLong(KEY_LAST_LOCKER, lockerId).apply();
    }
}
