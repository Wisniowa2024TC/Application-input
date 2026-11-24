package com.example.demoemptyactivity.client;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.common.Mascot;
import com.example.demoemptyactivity.common.OrderViews;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.example.demoemptyactivity.common.OrderViews.text;

/**
 * Zakładka "Moje zamówienia".
 *
 * <p>Pobiera zamówienia klienta ({@code GET /api/orders}) i pokazuje je jako karty.
 * Filtry na górze działają w telefonie (bez ponownego pytania serwera):</p>
 * <ul>
 *     <li>Do odbioru – jedzenie czeka w paczkomacie (z kodem odbioru),</li>
 *     <li>W realizacji – restauracja przygotowuje albo kurier jest w drodze,</li>
 *     <li>Historia – odebrane i anulowane.</li>
 * </ul>
 */
public class OrdersFragment extends Fragment {

    private ChipGroup chipGroup;
    private Chip chipAll;
    private Chip chipPickup;
    private Chip chipActive;
    private Chip chipHistory;
    private View mascotState;
    private LinearLayout ordersContainer;

    /** Ostatnio pobrane zamówienia (null = jeszcze nie pobrano). */
    private JSONArray orders;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_orders, parent, false);

        chipGroup = view.findViewById(R.id.chip_group);
        chipAll = view.findViewById(R.id.chip_all);
        chipPickup = view.findViewById(R.id.chip_pickup);
        chipActive = view.findViewById(R.id.chip_active);
        chipHistory = view.findViewById(R.id.chip_history);
        mascotState = view.findViewById(R.id.mascot_state);
        ordersContainer = view.findViewById(R.id.orders_container);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> showOrders());
        view.findViewById(R.id.tv_refresh).setOnClickListener(v -> loadOrders());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Odświeżamy przy każdym powrocie na ekran – np. po odebraniu jedzenia w szczegółach.
        loadOrders();
    }

    private void loadOrders() {
        if (orders == null) {
            ordersContainer.removeAllViews();
            Mascot.showLoading(mascotState);
        }
        ApiClient.get(requireContext(), "/api/orders", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) {
                    return;
                }
                orders = response.optJSONArray("orders");
                updateChipCounts();
                showOrders();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                ordersContainer.removeAllViews();
                Mascot.showError(mascotState, message, () -> loadOrders());
            }
        });
    }

    /** Dopisuje liczby do filtrów, np. "Do odbioru (1)". */
    private void updateChipCounts() {
        int pickup = 0;
        int active = 0;
        int history = 0;
        for (int i = 0; i < orders.length(); i++) {
            String group = groupOf(orders.optJSONObject(i));
            if (group.equals("pickup")) {
                pickup++;
            } else if (group.equals("active")) {
                active++;
            } else {
                history++;
            }
        }
        chipAll.setText("Wszystkie (" + orders.length() + ")");
        chipPickup.setText("Do odbioru (" + pickup + ")");
        chipActive.setText("W realizacji (" + active + ")");
        chipHistory.setText("Historia (" + history + ")");
    }

    /** Do której grupy filtrów należy zamówienie: pickup, active albo history. */
    private static String groupOf(JSONObject order) {
        String status = text(order, "status");
        if (status.equals("READY_FOR_PICKUP")) {
            return "pickup";
        }
        if (status.equals("PLACED") || status.equals("IN_DELIVERY")) {
            return "active";
        }
        return "history";
    }

    /** Pokazuje zamówienia pasujące do zaznaczonego filtra. */
    private void showOrders() {
        if (orders == null) {
            return;
        }
        int checked = chipGroup.getCheckedChipId();
        String filter = null;   // null = wszystkie
        if (checked == R.id.chip_pickup) {
            filter = "pickup";
        } else if (checked == R.id.chip_active) {
            filter = "active";
        } else if (checked == R.id.chip_history) {
            filter = "history";
        }

        ordersContainer.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (filter == null || filter.equals(groupOf(order))) {
                ordersContainer.addView(OrderViews.createOrderCard(inflater, ordersContainer, order, false));
            }
        }

        if (ordersContainer.getChildCount() > 0) {
            Mascot.hide(mascotState);
        } else if (orders.length() == 0) {
            Mascot.show(mascotState, R.drawable.maskotka_szukam, "Nie masz jeszcze zamówień",
                    "Wybierz coś pysznego na stronie głównej – dowieziemy to do paczkomatu.",
                    "Przejdź do menu", () -> ((ClientActivity) requireActivity()).openTab(R.id.nav_home));
        } else if ("pickup".equals(filter)) {
            Mascot.show(mascotState, R.drawable.maskotka_szukam, "Nic nie czeka na odbiór",
                    "Damy znać, gdy jedzenie będzie w paczkomacie.");
        } else {
            Mascot.show(mascotState, R.drawable.maskotka_szukam, "Brak zamówień w tej grupie", "");
        }
    }
}
