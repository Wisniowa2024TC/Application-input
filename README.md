# INPUT – sieć paczkomatów z zamawianiem jedzenia

Projekt szkolny: zintegrowany system zamawiania posiłków z dostawą do skrytek paczkomatu. Składa się z trzech współpracujących ze sobą modułów:

1. **Aplikacja Android** (czysta Java 11, Material 3, minSdk 24): intuicyjny interfejs z osobnymi panelami dla **klienta** i **kuriera**.
2. **Serwer backendowy** (Java 11, wbudowany `HttpServer` + SQLite): zarządza kontami, menu restauracji, zamówieniami i stanem paczkomatów, udostępniając REST API.
3. **Program paczkomatu** (Java 11, moduł dla **Raspberry Pi**): steruje fizycznymi zamkami skrytek przez piny GPIO oraz obsługuje klawiaturę do wprowadzania kodów odbioru.

```
 ┌──────────────────────┐        HTTP / JSON         ┌───────────────────────────┐        HTTP / JSON        ┌──────────────────────────┐
 │  Aplikacja Android   │ ─────────────────────────▶ │       SERWER INPUT        │ ◀───────────────────────── │  Paczkomat (Raspberry Pi) │
 │  • klient (zamawia)  │   menu, koszyk, zlecenia,   │  • REST API (port 8080)   │   "czy otworzyć skrytkę?"  │  • klawiatura (kod 6 cyfr)│
 │  • kurier (dostarcza)│   "otwórz skrytkę"          │  • baza SQLite (input.db) │   "wpisano kod odbioru"    │  • zamki skrytek (GPIO)   │
 └──────────────────────┘                            └───────────────────────────┘                            └──────────────────────────┘
```

Telefon i paczkomat **nigdy nie łączą się ze sobą bezpośrednio** – cała komunikacja przechodzi przez centralny serwer:
- Gdy klient otwiera skrytkę przyciskiem w aplikacji, serwer zapisuje polecenie w kolejce urządzenia.
- Raspberry Pi co 2 sekundy pyta serwer o nowe polecenia (odpytywanie `/api/device/{code}/poll`) i po odebraniu sygnału natychmiast otwiera odpowiednią skrytkę.
- Klient może również podejść do paczkomatu i wpisać swój 6-cyfrowy kod odbioru na klawiaturze – paczkomat weryfikuje kod na serwerze i otwiera drzwiczki.

---

## Jak działa zamówienie (cykl życia)

| Status | Nazwa w aplikacji | Co się dzieje | Kto wykonuje kolejny krok |
|---|---|---|---|
| `CREATED` | Oczekuje | Klient skompletował koszyk, wybrał paczkomat docelowy i złożył zamówienie. Restauracja przygotowuje posiłek. | Kurier: klika „Odebrałem z restauracji” po dotarciu do lokalu |
| `IN_TRANSIT` | W drodze | Kurier odebrał świeże jedzenie i wiezie je w torbie termicznej do wybranego paczkomatu. | Kurier: po dojechaniu klika „Włóż do paczkomatu” (otwiera się wolna skrytka) |
| `READY_FOR_PICKUP` | Do odbioru | Jedzenie czeka bezpiecznie w skrytce paczkomatu. Klient widzi 6-cyfrowy kod odbioru. | Klient: wpisuje kod na klawiaturze paczkomatu lub klika „Otwórz skrytkę” w aplikacji |
| `DELIVERED` | Odebrane | Klient wyjął zamówienie i zamknął drzwiczki. Zamówienie trafia do historii. | – (zamówienie zakończone sukcesem) |
| `CANCELLED` | Anulowane | Klient anulował zamówienie, zanim kurier wyruszył do restauracji. | – (zamówienie zamknięte) |

---

## Funkcje aplikacji

### Część wspólna
- **Ekran powitalny, logowanie i rejestracja** z wyborem roli (**Klient** / **Kurier**).
- Rejestracja kuriera wymaga podania tajnego kodu firmowego (w wersji demonstracyjnej: `KURIER2026`).
- Trwała sesja użytkownika na tokenie (logowanie zapamiętane w telefonie) oraz wylogowanie w menu bocznym.
- **Lista paczkomatów**: podgląd wolnych skrytek, adresów oraz statusu online/offline urządzenia.
- **Szczegóły zamówienia**: status na osi czasu, 6-cyfrowy kod odbioru, lista dań z restauracji, podsumowanie kosztów i pełna historia zdarzeń.

