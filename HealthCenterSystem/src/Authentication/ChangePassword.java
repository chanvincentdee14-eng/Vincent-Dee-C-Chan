package Authentication;

import java.io.Console;
import java.util.Arrays;

/** Command-line tool to set an account password. Run in a terminal so the password is not shown on screen. */
public final class ChangePassword {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java -cp HealthCenterSystem.jar Authentication.ChangePassword <username>");
            return;
        }
        Console console = System.console();
        if (console == null) {
            System.out.println("Run this in a terminal window so the password is not shown on screen.");
            return;
        }
        char[] first = console.readPassword("New password (at least 8 characters): ");
        char[] second = console.readPassword("Type it again: ");
        if (first == null || second == null || !Arrays.equals(first, second)) {
            System.out.println("The passwords did not match. Nothing was changed.");
            return;
        }
        try {
            new AuthenticationService().setPassword(args[0], new String(first));
            System.out.println("Password updated for " + args[0] + ".");
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage() + " Nothing was changed.");
        }
    }
}
