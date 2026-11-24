package com.example.demoemptyactivity.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.demoemptyactivity.R;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Wspólne "klocki" interfejsu używane na kilku ekranach:
 * karta zamówienia na liście, kolorowa plakietka statusu, ceny, otwieranie mapy itd.
 * Dzięki temu karta zamówienia wygląda tak samo u klienta i u kuriera.
 */
public final class OrderViews {

    private OrderViews() {
    }

    /**
     * Bezpieczne odczytanie tekstu z JSON-a.
     * Uwaga: zwykłe {@code optString} dla wartości null z serwera zwraca napis "null" – stąd ta metoda.
     */
    public static String text(JSONObject json, String key) {
        return json.isNull(key) ? "" : json.optString(key);
    }

    /** Grosze → tekst: 2999 → "29,99 zł" (tak samo jak na serwerze). */
    public static String money(int grosze) {
        return String.format(Locale.ROOT, "%d,%02d zł", grosze / 100, grosze % 100);
    }

    /**
     * Tworzy kartę zamówienia (layout item_order.xml) i wypełnia ją danymi.
     * Kliknięcie w kartę otwiera szczegóły zamówienia.
     *
     * @param courierView true – widok kuriera (dokąd zawieźć, skąd odebrać), false – widok klienta
     */
    public static View createOrderCard(LayoutInflater inflater, ViewGroup parent, JSONObject order, boolean courierView) {
        View card = inflater.inflate(R.layout.item_order, parent, false);

        TextView emoji = card.findViewById(R.id.tv_emoji);
        TextView number = card.findViewById(R.id.tv_number);
        TextView line1 = card.findViewById(R.id.tv_line1);
        TextView line2 = card.findViewById(R.id.tv_line2);
        TextView code = card.findViewById(R.id.tv_code);
        TextView time = card.findViewById(R.id.tv_time);
        TextView badge = card.findViewById(R.id.tv_badge);

        String status = text(order, "status");
        emoji.setText(text(order, "restaurant_emoji"));
        number.setText(text(order, "number"));
        styleStatusBadge(badge, status, statusLabel(status, text(order, "status_label"), courierView));

        if (courierView) {
            // Jak na makiecie: "#1047 – Dostawa do: Paczkomat A1", godzina dostawy i status.
            line1.setText("Dostawa do: " + text(order, "locker_name"));
            line2.setText("Odbiór: " + text(order, "restaurant_name"));
            time.setText(text(order, "planned_time"));
        } else {
            line1.setText(text(order, "restaurant_name") + " • " + text(order, "total_text"));
            line2.setText(text(order, "items_summary"));
            boolean active = status.equals("PLACED") || status.equals("IN_DELIVERY");
            time.setText(active ? text(order, "planned_time") : shortTime(text(order, "updated_at")));
        }

        // Kod odbioru pokazujemy od razu na liście – tak jest najszybciej przy paczkomacie.
        String pickupCode = text(order, "pickup_code");
        if (!pickupCode.isEmpty()) {
            code.setText("Kod odbioru: " + pickupCode + " • " + text(order, "locker_name"));
            code.setVisibility(View.VISIBLE);
        }

        long id = order.optLong("id");
        card.setOnClickListener(v -> openDetails(v.getContext(), id));
        return card;
    }

    /** Otwiera ekran szczegółów zamówienia. */
    public static void openDetails(Context context, long orderId) {
        Intent intent = new Intent(context, OrderDetailsActivity.class);
        intent.putExtra(OrderDetailsActivity.EXTRA_ORDER_ID, orderId);
        context.startActivity(intent);
    }

    /**
     * Ten sam status może mieć inną nazwę dla kuriera: zamówienie "Przyjęte" (restauracja gotuje)
     * dla kuriera "Oczekuje" na odbiór – tak jak na makiecie panelu dostawcy.
     */
    public static String statusLabel(String status, String label, boolean courierView) {
        if (courierView && status.equals("PLACED")) {
            return "Oczekuje";
        }
        return label;
    }

    /** Ustawia tekst i kolor plakietki statusu (np. różowe "W drodze", zielone "Do odbioru"). */
    public static void styleStatusBadge(TextView badge, String status, String label) {
        int background;
        int textColor;
        switch (status) {
            case "PLACED":
                background = Color.parseColor("#E0E7FF");
                textColor = Color.parseColor("#3949AB");
                break;
            case "IN_DELIVERY":
                background = Color.parseColor("#FCE7F3");
                textColor = Color.parseColor("#DB2777");
                break;
            case "READY_FOR_PICKUP":
                background = Color.parseColor("#DCFCE7");
                textColor = Color.parseColor("#15803D");
                break;
            case "CANCELLED":
                background = Color.parseColor("#FEE2E2");
                textColor = Color.parseColor("#B91C1C");
                break;
            default: // PICKED_UP i inne
                background = Color.parseColor("#F1F5F9");
                textColor = Color.parseColor("#64748B");
                break;
        }
        styleBadge(badge, label, background, textColor);
    }

    /** Zaokrąglona kolorowa plakietka z tekstem. */
    public static void styleBadge(TextView badge, String label, int backgroundColor, int textColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(100f);
        shape.setColor(backgroundColor);
        badge.setBackground(shape);
        badge.setTextColor(textColor);
        badge.setText(label);
    }

    /**
     * Skraca datę z serwera ("2026-11-24 12:30"):
     * dzisiaj → sama godzina "12:30", inny dzień → "24.11".
     */
    public static String shortTime(String dateTime) {
        if (dateTime.length() < 16) {
            return dateTime;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (dateTime.startsWith(today)) {
            return dateTime.substring(11, 16);
        }
        return dateTime.substring(8, 10) + "." + dateTime.substring(5, 7);
    }

    /**
     * Otwiera miejsce na mapie (Google Maps albo inna aplikacja z mapą).
     * Jeżeli w telefonie nie ma aplikacji z mapą – otwieramy mapę w przeglądarce.
     */
    public static void openMap(Context context, double latitude, double longitude, String label) {
        String position = latitude + "," + longitude;
        Uri geo = Uri.parse("geo:" + position + "?q=" + position + "(" + Uri.encode(label) + ")");
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, geo));
        } catch (ActivityNotFoundException e) {
            try {
                Uri web = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + position);
                context.startActivity(new Intent(Intent.ACTION_VIEW, web));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, "Brak aplikacji do wyświetlenia mapy", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
