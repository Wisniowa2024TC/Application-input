package com.example.demoemptyactivity.client;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.common.LockersFragment;
import com.example.demoemptyactivity.common.OrderViews;
import com.example.demoemptyactivity.common.TopBar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Panel klienta – "rama" z górnym paskiem, paskiem koszyka i dolnym menu.
 *
 * <p>Sama zawartość zakładek to fragmenty (jak na makiecie):</p>
 * <ul>
 *     <li>{@link ClientHomeFragment} – "Strona główna": menu z jedzeniem,</li>
 *     <li>{@link OrdersFragment} – "Moje zamówienia": status, kod odbioru, historia,</li>
 *     <li>{@link LockersFragment} – "Paczkomat": lista paczkomatów z wolnymi skrytkami.</li>
 * </ul>
 * <p>Fragment to "kawałek ekranu" z własnym layoutem i kodem. Dzięki fragmentom górny pasek
 * i dolne menu zostają na miejscu, a podmieniamy tylko środek ekranu.</p>
 */
public class ClientActivity extends AppCompatActivity {

    /** Dodatek do Intentu: od razu otwórz zakładkę "Moje zamówienia". */
    public static final String EXTRA_OPEN_ORDERS = "open_orders";

    private BottomNavigationView bottomNav;
    private View cartBar;
    private TextView cartText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        TopBar.setup(this);

        // Prawy górny róg: ikona profilu.
        ImageButton profileButton = findViewById(R.id.btn_top_right);
        profileButton.setImageResource(R.drawable.ic_person);
        profileButton.setOnClickListener(v -> TopBar.showProfile(this));

        // Pasek "Koszyk" nad dolnym menu – widoczny, gdy coś jest w koszyku.
        cartBar = findViewById(R.id.cart_bar);
        cartText = findViewById(R.id.tv_cart);
        cartBar.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            boolean openOrders = getIntent().getBooleanExtra(EXTRA_OPEN_ORDERS, false);
            bottomNav.setSelectedItemId(openOrders ? R.id.nav_orders : R.id.nav_home);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Po powrocie z koszyka (np. po złożeniu zamówienia) koszyk mógł się zmienić.
        updateCartBar();
    }

    /** Przełącza zakładkę z innego miejsca, np. z banera na stronie głównej. */
    public void openTab(int menuItemId) {
        bottomNav.setSelectedItemId(menuItemId);
    }

    /** Odświeża pasek koszyka: "🛒 3 szt. • 87,97 zł". */
    public void updateCartBar() {
        if (Cart.isEmpty()) {
            cartBar.setVisibility(View.GONE);
            return;
        }
        cartBar.setVisibility(View.VISIBLE);
        cartText.setText("🛒  " + Cart.count() + " szt. • " + OrderViews.money(Cart.itemsTotal()));
    }

    /** Wstawia odpowiedni fragment w miejsce fragment_container. */
    private void showTab(int menuItemId) {
        Fragment fragment;
        if (menuItemId == R.id.nav_orders) {
            fragment = new OrdersFragment();
        } else if (menuItemId == R.id.nav_lockers) {
            fragment = new LockersFragment();
        } else {
            fragment = new ClientHomeFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
