import Data.DataStorage;
import Patient.Patient;
import QueueManager.QueueManager;
import Store.Store;
import java.io.File;
import java.nio.file.Files;

public class HoldNextTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        File save = Files.createTempFile("health-center-hold", ".ser").toFile();
        try {
            QueueManager q = new QueueManager(new Store(), new DataStorage(save));
            Patient a = q.enqueue("Alpha Patient", "", "", "", "staff");
            Patient b = q.enqueue("Beta Patient", "", "", "", "staff");
            Patient c = q.enqueue("Gamma Patient", "", "", "", "staff");
            Patient d = q.enqueue("Delta Patient", "", "", "", "staff");
            check(q.current() == a && q.next() == b, "setup: A serving, B next");

            // The next waiting patient can be held while someone else is being served.
            q.hold(b, "staff");
            check("Skipped".equals(b.getStatus()), "next waiting patient placed on hold");
            check(q.current() == a, "serving patient unchanged when next is held");
            check(q.next() == c, "following patient becomes next");

            // A patient who is neither serving nor next cannot be held.
            boolean refused = false;
            try { q.hold(d, "staff"); } catch (IllegalArgumentException e) { refused = true; }
            check(refused && "Waiting".equals(d.getStatus()), "patient behind next cannot be held");

            // Holding the serving patient advances the line, as before.
            q.hold(a, "staff");
            check("Skipped".equals(a.getStatus()) && q.current() == c, "held serving patient advances the line");

            // Held patients return to the active line.
            q.returnToQueue(b, "staff");
            check("Waiting".equals(b.getStatus()) && q.current() == c, "held next patient rejoins the line");
            System.out.println("PASS: hold next waiting patient, serving hold, return to line");
        } finally {
            Files.deleteIfExists(save.toPath());
            Files.deleteIfExists(new File(save.getAbsolutePath() + ".bak").toPath());
        }
    }
}
