package QueueSimulation;

import Authentication.AuthenticationService;
import Components.DatePicker;
import Data.DataStorage;
import Patient.Patient;
import QueueManager.QueueManager;
import Store.Store;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.IOException;
import java.io.BufferedWriter;
import java.util.HashMap;
import java.util.Map;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class QueueSimulation extends JFrame {
    private final DataStorage storage = new DataStorage();
    private final Store store = storage.load();
    private final QueueManager manager = new QueueManager(store, storage);
    private final AuthenticationService auth = new AuthenticationService();
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final CardLayout sections = new CardLayout();
    private final JPanel staffSections = new JPanel(sections);

    private final JTextField username = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JTextField name = new JTextField(), contact = new JTextField(),
            address = new JTextField(), queueNo = new JTextField();
    private final DatePicker birthday = new DatePicker();
    private final JTextField patientSearch = new JTextField(), recordSearch = new JTextField();
    private final JComboBox<String> recordStatus = new JComboBox<>(new String[]{"All Statuses", "Serving", "Waiting", "Skipped", "Completed", "Dequeued", "Removed"});
    private final JLabel editMode = new JLabel("New registration - ready to check in");
    private final JLabel infoBar = new JLabel("Welcome. Your changes are saved automatically.");
    private final JComboBox<String> patientAction = new JComboBox<>(new String[]{
            "Edit", "Done / Dequeue", "Delete", "Enqueue", "Update", "Serve"});
    private final JLabel actionGuide = new JLabel("Register a new visit and assign a queue number.");
    private JButton applyPatientButton, skipServingButton, returnSkippedButton,
            completeServingButton, callNextButton;
    private boolean editingPatient = false;
    private final JLabel publicServing = new JLabel("No patient currently serving"),
            publicNext = new JLabel("No patient waiting"), publicUpdated = new JLabel("Live queue - automatic updates");
    private final JLabel lifetimeStats = new JLabel("");
    private final Map<String, JButton> navigation = new HashMap<>();
    private SwingWorker<Store, Void> publicReader;

    private final JLabel waitingLabel = new JLabel("0"), servingLabel = new JLabel("0"),
            servedLabel = new JLabel("0"), allVisitsLabel = new JLabel("0"),
            nowServing = new JLabel("No patient serving"), comingNext = new JLabel("No patient waiting"),
            sessionLabel = new JLabel("");

    private final DefaultTableModel patientsModel = readOnly("Queue No.", "Patient Name", "Contact", "Birthday", "Address", "Checked In", "Status");
    private final TableRowSorter<DefaultTableModel> patientSorter = new TableRowSorter<>(patientsModel);
    private final DefaultTableModel queueModel = readOnly("Queue No.", "Patient Name", "Position", "Status", "Checked In");
    private final DefaultTableModel recordsModel = readOnly("Queue No.", "Patient Name", "Checked In", "Finished / Removed", "Visit Status");
    private final JTable patientTable = new JTable(patientsModel), queueTable = new JTable(queueModel),
            recordsTable = new JTable(recordsModel);
    private final JPanel queueLine = new JPanel();
    private final JPanel publicLine = new JPanel();
    private String publicSignature = null;
    private final JLabel recordCount = new JLabel("0 visits");
    private final JComboBox<String> periodFilter = new JComboBox<>(new String[]{
            "All Time", "Today", "This Week", "This Month", "Last Month", "This Year", "Specific Month", "Custom Dates"});
    private final JComboBox<String> recordSort = new JComboBox<>(new String[]{
            "Newest First", "Oldest First", "Patient Name A-Z", "Queue Number"});
    private final JComboBox<Month> filterMonth = new JComboBox<>(Month.values());
    private final JSpinner filterYear = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 1900, 2200, 1));
    private final DatePicker filterFrom = new DatePicker(), filterTo = new DatePicker();
    private final JPanel specificMonthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    private final JPanel customRangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    private final JPanel filterOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    private final Color navy = new Color(32, 58, 80), teal = new Color(25, 111, 116);
    private final Color bg = new Color(244, 247, 250);
    private final Color border = new Color(216, 225, 232);
    private String currentUser = "", selectedId = null, currentScreen = "login";
    private boolean refreshing = false;
    private final Timer publicPolling;
    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("MMM dd, yyyy  hh:mm a");

    private static DefaultTableModel readOnly(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
    }

    public QueueSimulation() {
        super("Community Health Center - Queue Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1230, 790);
        setMinimumSize(new Dimension(950, 640));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH); // Open in a full window.
        patientTable.setRowSorter(patientSorter);
        patientAction.setSelectedItem("Enqueue");
        updateActionGuide();
        patientSorter.setSortsOnUpdates(true);
        onTextChange(patientSearch, () -> filterPatients());
        onTextChange(recordSearch, () -> refreshRecords());
        root.add(loginPage(), "login");
        root.add(managementPage(), "manage");
        root.add(publicPage(), "public");
        setContentPane(root);
        cards.show(root, "login");
        publicPolling = new Timer(1600, e -> {
            if (!"public".equals(currentScreen)) return;
            if (publicReader != null && !publicReader.isDone()) return;
            // Read secondary viewer updates off the Swing event-dispatch thread.
            publicReader = new SwingWorker<Store, Void>() {
                @Override protected Store doInBackground() { return storage.loadIfExists(); }
                @Override protected void done() {
                    if (!"public".equals(currentScreen)) return;
                    try {
                        Store latest = get();
                        populatePublic(latest == null ? store : latest);
                    } catch (Exception ex) {
                        publicUpdated.setText("Unable to read latest queue - please notify staff");
                    }
                }
            };
            publicReader.execute();
        });
        publicPolling.start();
        refreshViews();
    }

    private static void onTextChange(JTextField field, Runnable callback) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { callback.run(); }
            @Override public void removeUpdate(DocumentEvent e) { callback.run(); }
            @Override public void changedUpdate(DocumentEvent e) { callback.run(); }
        });
    }
    private void filterPatients() {
        String term = patientSearch.getText().trim().toLowerCase(Locale.ROOT);
        patientSorter.setRowFilter(term.isEmpty() ? null : new RowFilter<DefaultTableModel, Integer>() {
            @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                for (int col : new int[]{0, 1, 2, 6}) {
                    if (String.valueOf(entry.getValue(col)).toLowerCase(Locale.ROOT).contains(term)) return true;
                }
                return false;
            }
        });
    }

    private JPanel panel(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setBackground(Color.WHITE);
        return p;
    }
    private JLabel title(String s, int fontSize) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        l.setForeground(navy);
        return l;
    }
    private JButton action(String text, Color color) {
        JButton b = new JButton(text);
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return b;
    }
    private JPanel pageBody() {
        JPanel p = panel(new BorderLayout(12, 12));
        p.setBackground(bg);
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return p;
    }
    private JScrollPane scroll(JTable table) {
        table.setRowHeight(31);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        JScrollPane pane = new JScrollPane(table);
        pane.setBorder(BorderFactory.createLineBorder(border));
        return pane;
    }

    private JPanel loginPage() {
        JPanel page = pageBody();
        JPanel header = panel(new GridLayout(2, 1, 0, 5));
        header.setBackground(navy);
        header.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
        JLabel heading = title("COMMUNITY HEALTH CENTER", 23);
        heading.setForeground(Color.WHITE);
        JLabel caption = new JLabel("Queue Management System");
        caption.setForeground(new Color(202, 224, 229));
        header.add(heading);
        header.add(caption);
        page.add(header, BorderLayout.NORTH);

        JPanel columns = panel(new GridLayout(1, 2, 20, 0));
        columns.setBackground(bg);
        JPanel staff = panel(new GridBagLayout());
        staff.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(25, 25, 25, 25)));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 8, 0);
        c.gridy = 0; staff.add(title("Administrator / Staff Login", 20), c);
        c.gridy++; staff.add(new JLabel("Restricted staff access"), c);
        c.gridy++; c.insets = new Insets(20, 0, 4, 0); staff.add(new JLabel("Username"), c);
        c.gridy++; c.insets = new Insets(0, 0, 10, 0); username.setPreferredSize(new Dimension(280, 36)); staff.add(username, c);
        c.gridy++; c.insets = new Insets(0, 0, 4, 0); staff.add(new JLabel("Password"), c);
        c.gridy++; c.insets = new Insets(0, 0, 9, 0); password.setPreferredSize(new Dimension(280, 36)); staff.add(password, c);
        c.gridy++; JCheckBox showPassword = new JCheckBox("Show password");
        showPassword.setBackground(Color.WHITE);
        char echo = password.getEchoChar();
        showPassword.addActionListener(e -> password.setEchoChar(showPassword.isSelected() ? (char) 0 : echo));
        staff.add(showPassword, c);
        c.gridy++; c.insets = new Insets(17, 0, 8, 0);
        JButton sign = action("LOG IN", navy);
        sign.addActionListener(e -> signIn());
        staff.add(sign, c);
        getRootPane().setDefaultButton(sign);
        c.gridy++; c.weighty = 1; staff.add(new JLabel(" "), c);

        JPanel viewer = panel(new GridBagLayout());
        viewer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(25, 25, 25, 25)));
        GridBagConstraints v = new GridBagConstraints();
        v.gridx = 0; v.fill = GridBagConstraints.HORIZONTAL; v.weightx = 1;
        v.insets = new Insets(5, 0, 15, 0);
        v.gridy = 0; viewer.add(title("Patient / Doctor Queue View", 20), v);
        v.gridy++; viewer.add(new JLabel("No login required."), v);
        v.gridy++; viewer.add(new JLabel("Only queue numbers and positions are displayed."), v);
        v.gridy++; viewer.add(new JLabel("The display updates automatically."), v);
        v.gridy++; JButton publicView = action("VIEW LIVE QUEUE", teal);
        publicView.addActionListener(e -> showPublic());
        viewer.add(publicView, v);
        v.gridy++; v.weighty = 1; viewer.add(new JLabel(" "), v);
        columns.add(staff);
        columns.add(viewer);
        page.add(columns, BorderLayout.CENTER);
        return page;
    }

    private JPanel managementPage() {
        JPanel page = panel(new BorderLayout());
        JPanel header = panel(new BorderLayout(10, 0));
        header.setBorder(BorderFactory.createEmptyBorder(15, 18, 15, 18));
        header.add(title("Community Health Center", 23), BorderLayout.WEST);
        JPanel topActions = panel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        topActions.add(sessionLabel);
        JButton openPublic = action("OPEN PUBLIC DISPLAY", teal);
        openPublic.setToolTipText("Open the queue-only monitor while staff keeps working");
        openPublic.addActionListener(e -> new PublicQueueWindow(storage, store));
        topActions.add(openPublic);
        JButton home = new JButton("Log Out");
        home.addActionListener(e -> backHome());
        topActions.add(home);
        header.add(topActions, BorderLayout.EAST);
        page.add(header, BorderLayout.NORTH);

        JPanel sidebar = panel(new BorderLayout());
        JPanel sidebarItems = panel(null);
        sidebarItems.setLayout(new BoxLayout(sidebarItems, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(234, 241, 245));
        sidebarItems.setBackground(new Color(234, 241, 245));
        sidebar.setBorder(BorderFactory.createEmptyBorder(15, 12, 15, 12));
        sidebar.setPreferredSize(new Dimension(206, 0));
        String[] destinations = {"Dashboard", "Patients", "Queue Simulation", "Records"};
        for (String destination : destinations) {
            JButton nav = new JButton(destination);
            nav.setFont(new Font("SansSerif", Font.BOLD, 14));
            nav.setHorizontalAlignment(SwingConstants.LEFT);
            nav.setFocusPainted(false);
            nav.setBorder(BorderFactory.createEmptyBorder(13, 14, 13, 8));
            nav.setOpaque(true);
            nav.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            navigation.put(destination, nav);
            nav.addActionListener(e -> showSection(destination));
            sidebarItems.add(nav);
            sidebarItems.add(Box.createVerticalStrut(10));
        }
        sidebar.add(sidebarItems, BorderLayout.NORTH);
        page.add(sidebar, BorderLayout.WEST);
        staffSections.add(dashboardPage(), "Dashboard");
        staffSections.add(patientsPage(), "Patients");
        staffSections.add(queuePage(), "Queue Simulation");
        staffSections.add(recordsPage(), "Records");
        showSection("Dashboard");
        page.add(staffSections, BorderLayout.CENTER);
        infoBar.setForeground(teal);
        infoBar.setBorder(BorderFactory.createEmptyBorder(9, 18, 10, 18));
        page.add(infoBar, BorderLayout.SOUTH);
        return page;
    }

    private void showSection(String section) {
        sections.show(staffSections, section);
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) {
            boolean active = section.equals(entry.getKey());
            JButton nav = entry.getValue();
            nav.setBackground(active ? navy : new Color(234, 241, 245));
            nav.setForeground(active ? Color.WHITE : navy);
        }
        refreshViews();
    }

    private JPanel dashboardPage() {
        JPanel body = pageBody();
        body.add(title("Dashboard Overview", 25), BorderLayout.NORTH);
        JPanel contents = panel(new BorderLayout(13, 13));
        contents.setBackground(bg);
        JPanel stats = panel(new GridLayout(1, 4, 12, 0));
        stats.setBackground(bg);
        stats.add(stat("WAITING", waitingLabel));
        stats.add(stat("SERVING", servingLabel));
        stats.add(stat("COMPLETED TODAY", servedLabel));
        stats.add(stat("VISITS TODAY", allVisitsLabel));
        contents.add(stats, BorderLayout.NORTH);
        JPanel current = panel(new GridLayout(2, 1, 0, 18));
        current.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(26, 26, 26, 26)));
        JPanel first = panel(new BorderLayout(6, 6));
        first.add(title("NOW SERVING", 15), BorderLayout.NORTH);
        nowServing.setFont(new Font("SansSerif", Font.BOLD, 27));
        first.add(nowServing, BorderLayout.CENTER);
        JPanel second = panel(new BorderLayout(6, 6));
        second.add(title("NEXT TO SERVE", 15), BorderLayout.NORTH);
        comingNext.setFont(new Font("SansSerif", Font.BOLD, 24));
        second.add(comingNext, BorderLayout.CENTER);
        current.add(first); current.add(second);
        contents.add(current, BorderLayout.CENTER);
        JPanel dashboardFooter = panel(new BorderLayout(8, 9));
        dashboardFooter.setBackground(bg);
        dashboardFooter.add(lifetimeStats, BorderLayout.SOUTH);
        contents.add(dashboardFooter, BorderLayout.SOUTH);
        body.add(contents, BorderLayout.CENTER);
        return body;
    }

    private JPanel stat(String heading, JLabel value) {
        JPanel box = panel(new GridLayout(2, 1, 0, 4));
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(15, 14, 15, 14)));
        JLabel t = title(heading, 13);
        t.setHorizontalAlignment(SwingConstants.CENTER);
        value.setFont(new Font("SansSerif", Font.BOLD, 31));
        value.setHorizontalAlignment(SwingConstants.CENTER);
        box.add(t); box.add(value);
        return box;
    }

    private JPanel patientsPage() {
        JPanel body = pageBody();
        JPanel heading = panel(new BorderLayout());
        heading.add(title("Patients - Check In & CRUD", 23), BorderLayout.WEST);
        heading.add(new JLabel("Arrival time is recorded automatically"), BorderLayout.EAST);
        body.add(heading, BorderLayout.NORTH);
        JPanel area = panel(new BorderLayout(10, 10));
        JPanel tableHolder = panel(new BorderLayout(4, 4));
        JPanel patientSearchBar = panel(new BorderLayout(7, 5));
        patientSearchBar.add(new JLabel("Search name, queue number or contact:"), BorderLayout.WEST);
        patientSearch.setToolTipText("Search active patients by name, queue ID, contact, or status");
        patientSearchBar.add(patientSearch, BorderLayout.CENTER);
        tableHolder.add(patientSearchBar, BorderLayout.NORTH);
        tableHolder.add(scroll(patientTable), BorderLayout.CENTER);
        area.add(tableHolder, BorderLayout.CENTER);
        JPanel form = panel(new BorderLayout(6, 9));
        form.setPreferredSize(new Dimension(286, 0));
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        JPanel formHeader = panel(new GridLayout(2, 1, 0, 3));
        formHeader.add(title("Patient Registration", 17));
        editMode.setFont(editMode.getFont().deriveFont(11f));
        editMode.setForeground(teal);
        formHeader.add(editMode);
        form.add(formHeader, BorderLayout.NORTH);
        JPanel fields = panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 5, 0);
        int row = 0;
        row = addField(fields, c, row, "Queue Number (automatic)", queueNo);
        queueNo.setEditable(false);
        queueNo.setText("Auto-generated");
        row = addField(fields, c, row, "Full Name *", name);
        row = addField(fields, c, row, "Contact Number", contact);
        row = addField(fields, c, row, "Birthday (choose from calendar)", birthday);
        row = addField(fields, c, row, "Address", address);
        c.gridy = row; c.weighty = 1;
        fields.add(new JLabel(" "), c);
        form.add(fields, BorderLayout.CENTER);
        // A single context-sensitive action selector replaces four stacked buttons.
        JPanel actionPanel = panel(new BorderLayout(0, 9));
        actionPanel.setBackground(new Color(235, 244, 247));
        actionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        JPanel selectorHeading = new JPanel(new BorderLayout(4, 0));
        selectorHeading.setOpaque(false);
        selectorHeading.add(title("Select Action", 15), BorderLayout.WEST);
        JButton newPatient = new JButton("New / Clear");
        newPatient.setToolTipText("Start a fresh registration and clear the selected patient");
        newPatient.setFont(new Font("SansSerif", Font.PLAIN, 11));
        newPatient.setForeground(teal);
        newPatient.setContentAreaFilled(false);
        newPatient.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        newPatient.addActionListener(e -> clearForm());
        selectorHeading.add(newPatient, BorderLayout.EAST);
        actionPanel.add(selectorHeading, BorderLayout.NORTH);
        JPanel pickerPanel = new JPanel(new BorderLayout(0, 7));
        pickerPanel.setOpaque(false);
        patientAction.setPreferredSize(new Dimension(240, 32));
        patientAction.setToolTipText("Choose Edit, Done / Dequeue, Delete, Enqueue, Update or Serve");
        pickerPanel.add(patientAction, BorderLayout.NORTH);
        actionGuide.setFont(new Font("SansSerif", Font.PLAIN, 10));
        actionGuide.setForeground(navy);
        pickerPanel.add(actionGuide, BorderLayout.SOUTH);
        actionPanel.add(pickerPanel, BorderLayout.CENTER);
        applyPatientButton = action("APPLY ACTION", teal);
        applyPatientButton.setToolTipText("Perform the selected action on this patient or the new registration");
        applyPatientButton.addActionListener(e -> applyPatientAction());
        actionPanel.add(applyPatientButton, BorderLayout.SOUTH);
        patientAction.addActionListener(e -> updateActionGuide());
        form.add(actionPanel, BorderLayout.SOUTH);
        area.add(form, BorderLayout.EAST);
        body.add(area, BorderLayout.CENTER);
        patientTable.getSelectionModel().addListSelectionListener(e -> {
            if (!refreshing && !e.getValueIsAdjusting()) selectFrom(patientTable, patientsModel);
        });
        return body;
    }

    private static int addField(JPanel fields, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridy = row++; c.insets = new Insets(6, 0, 2, 0);
        fields.add(new JLabel(label), c);
        c.gridy = row++; c.insets = new Insets(0, 0, 3, 0);
        field.setPreferredSize(new Dimension(230, 32));
        fields.add(field, c);
        return row;
    }

    private JPanel queuePage() {
        JPanel body = pageBody();
        body.add(title("Queue Simulation - First In, First Out", 23), BorderLayout.NORTH);
        JPanel contents = panel(new GridLayout(1, 2, 14, 0));
        JPanel linePane = panel(new BorderLayout(8, 10));
        linePane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        linePane.add(title("People in Line - Queue Number Order", 17), BorderLayout.NORTH);
        queueLine.setLayout(new BoxLayout(queueLine, BoxLayout.Y_AXIS));
        queueLine.setBackground(Color.WHITE);
        JScrollPane lineScroll = new JScrollPane(queueLine);
        lineScroll.setBorder(null);
        lineScroll.getVerticalScrollBar().setUnitIncrement(16);
        linePane.add(lineScroll, BorderLayout.CENTER);
        JPanel queueActions = panel(new GridLayout(2, 2, 7, 7));
        callNextButton = action("CALL NEXT", teal);
        completeServingButton = action("COMPLETE & CALL NEXT", navy);
        skipServingButton = action("PLACE ON HOLD", new Color(165, 107, 35));
        returnSkippedButton = action("RETURN SELECTED TO LINE", teal);
        callNextButton.addActionListener(e -> callNext());
        completeServingButton.addActionListener(e -> completeServing());
        skipServingButton.addActionListener(e -> skipServing());
        returnSkippedButton.addActionListener(e -> returnSkipped());
        queueActions.add(completeServingButton); queueActions.add(skipServingButton);
        queueActions.add(callNextButton); queueActions.add(returnSkippedButton);
        linePane.add(queueActions, BorderLayout.SOUTH);
        contents.add(linePane);
        JPanel tablePane = panel(new BorderLayout(8, 8));
        tablePane.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        tablePane.add(title("Queue Details", 17), BorderLayout.NORTH);
        tablePane.add(scroll(queueTable), BorderLayout.CENTER);
        JLabel note = new JLabel("Active line follows queue order. On Hold patients may rejoin at the end.");
        tablePane.add(note, BorderLayout.SOUTH);
        contents.add(tablePane);
        body.add(contents, BorderLayout.CENTER);
        queueTable.getSelectionModel().addListSelectionListener(e -> {
            if (!refreshing && !e.getValueIsAdjusting()) {
                selectFrom(queueTable, queueModel);
                updateQueueControls();
            }
        });
        return body;
    }

    private JPanel recordsPage() {
        JPanel body = pageBody();
        JPanel heading = panel(new BorderLayout());
        heading.add(title("Records - All Patient Visits", 23), BorderLayout.WEST);
        heading.add(recordCount, BorderLayout.EAST);
        body.add(heading, BorderLayout.NORTH);
        JPanel contents = panel(new BorderLayout(7, 9));
        JPanel filters = panel(new BorderLayout(6, 6));
        JPanel primaryFilters = panel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        primaryFilters.add(new JLabel("Period:"));
        primaryFilters.add(periodFilter);
        primaryFilters.add(new JLabel("Sort:"));
        primaryFilters.add(recordSort);
        primaryFilters.add(new JLabel("Status:"));
        primaryFilters.add(recordStatus);
        filters.add(primaryFilters, BorderLayout.NORTH);
        JPanel searchAndExport = panel(new BorderLayout(10, 4));
        searchAndExport.add(new JLabel("Search visit history:"), BorderLayout.WEST);
        searchAndExport.add(recordSearch, BorderLayout.CENTER);
        JButton exportButton = action("EXPORT CSV", teal);
        exportButton.setToolTipText("Export the filtered visit records as a spreadsheet-friendly CSV file");
        exportButton.addActionListener(e -> exportRecords());
        JButton backupButton = new JButton("Back Up Data");
        backupButton.setToolTipText("Save a backup of your last stored data");
        backupButton.addActionListener(e -> {
            try { storage.backupNow(); message("Backup created next to your saved file."); }
            catch (IllegalStateException ex) { message(ex.getMessage()); }
        });
        JPanel recordButtons = panel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton printButton = new JButton("Print Report");
        printButton.addActionListener(e -> {
            try { recordsTable.print(JTable.PrintMode.FIT_WIDTH); }
            catch (java.awt.print.PrinterException ex) { message("Print failed: " + ex.getMessage()); }
        });
        recordButtons.add(printButton);
        recordButtons.add(backupButton);
        recordButtons.add(exportButton);
        searchAndExport.add(recordButtons, BorderLayout.EAST);
        filterMonth.setSelectedItem(LocalDate.now().getMonth());
        specificMonthPanel.add(new JLabel("Month:")); specificMonthPanel.add(filterMonth);
        specificMonthPanel.add(new JLabel("Year:")); specificMonthPanel.add(filterYear);
        customRangePanel.add(new JLabel("From:"));
        filterFrom.setPreferredSize(new Dimension(137, 30)); customRangePanel.add(filterFrom);
        customRangePanel.add(new JLabel("To:"));
        filterTo.setPreferredSize(new Dimension(137, 30)); customRangePanel.add(filterTo);
        JButton applyRange = new JButton("Apply Dates");
        applyRange.addActionListener(e -> refreshRecords());
        customRangePanel.add(applyRange);
        filterOptions.add(specificMonthPanel);
        filterOptions.add(customRangePanel);
        filters.add(filterOptions, BorderLayout.SOUTH);
        JPanel filterTop = panel(new BorderLayout(3, 6));
        filterTop.add(filters, BorderLayout.NORTH);
        filterTop.add(searchAndExport, BorderLayout.SOUTH);
        contents.add(filterTop, BorderLayout.NORTH);
        contents.add(scroll(recordsTable), BorderLayout.CENTER);
        JLabel note = new JLabel("Records include checked-in, completed, dequeued, and removed visits. Dates use original check-in time.");
        contents.add(note, BorderLayout.SOUTH);
        body.add(contents, BorderLayout.CENTER);
        periodFilter.addActionListener(e -> { updateFilterVisibility(); refreshRecords(); });
        recordSort.addActionListener(e -> refreshRecords());
        recordStatus.addActionListener(e -> refreshRecords());
        filterMonth.addActionListener(e -> refreshRecords());
        filterYear.addChangeListener(e -> refreshRecords());
        updateFilterVisibility();
        return body;
    }

    private JPanel publicPage() {
        JPanel body = pageBody();
        JPanel header = panel(new BorderLayout(12, 0));
        header.add(title("Live Queue - Patient / Doctor View", 24), BorderLayout.WEST);
        JPanel right = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton back = new JButton("Log Out");
        back.addActionListener(e -> backHome());
        right.add(back);
        header.add(right, BorderLayout.EAST);
        body.add(header, BorderLayout.NORTH);

        JPanel center = panel(new BorderLayout(11, 11));
        center.setBackground(bg);
        JPanel currentStatus = panel(new GridLayout(1, 2, 12, 0));
        currentStatus.setBackground(bg);
        currentStatus.add(publicSummary("NOW SERVING", publicServing, teal));
        currentStatus.add(publicSummary("NEXT TO SERVE", publicNext, navy));
        center.add(currentStatus, BorderLayout.NORTH);
        publicLine.setLayout(new BoxLayout(publicLine, BoxLayout.Y_AXIS));
        publicLine.setBackground(Color.WHITE);
        publicLine.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane lineScroll = new JScrollPane(publicLine);
        lineScroll.setBorder(BorderFactory.createLineBorder(border));
        lineScroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel linePane = panel(new BorderLayout(8, 10));
        linePane.add(title("People in Line - Queue Number Order", 18), BorderLayout.NORTH);
        linePane.add(lineScroll, BorderLayout.CENTER);
        center.add(linePane, BorderLayout.CENTER);
        body.add(center, BorderLayout.CENTER);
        JPanel footer = panel(new BorderLayout(5, 2));
        footer.add(new JLabel("Queue numbers only - names, contact details, and addresses are hidden."), BorderLayout.NORTH);
        publicUpdated.setForeground(teal);
        footer.add(publicUpdated, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH);
        return body;
    }
    private JPanel publicSummary(String heading, JLabel value, Color accent) {
        JPanel card = panel(new BorderLayout(6, 12));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)));
        JLabel headingLabel = title(heading, 16);
        headingLabel.setForeground(accent);
        card.add(headingLabel, BorderLayout.NORTH);
        value.setFont(new Font("SansSerif", Font.BOLD, 31));
        value.setForeground(navy);
        card.add(value, BorderLayout.CENTER);
        return card;
    }
    private void signIn() {
        String user = username.getText().trim().toLowerCase(Locale.ROOT);
        if (!auth.authenticate(user, new String(password.getPassword()))) {
            message("Incorrect username or password.");
            return;
        }
        currentUser = user;
        sessionLabel.setText(user.equals("admin") ? "Administrator" : "Staff");
        password.setText("");
        currentScreen = "manage";
        refreshViews();
        cards.show(root, "manage");
        showSection("Dashboard");
        if (storage.wasRestoredFromBackup())
            message("The original save was unreadable; the last backup was recovered. Please check your data.");
    }
    private void backHome() {
        currentUser = "";
        clearForm();
        currentScreen = "login";
        cards.show(root, "login");
    }
    private void showPublic() {
        currentScreen = "public";
        Store latest = storage.loadIfExists();
        populatePublic(latest == null ? store : latest);
        cards.show(root, "public");
    }

    private void enqueue() {
        try {
            if (selectedId != null) {
                message("A patient is selected. Click New / Clear to register another patient.");
                return;
            }
            Patient possible = manager.possibleDuplicate(name.getText(), contact.getText(), birthday.getIsoDate());
            if (possible != null && JOptionPane.showConfirmDialog(this,
                    "This person may already be checked in (" + possible.getId()
                    + "). Register another visit anyway?", "Possible duplicate",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            Patient p = manager.enqueue(name.getText(), contact.getText(), birthday.getIsoDate(),
                    address.getText(), currentUser);
            clearForm(); refreshViews();
            success(p.getId() + " registered at " + displayTime(p.getCheckedInAt()) + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    private void update() {
        try {
            manager.update(manager.find(selectedId), name.getText(), contact.getText(), birthday.getIsoDate(),
                    address.getText(), currentUser);
            editingPatient = false;
            setPatientFieldsEditable(false);
            editMode.setText("Selected " + selectedId + " - choose Edit to change details");
            refreshViews();
            success("Changes saved for " + selectedId + ". Public queue updated automatically.");
        } catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    private void deleteSelected() {
        Patient p = manager.find(selectedId);
        if (p == null) { message("Select a patient from the Patients table first."); return; }
        if (JOptionPane.showConfirmDialog(this, "Remove " + p.getId() + " from the active queue?\nThe visit will remain in Records.",
                "Confirm removal", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            manager.delete(p, currentUser);
            clearForm(); refreshViews();
            success(p.getId() + " removed from the active queue. Its visit remains in Records.");
        } catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    private void completeServing() {
        Patient p = manager.current();
        if (p == null) { message("No patient is currently being served."); return; }
        try {
            manager.finish(p, currentUser);
            clearForm(); refreshViews();
            success(p.getId() + " completed. The next waiting patient was called automatically.");
        } catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    private void skipServing() {
        Patient target = holdTarget(manager.find(selectedId));
        if (target == null) return;
        String role = "Serving".equals(target.getStatus()) ? "currently serving" : "next in line";
        int answer = JOptionPane.showConfirmDialog(this, "Place " + target.getId() + " (" + role + ") on hold?\n"
                + "They can rejoin the end of the line later from Return Selected to Line.", "Place on Hold",
                JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            manager.hold(target, currentUser);
            clearForm(); refreshViews();
            success(target.getId() + " placed on hold. Select it and click Return Selected to Line when ready.");
        } catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    /** The patient to hold: the selected one if it is serving or next, otherwise the serving patient, otherwise the next patient. */
    private Patient holdTarget(Patient selected) {
        Patient serving = manager.current(), next = manager.next();
        if (selected != null) {
            if (selected == serving || selected == next) return selected;
            message("Select the serving patient or the next waiting patient to place on hold.");
            return null;
        }
        if (serving != null) return serving;
        if (next != null) return next;
        message("No one is serving or waiting to be placed on hold.");
        return null;
    }
    private void returnSkipped() {
        Patient selected = manager.find(selectedId);
        try {
            manager.returnToQueue(selected, currentUser);
            clearForm(); refreshViews();
            success(selected.getId() + " returned to the waiting line.");
        } catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    private void updateQueueControls() {
        boolean hasServing = manager.current() != null;
        if (completeServingButton != null) completeServingButton.setEnabled(hasServing);
        if (skipServingButton != null) skipServingButton.setEnabled(hasServing || manager.next() != null);
        if (callNextButton != null) callNextButton.setEnabled(!hasServing && manager.next() != null);
        Patient p = manager.find(selectedId);
        if (returnSkippedButton != null) returnSkippedButton.setEnabled(p != null && "Skipped".equals(p.getStatus()));
    }
    private void callNext() {
        if (manager.current() != null) {
            message("Complete the serving patient first. The next person will move forward automatically.");
            return;
        }
        Patient next = manager.next();
        if (next == null) { message("There are no waiting patients."); return; }
        try { manager.serve(next, currentUser); refreshViews(); success(next.getId() + " called to consultation."); }
        catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    /** Actions use the same selected patient whether chosen in Patients or Queue Simulation. */
    private void applyPatientAction() {
        String choice = String.valueOf(patientAction.getSelectedItem());
        Patient selected = manager.find(selectedId);
        switch (choice) {
            case "Enqueue":
                enqueue();
                break;
            case "Edit":
                if (selected == null) { message("Select a patient in the table first, or choose Enqueue for a new visit."); return; }
                editingPatient = true;
                setPatientFieldsEditable(true);
                editMode.setText("Editing " + selectedId + " - select Update to save");
                patientAction.setSelectedItem("Update");
                name.requestFocusInWindow();
                success("Editing " + selectedId + ". Change the fields and select Update > Apply Action to save.");
                break;
            case "Update":
                if (selected == null || !editingPatient) {
                    message("Select a patient, choose Edit > Apply Action, then choose Update to save.");
                    return;
                }
                update();
                break;
            case "Done / Dequeue":
                if (selected == null) { message("Select a patient first, then choose how the visit ends."); return; }
                String outcome = chooseOutcome(selected);
                if ("Done".equals(outcome)) markDone(selected);
                else if ("Dequeue".equals(outcome)) markDequeued(selected);
                break;
            case "Delete":
                deleteSelected();
                break;
            case "Serve":
                if (selected == null) { message("Select the next waiting patient first."); return; }
                if (selected != manager.next()) { message("Only the next waiting patient can be served, in queue order."); return; }
                if (manager.current() != null) { message("Another patient is currently serving. Complete or place that visit on hold first."); return; }
                callNext();
                break;
            default:
                message("Select an action to continue.");
        }
    }
    private String chooseOutcome(Patient selected) {
        Object[] options = {"Done", "Dequeue", "Cancel"};
        int pick = JOptionPane.showOptionDialog(this,
                selected.getId() + " - how did this visit end?\n"
                        + "Done: consultation completed; the next patient is called.\n"
                        + "Dequeue: the patient left without a consultation.",
                "Done / Dequeue", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[2]);
        return pick >= 0 && pick < 2 ? (String) options[pick] : "";
    }
    private void markDone(Patient selected) {
        if (selected != manager.current()) {
            message("Select the currently serving patient before marking Done.");
            return;
        }
        if (!confirm("Finish consultation for " + selected.getId() + "?\nThe next patient will be called automatically.", "Confirm consultation")) return;
        completeServing();
    }
    private void markDequeued(Patient selected) {
        if (!confirm("Dequeue " + selected.getId() + " without completing consultation?\nThe visit will be retained in Records as Dequeued.", "Confirm dequeue")) return;
        try {
            manager.dequeue(selected, currentUser);
            clearForm(); refreshViews();
            success(selected.getId() + " dequeued; record retained and queue advanced.");
        } catch (IllegalArgumentException | IllegalStateException ex) { message(ex.getMessage()); }
    }
    private boolean confirm(String description, String heading) {
        return JOptionPane.showConfirmDialog(this, description, heading,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
    private void updateActionGuide() {
        if (actionGuide == null) return;
        String choice = String.valueOf(patientAction.getSelectedItem());
        switch (choice) {
            case "Enqueue": actionGuide.setText("Register visit, assign Q-number."); break;
            case "Edit": actionGuide.setText("Unlock selected patient's details."); break;
            case "Update": actionGuide.setText("Save edited patient information."); break;
            case "Done / Dequeue": actionGuide.setText("Finish serving visit, or record a patient who left."); break;
            case "Delete": actionGuide.setText("Cancel a mistaken registration."); break;
            case "Serve": actionGuide.setText("Call next waiting patient."); break;
            default: actionGuide.setText("Choose an action.");
        }
    }
    private void setPatientFieldsEditable(boolean editable) {
        name.setEditable(editable);
        contact.setEditable(editable);
        address.setEditable(editable);
        birthday.setEnabled(editable);
        Color shade = editable ? Color.WHITE : new Color(241, 244, 246);
        name.setBackground(shade);
        contact.setBackground(shade);
        address.setBackground(shade);
    }
    private void selectFrom(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int index = table.convertRowIndexToModel(row);
        Patient p = manager.find(String.valueOf(model.getValueAt(index, 0)));
        if (p == null) return;
        selectedId = p.getId();
        editingPatient = false;
        queueNo.setText(p.getId());
        name.setText(p.getName());
        contact.setText(p.getContact());
        birthday.setIsoDate(p.getBirthday());
        address.setText(p.getAddress());
        setPatientFieldsEditable(false);
        editMode.setText("Selected " + p.getId() + " - choose an action");
        patientAction.setSelectedItem("Edit");
        updateQueueControls();
    }
    private void clearForm() {
        selectedId = null;
        editingPatient = false;
        queueNo.setText("Auto-generated"); name.setText(""); contact.setText("");
        birthday.setDate(null); address.setText("");
        editMode.setText("New registration - ready to check in");
        setPatientFieldsEditable(true);
        patientAction.setSelectedItem("Enqueue");
        refreshing = true;
        patientTable.clearSelection(); queueTable.clearSelection();
        refreshing = false;
        updateQueueControls();
    }

    private void refreshViews() {
        refreshing = true;
        try {
            patientsModel.setRowCount(0);
            queueModel.setRowCount(0);
            int waiting = 0, serving = 0, position = 0;
            Patient next = manager.next();
            for (Patient p : store.getPatients()) {
                position++;
                String status = p == next ? "Next to Serve" : p.getStatus();
                if ("Waiting".equals(p.getStatus())) waiting++;
                if ("Serving".equals(p.getStatus())) serving++;
                patientsModel.addRow(new Object[]{p.getId(), p.getName(), p.getContact(),
                        p.getBirthday().isEmpty() ? "-" : p.getBirthday(), p.getAddress(),
                        displayTime(p.getCheckedInAt()), status});
                queueModel.addRow(new Object[]{p.getId(), p.getName(), position, status,
                        displayTime(p.getCheckedInAt())});
            }
            waitingLabel.setText(Integer.toString(waiting));
            servingLabel.setText(Integer.toString(serving));
            int visitsToday = 0, completedToday = 0;
            for (Patient visit : store.getHistory()) {
                if (visit.getCheckedInAt() != null && visit.getCheckedInAt().startsWith(LocalDate.now().toString()))
                    visitsToday++;
                if ("Completed".equals(visit.getStatus()) && visit.getCompletedAt() != null
                        && visit.getCompletedAt().startsWith(LocalDate.now().toString())) completedToday++;
            }
            servedLabel.setText(Integer.toString(completedToday));
            allVisitsLabel.setText(Integer.toString(visitsToday));
            lifetimeStats.setText("All-time visits: " + store.getHistory().size()
                    + "    |    All-time completed: " + store.getServed());
            Patient current = manager.current();
            nowServing.setText(current == null ? "No patient serving" : current.getId() + " - " + current.getName());
            comingNext.setText(next == null ? "No patient waiting" : next.getId() + " - " + next.getName());
            drawLine();
            refreshRecords();
            populatePublic(store);
            if (selectedId != null) {
                selectId(patientTable, patientsModel, selectedId);
                selectId(queueTable, queueModel, selectedId);
            }
            updateQueueControls();
        } finally { refreshing = false; }
    }
    private static void selectId(JTable table, DefaultTableModel model, String id) {
        for (int row = 0; row < model.getRowCount(); row++) {
            if (id.equals(model.getValueAt(row, 0))) {
                int viewRow = table.convertRowIndexToView(row);
                if (viewRow >= 0) table.setRowSelectionInterval(viewRow, viewRow);
                break;
            }
        }
    }
    private void drawLine() {
        queueLine.removeAll();
        List<Patient> people = store.getPatients();
        if (people.isEmpty()) {
            JLabel empty = new JLabel("No patients in line yet. Add a patient from Patients.");
            empty.setBorder(BorderFactory.createEmptyBorder(30, 12, 30, 12));
            queueLine.add(empty);
        }
        Patient next = manager.next();
        for (int i = 0; i < people.size(); i++) {
            Patient p = people.get(i);
            boolean serving = "Serving".equals(p.getStatus());
            boolean upNext = p == next;
            boolean onHold = "Skipped".equals(p.getStatus());
            JPanel item = panel(new BorderLayout(10, 0));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
            item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(serving ? teal : upNext ? new Color(209, 148, 49) : border, 2),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            JComponent icon = new JComponent() {
                @Override protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(serving ? teal : upNext ? new Color(185, 124, 24) : new Color(124, 149, 171));
                    g.fillOval(15, 4, 17, 17); // head
                    g.fillRoundRect(9, 25, 29, 25, 15, 15); // shoulders and body
                    g.dispose();
                }
                @Override public Dimension getPreferredSize() { return new Dimension(46, 54); }
            };
            item.add(icon, BorderLayout.WEST);
            JPanel info = panel(new GridLayout(2, 1, 0, 3));
            JLabel heading = title((i + 1) + ".  " + p.getId() + "  -  " + p.getName(), 15);
            JLabel status = new JLabel(serving ? "SERVING NOW" : onHold ? "ON HOLD - SELECT TO RETURN" : upNext ? "NEXT TO SERVE" : "WAITING IN LINE");
            status.setForeground(serving ? teal : upNext ? new Color(157, 98, 13) : new Color(91, 108, 123));
            info.add(heading); info.add(status);
            item.add(info, BorderLayout.CENTER);
            queueLine.add(item);
            if (i + 1 < people.size()) {
                JLabel line = new JLabel("\u2502", SwingConstants.CENTER);
                line.setForeground(new Color(129, 156, 176));
                line.setFont(new Font("SansSerif", Font.BOLD, 19));
                line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                queueLine.add(line);
            }
        }
        queueLine.revalidate(); queueLine.repaint();
    }
    private void populatePublic(Store source) {
        Patient next = null, serving = null;
        for (Patient p : source.getPatients()) {
            if (serving == null && "Serving".equals(p.getStatus())) serving = p;
            if (next == null && "Waiting".equals(p.getStatus())) next = p;
        }
        publicServing.setText(serving == null ? "--" : serving.getId());
        publicNext.setText(next == null ? "--" : next.getId());
        ArrayList<String[]> rows = QueuePeopleView.rowsFor(source);
        String signature = QueuePeopleView.signature(rows);
        if (!signature.equals(publicSignature)) {
            publicSignature = signature;
            QueuePeopleView.fill(publicLine, rows);
        }
        publicUpdated.setText("Automatically updated at "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
    }

    private void updateFilterVisibility() {
        String filter = String.valueOf(periodFilter.getSelectedItem());
        specificMonthPanel.setVisible("Specific Month".equals(filter));
        customRangePanel.setVisible("Custom Dates".equals(filter));
        filterOptions.revalidate();
    }
    private void refreshRecords() {
        recordsModel.setRowCount(0);
        List<Patient> visits = new ArrayList<>(store.getHistory());
        Comparator<Patient> comparator = Comparator.comparing(p -> p.getCheckedInAt() == null ? "" : p.getCheckedInAt());
        String sort = String.valueOf(recordSort.getSelectedItem());
        if ("Newest First".equals(sort)) comparator = comparator.reversed();
        if ("Patient Name A-Z".equals(sort)) comparator = Comparator.comparing(p -> p.getName() == null ? "" : p.getName().toLowerCase(Locale.ROOT));
        if ("Queue Number".equals(sort)) comparator = Comparator.comparingInt(p -> {
            try { return Integer.parseInt(p.getId().replaceAll("[^0-9]", "")); }
            catch (Exception ex) { return 0; }
        });
        Collections.sort(visits, comparator);
        int shown = 0;
        String phrase = recordSearch.getText().trim().toLowerCase(Locale.ROOT);
        String desiredStatus = String.valueOf(recordStatus.getSelectedItem());
        for (Patient p : visits) {
            if (!matchesPeriod(p.getCheckedInAt())) continue;
            if (!"All Statuses".equals(desiredStatus) && !desiredStatus.equals(p.getStatus())) continue;
            if (!phrase.isEmpty() && !(p.getId() + " " + p.getName()).toLowerCase(Locale.ROOT).contains(phrase)) continue;
            recordsModel.addRow(new Object[]{p.getId(), p.getName(), displayTime(p.getCheckedInAt()),
                    (p.getCompletedAt() == null ? "-" : displayTime(p.getCompletedAt())), p.getStatus()});
            shown++;
        }
        recordCount.setText(shown + " of " + visits.size() + " visits");
    }
    private boolean matchesPeriod(String checkedIn) {
        String mode = String.valueOf(periodFilter.getSelectedItem());
        if ("All Time".equals(mode)) return true;
        LocalDate date;
        try { date = LocalDateTime.parse(checkedIn).toLocalDate(); }
        catch (Exception ex) { return false; } // Unknown times from older versions cannot be date-filtered.
        LocalDate today = LocalDate.now();
        switch (mode) {
            case "Today": return date.equals(today);
            case "This Week":
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                return !date.isBefore(monday) && date.isBefore(monday.plusWeeks(1));
            case "This Month": return YearMonth.from(date).equals(YearMonth.from(today));
            case "Last Month": return YearMonth.from(date).equals(YearMonth.from(today.minusMonths(1)));
            case "This Year": return date.getYear() == today.getYear();
            case "Specific Month":
                return date.getYear() == (Integer) filterYear.getValue() && date.getMonth() == filterMonth.getSelectedItem();
            case "Custom Dates":
                LocalDate from = filterFrom.getDate(), to = filterTo.getDate();
                return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
            default: return true;
        }
    }

    private static String displayTime(String iso) {
        if (iso == null || iso.isEmpty()) return "Unknown (older record)";
        try { return LocalDateTime.parse(iso).format(SHOW_TIME); }
        catch (Exception ex) { return "Unknown (older record)"; }
    }
    private void success(String notice) {
        infoBar.setForeground(teal);
        infoBar.setText(notice);
    }

    private static String escapeCsv(Object value) {
        String text = String.valueOf(value == null ? "" : value);
        // Prevent spreadsheet programs from evaluating untrusted cells as formulas.
        if (!text.isEmpty() && "=+@-".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    private void exportRecords() {
        if (recordsModel.getRowCount() == 0) { message("No records match the current filters."); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export visible patient visit records (CSV)");
        chooser.setSelectedFile(new java.io.File("health_center_visits_" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path output = chooser.getSelectedFile().toPath();
        if (!output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
            output = output.resolveSibling(output.getFileName().toString() + ".csv");
        if (Files.exists(output) && JOptionPane.showConfirmDialog(this, "Overwrite existing file?",
                "Confirm export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int column = 0; column < recordsModel.getColumnCount(); column++) {
                if (column > 0) writer.write(',');
                writer.write(escapeCsv(recordsModel.getColumnName(column)));
            }
            writer.newLine();
            for (int row = 0; row < recordsModel.getRowCount(); row++) {
                for (int col = 0; col < recordsModel.getColumnCount(); col++) {
                    if (col > 0) writer.write(',');
                    writer.write(escapeCsv(recordsModel.getValueAt(row, col)));
                }
                writer.newLine();
            }
            success("Exported " + recordsModel.getRowCount() + " filtered visits to " + output.getFileName());
        } catch (IOException ex) { message("Could not export CSV: " + ex.getMessage()); }
    }
    private void message(String s) { JOptionPane.showMessageDialog(this, s); }
}
