package Components;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

/** Built-in-library calendar picker: day grid, month/year navigation, no typing required. */
public class DatePicker extends JPanel {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private final JTextField display = new JTextField();
    private final JButton openButton = new JButton("\u25be");
    private LocalDate selected;
    private LocalDate shown = LocalDate.now().withDayOfMonth(1);
    private final JPopupMenu popup = new JPopupMenu();
    // Month is picked from a grid of buttons instead of a dropdown: a dropdown inside the
    // popup closed the calendar and got clipped on screen.
    private final JButton monthButton = new JButton();
    private final JPanel monthGrid = new JPanel(new GridLayout(3, 4, 4, 4));
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 1900, 2200, 1));
    private final JPanel days = new JPanel(new GridLayout(0, 7, 2, 2));

    public DatePicker() {
        super(new BorderLayout(0, 0));
        display.setEditable(false);
        display.setBackground(Color.WHITE);
        display.setText("Select a date...");
        display.setToolTipText("Click the arrow to choose a date from the calendar");
        openButton.setFocusable(false);
        openButton.setMargin(new Insets(1, 8, 1, 8));
        add(display, BorderLayout.CENTER);
        add(openButton, BorderLayout.EAST);
        openButton.addActionListener(e -> showPopup());
        display.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { showPopup(); }
        });
        buildCalendar();
    }

    private void buildCalendar() {
        JPanel container = new JPanel(new BorderLayout(4, 6));
        container.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel heading = new JPanel(new BorderLayout(6, 0));
        JPanel center = new JPanel(new BorderLayout(4, 0));

        monthButton.setToolTipText("Click to choose a month");
        monthButton.setFocusPainted(false);
        monthButton.addActionListener(e -> cards.show(cardPanel, "months"));
        center.add(monthButton, BorderLayout.CENTER);
        yearSpinner.setEditor(new JSpinner.NumberEditor(yearSpinner, "0000"));
        yearSpinner.setPreferredSize(new Dimension(74, 28));
        center.add(yearSpinner, BorderLayout.EAST);
        heading.add(center, BorderLayout.CENTER);
        yearSpinner.addChangeListener(e -> {
            int year = (Integer) yearSpinner.getValue();
            if (shown.getYear() != year) { shown = shown.withYear(year); drawDays(); }
        });

        for (Month m : Month.values()) {
            JButton b = new JButton(m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            b.setFocusPainted(false);
            b.setMargin(new Insets(2, 2, 2, 2));
            b.addActionListener(e -> {
                shown = shown.withMonth(m.getValue());
                cards.show(cardPanel, "days");
                drawDays();
            });
            monthGrid.add(b);
        }

        cardPanel.add(days, "days");
        cardPanel.add(monthGrid, "months");
        container.add(heading, BorderLayout.NORTH);
        container.add(cardPanel, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton today = new JButton("Today");
        JButton clear = new JButton("Clear");
        today.addActionListener(e -> { setDate(LocalDate.now()); popup.setVisible(false); });
        clear.addActionListener(e -> { setDate(null); popup.setVisible(false); });
        footer.add(today);
        footer.add(clear);
        container.add(footer, BorderLayout.SOUTH);
        popup.setLayout(new BorderLayout());
        popup.add(container, BorderLayout.CENTER);
        popup.setPopupSize(318, 290);
        drawDays();
    }

    private void drawDays() {
        monthButton.setText(shown.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " \u25be");
        if ((Integer) yearSpinner.getValue() != shown.getYear()) yearSpinner.setValue(shown.getYear());
        days.removeAll();
        String[] names = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (String name : names) {
            JLabel label = new JLabel(name, SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
            days.add(label);
        }
        YearMonth month = YearMonth.from(shown);
        int offset = shown.getDayOfWeek().getValue() % 7;
        for (int i = 0; i < offset; i++) days.add(new JLabel());
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            final LocalDate date = shown.withDayOfMonth(day);
            JButton cell = new JButton(Integer.toString(day));
            cell.setFont(cell.getFont().deriveFont(11f));
            cell.setMargin(new Insets(2, 1, 2, 1));
            cell.setFocusPainted(false);
            if (date.equals(LocalDate.now())) cell.setBorder(BorderFactory.createLineBorder(new Color(27, 119, 140)));
            if (date.equals(selected)) {
                cell.setBackground(new Color(49, 117, 139));
                cell.setForeground(Color.WHITE);
            }
            cell.addActionListener(e -> { setDate(date); popup.setVisible(false); });
            days.add(cell);
        }
        days.revalidate();
        days.repaint();
    }

    private void showPopup() {
        if (!isEnabled()) return;
        shown = (selected == null ? LocalDate.now() : selected).withDayOfMonth(1);
        cards.show(cardPanel, "days");
        drawDays();
        popup.show(this, Math.min(0, getWidth() - 318), getHeight());
    }
    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (display != null) {
            display.setEnabled(enabled);
            openButton.setEnabled(enabled);
        }
    }
    public LocalDate getDate() { return selected; }
    public String getIsoDate() { return selected == null ? "" : selected.toString(); }
    public void setDate(LocalDate date) {
        selected = date;
        if (date != null) shown = date.withDayOfMonth(1);
        display.setText(date == null ? "Select a date..." : date.format(DISPLAY));
        drawDays();
    }
    public void setIsoDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) { setDate(null); return; }
        try { setDate(LocalDate.parse(iso.trim())); }
        catch (DateTimeParseException ex) { setDate(null); }
    }
}
