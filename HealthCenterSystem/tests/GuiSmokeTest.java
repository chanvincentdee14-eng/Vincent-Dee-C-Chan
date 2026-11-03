import QueueSimulation.QueueSimulation;
import Components.DatePicker;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.nio.file.Files;

public class GuiSmokeTest {
    public static void main(String[] args) throws Exception {
        File home = Files.createTempDirectory("hc-gui").toFile();
        System.setProperty("user.home", home.getAbsolutePath());
        File users = new File(home, "users.txt");
        System.setProperty("hc.users", users.getAbsolutePath());
        new Authentication.AuthenticationService(users).setPassword("admin", "admin123");
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    QueueSimulation frame = new QueueSimulation();
                    frame.setVisible(true);
                    Field userField = QueueSimulation.class.getDeclaredField("username");
                    Field passwordField = QueueSimulation.class.getDeclaredField("password");
                    Field publicField = QueueSimulation.class.getDeclaredField("publicLine");
                    Field recordsField = QueueSimulation.class.getDeclaredField("recordsModel");
                    Field patientField = QueueSimulation.class.getDeclaredField("patientsModel");
                    Field dateField = QueueSimulation.class.getDeclaredField("birthday");
                    for (Field f : new Field[]{userField, passwordField, publicField, recordsField, patientField, dateField}) f.setAccessible(true);
                    JPanel pub = (JPanel) publicField.get(frame);
                    DefaultTableModel records = (DefaultTableModel) recordsField.get(frame);
                    DefaultTableModel patients = (DefaultTableModel) patientField.get(frame);
                    if (!(pub.getComponentCount() == 0 || (pub.getComponentCount() == 1 && pub.getComponent(0) instanceof JLabel))) throw new AssertionError("private public queue only");
                    DatePicker picker = (DatePicker) dateField.get(frame);
                    picker.setDate(LocalDate.of(2006, 5, 23));
                    if (!"2006-05-23".equals(picker.getIsoDate())) throw new AssertionError("birthday date picker");
                    ((JTextField) userField.get(frame)).setText("admin");
                    ((JPasswordField) passwordField.get(frame)).setText("admin123");
                    Method login = QueueSimulation.class.getDeclaredMethod("signIn");
                    login.setAccessible(true);
                    login.invoke(frame);
                    Field currentUser = QueueSimulation.class.getDeclaredField("currentUser");
                    currentUser.setAccessible(true);
                    if (!"admin".equals(currentUser.get(frame))) throw new AssertionError("login failed");
                    if (records.getRowCount() != 0 || patients.getRowCount() != 0) throw new AssertionError("empty demo start");
                    Method homeAction = QueueSimulation.class.getDeclaredMethod("backHome");
                    homeAction.setAccessible(true);
                    homeAction.invoke(frame);
                    if (!"".equals(currentUser.get(frame))) throw new AssertionError("back home resets session");
                    frame.dispose();
                    System.out.println("PASS: Swing initialization, navigation, date picker, view-only privacy");
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
