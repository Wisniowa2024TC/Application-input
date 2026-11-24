package com.example.demoemptyactivity.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AlertDialog;

import com.example.demoemptyactivity.R;

/**
 * Maskotka INPUT – kotka, która "rozmawia" z użytkownikiem w całej aplikacji.
 *
 * <p>Obrazki leżą w {@code res/drawable-nodpi/maskotka_*.png}. Każda poza ma swoje znaczenie:</p>
 * <ul>
 *     <li>{@code maskotka_czesc} – powitanie (logowanie, rejestracja, "Jak działa INPUT?"),</li>
 *     <li>{@code maskotka_czekaj} – ładowanie i "restauracja przygotowuje jedzenie",</li>
 *     <li>{@code maskotka_blad} – błąd (np. brak połączenia z serwerem),</li>
 *     <li>{@code maskotka_sukces} – zamówienie złożone,</li>
 *     <li>{@code maskotka_dostawa} – kurier w drodze,</li>
 *     <li>{@code maskotka_pomoc} – pomoc / kontakt,</li>
 *     <li>{@code maskotka_dziekujemy} – jedzenie odebrane,</li>
 *     <li>{@code maskotka_powiadomienie} – jedzenie czeka w paczkomacie,</li>
 *     <li>{@code maskotka_szukam} – pusta lista albo brak wyników wyszukiwania.</li>
 * </ul>
 * <p>Żeby podmienić obrazek, wystarczy wrzucić nowy plik PNG o tej samej nazwie – kodu nie trzeba zmieniać.</p>
 *
 * <p>Dwa sposoby użycia:</p>
 * <ol>
 *     <li>{@link #show} / {@link #hide} – "stan ekranu" (layout view_mascot_state.xml dołączony przez
 *         {@code <include>}): ładowanie, pusta lista, błąd z przyciskiem "Spróbuj ponownie",</li>
 *     <li>{@link #dialog} – okienko z maskotką (np. "Zamówienie przyjęte!").</li>
 * </ol>
 */
public final class Mascot {

    private Mascot() {
    }

    /**
     * Pokazuje maskotkę z tytułem i opisem w miejscu listy.
     *
     * @param state widok z layoutu view_mascot_state.xml
     */
    public static void show(View state, @DrawableRes int image, String title, String message) {
        state.setVisibility(View.VISIBLE);
        ((ImageView) state.findViewById(R.id.iv_mascot)).setImageResource(image);
        ((TextView) state.findViewById(R.id.tv_mascot_title)).setText(title);

        TextView messageText = state.findViewById(R.id.tv_mascot_message);
        messageText.setText(message);
        messageText.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);

        state.findViewById(R.id.btn_mascot_action).setVisibility(View.GONE);
    }

    /** Jak {@link #show}, ale z przyciskiem pod spodem (np. "Spróbuj ponownie"). */
    public static void show(View state, @DrawableRes int image, String title, String message,
                            String buttonText, Runnable onClick) {
        show(state, image, title, message);
        Button button = state.findViewById(R.id.btn_mascot_action);
        button.setVisibility(View.VISIBLE);
        button.setText(buttonText);
        button.setOnClickListener(v -> onClick.run());
    }

    /** Stan "ładowanie". */
    public static void showLoading(View state) {
        show(state, R.drawable.maskotka_czekaj, "Chwileczkę…", "Pobieram dane z serwera");
    }

    /** Stan "błąd" z przyciskiem ponowienia. */
    public static void showError(View state, String message, Runnable retry) {
        show(state, R.drawable.maskotka_blad, "Ups, coś poszło nie tak", message, "Spróbuj ponownie", retry);
    }

    /** Ukrywa maskotkę (gdy dane są już na ekranie). */
    public static void hide(View state) {
        state.setVisibility(View.GONE);
    }

    /**
     * Okienko z maskotką. Zwracamy Builder, żeby można było dodać własne przyciski:
     * <pre>
     *     Mascot.dialog(this, R.drawable.maskotka_sukces, "Gotowe!", "...")
     *           .setPositiveButton("OK", null)
     *           .show();
     * </pre>
     */
    public static AlertDialog.Builder dialog(Context context, @DrawableRes int image, String title, String message) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_mascot, null);
        ((ImageView) view.findViewById(R.id.iv_mascot)).setImageResource(image);
        ((TextView) view.findViewById(R.id.tv_mascot_title)).setText(title);
        ((TextView) view.findViewById(R.id.tv_mascot_message)).setText(message);
        return new AlertDialog.Builder(context).setView(view);
    }
}
