package com.example.demoemptyactivity.common;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.example.demoemptyactivity.common.OrderViews.text;

/**
 * Szczegóły jednego zamówienia – wspólne dla klienta i kuriera.
 *
 * <p>Serwer w odpowiedzi mówi, co zalogowana osoba może teraz zrobić
 * (pola {@code can_pick_up}, {@code can_collect}, {@code can_deliver}, {@code can_cancel}).
 * Na tej podstawie pokazujemy najwyżej jeden przycisk głównej akcji:</p>
 * <ul>
 *     <li>klient: "Otwórz skrytkę i odbierz jedzenie" (gdy jedzenie jest w paczkomacie),</li>
 *     <li>kurier: "Odebrałem jedzenie z restauracji", a potem "Włóż do paczkomatu".</li>
 * </ul>
 * <p>Maskotka na górze opisuje zwykłymi słowami, co się teraz dzieje z zamówieniem.</p>
 */
public class OrderDetailsActivity extends AppCompatActivity {

    /** Klucz, pod którym przekazujemy numer zamówienia (long) w Intencie. */
    public static final String EXTRA_ORDER_ID = "order_id";

    /** Kolejne kroki dostawy – w tej samej kolejności co statusy na serwerze. */
    private static final String[] STEP_STATUSES = {"PLACED", "IN_DELIVERY", "READY_FOR_PICKUP", "PICKED_UP"};
    private static final String[] STEP_LABELS = {"Przyjęte", "W drodze", "Do odbioru", "Odebrane"};

    private long orderId;
    private boolean courierView;