### Panel klienta
- **Katalog jedzenia**: podział na restauracje (pizzeria, burgery, sushi, sałatki) z filtrowaniem kategorii, opisami dań i cenami.
- **Koszyk**: dodawanie i usuwanie pozycji, automatyczne sumowanie kwoty zamówienia, wybór docelowego paczkomatu z informacją o wolnych skrytkach.
- **Moje zamówienia**: zakładka z aktywnymi zamówieniami (kod odbioru widoczny od razu na karcie dania) oraz historią zrealizowanych odbiorów.
- **Zdalne otwieranie skrytki**: możliwość otwarcia paczkomatu z poziomu aplikacji bez dotykania klawiatury fizycznej.

### Panel kuriera
- **Zlecenia**: lista zamówień oczekujących w restauracjach na odbiór oraz zamówień będących aktualnie w drodze do paczkomatu.
- **Obsługa dostawy**: zmiana statusu na „Odebrałem z restauracji”, a przy paczkomacie przycisk „Włóż do paczkomatu”, który automatycznie otwiera wolną skrytkę o odpowiednim gabarycie.
- **Mapa i paczkomaty**: podgląd lokalizacji paczkomatów i ich obłożenia.
- **Statystyki**: liczba zrealizowanych dostaw, aktywne kursy oraz wskaźniki ukończonych zleceń.

---

## Struktura projektu

```
app/                                        Moduł aplikacji Android (Java 11, Material 3)
└── src/main/java/com/example/demoemptyactivity/
    ├── MainActivity.java                   Ekran startowy i zmiana adresu serwera
    ├── api/
    │   ├── ApiClient.java                  Wykonywanie zapytań HTTP w tle (GET / POST)
    │   └── Session.java                    Zapis tokenu, roli i danych użytkownika w SharedPreferences
    ├── auth/
    │   ├── LoginActivity.java              Logowanie z wyborem roli (Klient / Kurier)
    │   └── SignUpActivity.java             Rejestracja nowego użytkownika
    ├── client/
    │   ├── Cart.java                       Pamięć podręczna koszyka (pozycje, ilości)
    │   ├── CartActivity.java               Podgląd koszyka, wybór paczkomatu i składanie zamówienia
    │   ├── ClientActivity.java             Nawigacja dolna klienta (Główna, Zamówienia, Paczkomat)
    │   ├── ClientHomeFragment.java         Przeglądanie restauracji i menu
    │   └── OrdersFragment.java             Lista zamówień klienta (Do odbioru, W drodze, Historia)
    ├── courier/
    │   ├── CourierActivity.java            Nawigacja dolna kuriera (Zlecenia, Mapa, Statystyki)
    │   ├── CourierTasksFragment.java       Odbiór dań z restauracji i wkładanie do paczkomatu
    │   └── CourierStatsFragment.java       Statystyki wykonanych dostaw
    └── common/
        ├── LockersFragment.java            Lista i status paczkomatów w mieście
        ├── Mascot.java                     Obsługa stanów pustych/błędów i okien dialogowych z kotką
        ├── OrderDetailsActivity.java       Karta szczegółów zamówienia (oś czasu, kod, pozycje)
        ├── OrderViews.java                 Pomocnik formatowania statusów, cen i widoków dań
        └── TopBar.java                     Pasek górny z profilem, instrukcją i wylogowaniem

backend/                                    Projekt serwera i paczkomatu (Java 11)
├── server/src/main/java/pl/input/server/
│   ├── Main.java                           Główny punkt startowy serwera HTTP
│   ├── Config.java                         Ustawienia portu (8080), pliku bazy SQLite i kluczy API
│   ├── http/                               Lekki router HTTP, obsługa żądań Request i błędów ApiException
│   ├── db/
│   │   ├── Database.java                   Wrapper bazy SQLite z obsługą transakcji
│   │   └── DemoData.java                   Dane początkowe: konta, restauracje, menu i zamówienia
│   ├── logic/
│   │   ├── OrderService.java               Logika biznesowa zamówień i przydziału skrytek
│   │   └── PasswordHasher.java             Bezpieczne haszowanie haseł (SHA-256 + sól)
│   └── api/
│       ├── AuthController.java             Logowanie, rejestracja, profil
│       ├── MenuController.java             Lista restauracji i menu
│       ├── LockerController.java           Lista paczkomatów i stan skrytek
│       ├── OrderController.java            Obsługa zamówień klienta
│       ├── CourierController.java          Zlecenia, statystyki i mapa kuriera
│       └── DeviceController.java           Odpytywanie i obsługa kodów przez Raspberry Pi
└── paczkomat/src/main/java/pl/input/paczkomat/
    ├── Main.java                           Pętla obsługi paczkomatu (konsola/klawiatura + wątek poll)
    ├── ServerClient.java                   Klient HTTP łączący się z serwerem INPUT
    ├── LockHardware.java                   Wspólny interfejs otwierania zamków skrytek
    ├── SimulatedHardware.java              Symulacja otwierania skrytek na komputerze (wypisywanie w konsoli)
    └── RaspberryPiHardware.java            Fizyczne sterowanie pinami GPIO na Raspberry Pi (narzędzie pinctrl)
```

