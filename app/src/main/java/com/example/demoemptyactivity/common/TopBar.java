package com.example.demoemptyactivity.common;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;

import com.example.demoemptyactivity.MainActivity;
import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;

import org.json.JSONObject;

/**
 * Obsługa górnego paska (view_top_bar.xml), wspólna dla panelu klienta i kuriera:
 * menu z profilem, pomocą, opisem aplikacji i wylogowaniem. Wszystkie okienka pokazuje maskotka.
 */
public final class TopBar {

    private static final int MENU_PROFILE = 1;
    private static final int MENU_HELP = 2;
    private static final int MENU_ABOUT = 3;
    private static final int MENU_LOGOUT = 4;

    /** Przykładowy numer infolinii (projekt szkolny – numer nie jest prawdziwy). */
    private static final String HELP_PHONE = "800123456";

    private TopBar() {
    }

    /** Podpina przycisk menu (☰) w lewym górnym rogu. */
    public static void setup(AppCompatActivity activity) {
        activity.findViewById(R.id.btn_menu).setOnClickListener(v -> showMenu(activity, v));
    }

    private static void showMenu(AppCompatActivity activity, View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, MENU_PROFILE, 0, "Mój profil");
        menu.getMenu().add(0, MENU_HELP, 1, "Pomoc");
        menu.getMenu().add(0, MENU_ABOUT, 2, "Jak działa INPUT?");
        menu.getMenu().add(0, MENU_LOGOUT, 3, "Wyloguj się");

        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_PROFILE) {
                showProfile(activity);
            } else if (item.getItemId() == MENU_HELP) {
                showHelp(activity);
            } else if (item.getItemId() == MENU_ABOUT) {
                MainActivity.showAboutDialog(activity);
            } else if (item.getItemId() == MENU_LOGOUT) {
                logout(activity);
            }
            return true;
        });
        menu.show();
    }

    /** Okienko z danymi zalogowanego użytkownika. */
    public static void showProfile(AppCompatActivity activity) {
        JSONObject user = Session.getUser(activity);
        String role = Session.isCourier(activity) ? "Kurier" : "Klient";

        String message = "Imię i nazwisko: " + user.optString("full_name") + "\n"
                + "Login: " + user.optString("username") + "\n"
                + "Telefon: " + user.optString("phone") + "\n"
                + "Rola: " + role + "\n\n"
                + "Serwer: " + Session.getServerUrl(activity);

        Mascot.dialog(activity, R.drawable.maskotka_czesc, "Cześć, " + Session.getFirstName(activity) + "!", message)
                .setPositiveButton("Zamknij", null)
                .setNegativeButton("Wyloguj się", (dialog, which) -> logout(activity))
                .show();
    }

    /**
     * Pomoc: najczęstsze pytania (inne dla klienta i kuriera) i przycisk "Zadzwoń",
     * który otwiera telefon z wpisanym numerem infolinii (niczego nie wybiera sam).
     */
    public static void showHelp(AppCompatActivity activity) {
        String message;
        if (Session.isCourier(activity)) {
            message = "• Restauracja jeszcze nie wydała jedzenia? Poczekaj chwilę – czas przygotowania "
                    + "widać na zakładce \"Mapa\".\n\n"
                    + "• Skrytka się nie otworzyła? Sprawdź, czy paczkomat jest \"Online\", i spróbuj ponownie.\n\n"
                    + "• Paczkomat jest pełny? Zadzwoń na infolinię – wskażemy inny.";
        } else {
            message = "• Kiedy dostanę kod odbioru? Gdy kurier włoży jedzenie do skrytki – "
                    + "zobaczysz go w \"Moje zamówienia\".\n\n"
                    + "• Mogę anulować zamówienie? Tak, dopóki kurier nie odebrał jedzenia z restauracji.\n\n"
                    + "• Skrytka się nie otwiera? Spróbuj przyciskiem w szczegółach zamówienia albo zadzwoń.";
        }
        Mascot.dialog(activity, R.drawable.maskotka_pomoc, "W czym mogę pomóc?",
                        message + "\n\nInfolinia: 800 123 456 (numer przykładowy)")
                .setPositiveButton("Zadzwoń", (dialog, which) -> activity.startActivity(
                        new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + HELP_PHONE))))
                .setNegativeButton("Zamknij", null)
                .show();
    }

    /** Wylogowanie: informujemy serwer (usuwa token), czyścimy dane w telefonie, wracamy na start. */
    public static void logout(AppCompatActivity activity) {
        ApiClient.post(activity, "/api/auth/logout", null, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                // Nic więcej nie trzeba robić.
            }

            @Override
            public void onError(String message) {
                // Nawet jeśli serwer nie odpowiada, w telefonie i tak się wylogowujemy.
            }
        });

        Session.clear(activity);
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
