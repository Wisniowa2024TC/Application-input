package pl.input.server.http;

/**
 * Błąd, który ma trafić do aplikacji jako czytelny komunikat.
 *
 * <p>Wystarczy w dowolnym miejscu napisać np.
 * {@code throw new ApiException(400, "Hasło musi mieć minimum 8 znaków");}
 * a {@link Router} odeśle odpowiedź {@code {"error": "Hasło musi mieć minimum 8 znaków"}}
 * z podanym kodem HTTP. Aplikacja pokazuje ten tekst użytkownikowi.</p>
 *
 * <p>Najczęściej używane kody HTTP:</p>
 * <ul>
 *     <li>400 – złe dane w żądaniu (np. puste pole),</li>
 *     <li>401 – brak logowania albo sesja wygasła,</li>
 *     <li>403 – brak uprawnień (np. klient próbuje użyć funkcji kuriera),</li>
 *     <li>404 – nie znaleziono (np. nie ma zamówienia o takim numerze),</li>
 *     <li>409 – konflikt (np. brak wolnej skrytki, zamówienie jest w złym statusie).</li>
 * </ul>
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** Kod HTTP, który zostanie wysłany do klienta. */
    public int getStatus() {
        return status;
    }
}