---

## Uruchomienie

Wymagania: **JDK 11 lub nowszy** (np. JDK 17) oraz Android Studio.

### 1. Uruchomienie serwera

W głównym folderze projektu:

```powershell
# Zbudowanie plików .jar serwera i paczkomatu
cd backend
.\gradlew :server:jar :paczkomat:jar
cd ..

# Uruchomienie serwera
java -jar backend/server/build/libs/server.jar
```

Domyślny port to `8080`. Poprawność działania można od razu sprawdzić w przeglądarce pod adresem: <http://localhost:8080/api/health>.

Baza danych `input.db` tworzy się automatycznie w katalogu roboczym przy pierwszym uruchomieniu i od razu wypełnia danymi demonstracyjnymi. Aby zresetować bazę do stanu początkowego, wystarczy zatrzymać serwer i usunąć plik `input.db`.

### 2. Uruchomienie symulatora paczkomatu (na komputerze)

W osobnym oknie terminala uruchom program paczkomatu dla urządzenia `A1`:

```powershell
java -jar backend/paczkomat/build/libs/paczkomat.jar A1 http://localhost:8080
```

Program nawiąże połączenie z serwerem, zsynchronizuje stan skrytek i wyświetli konsolowy interfejs. Można w nim wpisać 6-cyfrowy kod odbioru, wpisać polecenie `stan` (wyświetla status skrytek) lub `koniec`.

### 3. Uruchomienie aplikacji Android

1. Otwórz projekt w **Android Studio**.
2. W przypadku testowania na **fizycznym telefonie podłączonym kablem USB**:
   - Przekieruj port serwera za pomocą ADB:
     ```powershell
     adb reverse tcp:8080 tcp:8080
     ```
   - W aplikacji na ekranie startowym kliknij „Serwer: ... (zmień)” i wpisz:
     ```
     http://127.0.0.1:8080
     ```
3. W przypadku testowania na **emulatorze Android Studio**:
   - Ustaw adres serwera na:
     ```
     http://10.0.2.2:8080
     ```

---

## Tabela API serwera

Wszystkie żądania i odpowiedzi korzystają z formatu JSON (`application/json`). W przypadku błędu serwer zwraca obiekt `{"error": "treść błędu"}` z odpowiednim kodem HTTP (400, 401, 404 itp.).

Autoryzacja użytkowników odbywa się za pomocą nagłówka: `Authorization: Bearer <TOKEN>`.
Autoryzacja paczkomatów odbywa się nagłówkiem: `X-Locker-Key: paczkomat-tajny-klucz`.

| Metoda | Endpoint | Rola | Opis |
|---|---|---|---|
| `GET` | `/api/health` | Wszyscy | Sprawdzenie stanu serwera (zwraca `status: ok`) |
| `POST` | `/api/auth/register` | Wszyscy | Rejestracja nowego konta (`role, full_name, username, phone, password, courier_code`) |
| `POST` | `/api/auth/login` | Wszyscy | Logowanie użytkownika (`username, password, role`) |
| `POST` | `/api/auth/logout` | Zalogowany | Unieważnienie tokenu sesji |
| `GET` | `/api/me` | Zalogowany | Pobranie informacji o zalogowanym użytkowniku |
| `GET` | `/api/menu` | Zalogowany | Pełne menu: lista restauracji i oferowanych dań |
| `GET` | `/api/lockers` | Zalogowany | Lista paczkomatów, ich lokalizacje i stan skrytek |
| `GET` | `/api/orders` | Klient | Lista zamówień zalogowanego klienta |
| `POST` | `/api/orders` | Klient | Złożenie nowego zamówienia z koszyka do wybranego paczkomatu |
| `GET` | `/api/orders/{id}` | Zalogowany | Szczegółowe informacje o zamówieniu wraz z historią |
| `POST` | `/api/orders/{id}/open` | Klient | Zdalne otwarcie skrytki z jedzeniem oczekującym na odbiór |
| `POST` | `/api/orders/{id}/cancel` | Klient | Anulowanie zamówienia przed odbiorem przez kuriera |
| `GET` | `/api/courier/tasks` | Kurier | Lista zadań kuriera: oczekujące w lokalach i w drodze |
| `GET` | `/api/courier/stats` | Kurier | Podsumowanie statystyk dostaw kuriera |
| `GET` | `/api/courier/map` | Kurier | Zestawienie paczkomatów z liczbą paczek do włożenia |
| `POST` | `/api/courier/orders/{id}/collect` | Kurier | Oznaczenie odebrania jedzenia z restauracji |
| `POST` | `/api/courier/orders/{id}/deliver` | Kurier | Włożenie jedzenia do skrytki i wygenerowanie kodu odbioru |
| `POST` | `/api/device/{code}/poll` | Paczkomat | Sygnał obecności urządzenia i pobranie kolejki poleceń otwarcia |
| `POST` | `/api/device/{code}/enter-code`| Paczkomat | Weryfikacja kodu odbioru wpisanego na klawiaturze urządzenia |

