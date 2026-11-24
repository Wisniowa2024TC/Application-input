package com.example.demoemptyactivity.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;
import com.example.demoemptyactivity.common.Mascot;
import com.example.demoemptyactivity.common.OrderDetailsActivity;
import com.example.demoemptyactivity.common.OrderViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static com.example.demoemptyactivity.common.OrderViews.text;

/**
 * Koszyk i złożenie zamówienia.
 *
 * <ol>
 *     <li>Pokazujemy dania z {@link Cart} – można zmienić ilość przyciskami − / +.</li>
 *     <li>Pobieramy paczkomaty ({@code GET /api/lockers}) – klient wybiera, gdzie odbierze jedzenie.</li>
 *     <li>"Zamawiam i płacę" wysyła {@code POST /api/orders}. Serwer sam liczy cenę i zwraca numer zamówienia.</li>
 * </ol>
 */
public class CartActivity extends AppCompatActivity {

    /** Opłata za dostawę – taka sama jak na serwerze (OrderService.DELIVERY_FEE). */
    private static final int DELIVERY_FEE = 499;

    private final List<JSONObject> lockers = new ArrayList<>();

    private View mascotState;
    private View content;
    private TextView restaurantText;
    private LinearLayout linesContainer;
    private Spinner lockerSpinner;
    private TextView itemsTotalText;
    private TextView deliveryFeeText;
    private TextView totalText;
    private Button orderButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        mascotState = findViewById(R.id.mascot_state);
        content = findViewById(R.id.content);
        restaurantText = findViewById(R.id.tv_restaurant);
        linesContainer = findViewById(R.id.lines_container);
        lockerSpinner = findViewById(R.id.spinner_locker);
        itemsTotalText = findViewById(R.id.tv_items_total);
        deliveryFeeText = findViewById(R.id.tv_delivery_fee);
        totalText = findViewById(R.id.tv_total);
        orderButton = findViewById(R.id.btn_order);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        orderButton.setOnClickListener(v -> placeOrder());

        showCart();
        loadLockers();
    }

    /** Rysuje pozycje koszyka i kwoty. Wywoływana po każdej zmianie ilości. */
    private void showCart() {
        if (Cart.isEmpty()) {
            content.setVisibility(View.GONE);
            orderButton.setVisibility(View.GONE);
            Mascot.show(mascotState, R.drawable.maskotka_szukam, "Koszyk jest pusty",
                    "Dodaj coś pysznego z menu.", "Wróć do menu", this::finish);
            return;
        }

        restaurantText.setText("Z restauracji: " + Cart.restaurantName());
        linesContainer.removeAllViews();
        for (Cart.Line line : Cart.lines()) {
            View row = getLayoutInflater().inflate(R.layout.item_cart_line, linesContainer, false);
            ((TextView) row.findViewById(R.id.tv_emoji)).setText(line.emoji);
            ((TextView) row.findViewById(R.id.tv_name)).setText(line.name);
            ((TextView) row.findViewById(R.id.tv_price)).setText(OrderViews.money(line.price * line.quantity));
            ((TextView) row.findViewById(R.id.tv_quantity)).setText(String.valueOf(line.quantity));
            row.findViewById(R.id.btn_minus).setOnClickListener(v -> changeQuantity(line.productId, -1));
            row.findViewById(R.id.btn_plus).setOnClickListener(v -> changeQuantity(line.productId, +1));
            linesContainer.addView(row);
        }

        int itemsTotal = Cart.itemsTotal();
        itemsTotalText.setText(OrderViews.money(itemsTotal));
        deliveryFeeText.setText(OrderViews.money(DELIVERY_FEE));
        totalText.setText(OrderViews.money(itemsTotal + DELIVERY_FEE));
        orderButton.setText("Zamawiam i płacę " + OrderViews.money(itemsTotal + DELIVERY_FEE));
    }

    private void changeQuantity(long productId, int delta) {
        Cart.change(productId, delta);
        showCart();
    }

    /** Pobiera paczkomaty do listy wyboru. Na górze podpowiadamy ten z ostatniego zamówienia. */
    private void loadLockers() {
        ApiClient.get(this, "/api/lockers", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (isDestroyed()) {
                    return;
                }
                JSONArray array = response.optJSONArray("lockers");
                if (array == null) {
                    return;
                }
                lockers.clear();
                List<String> names = new ArrayList<>();
                int lastUsed = 0;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject locker = array.optJSONObject(i);
                    lockers.add(locker);
                    names.add(text(locker, "name") + " – " + text(locker, "address")
                            + " (wolne: " + locker.optInt("free_compartments") + ")");
                    if (locker.optLong("id") == Session.getLastLockerId(CartActivity.this)) {
                        lastUsed = i;
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(CartActivity.this,
                        android.R.layout.simple_spinner_item, names);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                lockerSpinner.setAdapter(adapter);
                lockerSpinner.setSelection(lastUsed);
                orderButton.setEnabled(true);
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) {
                    return;
                }
                Toast.makeText(CartActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Wysyła zamówienie na serwer. */
    private void placeOrder() {
        int position = lockerSpinner.getSelectedItemPosition();
        if (position < 0 || position >= lockers.size() || Cart.isEmpty()) {
            return;
        }
        long lockerId = lockers.get(position).optLong("id");

        JSONObject body = new JSONObject();
        try {
            body.put("locker_id", lockerId);
            body.put("items", Cart.toJson());
        } catch (JSONException e) {
            return;
        }

        orderButton.setEnabled(false);
        ApiClient.post(this, "/api/orders", body, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (isDestroyed()) {
                    return;
                }
                Cart.clear();
                Session.setLastLockerId(CartActivity.this, lockerId);
                showSuccess(response.optJSONObject("order"), response.optString("message"));
            }

            @Override
            public void onError(String message) {
                if (isDestroyed()) {
                    return;
                }
                orderButton.setEnabled(true);
                Toast.makeText(CartActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Okienko z radosną maskotką i przejście do szczegółów nowego zamówienia. */
    private void showSuccess(JSONObject order, String message) {
        long orderId = order.optLong("id");
        Mascot.dialog(this, R.drawable.maskotka_sukces, "Zamówienie przyjęte!",
                        message + "\n\nKod odbioru pojawi się, gdy kurier włoży jedzenie do skrytki.")
                .setCancelable(false)
                .setPositiveButton("Śledź zamówienie", (dialog, which) -> {
                    // Budujemy stos ekranów: panel klienta (zakładka "Moje zamówienia") → szczegóły.
                    // Przycisk "wstecz" ze szczegółów wróci wtedy do listy zamówień, a nie do koszyka.
                    Intent home = new Intent(this, ClientActivity.class);
                    home.putExtra(ClientActivity.EXTRA_OPEN_ORDERS, true);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    Intent details = new Intent(this, OrderDetailsActivity.class);
                    details.putExtra(OrderDetailsActivity.EXTRA_ORDER_ID, orderId);
                    startActivities(new Intent[]{home, details});
                    finish();
                })
                .show();
    }
}
