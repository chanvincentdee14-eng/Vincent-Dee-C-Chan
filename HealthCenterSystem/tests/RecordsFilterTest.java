import QueueSimulation.QueueSimulation;
import QueueManager.QueueManager;
import Data.DataStorage;
import Store.Store;
import Patient.Patient;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** End-to-end GUI date-filter test, run with a display or under Xvfb. */
public class RecordsFilterTest {
    private static Object get(Object obj, String field) throws Exception {
        Field f = obj.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(obj);
    }
    private static void check(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        File home = Files.createTempDirectory("hc-date-filters").toFile();
        System.setProperty("user.home", home.getAbsolutePath());
        System.setProperty("hc.users", new File(home, "users.txt").getAbsolutePath());
        try {
            DataStorage storage = new DataStorage();
            Store store = new Store();
            QueueManager q = new QueueManager(store, storage);
            Patient a = q.enqueue("Today Patient", "", "", "", "staff");
            Patient b = q.enqueue("Last Month Patient", "", "", "", "staff");
            Patient c = q.enqueue("Two Years Ago Patient", "", "", "", "staff");
            b.setCheckedInAt(LocalDateTime.now().minusMonths(1).toString());
            c.setCheckedInAt(LocalDateTime.now().minusYears(2).toString());
            q.finish(a, "staff"); // b is now serving; keep all three in history.
            storage.save(store);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    QueueSimulation gui = new QueueSimulation();
                    DefaultTableModel model = (DefaultTableModel)get(gui, "recordsModel");
                    @SuppressWarnings("unchecked")
                    JComboBox<String> period = (JComboBox<String>)get(gui, "periodFilter");
                    check(model.getRowCount() == 3, "all-time includes all current and historic visits");
                    period.setSelectedItem("Today");
                    check(model.getRowCount() == 1 && "Q001".equals(model.getValueAt(0, 0)), "today filter");
                    period.setSelectedItem("Last Month");
                    check(model.getRowCount() == 1 && "Q002".equals(model.getValueAt(0, 0)), "last-month filter");
                    period.setSelectedItem("Specific Month");
                    @SuppressWarnings("unchecked")
                    JComboBox<java.time.Month> month = (JComboBox<java.time.Month>)get(gui, "filterMonth");
                    JSpinner year = (JSpinner)get(gui, "filterYear");
                    month.setSelectedItem(LocalDate.now().minusYears(2).getMonth());
                    year.setValue(LocalDate.now().minusYears(2).getYear());
                    check(model.getRowCount() == 1 && "Q003".equals(model.getValueAt(0, 0)), "historic month/year filter");
                    period.setSelectedItem("All Time");
                    check(model.getRowCount() == 3, "reset filter");
                    @SuppressWarnings("unchecked")
                    JComboBox<String> status = (JComboBox<String>)get(gui, "recordStatus");
                    status.setSelectedItem("Completed");
                    check(model.getRowCount() == 1 && "Q001".equals(model.getValueAt(0, 0)), "completed-status filter");
                    status.setSelectedItem("All Statuses");
                    JTextField search = (JTextField)get(gui, "recordSearch");
                    search.setText("Two Years Ago");
                    check(model.getRowCount() == 1 && "Q003".equals(model.getValueAt(0, 0)), "record search");
                    search.setText("");
                    JTextField patientSearch = (JTextField)get(gui, "patientSearch");
                    JTable patients = (JTable)get(gui, "patientTable");
                    patientSearch.setText("Last Month");
                    check(patients.getRowCount() == 1, "live patient search");
                    patientSearch.setText("");
                    gui.dispose();
                    System.out.println("PASS: Records all-time, today, last month, historic month/year filters");
                } catch (Exception ex) { throw new RuntimeException(ex); }
            });
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(home.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (java.io.IOException ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }
}