---

## Konta testowe i dane demonstracyjne

Hasło do wszystkich kont demonstracyjnych to: **`haslo123`**. Kod rejestracji nowego kuriera: **`KURIER2026`**.

### Konta demonstracyjne

| Login | Hasło | Imię i nazwisko | Rola | Numer telefonu |
|---|---|---|---|---|
| `jakub` | `haslo123` | Jakub Kowalski | Klient | 500100200 |
| `anna` | `haslo123` | Anna Nowak | Klientka | 500200300 |
| `kurier` | `haslo123` | Marek Wiśniewski | Kurier | 500900900 |

### Paczkomaty w systemie

- **A1**: „Paczkomat A1”, ul. Szkolna 1 (przy wejściu do szkoły) – 6 skrytek (rozmiary: 2×A, 2×B, 2×C). To urządzenie połączone z Raspberry Pi.
- **B2**: „Paczkomat B2”, ul. Marszałkowska 10 – 8 skrytek (rozmiary: 3×A, 3×B, 2×C).
- **C3**: „Paczkomat C3”, ul. Puławska 25 – 8 skrytek (rozmiary: 3×A, 3×B, 2×C).

### Gotowe zamówienia demonstracyjne (#1041 – #1049)

Przy pierwszym starcie bazy danych automatycznie tworzone są zamówienia prezentujące każdy etap realizacji:

| Nr | Klient | Restauracja i zawartość | Paczkomat | Status | Scenariusz do przetestowania |
|---|---|---|---|---|---|
| **#1041** | Jakub | Pizzeria Bella Napoli: 2× Pizza Margherita | A1 | Odebrane | Sprawdzenie karty zamówienia w historii |
| **#1042** | Anna | Sushi Master: 2× Maki z łososiem, 1× Zupa miso | B2 | Odebrane | Sprawdzenie karty zamówienia w historii |
| **#1043** | Jakub | Zdrowa Miska: 1× Wrap z kurczakiem | A1 | Anulowane | Podgląd anulowanego zamówienia |
| **#1044** | Anna | Burger Bros: 1× Cheeseburger, 1× Frytki | C3 | Odebrane | Sprawdzenie karty zamówienia w historii |
| **#1045** | Jakub | Burger Bros: 2× Burger Classic, 1× Frytki | A1 | **Do odbioru** | **Test odbioru**: Jakub otwiera skrytkę w A1 przyciskiem lub wpisuje kod na klawiaturze |
| **#1046** | Anna | Pizzeria Bella Napoli: 1× Pizza Capricciosa, 1× Margherita | B2 | **Do odbioru** | Anna widzi kod odbioru w paczkomacie B2 |
| **#1047** | Jakub | Sushi Master: 1× Zestaw California, 2× Zupa miso | A1 | **W drodze** | Kurier wiezie jedzenie do paczkomatu A1 |
| **#1048** | Anna | Zdrowa Miska: 1× Sałatka Cezar, 2× Lemoniada | B2 | **Oczekuje** | Restauracja przygotowuje; kurier ma zlecenie do odebrania |
| **#1049** | Jakub | Pizzeria Bella Napoli: 1× Pizza Margherita | A1 | **Oczekuje** | Restauracja przygotowuje; kurier ma zlecenie do odebrania |

---

## Maskotka INPUT

Aplikacja posiada dedykowaną maskotkę (kotkę INPUT), która towarzyszy użytkownikowi, komunikując aktualne stany systemu w sposób przyjazny i czytelny.

Wszystkie pliki graficzne znajdują się w katalogu: `app/src/main/res/drawable-nodpi/`.

