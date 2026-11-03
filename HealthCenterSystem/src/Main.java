import QueueSimulation.QueueSimulation;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new QueueSimulation().setVisible(true);
            } catch (IllegalStateException ex) {
                JOptionPane.showMessageDialog(null,
                        "Unable to start Health Center System.\n" + ex.getMessage()
                        + "\n\nNo saved records have been overwritten.",
                        "Data could not be loaded", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
