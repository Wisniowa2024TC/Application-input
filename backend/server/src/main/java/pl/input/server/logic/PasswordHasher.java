package pl.input.server.logic;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Bezpieczne przechowywanie haseł.
 *
 * <p>Hasła NIGDY nie zapisujemy w bazie wprost. Zapisujemy tylko:</p>
 * <ul>
 *     <li>sól – losowe 16 bajtów, inne dla każdego użytkownika (dwa takie same hasła dadzą różne skróty),</li>
 *     <li>skrót hasła – wynik algorytmu PBKDF2 (120 000 powtórzeń SHA-256, celowo wolny,
 *         żeby utrudnić zgadywanie haseł metodą prób i błędów).</li>
 * </ul>
 * <p>Przy logowaniu liczymy skrót wpisanego hasła z tą samą solą i porównujemy wyniki.</p>
 *
 * <p>Algorytm jest ten sam, którego używała wcześniejsza (lokalna) wersja logowania w aplikacji.</p>
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;

    private PasswordHasher() {
    }

    /** Losuje nową sól i zwraca ją jako tekst Base64 (tak zapisujemy ją w bazie). */
    public static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** Liczy skrót hasła dla podanej soli. Wynik w Base64. */
    public static String hash(String password, String saltBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Nie udało się obliczyć skrótu hasła", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Sprawdza, czy wpisane hasło pasuje do zapisanego skrótu.
     * MessageDigest.isEqual porównuje w stałym czasie – nie da się "zmierzyć", ile znaków się zgadza.
     */
    public static boolean matches(String password, String saltBase64, String savedHashBase64) {
        byte[] entered = Base64.getDecoder().decode(hash(password, saltBase64));
        byte[] saved = Base64.getDecoder().decode(savedHashBase64);
        return MessageDigest.isEqual(entered, saved);
    }
}
