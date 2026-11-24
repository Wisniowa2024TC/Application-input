package pl.input.paczkomat;

/**
 * Paczkomat "na niby" – zamiast otwierać zamek, wypisuje komunikat w konsoli.
 * Używany automatycznie, gdy program nie działa na Raspberry Pi.
 */
public class SimulatedHardware implements LockHardware {

    @Override
    public void open(int compartmentNumber) {
        System.out.println();
        System.out.println("   +-----------------------------+");
        System.out.println("   |  SKRYTKA NR " + pad(compartmentNumber) + " OTWARTA      |");
        System.out.println("   +-----------------------------+");
        System.out.println();
    }

    private String pad(int number) {
        return number < 10 ? number + " " : String.valueOf(number);
    }
}
