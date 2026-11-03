import Authentication.AuthenticationService;
import Data.DataStorage;
import Patient.Patient;
import QueueManager.QueueManager;
import Store.Store;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;

public class QueueManagerTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        File save = Files.createTempFile("health-center-test", ".ser").toFile();
        try {
            Store store = new Store();
            DataStorage storage = new DataStorage(save);
            QueueManager q = new QueueManager(store, storage);
            File users = File.createTempFile("hc-users", ".txt");
            users.delete();
            AuthenticationService auth = new AuthenticationService(users);
            auth.setPassword("admin", "admin123");
            auth.setPassword("staff", "staff123");
            check(auth.authenticate("admin", "admin123"), "admin login");
            check(auth.authenticate("staff", "staff123"), "staff login");
            check(!auth.authenticate("admin", "nope"), "bad login blocked");
            check(!auth.authenticate(null, null), "null login blocked");
            boolean invalid = false;
            try { q.enqueue("", "", "", "", "admin"); }
            catch (IllegalArgumentException e) { invalid = true; }
            check(invalid, "blank patient name blocked");
            invalid = false;
            try { q.enqueue("Invalid", "", "2025-23-91", "", "admin"); }
            catch (IllegalArgumentException e) { invalid = true; }
            check(invalid, "invalid birthday blocked");

            invalid = false;
            try { q.enqueue("Invalid Phone", "ABC123", "", "", "admin"); }
            catch (IllegalArgumentException e) { invalid = true; }
            check(invalid, "invalid phone blocked");

            Patient first = q.enqueue("One", "09171234501", "", "Address 1", "admin");
            Patient second = q.enqueue("Two", "09171234502", "", "Address 2", "admin");
            Patient third = q.enqueue("Three", "09171234503", "", "Address 3", "admin");
            check(first.getId().equals("Q001") && third.getId().equals("Q003"), "queue IDs");
            check(q.current() == first && q.next() == second, "first visit starts immediately, FIFO waits");
            check(first.getCheckedInAt() != null && LocalDateTime.parse(first.getCheckedInAt()) != null,
                    "automatic arrival timestamp");
            check(store.getHistory().size() == 3, "each checked-in patient in records");
            invalid = false;
            try { q.serve(third, "staff"); }
            catch (IllegalArgumentException e) { invalid = true; }
            check(invalid, "cannot serve a patient out of order");
            q.update(second, "Second Edited", "09171234599", "2005-10-01", "New Address", "staff");
            check(second.getName().equals("Second Edited"), "update patient");
            check(store.getHistory().get(1).getName().equals("Second Edited"), "record updates together");
            check(q.possibleDuplicate("  SECOND EDITED ", "09171234599", "") == second,
                    "potential duplicate detected by name and phone");
            q.skipServing("admin");
            check("Skipped".equals(first.getStatus()) && q.current() == second,
                    "skip holds serving and promotes next");
            q.returnToQueue(first, "staff");
            check("Waiting".equals(first.getStatus()), "held patient rejoins active line");
            // The first patient was moved to the end; complete the new current first.
            q.finish(second, "admin");
            check(q.current() == third, "next waiting patient automatically promoted");
            check(q.getStore().getServed() == 1, "served count");
            check(q.find(second.getId()) == null, "done patient removed from active queue");
            check("Completed".equals(second.getStatus()) && second.getCompletedAt() != null,
                    "completed entry archived with end time");
            q.delete(third, "staff");
            check(q.current() == first, "patient on hold comes back after other visits");
            check("Removed".equals(third.getStatus()) && store.getHistory().size() == 3,
                    "removed patients retained in records");
            Store restored = storage.load();
            check(restored.getServed() == 1 && restored.getPatients().size() == 1,
                    "data survived serialization");
            check(restored.getPatients().get(0).getId().equals("Q001"), "FIFO persisted");
            check(restored.getHistory().size() == 3, "history survived restart");
            check("Completed".equals(restored.getHistory().get(1).getStatus()), "completion persisted");
            check(new File(save.getAbsolutePath() + ".bak").exists(), "automatic backup exists");
            check(restored.getLogs().size() >= 7, "activity logs persisted");
            System.out.println("PASS: login, validation, FIFO, CRUD, arrival timestamps, history, persistence, logs");
        } finally {
            Files.deleteIfExists(save.toPath());
            Files.deleteIfExists(new File(save.getAbsolutePath() + ".bak").toPath());
        }
    }
}
