package com.example.demoemptyactivity.courier;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.demoemptyactivity.R;
import com.example.demoemptyactivity.api.ApiClient;
import com.example.demoemptyactivity.api.Session;

import org.json.JSONObject;

import static com.example.demoemptyactivity.common.OrderViews.text;

/**
 * Zakładka "Statystyki" kuriera – liczby z {@code GET /api/courier/stats}.
 *
 * <p>Na dole maskotka: czeka na pierwszą dostawę dnia albo cieszy się z wyników.</p>
 */
public class CourierStatsFragment extends Fragment {

    private TextView deliveredToday;
    private TextView collectedToday;
    private TextView inVehicle;
    private TextView waiting;
    private TextView deliveredTotal;
    private TextView earnedToday;
    private ImageView mascot;
    private TextView motivation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_courier_stats, parent, false);

        deliveredToday = view.findViewById(R.id.tv_delivered_today);
        collectedToday = view.findViewById(R.id.tv_collected_today);
        inVehicle = view.findViewById(R.id.tv_in_vehicle);
        waiting = view.findViewById(R.id.tv_waiting);
        deliveredTotal = view.findViewById(R.id.tv_delivered_total);
        earnedToday = view.findViewById(R.id.tv_earned_today);
        mascot = view.findViewById(R.id.iv_mascot);
        motivation = view.findViewById(R.id.tv_motivation);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        ApiClient.get(requireContext(), "/api/courier/stats", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (!isAdded()) {
                    return;
                }
                int today = response.optInt("delivered_today");
                deliveredToday.setText(String.valueOf(today));
                collectedToday.setText(String.valueOf(response.optInt("collected_today")));
                inVehicle.setText(String.valueOf(response.optInt("in_vehicle")));
                waiting.setText(String.valueOf(response.optInt("waiting")));
                deliveredTotal.setText(String.valueOf(response.optInt("delivered_total")));
                earnedToday.setText(text(response, "earned_today_text"));

                String name = Session.getFirstName(requireContext());
                if (today == 0) {
                    mascot.setImageResource(R.drawable.maskotka_czekaj);
                    motivation.setText("Do dzieła, " + name + "! Pierwsza dostawa czeka 🚚");
                } else {
                    mascot.setImageResource(R.drawable.maskotka_sukces);
                    motivation.setText("Świetna robota, " + name + "! Dziś dostarczone: " + today + " 💪");
                }
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                mascot.setImageResource(R.drawable.maskotka_blad);
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
