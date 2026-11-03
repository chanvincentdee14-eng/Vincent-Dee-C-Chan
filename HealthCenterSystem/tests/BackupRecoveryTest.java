import Data.DataStorage;
import Store.Store;
import QueueManager.QueueManager;
import java.io.File;
import java.nio.file.Files;

public class BackupRecoveryTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        File file = Files.createTempFile("hc-recovery", ".ser").toFile();
        File backup = new File(file.getAbsolutePath() + ".bak");
        try {
            Store store = new Store();
            DataStorage storage = new DataStorage(file);
            QueueManager q = new QueueManager(store, storage);
            q.enqueue("Sample Patient", "", "", "", "test");
            q.enqueue("Other Patient", "", "", "", "test");
            check(backup.isFile(), "a backup must exist");
            Files.write(file.toPath(), new byte[]{0, 1, 2, 3});
            DataStorage restarted = new DataStorage(file);
            Store restored = restarted.load();
            check(restarted.wasRestoredFromBackup(), "backup recovery was used");
            check(restored.getPatients().size() == 1, "recovered last complete prior save");
            QueueManager recoveredQ = new QueueManager(restored, restarted);
            recoveredQ.enqueue("Third Patient", "", "", "", "test");
            check(restarted.load().getPatients().size() == 2, "recovered state can be saved");
            check(new DataStorage(backup).load().getPatients().size() == 1,
                    "valid backup preserved despite corrupted original");
            Files.write(file.toPath(), new byte[]{0, 1, 2, 3});
            Files.deleteIfExists(backup.toPath());
            boolean refused = false;
            try { new DataStorage(file).load(); }
            catch (IllegalStateException expected) { refused = true; }
            check(refused, "corrupt primary without backup must not silently erase visit history");
            System.out.println("PASS: automatic backups, backup recovery, corrupt-file protections");
        } finally {
            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(backup.toPath());
        }
    }
}
