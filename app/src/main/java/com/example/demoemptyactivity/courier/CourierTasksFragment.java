package com.example.demoemptyactivity.courier;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;
import com.example.demoemptyactivity.common.Mascot;
import com.example.demoemptyactivity.common.OrderViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Zakładka "Zlecenia" kuriera – "Panel dostawcy" z makiety.
 *
 * <p>Serwer ({@code GET /api/courier/tasks}) zwraca jedną listę "Nadchodzące dostawy":</p>
 * <ul>
 *     <li>najpierw zamówienia, które kurier ma już w aucie (W drodze),</li>
 *     <li>potem zamówienia czekające w restauracjach na dowolnego kuriera (Oczekuje).</li>
 * </ul>
 * <p>Kliknięcie w zamówienie otwiera szczegóły z przyciskiem
 * "Odebrałem jedzenie z restauracji" albo "Włóż do paczkomatu".</p>
 */
public class CourierTasksFragment extends Fragment {

    private TextView activeCountText;
    private TextView activeDetailsText;
    private TextView subtitleText;
    private View mascotState;
    private LinearLayout ordersContainer;
    private View footerMascot;

    /** Czy lista została już raz pokazana (wtedy przy odświeżaniu nie zasłaniamy jej maskotką ładowania). */
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_courier_tasks, parent, false);

        activeCountText = view.findViewById(R.id.tv_active_count);
        activeDetailsText = view.findViewById(R.id.tv_active_details);
        subtitleText = view.findViewById(R.id.tv_subtitle);
        mascotState = view.findViewById(R.id.mascot_state);
        ordersContainer = view.findViewById(R.id.orders_container);
        footerMascot = view.findViewById(R.id.footer_mascot);

        subtitleText.setText("Cześć, " + Session.getFirstName(requireContext()) + "! Oto Twoje zlecenia");
        view.findViewById(R.id.card_active).setOnClickListener(v -> loadTasks());
        loaded = false;
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Odświeżamy przy każdym powrocie – np. po odebraniu jedzenia w szczegółach zamówienia.
        loadTasks();
    }

    private void loadTasks() {
        if (!loaded) {
            Mascot.showLoading(mascotState);
        }
        ApiClient.get(requireContext(), "/api/courier/tasks", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) {
                    return;
                }
                loaded = true;
                showTasks(response);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                loaded = false;
                ordersContainer.removeAllViews();
                footerMascot.setVisibility(View.GONE);
                Mascot.showError(mascotState, message, () -> loadTasks());
            }
        });
    }

    private void showTasks(JSONObject response) {
        int inVehicle = response.optInt("in_vehicle");
        int waiting = response.optInt("waiting");
        activeCountText.setText(String.valueOf(response.optInt("active_count")));
        activeDetailsText.setText("W aucie: " + inVehicle + " • Czeka w restauracjach: " + waiting);

        ordersContainer.removeAllViews();
        JSONArray orders = response.optJSONArray("orders");
        int count = orders == null ? 0 : orders.length();
        for (int i = 0; i < count; i++) {
            ordersContainer.addView(OrderViews.createOrderCard(
                    getLayoutInflater(), ordersContainer, orders.optJSONObject(i), true));
        }

        if (count == 0) {
            footerMascot.setVisibility(View.GONE);
            Mascot.show(mascotState, R.drawable.maskotka_szukam, "Brak zleceń",
                    "Gdy ktoś zamówi jedzenie, zlecenie pojawi się tutaj. Odśwież za chwilę.",
                    "Odśwież", () -> loadTasks());
        } else {
            Mascot.hide(mascotState);
            footerMascot.setVisibility(View.VISIBLE);
        }

        // Różowa kropka przy dzwonku, gdy w restauracjach czeka jedzenie do odebrania.
        if (getActivity() instanceof CourierActivity) {
            ((CourierActivity) getActivity()).setNotificationDot(waiting > 0);
        }
    }
}
