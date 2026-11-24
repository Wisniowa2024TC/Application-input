package com.example.demoemptyactivity.client;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.demoemptyactivity.MainActivity;
import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;
import com.example.demoemptyactivity.common.Mascot;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

import static com.example.demoemptyactivity.common.OrderViews.text;

/**
 * Strona główna klienta – menu z jedzeniem (jak na makiecie).
 *
 * <p>Całe menu pobieramy raz ({@code GET /api/menu}), a filtrowanie po kategorii i wyszukiwanie
 * robimy w telefonie – lista zmienia się od razu podczas pisania.</p>
 *
 * <p>Kliknięcie "+" dodaje danie do koszyka ({@link Cart}). Na dole ekranu pojawia się wtedy
 * pasek koszyka (obsługuje go {@link ClientActivity}).</p>
 */
public class ClientHomeFragment extends Fragment {

    /** Kod kategorii "Wszystko" (pozostałe kody przychodzą z serwera: PIZZA, BURGER...). */
    private static final String ALL = "ALL";

    private JSONArray products = new JSONArray();
    private JSONArray restaurants = new JSONArray();
    private JSONArray categories = new JSONArray();
    private String selectedCategory = ALL;

    private ScrollView scroll;
    private EditText searchInput;
    private ChipGroup chipGroup;
    private View banner;
    private View recommendedSection;
    private LinearLayout recommendedContainer;
    private TextView menuTitle;
    private View mascotState;
    private LinearLayout menuContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_client_home, parent, false);

        scroll = view.findViewById(R.id.scroll);
        searchInput = view.findViewById(R.id.et_search);
        chipGroup = view.findViewById(R.id.chip_group);
        banner = view.findViewById(R.id.banner);
        recommendedSection = view.findViewById(R.id.recommended_section);
        recommendedContainer = view.findViewById(R.id.recommended_container);
        menuTitle = view.findViewById(R.id.tv_menu_title);
        mascotState = view.findViewById(R.id.mascot_state);
        menuContainer = view.findViewById(R.id.menu_container);

        TextView greeting = view.findViewById(R.id.tv_greeting);
        greeting.setText("Cześć, " + Session.getFirstName(requireContext()) + "!");

        // Wyszukiwanie "na żywo" – po każdej wpisanej literze.
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                showMenu();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        // "Szukaj" na klawiaturze tylko chowa klawiaturę – lista jest już przefiltrowana.
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        banner.setOnClickListener(v -> MainActivity.showAboutDialog(requireActivity()));
        view.findViewById(R.id.tv_see_all).setOnClickListener(v -> scroll.smoothScrollTo(0, menuTitle.getTop()));

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                selectedCategory = (String) group.findViewById(checkedIds.get(0)).getTag();
                showMenu();
            }
        });

        loadMenu();
        return view;
    }

    /** Pobiera menu z serwera. */
    private void loadMenu() {
        Mascot.showLoading(mascotState);
        ApiClient.get(requireContext(), "/api/menu", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) {
                    return;
                }
                products = response.optJSONArray("products");
                restaurants = response.optJSONArray("restaurants");
                categories = response.optJSONArray("categories");
                buildCategoryChips();
                showMenu();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Mascot.showError(mascotState, message, () -> loadMenu());
            }
        });
    }

    /** Tworzy przyciski kategorii: "Wszystko" + kategorie z serwera. */
    private void buildCategoryChips() {
        chipGroup.removeAllViews();
        addChip(ALL, "🍽️ Wszystko");
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            addChip(text(category, "code"), text(category, "emoji") + " " + text(category, "name"));
        }
        // Zaznaczamy aktualną kategorię (po pierwszym pobraniu: "Wszystko").
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            if (selectedCategory.equals(chip.getTag())) {
                chip.setChecked(true);
            }
        }
    }

    private void addChip(String code, String label) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_category_chip, chipGroup, false);
        chip.setId(View.generateViewId());
        chip.setTag(code);   // w tagu trzymamy kod kategorii
        chip.setText(label);
        chipGroup.addView(chip);
    }

    /**
     * Pokazuje menu zgodnie z wybraną kategorią i tekstem w wyszukiwarce.
     * Gdy użytkownik nic nie filtruje – widać też baner i sekcję "Polecane".
     */
    private void showMenu() {
        if (products == null || restaurants == null) {
            return;
        }
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean browsing = query.isEmpty() && selectedCategory.equals(ALL);

        banner.setVisibility(browsing ? View.VISIBLE : View.GONE);
        recommendedSection.setVisibility(browsing ? View.VISIBLE : View.GONE);
        if (browsing) {
            showRecommended();
        }

        if (!query.isEmpty()) {
            menuTitle.setText("Wyniki wyszukiwania");
        } else if (browsing) {
            menuTitle.setText("Całe menu");
        } else {
            menuTitle.setText(categoryName(selectedCategory));
        }

        // Dania są posortowane przez serwer według restauracji, więc nagłówek restauracji
        // wstawiamy za każdym razem, gdy zmienia się restaurant_id.
        menuContainer.removeAllViews();
        long lastRestaurantId = -1;
        int shown = 0;
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (!matches(product, query)) {
                continue;
            }
            long restaurantId = product.optLong("restaurant_id");
            if (restaurantId != lastRestaurantId) {
                menuContainer.addView(createRestaurantHeader(restaurantId));
                lastRestaurantId = restaurantId;
            }
            menuContainer.addView(createProductRow(product));
            shown++;
        }

        if (shown == 0) {
            Mascot.show(mascotState, R.drawable.maskotka_szukam, "Nic nie znalazłam",
                    "Spróbuj wpisać coś innego, np. „pizza” albo „sushi”.");
        } else {
            Mascot.hide(mascotState);
        }
    }

    /** Czy danie pasuje do wybranej kategorii i wpisanego tekstu? */
    private boolean matches(JSONObject product, String query) {
        if (!selectedCategory.equals(ALL) && !selectedCategory.equals(text(product, "category"))) {
            return false;
        }
        if (query.isEmpty()) {
            return true;
        }
        String searchable = (text(product, "name") + " " + text(product, "description") + " "
                + text(product, "restaurant_name")).toLowerCase(Locale.ROOT);
        return searchable.contains(query);
    }

    /** Sekcja "Polecane": dania oznaczone na serwerze jako polecane, po dwa w rzędzie. */
    private void showRecommended() {
        recommendedContainer.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < products.length(); i++) {
            JSONObject product = products.optJSONObject(i);
            if (product.optInt("recommended") != 1) {
                continue;
            }
            if (row == null || row.getChildCount() == 2) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                recommendedContainer.addView(row);
            }
            View tile = getLayoutInflater().inflate(R.layout.item_product_tile, row, false);
            ((TextView) tile.findViewById(R.id.tv_emoji)).setText(text(product, "emoji"));
            ((TextView) tile.findViewById(R.id.tv_name)).setText(text(product, "name"));
            ((TextView) tile.findViewById(R.id.tv_restaurant)).setText(text(product, "restaurant_name"));
            ((TextView) tile.findViewById(R.id.tv_price)).setText(text(product, "price_text"));
            tile.findViewById(R.id.btn_add).setOnClickListener(v -> addToCart(product));
            if (row.getChildCount() == 0) {
                // Odstęp między dwoma kafelkami w rzędzie.
                ((LinearLayout.LayoutParams) tile.getLayoutParams()).setMarginEnd(dp(10));
            }
            row.addView(tile);
        }
    }

    private View createRestaurantHeader(long restaurantId) {
        View header = getLayoutInflater().inflate(R.layout.item_restaurant_header, menuContainer, false);
        JSONObject restaurant = findRestaurant(restaurantId);
        if (restaurant != null) {
            ((TextView) header.findViewById(R.id.tv_name)).setText(
                    text(restaurant, "emoji") + " " + text(restaurant, "name"));
            ((TextView) header.findViewById(R.id.tv_info)).setText(
                    "Przygotowanie ok. " + restaurant.optInt("prep_minutes") + " min • " + text(restaurant, "address"));
        }
        return header;
    }

    private View createProductRow(JSONObject product) {
        View row = getLayoutInflater().inflate(R.layout.item_product, menuContainer, false);
        ((TextView) row.findViewById(R.id.tv_emoji)).setText(text(product, "emoji"));
        ((TextView) row.findViewById(R.id.tv_name)).setText(text(product, "name"));
        ((TextView) row.findViewById(R.id.tv_description)).setText(text(product, "description"));
        ((TextView) row.findViewById(R.id.tv_price)).setText(text(product, "price_text"));
        row.findViewById(R.id.btn_add).setOnClickListener(v -> addToCart(product));
        return row;
    }

    /**
     * Dodaje danie do koszyka. Jeżeli w koszyku są dania z innej restauracji – pytamy,
     * czy zacząć nowy koszyk (jedno zamówienie = jedna restauracja).
     */
    private void addToCart(JSONObject product) {
        if (Cart.isFromOtherRestaurant(product)) {
            Mascot.dialog(requireContext(), R.drawable.maskotka_powiadomienie, "Nowy koszyk?",
                            "W koszyku masz dania z: " + Cart.restaurantName() + ".\n"
                                    + "Jedno zamówienie to jedna restauracja – kurier jedzie po jedzenie w jedno miejsce.\n\n"
                                    + "Wyczyścić koszyk i dodać „" + text(product, "name") + "”?")
                    .setPositiveButton("Tak, nowy koszyk", (dialog, which) -> {
                        Cart.add(product);
                        afterAdd(product);
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
            return;
        }
        Cart.add(product);
        afterAdd(product);
    }

    private void afterAdd(JSONObject product) {
        Toast.makeText(requireContext(), "Dodano: " + text(product, "name"), Toast.LENGTH_SHORT).show();
        ((ClientActivity) requireActivity()).updateCartBar();
    }

    private JSONObject findRestaurant(long id) {
        for (int i = 0; i < restaurants.length(); i++) {
            JSONObject restaurant = restaurants.optJSONObject(i);
            if (restaurant.optLong("id") == id) {
                return restaurant;
            }
        }
        return null;
    }

    private String categoryName(String code) {
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (code.equals(text(category, "code"))) {
                return text(category, "emoji") + " " + text(category, "name");
            }
        }
        return "Menu";
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = requireContext().getSystemService(InputMethodManager.class);
        keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