| Plik graficzny | Poza maskotki | Gdzie jest wykorzystywana |
|---|---|---|
| `maskotka_czesc.png` | Kotka macha łapką na powitanie | Ekran logowania (`activity_login.xml`), ekran rejestracji (`activity_sign_up.xml`), okno dialogowe „Jak działa INPUT?” |
| `maskotka_czekaj.png` | Kotka z zegarkiem / oczekująca | Stan ładowania danych w listach (`Mascot.showLoading`), etap przygotowywania dania w restauracji |
| `maskotka_blad.png` | Kotka zmartwiona | Stan błędu połączenia z serwerem lub niepowodzenia operacji (`Mascot.showError`) |
| `maskotka_sukces.png` | Kotka zadowolona / kciuk w górę | Komunikat po pomyślnym złożeniu zamówienia, pomyślne dostarczenie u kuriera, ekran statystyk |
| `maskotka_dostawa.png` | Kotka-kurierka z torbą termiczną | Status zamówienia „W drodze” w szczegółach oraz ilustracja w panelu zleceń kuriera |
| `maskotka_pomoc.png` | Kotka z dymkiem pomocy | Okno dialogowe „Pomoc i kontakt” w menu bocznym oraz instrukcja odbioru w zakładce paczkomatów |
| `maskotka_dziekujemy.png` | Kotka z serduszkiem | Ekran po wyjęciu zamówienia ze skrytki paczkomatu („Smacznego!”) |
| `maskotka_powiadomienie.png` | Kotka z dzwoneczkiem | Status „Do odbioru” – powiadomienie, że jedzenie czeka już w skrytce |
| `maskotka_szukam.png` | Kotka z lupą | Pusty koszyk, brak wyników wyszukiwania dań oraz pusta lista zamówień |

> **Podmiana grafiki:** Każdą pozę maskotki można w dowolnej chwili zmienić na inną grafikę. Wystarczy podmienić odpowiedni plik PNG w folderze `app/src/main/res/drawable-nodpi/` zachowując tę samą nazwę pliku. Kod aplikacji nie wymaga wówczas żadnych modyfikacji.

---

## Raspberry Pi i fizyczny paczkomat

Paczkomat **A1** jest przystosowany do pracy jako rzeczywiste urządzenie sterowane przez minikomputer **Raspberry Pi** z systemem **Raspberry Pi OS (Bookworm)**.

### Jak to działa sprzętowo
1. Raspberry Pi uruchamia aplikację `paczkomat.jar` w trybie sprzętowym (klasa `RaspberryPiHardware`).
2. Program cyklicznie odpytuje serwer HTTP o polecenia otwarcia przypisane do kodu urządzenia `A1`.
3. Po otrzymaniu polecenia program aktywuje odpowiedni pin GPIO za pomocą wbudowanego narzędzia `pinctrl` (np. `pinctrl set 17 op dh` na czas 1,5 sekundy), co powoduje załączenie przekaźnika.
4. Przekaźnik podaje napięcie 12 V na elektrozaczep / elektrozamek wybranej skrytki, odskakują drzwiczki i użytkownik może włożyć lub wyjąć posiłek.

### Przypisanie pinów GPIO do skrytek

| Skrytka | Rozmiar | Pin logiczny BCM | Pin fizyczny na złączu |
|---|---|---|---|
| Skrytka 1 | A | GPIO 17 | Pin 11 |
| Skrytka 2 | A | GPIO 27 | Pin 13 |
| Skrytka 3 | B | GPIO 22 | Pin 15 |
| Skrytka 4 | B | GPIO 23 | Pin 16 |
| Skrytka 5 | C | GPIO 24 | Pin 18 |
| Skrytka 6 | C | GPIO 25 | Pin 22 |

### Podłączenie elektryczne
- **Masa (GND)**: Połączona wspólnie między Raspberry Pi (np. pin 6, 9 lub 14) a modułem przekaźników.
- **Zasilanie cewek przekaźników**: 5 V z Raspberry Pi (pin 2 lub 4).
- **Zasilanie zamków**: Elektrozamki 12 V muszą być zasilane z **osobnego zewnętrznego zasilacza 12 V**. Nie wolno zasilać zamków bezpośrednio z pinów Raspberry Pi!
- **Wersja demonstracyjna bez zamków**: Zamiast elektrozamków i przekaźników można do każdego pinu GPIO podłączyć zwykłą diodę LED z rezystorem 330 Ω do masy – dioda zapala się w momencie otwarcia skrytki.
