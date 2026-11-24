package com.example.demoemptyactivity.courier;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.common.LockersFragment;
import com.example.demoemptyactivity.common.TopBar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Panel kuriera – "rama" z górnym paskiem i dolnym menu.
 *
 * <p>Zakładki:</p>
 * <ul>
 *     <li>{@link CourierTasksFragment} – "Panel dostawcy": jedzenie w aucie i zamówienia czekające w restauracjach,</li>
 *     <li>{@link LockersFragment} – "Mapa": restauracje i paczkomaty (ten sam ekran co u klienta),</li>
 *     <li>{@link CourierStatsFragment} – statystyki pracy.</li>
 * </ul>
 * <p>Dzwonek w prawym górnym rogu odświeża zlecenia; różowa kropka oznacza, że w restauracjach
 * czeka jedzenie do odebrania.</p>
 */
public class CourierActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_courier);

        TopBar.setup(this);

        ImageButton bellButton = findViewById(R.id.btn_top_right);
        bellButton.setImageResource(R.drawable.ic_bell);
        bellButton.setContentDescription("Odśwież zlecenia");
        bellButton.setOnClickListener(v -> refreshTasks());

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            showTab(R.id.nav_tasks);
        }
    }

    /** Pokazuje / ukrywa różową kropkę przy dzwonku (wołane przez CourierTasksFragment). */
    public void setNotificationDot(boolean visible) {
        findViewById(R.id.top_dot).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Dzwonek: przejście do zleceń i ich odświeżenie. */
    private void refreshTasks() {
        if (bottomNav.getSelectedItemId() == R.id.nav_tasks) {
            showTab(R.id.nav_tasks);
        } else {
            bottomNav.setSelectedItemId(R.id.nav_tasks);
        }
        Toast.makeText(this, "Odświeżono zlecenia", Toast.LENGTH_SHORT).show();
    }

    private void showTab(int menuItemId) {
        Fragment fragment;
        if (menuItemId == R.id.nav_lockers) {
            fragment = new LockersFragment();
        } else if (menuItemId == R.id.nav_stats) {
            fragment = new CourierStatsFragment();
        } else {
            fragment = new CourierTasksFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
