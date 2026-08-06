import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.sql.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ExpenseManagerPro {

    // ==========================================
    // 1. AUTHENTICATION SCREEN
    // ==========================================
    static class AuthScreen extends JFrame {
        private final UserStore userStore = new UserStore("data/users.csv");
        private JPanel rightPanel;
        private CardLayout cardLayout;

        public AuthScreen() {
            setTitle("Expense Manager Pro - Enterprise Edition");
            setSize(1150, 750);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new GridLayout(1, 2));

            add(new FinancialBackgroundPanel());

            rightPanel = new JPanel(new CardLayout());
            cardLayout = (CardLayout) rightPanel.getLayout();
            rightPanel.setBackground(Color.WHITE);

            rightPanel.add(createFormPanel(true), "LOGIN");
            rightPanel.add(createFormPanel(false), "SIGNUP");

            add(rightPanel);
        }

        private JPanel createFormPanel(boolean isLogin) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(Color.WHITE);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(12, 40, 12, 40);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.gridx = 0;

            JLabel title = new JLabel(isLogin ? "ENTERPRISE LOGIN" : "NEW ACCOUNT");
            title.setFont(new Font("Segoe UI", Font.BOLD, 40));
            title.setForeground(new Color(44, 62, 80));
            p.add(title, gbc);

            JLabel subtitle = new JLabel(isLogin ? "AP/AR & Expense Management" : "Join the financial platform");
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            subtitle.setForeground(Color.GRAY);
            p.add(subtitle, gbc);

            gbc.insets = new Insets(40, 40, 10, 40);

            JTextField localEmail = createStyledField();
            JPasswordField localPass = createStyledPassField();
            JTextField localName = isLogin ? null : createStyledField();

            if (!isLogin) {
                p.add(createBoldLabel("FULL NAME"), gbc);
                p.add(localName, gbc);
            }
            p.add(createBoldLabel("EMAIL ADDRESS"), gbc);
            p.add(localEmail, gbc);
            p.add(createBoldLabel("PASSWORD"), gbc);
            p.add(localPass, gbc);

            gbc.insets = new Insets(40, 40, 15, 40);
            JButton actionBtn = new JButton(isLogin ? "ACCESS DASHBOARD" : "REGISTER USER");
            stylePrimaryButton(actionBtn);

            actionBtn.addActionListener(e -> {
                try {
                    String email = localEmail.getText().trim();
                    String pwd = new String(localPass.getPassword());

                    if (email.isEmpty() || pwd.isEmpty()) throw new Exception("All fields are required.");

                    if (!isLogin) {
                        if (!email.contains("@")) throw new Exception("Invalid Email.");
                        if (userStore.userExists(email)) throw new Exception("Email already registered.");
                        userStore.addUser(email, localName.getText().trim(), pwd);
                        JOptionPane.showMessageDialog(this, "Account Created!");
                        cardLayout.show(rightPanel, "LOGIN");
                    } else {
                        if (userStore.verify(email, pwd)) {
                            dispose();
                            new MainDashboard(userStore.getName(email), email).setVisible(true);
                        } else {
                            throw new Exception("Invalid Credentials.");
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.WARNING_MESSAGE);
                }
            });
            p.add(actionBtn, gbc);

            JButton switchBtn = new JButton(isLogin ? "Create New Account" : "Back to Login");
            styleLinkButton(switchBtn);
            switchBtn.addActionListener(e -> cardLayout.show(rightPanel, isLogin ? "SIGNUP" : "LOGIN"));
            p.add(switchBtn, gbc);

            return p;
        }

        private JLabel createBoldLabel(String text) { JLabel l = new JLabel(text); l.setFont(new Font("Segoe UI", Font.BOLD, 14)); return l; }
        private JTextField createStyledField() { JTextField f = new JTextField(20); f.setFont(new Font("Segoe UI", Font.PLAIN, 16)); f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(200,200,200), 2), new EmptyBorder(10,10,10,10))); return f; }
        private JPasswordField createStyledPassField() { JPasswordField f = new JPasswordField(20); f.setFont(new Font("Segoe UI", Font.PLAIN, 16)); f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(200,200,200), 2), new EmptyBorder(10,10,10,10))); return f; }

        // --- BUTTON STYLE UPDATE (Requirement 1) ---
        private void stylePrimaryButton(JButton b) {
            b.setBackground(new Color(30, 136, 229)); // New Vibrant Blue
            b.setForeground(Color.WHITE);
            b.setFont(new Font("Segoe UI", Font.BOLD, 16));
            b.setFocusPainted(false);
            b.setPreferredSize(new Dimension(200, 55));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        private void styleLinkButton(JButton b) { b.setFont(new Font("Segoe UI", Font.BOLD, 14)); b.setBorderPainted(false); b.setContentAreaFilled(false); b.setForeground(new Color(100, 100, 100)); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); }
    }

    // ==========================================
    // 2. MAIN DASHBOARD
    // ==========================================
    static class MainDashboard extends JFrame {
        private final ExpenseStorage expStore = new ExpenseStorage("data/expenses.csv");
        private final BudgetStorage budgetStore = new BudgetStorage("data/budget.csv");
        private final LedgerStorage ledgerStore = new LedgerStorage("data/ledger.csv");

        private List<Expense> allExpenses = new ArrayList<>();
        private List<Expense> displayedExpenses = new ArrayList<>();
        private List<LedgerEntry> apEntries = new ArrayList<>();
        private List<LedgerEntry> arEntries = new ArrayList<>();

        private final String currentUserEmail;
        private final String currentUserName;
        private double monthlyBudget = 0.0;

        // UI Components
        private JPanel mainContentPanel;
        private CardLayout cardLayout;
        private DefaultTableModel tableModel;
        private DefaultTableModel apTableModel, arTableModel;

        private JLabel lblTotal, lblCount, lblBudgetStatus;
        private JProgressBar budgetProgress;

        // Search & Filter
        private JTextField searchField;
        private JComboBox<String> filterCategoryBox;
        private JButton btnClearSearch; // The Cross Icon Button

        // AP/AR UI Labels
        private JLabel lblApDue, lblArPending;
        private JTextArea txtAgingReport;

        public MainDashboard(String name, String email) {
            this.currentUserName = name;
            this.currentUserEmail = email;
            loadData();

            setTitle("Expense Manager Pro - Dashboard [" + currentUserName + "]");
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            add(createSidebar(), BorderLayout.WEST);

            cardLayout = new CardLayout();
            mainContentPanel = new JPanel(cardLayout);
            mainContentPanel.setBackground(new Color(240, 243, 244));

            mainContentPanel.add(createDashboardPage(), "HOME");
            mainContentPanel.add(createAPPage(), "AP");
            mainContentPanel.add(createARPage(), "AR");
            mainContentPanel.add(createAddExpensePage(), "ADD");
            mainContentPanel.add(createSummaryPage(), "SUMMARY");

            add(mainContentPanel, BorderLayout.CENTER);
            updateStats();
        }

        private void loadData() {
            try {
                allExpenses = expStore.load(currentUserEmail);
                displayedExpenses = new ArrayList<>(allExpenses);
                monthlyBudget = budgetStore.getBudget(currentUserEmail);

                List<LedgerEntry> allLedger = ledgerStore.load(currentUserEmail);
                apEntries = allLedger.stream().filter(e -> e.type == LedgerType.AP).collect(Collectors.toList());
                arEntries = allLedger.stream().filter(e -> e.type == LedgerType.AR).collect(Collectors.toList());
            } catch (Exception e) { e.printStackTrace(); }
        }

        private JPanel createSidebar() {
            JPanel sidebar = new JPanel();
            sidebar.setBackground(new Color(33, 47, 61));
            sidebar.setPreferredSize(new Dimension(300, 800));
            sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

            JLabel logo = new JLabel(" FINANCE PRO");
            logo.setForeground(Color.WHITE);
            logo.setFont(new Font("Segoe UI", Font.BOLD, 32));
            logo.setBorder(new EmptyBorder(50, 30, 50, 0));
            logo.setAlignmentX(Component.LEFT_ALIGNMENT);
            sidebar.add(logo);

            addSidebarBtn(sidebar, "Dashboard Overview", e -> showPage("HOME"));

            sidebar.add(Box.createRigidArea(new Dimension(0, 10)));
            addSidebarBtn(sidebar, "Accounts Payable (AP)", e -> showPage("AP"));
            addSidebarBtn(sidebar, "Accounts Receivable (AR)", e -> { updateAgingReport(); showPage("AR"); });
            sidebar.add(Box.createRigidArea(new Dimension(0, 10)));

            addSidebarBtn(sidebar, "Add Expense", e -> showPage("ADD"));
            addSidebarBtn(sidebar, "Reports", e -> showPage("SUMMARY"));

            sidebar.add(Box.createRigidArea(new Dimension(0, 20)));
            addSidebarBtn(sidebar, "Set Monthly Budget", e -> openBudgetDialog());
            addSidebarBtn(sidebar, "Save All Changes", e -> saveData());

            sidebar.add(Box.createVerticalGlue());

            JButton btnLogout = new JButton("LOGOUT");
            styleDangerButton(btnLogout);
            btnLogout.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnLogout.setMaximumSize(new Dimension(250, 50));
            btnLogout.addActionListener(e -> { dispose(); new AuthScreen().setVisible(true); });

            sidebar.add(btnLogout);
            sidebar.add(Box.createRigidArea(new Dimension(0, 40)));
            return sidebar;
        }

        // --- PAGE 1: DASHBOARD ---
        private JPanel createDashboardPage() {
            JPanel p = new JPanel(new BorderLayout(20, 20));
            p.setBackground(new Color(240, 243, 244));
            p.setBorder(new EmptyBorder(30, 30, 30, 30));

            // Stats
            JPanel topPanel = new JPanel(new GridLayout(1, 4, 20, 0));
            topPanel.setOpaque(false);
            topPanel.setPreferredSize(new Dimension(0, 160));

            lblTotal = new JLabel("$0.00");
            lblCount = new JLabel("0");

            // Budget Card
            JPanel budgetCard = new JPanel(new BorderLayout());
            budgetCard.setBackground(Color.WHITE);
            budgetCard.setBorder(BorderFactory.createMatteBorder(0,0,4,0, new Color(243, 156, 18)));
            JLabel bTitle = new JLabel("BUDGET STATUS");
            bTitle.setFont(new Font("Segoe UI", Font.BOLD, 12)); bTitle.setForeground(Color.GRAY); bTitle.setBorder(new EmptyBorder(10,20,0,0));
            lblBudgetStatus = new JLabel("Limit: $0");
            lblBudgetStatus.setFont(new Font("Segoe UI", Font.BOLD, 16)); lblBudgetStatus.setBorder(new EmptyBorder(5,20,5,20));
            budgetProgress = new JProgressBar(0, 100);
            budgetProgress.setStringPainted(true); budgetProgress.setBorder(new EmptyBorder(0, 20, 15, 20));
            budgetCard.add(bTitle, BorderLayout.NORTH); budgetCard.add(lblBudgetStatus, BorderLayout.CENTER); budgetCard.add(budgetProgress, BorderLayout.SOUTH);

            topPanel.add(createCard("TOTAL EXPENSES", lblTotal, new Color(192, 57, 43)));
            topPanel.add(budgetCard);
            topPanel.add(createCard("TRANSACTIONS", lblCount, new Color(39, 174, 96)));

            p.add(topPanel, BorderLayout.NORTH);

            // Filter & Table
            JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
            centerPanel.setOpaque(false);

            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
            searchPanel.setBackground(Color.WHITE);
            searchPanel.setBorder(new LineBorder(new Color(220,220,220), 1));

            // --- SEARCH BAR WITH CROSS ICON (Requirement 2) ---
            JPanel txtContainer = new JPanel(new BorderLayout());
            txtContainer.setBackground(Color.WHITE);
            txtContainer.setBorder(new LineBorder(Color.LIGHT_GRAY, 1));
            txtContainer.setPreferredSize(new Dimension(250, 30));

            searchField = new JTextField();
            searchField.setBorder(new EmptyBorder(0, 5, 0, 0));
            searchField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            btnClearSearch = new JButton("✖"); // Cross Icon
            btnClearSearch.setForeground(Color.GRAY);
            btnClearSearch.setBorderPainted(false);
            btnClearSearch.setContentAreaFilled(false);
            btnClearSearch.setFocusPainted(false);
            btnClearSearch.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnClearSearch.setPreferredSize(new Dimension(30, 30));
            btnClearSearch.setVisible(false); // Hidden initially

            // Cross Button Logic
            btnClearSearch.addActionListener(e -> {
                searchField.setText("");
                filterData();
                searchField.requestFocus();
            });

            // Show/Hide Cross based on text
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { update(); }
                public void removeUpdate(DocumentEvent e) { update(); }
                public void changedUpdate(DocumentEvent e) { update(); }
                void update() {
                    boolean hasText = searchField.getText().length() > 0;
                    btnClearSearch.setVisible(hasText);
                    filterData();
                }
            });

            txtContainer.add(searchField, BorderLayout.CENTER);
            txtContainer.add(btnClearSearch, BorderLayout.EAST);
            // ----------------------------------------------------

            filterCategoryBox = new JComboBox<>(new String[]{"All Categories", "Food", "Transport", "Utilities", "Entertainment", "Health", "Other"});
            filterCategoryBox.addActionListener(e -> filterData());

            searchPanel.add(new JLabel("Search:"));
            searchPanel.add(txtContainer); // Add the custom container
            searchPanel.add(Box.createHorizontalStrut(15));
            searchPanel.add(new JLabel("Category:"));
            searchPanel.add(filterCategoryBox);

            centerPanel.add(searchPanel, BorderLayout.NORTH);

            String[] cols = {"ID", "Title", "Category", "Amount", "Date", "Payment"};
            tableModel = new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r,int c){return false;}};
            JTable table = new JTable(tableModel);
            styleTable(table);
            refreshTable();

            centerPanel.add(new JScrollPane(table), BorderLayout.CENTER);

            // --- RESTORED DELETE BUTTON (Requirement 3) ---
            JButton btnDelete = new JButton("DELETE SELECTED EXPENSE");
            styleDangerButton(btnDelete);
            btnDelete.addActionListener(e -> {
                int selectedRow = table.getSelectedRow();
                if(selectedRow != -1) {
                    int id = (int) tableModel.getValueAt(selectedRow, 0);
                    int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete expense ID: " + id + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);

                    if(confirm == JOptionPane.YES_OPTION) {
                        allExpenses.removeIf(exp -> exp.getId() == id);
                        saveData();
                        filterData(); // Refresh list
                        updateStats();
                        JOptionPane.showMessageDialog(this, "Expense Deleted Successfully.");
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Please select an expense to delete.");
                }
            });

            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            bottomPanel.setOpaque(false);
            bottomPanel.add(btnDelete);
            centerPanel.add(bottomPanel, BorderLayout.SOUTH);
            // ----------------------------------------------------

            p.add(centerPanel, BorderLayout.CENTER);
            return p;
        }

        // --- PAGE 2: ACCOUNTS PAYABLE (AP) ---
        private JPanel createAPPage() {
            JPanel p = new JPanel(new BorderLayout(20, 20));
            p.setBackground(new Color(240, 243, 244));
            p.setBorder(new EmptyBorder(30, 30, 30, 30));

            // Header Stats
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel("ACCOUNTS PAYABLE (Vendor Management)");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(192, 57, 43));

            lblApDue = new JLabel("Pending: $0.00");
            lblApDue.setFont(new Font("Segoe UI", Font.BOLD, 18));

            header.add(title, BorderLayout.CENTER);
            header.add(lblApDue, BorderLayout.EAST);
            p.add(header, BorderLayout.NORTH);

            // Table
            String[] cols = {"ID", "Vendor / Biller", "Amount", "Due Date", "Status"};
            apTableModel = new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r,int c){return false;}};
            JTable table = new JTable(apTableModel);
            styleTable(table);

            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer(){
                @Override
                public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                    Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                    String status = (String)t.getValueAt(r, 4);
                    String dateStr = (String)t.getValueAt(r, 3);
                    try {
                        LocalDate due = LocalDate.parse(dateStr);
                        if("PENDING".equals(status)) {
                            if(LocalDate.now().isAfter(due)) comp.setForeground(Color.RED);
                            else comp.setForeground(new Color(211, 84, 0));
                        } else {
                            comp.setForeground(new Color(39, 174, 96));
                        }
                    } catch (Exception e) {}
                    return comp;
                }
            });
            refreshAPTable();

            p.add(new JScrollPane(table), BorderLayout.CENTER);

            // Actions
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
            actions.setBackground(Color.WHITE);

            JButton btnAddBill = new JButton("ADD NEW BILL");
            stylePrimaryButton(btnAddBill);
            btnAddBill.addActionListener(e -> openLedgerDialog(LedgerType.AP));

            JButton btnAutoPay = new JButton("AUTO-PAY DUE BILLS");
            // Secondary button style
            btnAutoPay.setBackground(new Color(52, 73, 94));
            btnAutoPay.setForeground(Color.WHITE);
            btnAutoPay.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btnAutoPay.setFocusPainted(false);
            btnAutoPay.setPreferredSize(new Dimension(200, 45));

            btnAutoPay.addActionListener(e -> {
                int count = 0;
                for(LedgerEntry le : apEntries) {
                    if(le.status == LedgerStatus.PENDING && !LocalDate.now().isBefore(LocalDate.parse(le.dueDate))) {
                        le.status = LedgerStatus.PAID;
                        allExpenses.add(new Expense("Bill Pay: " + le.partyName, le.amount, "Utilities", PaymentMethod.ONLINE, currentUserEmail));
                        count++;
                    }
                }
                if(count > 0) {
                    JOptionPane.showMessageDialog(this, count + " bills processed and added to Expenses.");
                    saveData(); refreshAPTable(); updateStats();
                } else {
                    JOptionPane.showMessageDialog(this, "No bills are due for auto-payment.");
                }
            });

            actions.add(btnAddBill);
            actions.add(btnAutoPay);
            p.add(actions, BorderLayout.SOUTH);

            return p;
        }

        // --- PAGE 3: ACCOUNTS RECEIVABLE (AR) ---
        private JPanel createARPage() {
            JPanel p = new JPanel(new BorderLayout(20, 20));
            p.setBackground(new Color(240, 243, 244));
            p.setBorder(new EmptyBorder(30, 30, 30, 30));

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            split.setResizeWeight(0.7);
            split.setBorder(null);

            JPanel tablePanel = new JPanel(new BorderLayout());
            JPanel header = new JPanel(new BorderLayout()); header.setBackground(Color.WHITE);
            JLabel title = new JLabel(" ACCOUNTS RECEIVABLE (Customer Invoicing)");
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(39, 174, 96));
            header.add(title, BorderLayout.WEST);

            lblArPending = new JLabel("To Collect: $0.00  ");
            lblArPending.setFont(new Font("Segoe UI", Font.BOLD, 18));
            header.add(lblArPending, BorderLayout.EAST);

            tablePanel.add(header, BorderLayout.NORTH);

            String[] cols = {"ID", "Customer Name", "Amount", "Due Date", "Days Overdue", "Status"};
            arTableModel = new DefaultTableModel(cols, 0) { public boolean isCellEditable(int r,int c){return false;}};
            JTable table = new JTable(arTableModel);
            styleTable(table);
            refreshARTable();
            tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel ctrls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            ctrls.setBackground(Color.WHITE);
            JButton btnAddInv = new JButton("CREATE INVOICE");
            stylePrimaryButton(btnAddInv);
            btnAddInv.setBackground(new Color(39, 174, 96)); // Green
            btnAddInv.addActionListener(e -> openLedgerDialog(LedgerType.AR));

            JButton btnMarkPaid = new JButton("MARK SELECTED PAID");
            btnMarkPaid.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btnMarkPaid.addActionListener(e -> {
                int r = table.getSelectedRow();
                if(r != -1) {
                    int id = (int) arTableModel.getValueAt(r, 0);
                    arEntries.stream().filter(x -> x.id == id).findFirst().ifPresent(x -> x.status = LedgerStatus.PAID);
                    refreshARTable(); updateAgingReport(); saveData();
                }
            });
            ctrls.add(btnAddInv); ctrls.add(btnMarkPaid);
            tablePanel.add(ctrls, BorderLayout.SOUTH);

            split.setTopComponent(tablePanel);

            JPanel reportPanel = new JPanel(new BorderLayout());
            reportPanel.setBorder(new TitledBorder(new LineBorder(Color.GRAY), "AGING REPORT (Credit Management)", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14)));
            reportPanel.setBackground(Color.WHITE);

            txtAgingReport = new JTextArea();
            txtAgingReport.setFont(new Font("Monospaced", Font.BOLD, 14));
            txtAgingReport.setEditable(false);
            txtAgingReport.setMargin(new Insets(10,10,10,10));
            reportPanel.add(new JScrollPane(txtAgingReport), BorderLayout.CENTER);

            split.setBottomComponent(reportPanel);

            p.add(split, BorderLayout.CENTER);
            return p;
        }

        // --- PAGE 4: ADD EXPENSE ---
        private JPanel createAddExpensePage() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(Color.WHITE);
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(10, 10, 10, 10); g.gridx=0; g.fill=GridBagConstraints.HORIZONTAL;

            JLabel h = new JLabel("LOG CASH EXPENSE"); h.setFont(new Font("Segoe UI", Font.BOLD, 24));
            p.add(h, g);

            JTextField t = new JTextField(25); styleInput(t);
            JTextField a = new JTextField(25); styleInput(a);
            JComboBox<String> c = new JComboBox<>(new String[]{"Food", "Transport", "Utilities", "Entertainment", "Health", "Other"});
            JComboBox<PaymentMethod> m = new JComboBox<>(PaymentMethod.values());

            p.add(new JLabel("Title"),g); p.add(t,g);
            p.add(new JLabel("Amount"),g); p.add(a,g);
            p.add(new JLabel("Category"),g); p.add(c,g);
            p.add(new JLabel("Method"),g); p.add(m,g);

            JButton b = new JButton("SAVE"); stylePrimaryButton(b);
            b.addActionListener(e -> {
                try {
                    allExpenses.add(new Expense(t.getText(), Double.parseDouble(a.getText()), (String)c.getSelectedItem(), (PaymentMethod)m.getSelectedItem(), currentUserEmail));
                    filterData(); updateStats(); JOptionPane.showMessageDialog(this, "Saved!"); showPage("HOME"); t.setText(""); a.setText("");
                } catch(Exception ex){ JOptionPane.showMessageDialog(this,"Invalid Input");}
            });
            p.add(b,g);
            return p;
        }

        // --- PAGE 5: SUMMARY ---
        private JPanel createSummaryPage() {
            JPanel p = new JPanel(new BorderLayout()); p.setBackground(Color.WHITE);
            JTextArea area = new JTextArea(); area.setFont(new Font("Monospaced", Font.BOLD, 15));
            p.addComponentListener(new ComponentAdapter(){
                public void componentShown(ComponentEvent e){
                    StringBuilder sb = new StringBuilder("FINANCIAL SUMMARY\n=================\n\n");
                    double totalExp = allExpenses.stream().mapToDouble(Expense::getAmount).sum();
                    double totalAP = apEntries.stream().filter(x->x.status==LedgerStatus.PENDING).mapToDouble(x->x.amount).sum();
                    double totalAR = arEntries.stream().filter(x->x.status==LedgerStatus.PENDING).mapToDouble(x->x.amount).sum();

                    sb.append(String.format("TOTAL EXPENSES PAID:   $%,10.2f\n", totalExp));
                    sb.append(String.format("ACCOUNTS PAYABLE (Out):$%,10.2f\n", totalAP));
                    sb.append(String.format("ACCOUNTS RECEIVABLE(In):$%,10.2f\n", totalAR));
                    sb.append(String.format("\nNET POSITION (Est.):   $%,10.2f\n", (totalAR - totalAP - totalExp)));
                    area.setText(sb.toString());
                }
            });
            p.add(new JScrollPane(area));
            return p;
        }

        // --- LOGIC & HELPERS ---

        private void openLedgerDialog(LedgerType type) {
            JDialog d = new JDialog(this, type == LedgerType.AP ? "Add Bill (AP)" : "Create Invoice (AR)", true);
            d.setLayout(new GridLayout(4, 2, 10, 10)); d.setSize(400, 200); d.setLocationRelativeTo(this);

            JTextField tfName = new JTextField();
            JTextField tfAmt = new JTextField();
            JTextField tfDate = new JTextField(LocalDate.now().plusDays(30).toString());

            d.add(new JLabel(type==LedgerType.AP ? "Vendor Name:" : "Customer Name:")); d.add(tfName);
            d.add(new JLabel("Amount ($):")); d.add(tfAmt);
            d.add(new JLabel("Due Date (YYYY-MM-DD):")); d.add(tfDate);

            JButton btn = new JButton("SAVE");
            stylePrimaryButton(btn); // Apply style
            btn.addActionListener(e -> {
                try {
                    String name = tfName.getText();
                    double amt = Double.parseDouble(tfAmt.getText());
                    String date = tfDate.getText();
                    LedgerEntry le = new LedgerEntry(name, amt, date, type, currentUserEmail);
                    if(type == LedgerType.AP) apEntries.add(le); else arEntries.add(le);

                    saveData();
                    if(type == LedgerType.AP) refreshAPTable(); else { refreshARTable(); updateAgingReport(); }
                    d.dispose();
                } catch(Exception ex) { JOptionPane.showMessageDialog(d, "Invalid Input"); }
            });
            d.add(btn); d.setVisible(true);
        }

        private void refreshAPTable() {
            apTableModel.setRowCount(0);
            double due = 0;
            for(LedgerEntry e : apEntries) {
                apTableModel.addRow(new Object[]{e.id, e.partyName, String.format("%.2f", e.amount), e.dueDate, e.status.toString()});
                if(e.status == LedgerStatus.PENDING) due += e.amount;
            }
            lblApDue.setText(String.format("Pending: $%,.2f", due));
        }

        private void refreshARTable() {
            arTableModel.setRowCount(0);
            double pending = 0;
            LocalDate now = LocalDate.now();
            for(LedgerEntry e : arEntries) {
                long daysOver = 0;
                if(e.status == LedgerStatus.PENDING) {
                    pending += e.amount;
                    daysOver = ChronoUnit.DAYS.between(LocalDate.parse(e.dueDate), now);
                    if(daysOver < 0) daysOver = 0;
                }
                arTableModel.addRow(new Object[]{e.id, e.partyName, String.format("%.2f", e.amount), e.dueDate, daysOver + " days", e.status});
            }
            lblArPending.setText(String.format("To Collect: $%,.2f  ", pending));
        }

        private void updateAgingReport() {
            double current = 0, d30 = 0, d60 = 0, d90 = 0;
            LocalDate now = LocalDate.now();

            for(LedgerEntry e : arEntries) {
                if(e.status == LedgerStatus.PENDING) {
                    long days = ChronoUnit.DAYS.between(LocalDate.parse(e.dueDate), now);
                    if(days <= 0) current += e.amount;
                    else if(days <= 30) d30 += e.amount;
                    else if(days <= 60) d60 += e.amount;
                    else d90 += e.amount;
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-20s %15s\n", "BUCKET", "AMOUNT"));
            sb.append("--------------------------------------\n");
            sb.append(String.format("%-20s $%,15.2f\n", "Current (Not Due)", current));
            sb.append(String.format("%-20s $%,15.2f\n", "1 - 30 Days Overdue", d30));
            sb.append(String.format("%-20s $%,15.2f\n", "31 - 60 Days Overdue", d60));
            sb.append(String.format("%-20s $%,15.2f\n", "61 - 90+ Days Overdue", d90));
            sb.append("--------------------------------------\n");
            sb.append(String.format("%-20s $%,15.2f", "TOTAL RECEIVABLE", current+d30+d60+d90));

            txtAgingReport.setText(sb.toString());
        }

        private void filterData() {
            String s = searchField.getText().toLowerCase();
            String c = (String) filterCategoryBox.getSelectedItem();
            displayedExpenses = allExpenses.stream().filter(e -> e.getTitle().toLowerCase().contains(s) && (c.equals("All Categories") || e.getCategory().equals(c))).collect(Collectors.toList());
            refreshTable();
        }
        private void refreshTable() {
            tableModel.setRowCount(0);
            for(Expense e : displayedExpenses) tableModel.addRow(new Object[]{e.getId(), e.getTitle(), e.getCategory(), e.getAmount(), e.getDate(), e.getPaymentMethod()});
        }
        private void updateStats() {
            double t = allExpenses.stream().mapToDouble(Expense::getAmount).sum();
            lblTotal.setText(String.format("$%,.2f", t));
            lblCount.setText(String.valueOf(allExpenses.size()));
            if(monthlyBudget > 0) {
                int p = (int)((t/monthlyBudget)*100);
                budgetProgress.setValue(Math.min(p, 100)); budgetProgress.setString(p+"%");
                lblBudgetStatus.setText(p > 100 ? "OVER BUDGET!" : "Limit: $"+(int)monthlyBudget);
                budgetProgress.setForeground(p>100?Color.RED: p>80?Color.ORANGE: new Color(46, 204, 113));
            }
        }
        private void saveData() {
            try {
                expStore.save(allExpenses, currentUserEmail);
                ledgerStore.save(apEntries, arEntries, currentUserEmail);
                JOptionPane.showMessageDialog(this, "Data Saved!");
            } catch(Exception e){ JOptionPane.showMessageDialog(this, "Error Saving"); }
        }
        private void openBudgetDialog() {
            String s = JOptionPane.showInputDialog(this, "Set Budget ($):", monthlyBudget);
            if(s!=null) { monthlyBudget=Double.parseDouble(s); budgetStore.saveBudget(currentUserEmail, monthlyBudget); updateStats(); }
        }
        private void showPage(String n) { cardLayout.show(mainContentPanel, n); }

        // --- STYLES (Color Updates - Requirement 1) ---
        private void addSidebarBtn(JPanel p, String t, ActionListener a) {
            JButton b = new JButton(t); b.setAlignmentX(Component.LEFT_ALIGNMENT); b.setMaximumSize(new Dimension(300, 50));
            b.setBackground(new Color(33, 47, 61)); b.setForeground(Color.WHITE); b.setFont(new Font("Segoe UI", Font.BOLD, 14));
            b.setFocusPainted(false); b.setBorder(new EmptyBorder(10, 30, 10, 0)); b.setHorizontalAlignment(SwingConstants.LEFT);
            b.addActionListener(a); b.setCursor(new Cursor(Cursor.HAND_CURSOR)); p.add(b);
        }

        // Updated Primary Button Color (Vibrant Blue)
        private void stylePrimaryButton(JButton b) {
            b.setBackground(new Color(30, 136, 229));
            b.setForeground(Color.WHITE);
            b.setFont(new Font("Segoe UI", Font.BOLD, 14));
            b.setFocusPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.setPreferredSize(new Dimension(150, 40));
        }

        // Updated Danger Button Color (Crimson Red)
        private void styleDangerButton(JButton b) {
            b.setBackground(new Color(231, 76, 60));
            b.setForeground(Color.WHITE);
            b.setFont(new Font("Segoe UI", Font.BOLD, 14));
            b.setFocusPainted(false);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        private void styleInput(JTextField t) { t.setFont(new Font("Segoe UI", Font.PLAIN, 16)); t.setPreferredSize(new Dimension(300, 40)); }
        private void styleTable(JTable t) { t.setRowHeight(35); t.setFont(new Font("Segoe UI", Font.PLAIN, 14)); t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14)); t.getTableHeader().setBackground(new Color(230,230,230)); }
        private JPanel createCard(String t, JLabel v, Color c) {
            JPanel p = new JPanel(new BorderLayout()); p.setBackground(Color.WHITE); p.setBorder(BorderFactory.createMatteBorder(0,0,4,0,c));
            JLabel title = new JLabel(t); title.setForeground(Color.GRAY); title.setFont(new Font("Segoe UI", Font.BOLD, 12)); title.setBorder(new EmptyBorder(10,20,0,0));
            v.setFont(new Font("Segoe UI", Font.BOLD, 24)); v.setBorder(new EmptyBorder(5,20,10,0));
            p.add(title, BorderLayout.NORTH); p.add(v, BorderLayout.CENTER); return p;
        }
    }

    // ==========================================
    // 3. MODELS & STORAGE (Unchanged)
    // ==========================================

    static class FinancialBackgroundPanel extends JPanel {
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint gp = new GradientPaint(0, 0, new Color(44, 62, 80), getWidth(), getHeight(), new Color(20, 30, 40));
            g2.setPaint(gp); g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(255, 255, 255, 10));
            for(int i=0; i<getWidth(); i+=50) g2.drawLine(i, 0, i, getHeight());

            g2.setStroke(new BasicStroke(3)); g2.setColor(new Color(46, 204, 113, 100));
            Path2D graph = new Path2D.Double(); graph.moveTo(0, getHeight()*0.7);
            Random r = new Random(123);
            for(int x=0; x<getWidth(); x+=40) graph.lineTo(x, getHeight()*0.5 + (r.nextDouble()*200-100));
            g2.draw(graph);

            g2.setColor(Color.WHITE); g2.setFont(new Font("Segoe UI", Font.BOLD, 48)); g2.drawString("FINANCE", 60, getHeight()/2 - 40);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 32)); g2.drawString("MANAGER PRO", 60, getHeight()/2 + 10);
        }
    }

    enum PaymentMethod { CASH, CARD, ONLINE }
    enum LedgerType { AP, AR }
    enum LedgerStatus { PENDING, PAID }

    static class Expense {
        private static int NEXT_ID = 1;
        private int id; private String title, category, date, ownerEmail; private double amount; private PaymentMethod pm;
        public Expense(String t, double a, String c, PaymentMethod p, String o) { this.id=NEXT_ID++; this.title=t; this.amount=a; this.category=c; this.pm=p; this.ownerEmail=o; this.date=LocalDate.now().toString(); }
        public Expense(int i, String t, double a, String c, String d, PaymentMethod p, String o) { this.id=i; if(i>=NEXT_ID) NEXT_ID=i+1; this.title=t; this.amount=a; this.category=c; this.date=d; this.pm=p; this.ownerEmail=o; }
        public int getId(){return id;} public String getTitle(){return title;} public String getCategory(){return category;} public double getAmount(){return amount;} public String getDate(){return date;} public PaymentMethod getPaymentMethod(){return pm;} public String getOwnerEmail(){return ownerEmail;}
        public String toCsv(){ return String.format("%d,\"%s\",\"%s\",%.2f,%s,%s,%s", id, title, category, amount, date, pm, ownerEmail); }
        public static Expense fromCsv(String l){ try{ String[] p=l.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); return new Expense(Integer.parseInt(p[0]), p[1].replace("\"",""), Double.parseDouble(p[3]), p[2].replace("\"",""), p[4], PaymentMethod.valueOf(p[5]), p.length>6?p[6]:"unknown"); }catch(Exception e){return null;}}
    }

    static class LedgerEntry {
        private static int NEXT_LID = 1;
        int id; String partyName, dueDate, owner; double amount; LedgerType type; LedgerStatus status;
        public LedgerEntry(String n, double a, String d, LedgerType t, String o) { this.id=NEXT_LID++; this.partyName=n; this.amount=a; this.dueDate=d; this.type=t; this.owner=o; this.status=LedgerStatus.PENDING; }
        public LedgerEntry(int i, String n, double a, String d, LedgerType t, LedgerStatus s, String o) { this.id=i; if(i>=NEXT_LID) NEXT_LID=i+1; this.partyName=n; this.amount=a; this.dueDate=d; this.type=t; this.status=s; this.owner=o; }
        public String toCsv() { return String.format("%d,\"%s\",%.2f,%s,%s,%s,%s", id, partyName, amount, dueDate, type, status, owner); }
        public static LedgerEntry fromCsv(String l) { try{ String[] p=l.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"); return new LedgerEntry(Integer.parseInt(p[0]), p[1].replace("\"",""), Double.parseDouble(p[2]), p[3], LedgerType.valueOf(p[4]), LedgerStatus.valueOf(p[5]), p[6]); }catch(Exception e){return null;} }
    }

    static class ExpenseStorage {
        Path p; public ExpenseStorage(String f){p=Paths.get(f);}
        public void save(List<Expense> l, String u) throws Exception { Utils.check(p); List<String> fl = new ArrayList<>(); if(Files.exists(p)) for(String s:Files.readAllLines(p)) {Expense e=Expense.fromCsv(s); if(e!=null && !e.getOwnerEmail().equalsIgnoreCase(u)) fl.add(s);} for(Expense e:l) fl.add(e.toCsv()); Files.write(p, fl, StandardCharsets.UTF_8); }
        public List<Expense> load(String u) throws Exception { List<Expense> l=new ArrayList<>(); if(!Files.exists(p)) return l; for(String s:Files.readAllLines(p)){Expense e=Expense.fromCsv(s); if(e!=null && e.getOwnerEmail().equalsIgnoreCase(u)) l.add(e);} return l; }
    }

    static class LedgerStorage {
        Path p; public LedgerStorage(String f){p=Paths.get(f);}
        public void save(List<LedgerEntry> ap, List<LedgerEntry> ar, String u) throws Exception { Utils.check(p); List<String> fl = new ArrayList<>(); if(Files.exists(p)) for(String s:Files.readAllLines(p)){LedgerEntry e=LedgerEntry.fromCsv(s); if(e!=null && !e.owner.equalsIgnoreCase(u)) fl.add(s);} for(LedgerEntry e:ap) fl.add(e.toCsv()); for(LedgerEntry e:ar) fl.add(e.toCsv()); Files.write(p, fl, StandardCharsets.UTF_8); }
        public List<LedgerEntry> load(String u) throws Exception { List<LedgerEntry> l=new ArrayList<>(); if(!Files.exists(p)) return l; for(String s:Files.readAllLines(p)){LedgerEntry e=LedgerEntry.fromCsv(s); if(e!=null && e.owner.equalsIgnoreCase(u)) l.add(e);} return l; }
    }

    static class BudgetStorage {
        Path p; public BudgetStorage(String f){p=Paths.get(f);}
        public void saveBudget(String e, double v) { try{Utils.check(p); Properties pr=new Properties(); if(Files.exists(p)) try(InputStream i=Files.newInputStream(p)){pr.load(i);} pr.setProperty(e, String.valueOf(v)); try(OutputStream o=Files.newOutputStream(p)){pr.store(o,"Budgets");}}catch(Exception x){} }
        public double getBudget(String e) { try{if(!Files.exists(p)) return 0; Properties pr=new Properties(); try(InputStream i=Files.newInputStream(p)){pr.load(i);} return Double.parseDouble(pr.getProperty(e,"0"));}catch(Exception x){return 0;} }
    }

    static class UserStore {
        Path p; public UserStore(String f){p=Paths.get(f);}
        public boolean userExists(String e) throws IOException { if(!Files.exists(p)) return false; for(String s:Files.readAllLines(p)) if(s.startsWith(e+",")) return true; return false; }
        public void addUser(String e, String n, String pw) throws Exception { Utils.check(p); String s=Utils.salt(), h=Utils.hash(pw, s); Files.write(p, Collections.singletonList(e+","+s+","+h+","+n), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        public boolean verify(String e, String pw) throws Exception { if(!Files.exists(p)) return false; for(String s:Files.readAllLines(p)){ String[] v=s.split(","); if(v[0].equalsIgnoreCase(e)) return Utils.hash(pw,v[1]).equals(v[2]); } return false; }
        public String getName(String e) throws Exception { if(Files.exists(p)) for(String s:Files.readAllLines(p)){ String[] v=s.split(","); if(v[0].equalsIgnoreCase(e)) return v[3]; } return "User"; }
    }

    static class Utils {
        static void check(Path p) throws IOException { if(p.getParent()!=null && !Files.exists(p.getParent())) Files.createDirectories(p.getParent()); }
        static String salt(){byte[] b=new byte[16]; new SecureRandom().nextBytes(b); return hex(b);}
        static String hash(String p, String s) throws Exception { MessageDigest m=MessageDigest.getInstance("SHA-256"); m.update(bytes(s)); return hex(m.digest(p.getBytes(StandardCharsets.UTF_8))); }
        static String hex(byte[] b){StringBuilder s=new StringBuilder(); for(byte x:b) s.append(String.format("%02x",x)); return s.toString();}
        static byte[] bytes(String h){int l=h.length(); byte[] d=new byte[l/2]; for(int i=0;i<l;i+=2) d[i/2]=(byte)Integer.parseInt(h.substring(i,i+2),16); return d;}
    }

    public static void main(String[] args) {
        Connection con = DatabaseConnection.getConnection();

        if(con != null)
            System.out.println("Connected");
        else
            System.out.println("Failed");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch(Exception ignored){}
        SwingUtilities.invokeLater(() -> new AuthScreen().setVisible(true));
    }
}