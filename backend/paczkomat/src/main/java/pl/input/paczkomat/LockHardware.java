package pl.input.paczkomat;

/**
 * "Sprzęt" paczkomatu – czyli to, co fizycznie otwiera drzwiczki skrytki.
 *
 * <p>Mamy dwie wersje:</p>
 * <ul>
 *     <li>{@link SimulatedHardware} – tylko wypisuje na ekranie, która skrytka się otworzyła
 *         (do testów na zwykłym komputerze),</li>
 *     <li>{@link RaspberryPiHardware} – na Raspberry Pi włącza na chwilę pin GPIO,
 *         do którego podłączony jest przekaźnik i elektrozamek (albo dioda LED w makiecie).</li>
 * </ul>
 * <p>Reszta programu nie wie, która wersja działa – woła tylko {@link #open(int)}.</p>
 */
public interface LockHardware {

    /** Otwiera skrytkę o podanym numerze (numeracja od 1, tak jak na serwerze). */
    void open(int compartmentNumber);
}
