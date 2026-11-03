package Data;

import Patient.Patient;
import Store.Store;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/** Atomic local-file persistence. Demo only: not encrypted or suitable for real health records. */
public class DataStorage {
    private final File file;
    private boolean restoredFromBackup;
    private static final DateTimeFormatter OLD_DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a");

    public DataStorage() {
        this(new File(System.getProperty("user.home"), "queue_simulation_oop_data.ser"));
    }
    public DataStorage(File file) { this.file = file; }
    public File getFile() { return file; }

    /** Loads the save (or last-known-good backup); refuses to overwrite unreadable data. */
    public synchronized Store loadIfExists() {
        if (!file.isFile()) {
            File backup = new File(file.getAbsolutePath() + ".bak");
            if (!backup.isFile()) return null;
            try {
                Store recovered = loadFile(backup);
                migrateHistory(recovered);
                restoredFromBackup = true;
                return recovered;
            } catch (IOException | ClassNotFoundException | ClassCastException ex) {
                throw new IllegalStateException("Backup is unreadable: " + backup.getAbsolutePath(), ex);
            }
        }
        try {
            Store current = loadFile(file);
            migrateHistory(current);
            return current;
        } catch (IOException | ClassNotFoundException | ClassCastException ex) {
            File backup = new File(file.getAbsolutePath() + ".bak");
            if (backup.isFile()) {
                try {
                    Store recovered = loadFile(backup);
                    migrateHistory(recovered);
                    restoredFromBackup = true;
                    return recovered;
                } catch (IOException | ClassNotFoundException | ClassCastException ignored) { }
            }
            throw new IllegalStateException("Saved data cannot be read. Data was not overwritten. "
                    + "Restore the backup or check file permissions: " + file.getAbsolutePath(), ex);
        }
    }

    private static Store loadFile(File source) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new SafeObjectInputStream(new FileInputStream(source))) {
            return (Store) input.readObject();
        }
    }

    public boolean wasRestoredFromBackup() { return restoredFromBackup; }

    public Store load() {
        Store saved = loadIfExists();
        return saved == null ? new Store() : saved;
    }

    /** Fill the historical list from legacy active patients and known activity-log facts. */
    private static void migrateHistory(Store store) {
        Set<String> ids = new HashSet<>();
        for (Patient p : store.getHistory()) ids.add(p.getId());
        for (Patient p : store.getPatients()) {
            if (ids.add(p.getId())) store.getHistory().add(p);
            if (p.getCheckedInAt() == null) {
                String fromLog = originalArrival(store, p.getId());
                // Never replace unknown historical information with the current time.
                p.setCheckedInAt(fromLog == null ? "" : fromLog);
            }
        }
        // Existing versions dropped completed visits. Where the activity log still
        // has one, restore known queue number, name, status and timestamps only.
        for (int i = 0; i < store.getLogs().size(); i++) {
            String[] log = store.getLogs().get(i);
            if (log == null || log.length < 7 || log[3] == null || !ids.add(log[3])) continue;
            String status = "Archived";
            if ("Done / Dequeue".equals(log[2])) status = "Completed";
            else if ("Delete".equals(log[2])) status = "Removed";
            Patient p = new Patient(log[3], log[4], "", "", "", status);
            String arrived = originalArrival(store, p.getId());
            p.setCheckedInAt(arrived == null ? "" : arrived);
            if ("Completed".equals(status) || "Removed".equals(status))
                p.setCompletedAt(readLegacyDate(log[0]));
            store.getHistory().add(p);
        }
    }

    private static String originalArrival(Store store, String id) {
        // Descending timestamp order: keep searching to obtain the original enqueue event.
        for (String[] log : store.getLogs()) {
            if (log != null && log.length >= 5 && id.equals(log[3]) && "Enqueue".equals(log[2]))
                return readLegacyDate(log[0]);
        }
        return null;
    }

    private static String readLegacyDate(String text) {
        try { return LocalDateTime.parse(text, OLD_DATE).toString(); }
        catch (Exception ex) { return ""; }
    }

    /** Creates a manual backup of the last saved data file. */
    public synchronized File backupNow() {
        if (!file.isFile()) throw new IllegalStateException("There is no saved data to back up yet.");
        File backup = new File(file.getAbsolutePath() + ".bak");
        if (restoredFromBackup && backup.isFile()) return backup;
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create a backup: " + ex.getMessage(), ex);
        }
    }

    public synchronized void save(Store store) {
        File parent = file.getAbsoluteFile().getParentFile();
        Path temp = null;
        try {
            if (parent != null && !parent.exists()) Files.createDirectories(parent.toPath());
            Path target = file.toPath();
            temp = Files.createTempFile(parent.toPath(), "queue-", ".tmp");
            try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(temp.toFile()))) {
                output.writeObject(store);
            }
            // Preserve the previous complete save before replacing it.
            if (file.isFile() && !restoredFromBackup) {
                Files.copy(target, new File(file.getAbsolutePath() + ".bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            restoredFromBackup = false;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save patient records. Check disk space and permissions: "
                    + ex.getMessage(), ex);
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }
}
