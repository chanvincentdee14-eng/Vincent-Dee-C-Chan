package QueueSimulation;

import Data.DataStorage;
import Patient.Patient;
import Store.Store;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/** Separate public-facing monitor: no names or personal patient data are displayed. */
public class PublicQueueWindow extends JFrame {

    private final DataStorage storage;
    private final Store localFallback;
    private final JLabel current = new JLabel("--", SwingConstants.CENTER);
    private final JLabel next = new JLabel("--", SwingConstants.CENTER);
    private final JLabel lastUpdate = new JLabel("Waiting for latest queue...");
    private final JPanel queueLine = new JPanel();
    private final Timer timer;
    private SwingWorker<Store, Void> reader;
    private String lastSignature = null;

    public PublicQueueWindow(DataStorage storage, Store localFallback) {
        super("Health Center - Public Queue Display");
        this.storage = storage;
        this.localFallback = localFallback;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1040, 700);
        setMinimumSize(new Dimension(780, 500));
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(new Color(244, 247, 250));
        root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));

        JLabel heading = new JLabel("COMMUNITY HEALTH CENTER - LIVE QUEUE", SwingConstants.CENTER);
        heading.setForeground(QueuePeopleView.NAVY);
        heading.setFont(new Font("SansSerif", Font.BOLD, 26));

        JPanel top = new JPanel(new BorderLayout(9, 12));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        JPanel panels = new JPanel(new GridLayout(1, 2, 15, 0));
        panels.setOpaque(false);
        panels.add(statusCard("NOW SERVING", current, QueuePeopleView.TEAL));
        panels.add(statusCard("NEXT TO SERVE", next, QueuePeopleView.NAVY));
        top.add(panels, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        // People-in-line panel, styled like the Queue Simulation display.
        queueLine.setLayout(new BoxLayout(queueLine, BoxLayout.Y_AXIS));
        queueLine.setBackground(Color.WHITE);
        queueLine.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel lineWrapper = new JPanel(new BorderLayout(0, 8));
        lineWrapper.setBackground(Color.WHITE);
        lineWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(QueuePeopleView.BORDER), BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        JLabel lineTitle = new JLabel("People in Line - Queue Number Order");
        lineTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        lineTitle.setForeground(QueuePeopleView.NAVY);
        lineTitle.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        JScrollPane pane = new JScrollPane(queueLine);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getVerticalScrollBar().setUnitIncrement(24);
        lineWrapper.add(lineTitle, BorderLayout.NORTH);
        lineWrapper.add(pane, BorderLayout.CENTER);
        root.add(lineWrapper, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(2, 1));
        footer.setOpaque(false);
        footer.add(new JLabel("Queue numbers only. Private patient information is never shown."));
        footer.add(lastUpdate);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        setVisible(true);
        updateDisplay(localFallback);
        timer = new Timer(1500, e -> loadInBackground());
        timer.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { timer.stop(); }
        });
    }

    private static JPanel statusCard(String title, JLabel label, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 16));
        heading.setForeground(color);
        card.add(heading, BorderLayout.NORTH);
        label.setFont(new Font("SansSerif", Font.BOLD, 42));
        label.setForeground(color);
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private void loadInBackground() {
        if (reader != null && !reader.isDone()) return;
        reader = new SwingWorker<Store, Void>() {
            @Override protected Store doInBackground() { return storage.loadIfExists(); }
            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    Store result = get();
                    updateDisplay(result == null ? localFallback : result);
                } catch (Exception ex) {
                    lastUpdate.setText("Unable to check latest queue - notify staff");
                }
            }
        };
        reader.execute();
    }

    private void updateDisplay(Store state) {
        Patient waiting = null, serving = null;
        for (Patient p : state.getPatients()) {
            if (serving == null && "Serving".equals(p.getStatus())) serving = p;
            if (waiting == null && "Waiting".equals(p.getStatus())) waiting = p;
        }
        current.setText(serving == null ? "--" : serving.getId());
        next.setText(waiting == null ? "--" : waiting.getId());

        ArrayList<String[]> rows = QueuePeopleView.rowsFor(state);
        String signature = QueuePeopleView.signature(rows);
        // Redraw only when the line actually changed, so the display does not flicker.
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            QueuePeopleView.fill(queueLine, rows);
        }
        lastUpdate.setText("Updated automatically at " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("hh:mm:ss a")));
    }
}
