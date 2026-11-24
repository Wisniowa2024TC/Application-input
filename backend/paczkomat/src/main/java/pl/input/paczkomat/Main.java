package pl.input.paczkomat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Scanner;

/**
 * Program fizycznego paczkomatu INPUT – uruchamiany na Raspberry Pi.
 *
 * <h2>Co robi</h2>
 * <ol>
 *     <li><b>Klawiatura:</b> czeka, aż ktoś wpisze 6-cyfrowy kod odbioru zamówienia.
 *         Wysyła go do serwera, a serwer odpowiada, którą skrytkę otworzyć.</li>
 *     <li><b>Zdalne otwieranie:</b> w osobnym wątku co 2 sekundy pyta serwer, czy ktoś nie kliknął
 *         w aplikacji "Otwórz skrytkę" (albo czy kurier nie wkłada jedzenia do skrytki).</li>
 * </ol>
 *
 * <h2>Uruchomienie</h2>
 * <pre>
 *   java -jar paczkomat.jar                              (paczkomat A1, serwer na tym samym komputerze)
 *   java -jar paczkomat.jar A1 http://192.168.1.10:8080   (Raspberry Pi łączy się z serwerem na laptopie)
 *   java -jar paczkomat.jar B2                           (drugi paczkomat – można uruchomić kilka naraz)
 * </pre>
 *
 * <p>W makiecie zamiast prawdziwej klawiatury numerycznej można podłączyć do Raspberry Pi
 * zwykłą klawiaturę USB (albo klawiaturę numeryczną USB) – program czyta po prostu wpisany tekst.</p>
 */
public class Main {

    /** Co ile milisekund pytamy serwer o polecenia. */
    private static final int POLL_INTERVAL_MS = 2000;

    public static void main(String[] args) {
        String lockerCode = args.length > 0 ? args[0] : "A1";
        String serverUrl = args.length > 1 ? args[1] : "http://localhost:8080";

        LockHardware hardware;
        if (RaspberryPiHardware.isRunningOnRaspberryPi()) {
            System.out.println("Wykryto Raspberry Pi – skrytki będą otwierane przez piny GPIO.");
            hardware = new RaspberryPiHardware();
        } else {
            System.out.println("To nie jest Raspberry Pi – tryb symulacji (otwarcie skrytki = komunikat na ekranie).");
            hardware = new SimulatedHardware();
        }

        ServerClient server = new ServerClient(serverUrl, lockerCode);

        System.out.println("Paczkomat " + lockerCode + " łączy się z serwerem " + serverUrl);
        startPollingThread(server, hardware);
        readCodesFromKeyboard(server, hardware);
    }

    /**
     * Wątek w tle: co 2 sekundy pyta serwer o polecenia "otwórz skrytkę".
     * Osobny wątek jest potrzebny, bo główny wątek cały czas czeka na wpisanie kodu.
     */
    private static void startPollingThread(ServerClient server, LockHardware hardware) {
        Thread thread = new Thread(() -> {
            boolean wasConnected = false;

            while (true) {
                try {
                    JSONObject response = server.poll();

                    if (!wasConnected) {
                        JSONObject locker = response.getJSONObject("locker");
                        System.out.println("Połączono! " + locker.getString("name") + ", " + locker.getString("address"));
                        printCompartments(response.getJSONArray("compartments"));
                        printPrompt();
                        wasConnected = true;
                    }

                    JSONArray commands = response.getJSONArray("commands");
                    for (int i = 0; i < commands.length(); i++) {
                        JSONObject command = commands.getJSONObject(i);
                        System.out.println();
                        System.out.println("Polecenie z serwera: " + command.getString("reason"));
                        hardware.open(command.getInt("compartment_number"));
                        printPrompt();
                    }

                } catch (Exception e) {
                    if (wasConnected || e instanceof ServerClient.ServerError) {
                        System.out.println();
                        System.out.println("Brak połączenia z serwerem: " + e.getMessage());
                    }
                    wasConnected = false;
                }

                sleep(POLL_INTERVAL_MS);
            }
        });
        // Wątek "daemon" kończy się razem z programem.
        thread.setDaemon(true);
        thread.start();
    }

    /** Główna pętla: czyta kody wpisane na klawiaturze i wysyła je do serwera. */
    private static void readCodesFromKeyboard(ServerClient server, LockHardware hardware) {
        Scanner scanner = new Scanner(System.in);
        printPrompt();

        while (scanner.hasNextLine()) {
            String code = scanner.nextLine().trim();

            if (code.equalsIgnoreCase("koniec")) {
                break;
            }
            if (code.equalsIgnoreCase("stan")) {
                try {
                    printCompartments(server.poll().getJSONArray("compartments"));
                } catch (Exception e) {
                    System.out.println("Nie udało się pobrać stanu: " + e.getMessage());
                }
                printPrompt();
                continue;
            }
            if (!code.matches("\\d{6}")) {
                if (!code.isEmpty()) {
                    System.out.println("Kod musi mieć 6 cyfr.");
                }
                printPrompt();
                continue;
            }

            try {
                JSONObject response = server.enterCode(code);
                System.out.println(response.getString("message"));
                hardware.open(response.getInt("compartment_number"));
            } catch (ServerClient.ServerError e) {
                System.out.println("ODMOWA: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Brak połączenia z serwerem – spróbuj ponownie za chwilę.");
            }
            printPrompt();
        }

        System.out.println("Paczkomat wyłączony.");
    }

    /** Rysuje w konsoli skrytki paczkomatu: [1 A wolna] [2 A ZAJĘTA] ... */
    private static void printCompartments(JSONArray compartments) {
        StringBuilder line = new StringBuilder("Skrytki: ");
        for (int i = 0; i < compartments.length(); i++) {
            JSONObject c = compartments.getJSONObject(i);
            boolean occupied = c.getInt("occupied") == 1;
            line.append("[").append(c.getInt("number")).append(" ").append(c.getString("size"))
                    .append(occupied ? " ZAJĘTA" : " wolna").append("] ");
        }
        System.out.println(line);
    }

    private static void printPrompt() {
        System.out.println("Wpisz kod odbioru (6 cyfr) i naciśnij Enter  |  'stan' – skrytki  |  'koniec' – wyjście");
        System.out.print("> ");
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
