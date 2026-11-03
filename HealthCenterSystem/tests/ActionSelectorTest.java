import QueueSimulation.QueueSimulation;
import QueueManager.QueueManager;
import Patient.Patient;
import Store.Store;
import javax.swing.*;
import java.lang.reflect.*;
import java.nio.file.*;

/** End-to-end action-selector integration, with no actual patient information. */
public class ActionSelectorTest {
    private static Object field(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(obj);
    }
    private static void check(boolean b, String explanation) {
        if (!b) throw new AssertionError(explanation);
    }
    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("hc-selector-gui-");
        System.setProperty("user.home", home.toString());
        System.setProperty("hc.users", home.resolve("users.txt").toString());
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    QueueSimulation gui = new QueueSimulation();
                    JComboBox<?> choices = (JComboBox<?>) field(gui, "patientAction");
                    JButton apply = (JButton) field(gui, "applyPatientButton");
                    JTextField name = (JTextField) field(gui, "name");
                    JTextField contact = (JTextField) field(gui, "contact");
                    JTable table = (JTable) field(gui, "patientTable");
                    Store store = (Store) field(gui, "store");
                    QueueManager manager = (QueueManager) field(gui, "manager");
                    String[] options = {"Edit", "Done / Dequeue", "Delete", "Enqueue", "Update", "Serve"};
                    check(choices.getItemCount() == options.length, "six selectable actions");
                    for (int i = 0; i < options.length; i++)
                        check(options[i].equals(choices.getItemAt(i)), "matching action option " + options[i]);
                    name.setText("Sample Patient Alpha");
                    contact.setText("09171234501");
                    apply.doClick();
                    check(store.getPatients().size() == 1, "Enqueue action registered a visit");
                    Patient a = store.getPatients().get(0);
                    check("Q001".equals(a.getId()) && "Serving".equals(a.getStatus()), "automatic serving");
                    name.setText("Sample Patient Beta");
                    contact.setText("09171234502");
                    apply.doClick();
                    check(store.getPatients().size() == 2 && table.getRowCount() == 2, "second visit shown in UI");
                    int second = -1;
                    for (int row = 0; row < table.getRowCount(); row++)
                        if ("Q002".equals(table.getValueAt(row, 0))) second = row;
                    check(second >= 0, "second visit displayed");
                    table.setRowSelectionInterval(second, second);
                    check("Edit".equals(choices.getSelectedItem()), "selection defaults to Edit");
                    check(!name.isEditable(), "selection locked until Edit action");
                    apply.doClick();
                    check(name.isEditable() && "Update".equals(choices.getSelectedItem()), "Edit unlocks form and selects Update");
                    name.setText("Sample Patient Beta Edited");
                    apply.doClick();
                    check("Sample Patient Beta Edited".equals(manager.find("Q002").getName()), "Update persists edited details");
                    check(!name.isEditable(), "Update locks form to prevent accidental changes");
                    check(store.getHistory().size() == 2, "history preserved");
                    gui.dispose();
                    System.out.println("PASS: action selector Enqueue, Edit, Update, selection state, and history");
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } finally {
            try (java.util.stream.Stream<Path> walk = Files.walk(home)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                    try { Files.deleteIfExists(f); } catch (Exception e) { throw new RuntimeException(e); }
                });
            }
        }
    }
}
