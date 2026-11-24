package pl.input.paczkomat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Sterowanie zamkami skrytek przez piny GPIO Raspberry Pi.
 *
 * <h2>Podłączenie (przykład dla modułu przekaźników 4/8-kanałowego)</h2>
 * <pre>
 *   Raspberry Pi            Moduł przekaźników         Zamek / LED
 *   ------------            ------------------         -----------
 *   5V   (pin 2)   ───────▶ VCC
 *   GND  (pin 6)   ───────▶ GND
 *   GPIO17 (pin 11) ──────▶ IN1  ── przekaźnik 1 ──▶  skrytka nr 1
 *   GPIO27 (pin 13) ──────▶ IN2  ── przekaźnik 2 ──▶  skrytka nr 2
 *   GPIO22 (pin 15) ──────▶ IN3  ── przekaźnik 3 ──▶  skrytka nr 3
 *   GPIO23 (pin 16) ──────▶ IN4  ── przekaźnik 4 ──▶  skrytka nr 4
 *   GPIO24 (pin 18) ──────▶ IN5  ── przekaźnik 5 ──▶  skrytka nr 5
 *   GPIO25 (pin 22) ──────▶ IN6  ── przekaźnik 6 ──▶  skrytka nr 6
 * </pre>
 * <p>Elektrozamek 12 V zasilamy z osobnego zasilacza przez styki przekaźnika (COM/NO) – NIE z Raspberry Pi.
 * W prostej makiecie zamiast zamka można podłączyć diodę LED z rezystorem 330 Ω bezpośrednio do pinu GPIO.</p>
 *
 * <h2>Jak włączamy pin</h2>
 * <p>Używamy programu {@code pinctrl}, który jest wbudowany w Raspberry Pi OS (Bookworm i nowsze).
 * Nie potrzeba żadnej dodatkowej biblioteki Javy:</p>
 * <pre>
 *   pinctrl set 17 op dh   – pin 17 jako wyjście, stan wysoki (zamek otwarty)
 *   pinctrl set 17 op dl   – stan niski (zamek zamknięty)
 * </pre>
 */
public class RaspberryPiHardware implements LockHardware {

    /** Numer skrytki → numer pinu GPIO (numeracja BCM). Zmień, jeśli podłączysz inaczej. */
    private static final Map<Integer, Integer> COMPARTMENT_TO_GPIO = Map.of(
            1, 17,
            2, 27,
            3, 22,
            4, 23,
            5, 24,
            6, 25
    );

    /**
     * Wiele modułów przekaźników włącza się stanem NISKIM (tzw. "active low").
     * Jeżeli po starcie programu przekaźniki są włączone, a przy otwieraniu się wyłączają – ustaw true.
     */
    private static final boolean RELAY_ACTIVE_LOW = false;

    /** Jak długo trzymamy zamek otwarty (elektrozamek wystarczy "kopnąć" na chwilę). */
    private static final int OPEN_TIME_MS = 1500;

    /** Czy program działa na Raspberry Pi (sprawdzamy opis płyty w systemie Linux). */
    public static boolean isRunningOnRaspberryPi() {
        try {
            Path model = Path.of("/proc/device-tree/model");
            return Files.exists(model) && Files.readString(model).contains("Raspberry Pi");
        } catch (IOException e) {
            return false;
        }
    }

    public RaspberryPiHardware() {
        // Na starcie wszystkie zamki zamknięte.
        for (int pin : COMPARTMENT_TO_GPIO.values()) {
            setPin(pin, false);
        }
    }

    @Override
    public void open(int compartmentNumber) {
        Integer pin = COMPARTMENT_TO_GPIO.get(compartmentNumber);
        if (pin == null) {
            System.out.println("[GPIO] Skrytka nr " + compartmentNumber + " nie ma przypisanego pinu – pomijam.");
            return;
        }

        System.out.println("[GPIO] Otwieram skrytkę nr " + compartmentNumber + " (GPIO" + pin + ")");
        setPin(pin, true);
        try {
            Thread.sleep(OPEN_TIME_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        setPin(pin, false);
    }

    /** Ustawia pin w stan "otwarty" (true) albo "zamknięty" (false), uwzględniając active low. */
    private void setPin(int pin, boolean open) {
        boolean high = RELAY_ACTIVE_LOW != open;
        try {
            new ProcessBuilder("pinctrl", "set", String.valueOf(pin), "op", high ? "dh" : "dl")
                    .inheritIO()
                    .start()
                    .waitFor();
        } catch (IOException e) {
            System.out.println("[GPIO] Nie udało się uruchomić 'pinctrl': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
