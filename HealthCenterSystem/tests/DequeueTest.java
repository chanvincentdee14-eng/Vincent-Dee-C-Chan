import Data.DataStorage;
import Patient.Patient;
import QueueManager.QueueManager;
import Store.Store;
import java.nio.file.*;

public class DequeueTest {
    private static void check(boolean result, String what) {
        if (!result) throw new AssertionError(what);
    }
    public static void main(String[] args) throws Exception {
        Path path = Files.createTempFile("hc-dequeue-", ".ser");
        try {
            Store s = new Store();
            DataStorage data = new DataStorage(path.toFile());
            QueueManager q = new QueueManager(s, data);
            Patient a = q.enqueue("First Example", "", "", "", "staff");
            Patient b = q.enqueue("Second Example", "", "", "", "staff");
            Patient c = q.enqueue("Third Example", "", "", "", "staff");
            q.dequeue(b, "staff");
            check("Dequeued".equals(b.getStatus()) && b.getCompletedAt() != null, "waiting visit dequeued with timestamp");
            check(q.current() == a && q.next() == c, "waiting patient removed without disturbing serving");
            check(s.getServed() == 0 && s.getHistory().size() == 3, "not counted as completed; history preserved");
            q.dequeue(a, "staff");
            check(q.current() == c && s.getServed() == 0, "dequeue serving advances line but does not count consultation");
            boolean invalid = false;
            try { q.dequeue(a, "staff"); } catch (IllegalArgumentException e) { invalid = true; }
            check(invalid, "cannot dequeue same patient twice");
            Store restored = data.load();
            check(restored.getPatients().size() == 1 && "Q003".equals(restored.getPatients().get(0).getId()), "queue state saved");
            check("Dequeued".equals(restored.getHistory().get(0).getStatus()) && restored.getHistory().size() == 3,
                    "dequeued history persisted");
            check(restored.getLogs().stream().anyMatch(log -> "Dequeue".equals(log[2])), "action logged");
            System.out.println("PASS: Dequeue retention, status, FIFO, audit log, persistence");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(Paths.get(path.toString() + ".bak"));
        }
    }
}