    private View mascotState;
    private View content;
    private TextView numberText;
    private TextView badgeText;
    private TextView routeText;
    private TextView summaryText;
    private ImageView statusMascot;
    private TextView statusTitle;
    private TextView statusText;
    private LinearLayout stepsContainer;
    private View codeBox;
    private TextView codeText;
    private TextView codeHint;
    private MaterialButton actionButton;
    private Button cancelButton;
    private Button mapButton;
    private LinearLayout itemsContainer;
    private TextView itemsTotalText;
    private TextView deliveryFeeText;
    private TextView totalText;
    private LinearLayout infoContainer;
    private LinearLayout historyContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_details);

        orderId = getIntent().getLongExtra(EXTRA_ORDER_ID, -1);
        courierView = Session.isCourier(this);

        mascotState = findViewById(R.id.mascot_state);
        content = findViewById(R.id.content);
        numberText = findViewById(R.id.tv_number);
        badgeText = findViewById(R.id.tv_badge);
        routeText = findViewById(R.id.tv_route);
        summaryText = findViewById(R.id.tv_summary);
        statusMascot = findViewById(R.id.iv_status_mascot);
        statusTitle = findViewById(R.id.tv_status_title);
        statusText = findViewById(R.id.tv_status_text);
        stepsContainer = findViewById(R.id.steps_container);
        codeBox = findViewById(R.id.code_box);
        codeText = findViewById(R.id.tv_code);
        codeHint = findViewById(R.id.tv_code_hint);
        actionButton = findViewById(R.id.btn_action);
        cancelButton = findViewById(R.id.btn_cancel);
        mapButton = findViewById(R.id.btn_map);
        itemsContainer = findViewById(R.id.items_container);
        itemsTotalText = findViewById(R.id.tv_items_total);
        deliveryFeeText = findViewById(R.id.tv_delivery_fee);
        totalText = findViewById(R.id.tv_total);
        infoContainer = findViewById(R.id.info_container);
        historyContainer = findViewById(R.id.history_container);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> loadDetails());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Odświeżamy po każdym powrocie – np. kurier mógł w tym czasie zmienić status.
        loadDetails();
    }

    /** Pobiera zamówienie, dania i historię: GET /api/orders/{id}. */
    private void loadDetails() {
        if (content.getVisibility() != View.VISIBLE) {
            Mascot.showLoading(mascotState);
        }
        ApiClient.get(this, "/api/orders/" + orderId, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (isDestroyed()) {
                    return; // użytkownik zdążył zamknąć ekran
                }
                Mascot.hide(mascotState);
                content.setVisibility(View.VISIBLE);
                showOrder(response.optJSONObject("order"), response.optJSONArray("items"),
                        response.optJSONArray("history"));
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) {
                    return;
                }
                if (content.getVisibility() == View.VISIBLE) {
                    // Dane już widać – wystarczy krótka informacja.
                    Toast.makeText(OrderDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                } else {
                    Mascot.showError(mascotState, message, () -> loadDetails());
                }
            }
        });
    }

    /** Wypełnia cały ekran danymi zamówienia. */
    private void showOrder(JSONObject order, JSONArray items, JSONArray history) {
        String status = text(order, "status");
        String restaurant = text(order, "restaurant_name");
        String locker = text(order, "locker_name");

        // --- Nagłówek ---
        numberText.setText(text(order, "number"));
        OrderViews.styleStatusBadge(badgeText, status,
                OrderViews.statusLabel(status, text(order, "status_label"), courierView));
        routeText.setText(text(order, "restaurant_emoji") + " " + restaurant + " → " + locker);
        summaryText.setText(order.optInt("items_count") + " szt. • " + text(order, "total_text"));

        showStatusMascot(order, status);
        showSteps(status);

        // --- Kod odbioru (serwer wysyła go tylko właścicielowi, gdy jedzenie jest w skrytce) ---
        String pickupCode = text(order, "pickup_code");
        if (!pickupCode.isEmpty()) {
            codeBox.setVisibility(View.VISIBLE);
            codeText.setText(pickupCode);
            codeHint.setText(locker + ", skrytka nr " + order.optInt("compartment_number")
                    + ".\nWpisz ten kod na klawiaturze paczkomatu albo otwórz skrytkę przyciskiem poniżej.");
        } else {
            codeBox.setVisibility(View.GONE);
        }

        // --- Przycisk głównej akcji (najwyżej jeden naraz) ---
        actionButton.setVisibility(View.GONE);
        if (order.optBoolean("can_pick_up")) {
            setupAction("Otwórz skrytkę i odbierz jedzenie", R.drawable.ic_lock_open,
                    "/api/orders/" + orderId + "/open",
                    "Czy jesteś teraz przy paczkomacie?\n" + locker + "\n\nSkrytka otworzy się od razu.",
                    R.drawable.maskotka_dziekujemy, "Smacznego!");
        } else if (order.optBoolean("can_collect")) {
            setupAction("Odebrałem jedzenie z restauracji", R.drawable.ic_bag,
                    "/api/courier/orders/" + orderId + "/collect",
                    "Czy masz już jedzenie z restauracji " + restaurant + "?",
                    R.drawable.maskotka_dostawa, "W drogę!");
        } else if (order.optBoolean("can_deliver")) {
            setupAction("Włóż do paczkomatu – otwórz skrytkę", R.drawable.ic_lock_open,
                    "/api/courier/orders/" + orderId + "/deliver",
                    "Czy jesteś teraz przy paczkomacie?\n" + locker + "\n\nWolna skrytka otworzy się od razu.",
                    R.drawable.maskotka_sukces, "Dostarczone!");
        }

        cancelButton.setVisibility(order.optBoolean("can_cancel") ? View.VISIBLE : View.GONE);
        cancelButton.setOnClickListener(v -> confirmCancel());

        // --- Mapa: kurier przed odbiorem jedzie do restauracji, w pozostałych przypadkach liczy się paczkomat ---
        boolean toRestaurant = courierView && status.equals("PLACED");
        String mapName = toRestaurant ? restaurant : locker;
        double latitude = order.optDouble(toRestaurant ? "restaurant_latitude" : "locker_latitude");
        double longitude = order.optDouble(toRestaurant ? "restaurant_longitude" : "locker_longitude");
        mapButton.setText("Pokaż " + mapName + " na mapie");
        mapButton.setOnClickListener(v -> OrderViews.openMap(this, latitude, longitude, mapName));

        // --- Dania i kwoty ---
        itemsContainer.removeAllViews();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                View row = getLayoutInflater().inflate(R.layout.item_order_line, itemsContainer, false);
                ((TextView) row.findViewById(R.id.tv_emoji)).setText(text(item, "emoji"));
                ((TextView) row.findViewById(R.id.tv_name)).setText(item.optInt("quantity") + "× " + text(item, "name"));
                ((TextView) row.findViewById(R.id.tv_price)).setText(text(item, "line_total_text"));
                itemsContainer.addView(row);
            }
        }
        itemsTotalText.setText(text(order, "items_total_text"));
        deliveryFeeText.setText(text(order, "delivery_fee_text"));
        totalText.setText(text(order, "total_text"));

        // --- Informacje ---
        infoContainer.removeAllViews();
        addInfoRow("Restauracja", restaurant + ", " + text(order, "restaurant_address"));
        addInfoRow("Paczkomat", locker + ", " + text(order, "locker_address"));
        if (!order.isNull("compartment_number")) {
            addInfoRow("Skrytka", "nr " + order.optInt("compartment_number"));
        }
        if (courierView) {
            addInfoRow("Klient", text(order, "client_name") + ", tel. " + text(order, "client_phone"));
        }
        addInfoRow("Kurier", text(order, "courier_name"));
        if (status.equals("PLACED") || status.equals("IN_DELIVERY")) {
            addInfoRow("Planowana dostawa", "ok. " + text(order, "planned_time"));
        }
        addInfoRow("Złożone", text(order, "created_at"));

        // --- Historia ---
        historyContainer.removeAllViews();
        if (history != null) {
            for (int i = 0; i < history.length(); i++) {
                JSONObject event = history.optJSONObject(i);
                View row = getLayoutInflater().inflate(R.layout.item_history, historyContainer, false);
                ((TextView) row.findViewById(R.id.tv_status)).setText(text(event, "status_label"));
                ((TextView) row.findViewById(R.id.tv_description)).setText(text(event, "description"));
                ((TextView) row.findViewById(R.id.tv_date)).setText(text(event, "created_at"));
                // Najnowszy wpis ma różową kropkę, starsze – przygaszoną.
                if (i > 0) {
                    row.findViewById(R.id.dot).setAlpha(0.3f);
                }
                historyContainer.addView(row);
            }
        }
    }

    /** Maskotka w ramce: inna poza i inny tekst dla każdego etapu (osobno dla klienta i kuriera). */
    private void showStatusMascot(JSONObject order, String status) {
        String restaurant = text(order, "restaurant_name");
        String locker = text(order, "locker_name");
        String planned = text(order, "planned_time");
        String courier = text(order, "courier_name");
        int compartment = order.optInt("compartment_number");

        int image;
        String title;
        String message;
        switch (status) {
            case "PLACED":
                image = R.drawable.maskotka_czekaj;
                if (courierView) {
                    title = "Czeka na odbiór z restauracji";
                    message = "Odbierz jedzenie z " + restaurant + " (" + text(order, "restaurant_address")
                            + ") i zawieź je do: " + locker + ".";
                } else {
                    title = "Restauracja przygotowuje jedzenie";
                    message = restaurant + " szykuje Twoje zamówienie. Planowana dostawa do: "
                            + locker + " ok. " + planned + ".";
                }
                break;
            case "IN_DELIVERY":
                image = R.drawable.maskotka_dostawa;
                if (courierView) {
                    title = order.optBoolean("can_deliver") ? "Jedzenie jest w Twoim aucie" : "W drodze";
                    message = "Zawieź je do: " + locker + " (" + text(order, "locker_address")
                            + "), planowo ok. " + planned + ".";
                } else {
                    title = "Kurier jest w drodze";
                    message = (courier.isEmpty() ? "Kurier" : courier) + " wiezie jedzenie do: "
                            + locker + ". Będzie ok. " + planned + ".";
                }
                break;
            case "READY_FOR_PICKUP":
                image = R.drawable.maskotka_powiadomienie;
                if (courierView) {
                    title = "Dostarczone";
                    message = "Jedzenie czeka w: " + locker + ", skrytka nr " + compartment
                            + ". Klient dostał kod odbioru.";
                } else {
                    title = "Jedzenie czeka na Ciebie!";
                    message = locker + ", skrytka nr " + compartment + ". Odbierz je, póki jest ciepłe.";
                }
                break;
            case "PICKED_UP":
                image = R.drawable.maskotka_dziekujemy;
                title = courierView ? "Klient odebrał jedzenie" : "Smacznego!";
                message = courierView ? "Dziękujemy za dostawę." : "Dziękujemy za zamówienie. Zapraszamy ponownie!";
                break;
            default: // CANCELLED
                image = R.drawable.maskotka_blad;
                title = "Zamówienie anulowane";
                message = courierView
                        ? "Klient zrezygnował, zanim jedzenie zostało odebrane z restauracji."
                        : "To zamówienie zostało anulowane. Możesz złożyć nowe na stronie głównej.";
                break;
        }
        statusMascot.setImageResource(image);
        statusTitle.setText(title);
        statusText.setText(message);
    }

    /**
     * Rysuje 4 kroki dostawy. Zrobione kroki mają granatowe kółko z ✓,
     * obecny – różowe z numerem, przyszłe – szare. Anulowanego zamówienia nie rysujemy.
     */
    private void showSteps(String status) {
        stepsContainer.removeAllViews();
        int current = -1;
        for (int i = 0; i < STEP_STATUSES.length; i++) {
            if (STEP_STATUSES[i].equals(status)) {
                current = i;
            }
        }
        if (current < 0) {
            stepsContainer.setVisibility(View.GONE);
            return;
        }
        stepsContainer.setVisibility(View.VISIBLE);

        // Ostatni krok ("Odebrane") to koniec – wtedy wszystkie kroki są zrobione.
        boolean finished = current == STEP_STATUSES.length - 1;
        for (int i = 0; i < STEP_STATUSES.length; i++) {
            View step = getLayoutInflater().inflate(R.layout.item_order_step, stepsContainer, false);
            TextView dot = step.findViewById(R.id.tv_dot);
            TextView label = step.findViewById(R.id.tv_label);
            label.setText(STEP_LABELS[i]);

            int dotColor;
            int dotTextColor;
            int labelColor;
            if (i < current || finished) {
                dot.setText("✓");
                dotColor = R.color.navy;
                dotTextColor = R.color.white;
                labelColor = R.color.navy_dark;
            } else if (i == current) {
                dot.setText(String.valueOf(i + 1));
                dotColor = R.color.pink;
                dotTextColor = R.color.white;
                labelColor = R.color.navy_dark;
                label.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                dot.setText(String.valueOf(i + 1));
                dotColor = R.color.blue_light;
                dotTextColor = R.color.text_hint;
                labelColor = R.color.text_hint;
            }
            dot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, dotColor)));
            dot.setTextColor(ContextCompat.getColor(this, dotTextColor));
            label.setTextColor(ContextCompat.getColor(this, labelColor));
            stepsContainer.addView(step);
        }
    }

    /** Dodaje wiersz "etykieta / wartość". Puste wartości pomijamy. */
    private void addInfoRow(String label, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        View row = getLayoutInflater().inflate(R.layout.item_info_row, infoContainer, false);
        ((TextView) row.findViewById(R.id.tv_label)).setText(label);
        ((TextView) row.findViewById(R.id.tv_value)).setText(value);
        infoContainer.addView(row);
    }

    /**
     * Pokazuje przycisk akcji. Przed wykonaniem pytamy o potwierdzenie
     * – skrytka otwiera się od razu, więc nie chcemy otworzyć jej przez przypadek.
     */
    private void setupAction(String label, @DrawableRes int icon, String path, String question,
                             @DrawableRes int successImage, String successTitle) {
        actionButton.setVisibility(View.VISIBLE);
        actionButton.setText(label);
        actionButton.setIconResource(icon);
        actionButton.setOnClickListener(v -> Mascot.dialog(this, R.drawable.maskotka_pomoc, label, question)
                .setPositiveButton("Tak", (dialog, which) -> runAction(path, successImage, successTitle))
                .setNegativeButton("Nie", null)
                .show());
    }

    /** Wysyła akcję do serwera i pokazuje maskotkę z komunikatem (np. "Skrytka nr 3 otwiera się"). */
    private void runAction(String path, @DrawableRes int successImage, String successTitle) {
        actionButton.setEnabled(false);
        ApiClient.post(this, path, null, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (isDestroyed()) {
                    return;
                }
                actionButton.setEnabled(true);
                Mascot.dialog(OrderDetailsActivity.this, successImage, successTitle, response.optString("message"))
                        .setPositiveButton("OK", null)
                        .show();
                loadDetails();
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) {
                    return;
                }
                actionButton.setEnabled(true);
                Mascot.dialog(OrderDetailsActivity.this, R.drawable.maskotka_blad, "Nie udało się", message)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    /** Anulowanie – możliwe tylko, dopóki kurier nie odebrał jedzenia z restauracji. */
    private void confirmCancel() {
        Mascot.dialog(this, R.drawable.maskotka_blad, "Anulować zamówienie?",
                        "Kurier jeszcze nie odebrał jedzenia, więc możesz zrezygnować. "
                                + "Pieniądze wrócą na konto (płatność w projekcie jest symulowana).")
                .setPositiveButton("Anuluj zamówienie", (dialog, which) ->
                        ApiClient.post(this, "/api/orders/" + orderId + "/cancel", null, new ApiClient.Callback() {
                            @Override
                            public void onSuccess(JSONObject response) {
                                if (isDestroyed()) {
                                    return;
                                }
                                Toast.makeText(OrderDetailsActivity.this,
                                        response.optString("message"), Toast.LENGTH_SHORT).show();
                                loadDetails();
                            }

                            @Override
                            public void onError(String message) {
                                if (isDestroyed()) {
                                    return;
                                }
                                Toast.makeText(OrderDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Wróć", null)
                .show();
    }
}
