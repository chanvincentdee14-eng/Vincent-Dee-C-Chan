package QueueSimulation;

import Patient.Patient;
import Store.Store;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * Shared "people in line" cards used by the Patient / Doctor view and the separate public display.
 * Shows only queue numbers and status, never names or other personal details.
 */
final class QueuePeopleView {
    static final Color NAVY = new Color(32, 58, 80);
    static final Color TEAL = new Color(25, 111, 116);
    static final Color AMBER = new Color(209, 148, 49);
    static final Color AMBER_DARK = new Color(157, 98, 13);
    static final Color GRAY = new Color(124, 149, 171);
    static final Color GRAY_TEXT = new Color(91, 108, 123);
    static final Color BORDER = new Color(216, 225, 232);
    static final Color LINE = new Color(129, 156, 176);

    private QueuePeopleView() {}

    /** One row per person in queue order: {queue id, kind}; kind is serving, next, hold or waiting. */
    static ArrayList<String[]> rowsFor(Store state) {
        Patient waiting = null;
        for (Patient p : state.getPatients()) {
            if ("Waiting".equals(p.getStatus())) { waiting = p; break; }
        }
        ArrayList<String[]> rows = new ArrayList<>();
        for (Patient p : state.getPatients()) {
            String kind;
            if ("Serving".equals(p.getStatus())) kind = "serving";
            else if (p == waiting) kind = "next";
            else if ("Skipped".equals(p.getStatus())) kind = "hold";
            else kind = "waiting";
            rows.add(new String[]{p.getId(), kind});
        }
        return rows;
    }

    /** Text that changes whenever the visible line changes; used to avoid redrawing on every refresh. */
    static String signature(ArrayList<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] r : rows) sb.append(r[0]).append('|').append(r[1]).append(';');
        return sb.toString();
    }

    /** Replaces the cards inside the container with cards for the given rows. */
    static void fill(JPanel container, ArrayList<String[]> rows) {
        container.removeAll();
        if (rows.isEmpty()) {
            JLabel empty = new JLabel("No one is in line right now.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 18));
            empty.setForeground(GRAY_TEXT);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setBorder(BorderFactory.createEmptyBorder(30, 12, 30, 12));
            container.add(empty);
        }
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            container.add(personCard(i + 1, row[0], row[1]));
            if (i + 1 < rows.size()) container.add(connector());
        }
        container.revalidate();
        container.repaint();
    }

    static JComponent personCard(int position, String id, String kind) {
        final boolean serving = "serving".equals(kind);
        final boolean upNext = "next".equals(kind);
        final Color accent = serving ? TEAL : upNext ? AMBER : GRAY;
        final Color statusColor = serving ? TEAL : upNext ? AMBER_DARK : GRAY_TEXT;
        String statusText = serving ? "SERVING NOW" : upNext ? "NEXT TO SERVE"
                : "hold".equals(kind) ? "ON HOLD" : "WAITING IN LINE";

        JPanel card = new JPanel(new BorderLayout(18, 0));
        card.setBackground(Color.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(serving || upNext ? accent : BORDER, serving || upNext ? 3 : 2),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));

        JComponent icon = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(accent);
                g.fillOval(18, 2, 20, 20);               // head
                g.fillRoundRect(10, 28, 36, 36, 18, 18); // shoulders and body
                g.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(56, 66); }
        };
        card.add(icon, BorderLayout.WEST);

        JLabel heading = new JLabel(position + ".  " + id);
        heading.setFont(new Font("SansSerif", Font.BOLD, 26));
        heading.setForeground(NAVY);
        JLabel status = new JLabel(statusText);
        status.setFont(new Font("SansSerif", Font.BOLD, 15));
        status.setForeground(statusColor);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 4));
        info.setOpaque(false);
        info.add(heading);
        info.add(status);
        card.add(info, BorderLayout.CENTER);
        return card;
    }

    static JComponent connector() {
        JComponent line = new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                graphics.setColor(LINE);
                graphics.fillRect(37, 0, 2, getHeight());
            }
            @Override public Dimension getPreferredSize() { return new Dimension(80, 18); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 18); }
        };
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setOpaque(false);
        return line;
    }
}
