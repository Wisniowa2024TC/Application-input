package com.example.demoemptyactivity.common;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import static com.example.demoemptyactivity.common.OrderViews.text;

/**
 * Zakładka wspólna dla klienta i kuriera.
 *
 * <ul>
 *     <li>Klient ("Paczkomat") – instrukcja odbioru z maskotką i lista paczkomatów
 *         z liczbą wolnych skrytek ({@code GET /api/lockers}).</li>
 *     <li>Kurier ("Mapa") – restauracje, z których odbiera jedzenie, i paczkomaty,
 *         do których je zawozi ({@code GET /api/courier/map}).</li>
 * </ul>
 * <p>Przycisk "Mapa" przy każdym miejscu otwiera aplikację z mapą.</p>
 */
public class LockersFragment extends Fragment {

    private boolean courierView;
    private boolean loaded;

    private View mascotState;
    private TextView restaurantsTitle;
    private LinearLayout restaurantsContainer;
    private TextView lockersTitle;
    private LinearLayout lockersContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lockers, parent, false);
        courierView = Session.isCourier(requireContext());
        loaded = false;

        mascotState = view.findViewById(R.id.mascot_state);
        restaurantsTitle = view.findViewById(R.id.tv_restaurants_title);
        restaurantsContainer = view.findViewById(R.id.restaurants_container);
        lockersTitle = view.findViewById(R.id.tv_lockers_title);
        lockersContainer = view.findViewById(R.id.lockers_container);

        TextView title = view.findViewById(R.id.tv_title);
        TextView subtitle = view.findViewById(R.id.tv_subtitle);
        if (courierView) {
            title.setText("Mapa");
            subtitle.setText("Skąd odebrać jedzenie i dokąd je zawieźć");
        } else {
            title.setText("Paczkomaty");
            subtitle.setText("Tu odbierzesz swoje jedzenie. „Online” – paczkomat jest połączony z serwerem");
            view.findViewById(R.id.how_to).setVisibility(View.VISIBLE);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPlaces(); // odświeżamy przy każdym wejściu na zakładkę
    }

    /** Pobiera paczkomaty (klient) albo restauracje i paczkomaty (kurier). */
    private void loadPlaces() {
        if (!loaded) {
            Mascot.showLoading(mascotState);
        }
        String path = courierView ? "/api/courier/map" : "/api/lockers";
        ApiClient.get(requireContext(), path, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) {
                    return; // zakładka została już zamknięta
                }
                loaded = true;
                Mascot.hide(mascotState);
                if (courierView) {
                    showRestaurants(response.optJSONArray("restaurants"));
                }
                showLockers(response.optJSONArray("lockers"));
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                loaded = false;
                restaurantsContainer.removeAllViews();
                lockersContainer.removeAllViews();
                restaurantsTitle.setVisibility(View.GONE);
                lockersTitle.setVisibility(View.GONE);
                Mascot.showError(mascotState, message, () -> loadPlaces());
            }
        });
    }

    /** Karty restauracji – używamy tego samego wyglądu co dla paczkomatów. */
    private void showRestaurants(JSONArray restaurants) {
        restaurantsContainer.removeAllViews();
        if (restaurants == null) {
            return;
        }
        restaurantsTitle.setVisibility(View.VISIBLE);
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < restaurants.length(); i++) {
            JSONObject restaurant = restaurants.optJSONObject(i);
            View card = inflater.inflate(R.layout.item_locker, restaurantsContainer, false);
            String name = text(restaurant, "name");

            ((ImageView) card.findViewById(R.id.iv_icon)).setImageResource(R.drawable.ic_bag);
            ((TextView) card.findViewById(R.id.tv_name)).setText(text(restaurant, "emoji") + " " + name);
            ((TextView) card.findViewById(R.id.tv_address)).setText(text(restaurant, "address"));
            ((TextView) card.findViewById(R.id.tv_free)).setText("Przygotowanie: ok. "
                    + restaurant.optInt("prep_minutes") + " min");
            OrderViews.styleBadge(card.findViewById(R.id.tv_online), "Restauracja",
                    Color.parseColor("#FEF3C7"), Color.parseColor("#B45309"));

            setupMapButton(card, restaurant, name);
            restaurantsContainer.addView(card);
        }
    }

    private void showLockers(JSONArray lockers) {
        lockersContainer.removeAllViews();
        if (lockers == null) {
            return;
        }
        lockersTitle.setVisibility(courierView ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < lockers.length(); i++) {
            JSONObject locker = lockers.optJSONObject(i);
            View card = inflater.inflate(R.layout.item_locker, lockersContainer, false);
            String name = text(locker, "name");

            ((TextView) card.findViewById(R.id.tv_name)).setText(name);
            ((TextView) card.findViewById(R.id.tv_address)).setText(text(locker, "address"));
            ((TextView) card.findViewById(R.id.tv_free)).setText("Wolne skrytki: "
                    + locker.optInt("free_compartments") + " z " + locker.optInt("total_compartments"));

            TextView online = card.findViewById(R.id.tv_online);
            if (locker.optBoolean("online")) {
                OrderViews.styleBadge(online, "Online", Color.parseColor("#DCFCE7"), Color.parseColor("#15803D"));
            } else {
                OrderViews.styleBadge(online, "Offline", Color.parseColor("#F1F5F9"), Color.parseColor("#64748B"));
            }

            setupMapButton(card, locker, name);
            lockersContainer.addView(card);
        }
    }

    private void setupMapButton(View card, JSONObject place, String name) {
        double latitude = place.optDouble("latitude");
        double longitude = place.optDouble("longitude");
        card.findViewById(R.id.btn_map).setOnClickListener(v ->
                OrderViews.openMap(requireContext(), latitude, longitude, name));
    }
}
