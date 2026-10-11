package gui;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.rmi.Naming;

import rmi.LibraryInterface;

/**
 * Redesigned Swing GUI client for the Distributed Library Management System.
 * Features: split-panel login, stat cards, distinct sidebar icons, rounded UI.
 */
public class LibraryClientGUI {

    // ======================== Color Palette ========================
    private static final Color PRIMARY        = new Color(79, 70, 229);
    private static final Color PRIMARY_DARK   = new Color(55, 48, 163);
    private static final Color PRIMARY_LIGHT  = new Color(238, 242, 255);
    private static final Color SIDEBAR_BG     = new Color(15, 23, 42);
    private static final Color SIDEBAR_HOVER  = new Color(30, 41, 59);
    private static final Color SIDEBAR_ACTIVE = new Color(79, 70, 229);
    private static final Color CONTENT_BG     = new Color(241, 245, 249);
    private static final Color CARD_BG        = Color.WHITE;
    private static final Color TEXT_DARK      = new Color(15, 23, 42);
    private static final Color TEXT_SECONDARY = new Color(71, 85, 105);
    private static final Color TEXT_MUTED     = new Color(148, 163, 184);
    private static final Color TEXT_WHITE     = Color.WHITE;
    private static final Color BORDER         = new Color(226, 232, 240);
    private static final Color BORDER_LIGHT   = new Color(241, 245, 249);
    private static final Color SUCCESS        = new Color(34, 197, 94);
    private static final Color SUCCESS_BG     = new Color(240, 253, 244);
    private static final Color DANGER         = new Color(239, 68, 68);
    private static final Color DANGER_BG      = new Color(254, 242, 242);
    private static final Color INFO           = new Color(59, 130, 246);
    private static final Color INFO_BG        = new Color(239, 246, 255);
    private static final Color WARNING_BG     = new Color(255, 251, 235);
    private static final Color WARNING        = new Color(234, 179, 8);

    private static final Font FONT_BOLD   = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_PLAIN  = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_SMALL  = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_TITLE  = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font FONT_SUB    = new Font("Segoe UI", Font.PLAIN, 13);

    // ======================== State ========================
    private final JFrame frame;
    private final CardLayout rootCards;
    private final JPanel rootPanel;
    private LibraryInterface remote;
    private String currentRole;
    private int currentUserId;
    private String currentUsername;

    private DefaultTableModel adminBookModel;
    private DefaultTableModel adminTxModel;
    private DefaultTableModel userBookModel;
    private DefaultTableModel userBorrowedModel;

    private JPanel userCenterCards;
    private CardLayout userCardLayout;
    private JPanel adminCenterCards;
    private CardLayout adminCardLayout;

    private JLabel userCountLabel;
    private JLabel availCountLabel;
    private JLabel borrowedCountLabel;
    private JLabel adminUserCountLabel;
    private JLabel adminAvailCountLabel;
    private JLabel adminBorrowedCountLabel;

    // Track currently selected sidebar button for highlighting
    private JButton activeUserBtn;
    private JButton activeAdminBtn;

    // Sidebar user-name labels (updated after login; dashboards are built once at startup)
    private JLabel sidebarNameLabelUser;
    private JLabel sidebarNameLabelAdmin;

    // Search state (kept alive so search results can be auto-refreshed)
    private JTextField searchField;
    private DefaultTableModel searchModel;
    private JLabel searchInfo;
    private String lastSearchKeyword;

    // Periodic auto-refresh (client-polling) so every connected client sees
    // changes made by other clients without pressing the Refresh button.
    private static final int AUTO_REFRESH_MS = 3000;
    private javax.swing.Timer autoRefreshTimer;
    private boolean refreshing;

    // ==================================================================
    //  MAIN
    // ==================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            UIManager.put("defaultFont", FONT_PLAIN);
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            new LibraryClientGUI();
        });
    }

    // ==================================================================
    //  CONSTRUCTOR
    // ==================================================================

    private LibraryClientGUI() {
        frame = new JFrame("Library Management System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 700));
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);

        rootCards = new CardLayout();
        rootPanel = new JPanel(rootCards);
        rootPanel.setBackground(CONTENT_BG);

        try {
            remote = (LibraryInterface) Naming.lookup("rmi://localhost:1099/LibraryServer");
        } catch (Exception e) {
            JPanel err = buildErrorPanel();
            rootPanel.add(err, "error");
            frame.setContentPane(rootPanel);
            frame.setVisible(true);
            return;
        }

        rootPanel.add(buildLoginPanel(), "login");
        rootPanel.add(buildUserDashboard(), "user");
        rootPanel.add(buildAdminDashboard(), "admin");

        startAutoRefresh();

        frame.setContentPane(rootPanel);
        rootCards.show(rootPanel, "login");
        frame.setVisible(true);
    }

    // ==================================================================
    //  ERROR PANEL (RMI connection failed)
    // ==================================================================

    private JPanel buildErrorPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CONTENT_BG);
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JLabel icon = new JLabel("\u26A0");
        icon.setFont(new Font("Segoe UI", Font.PLAIN, 48));
        icon.setForeground(DANGER);
        icon.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(icon);
        inner.add(Box.createVerticalStrut(16));

        JLabel t = new JLabel("Connection Failed");
        t.setFont(FONT_TITLE);
        t.setForeground(TEXT_DARK);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(t);
        inner.add(Box.createVerticalStrut(8));

        JLabel m = new JLabel("Could not connect to RMI server at localhost:1099");
        m.setFont(FONT_PLAIN);
        m.setForeground(TEXT_SECONDARY);
        m.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(m);
        inner.add(Box.createVerticalStrut(4));

        JLabel h = new JLabel("Please start LibraryServer first, then re-launch this client.");
        h.setFont(FONT_SMALL);
        h.setForeground(TEXT_MUTED);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(h);

        p.add(inner);
        return p;
    }

    // ==================================================================
    //  LOGIN PANEL (split layout: decorative left + form right)
    // ==================================================================

    private JPanel buildLoginPanel() {
        JPanel split = new JPanel(new GridLayout(1, 2, 0, 0));
        split.setBackground(CONTENT_BG);

        // ---- LEFT: decorative branding panel ----
        JPanel left = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(67, 56, 202),
                        getWidth(), getHeight(), new Color(79, 70, 229));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 0, 0);
                g2.dispose();
            }
        };
        left.setLayout(new GridBagLayout());

        JPanel leftContent = new JPanel();
        leftContent.setLayout(new BoxLayout(leftContent, BoxLayout.Y_AXIS));
        leftContent.setOpaque(false);

        JLabel logoIcon = new JLabel(icon("logo.svg", 48, 48));
        logoIcon.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftContent.add(logoIcon);
        leftContent.add(Box.createVerticalStrut(20));

        JLabel brandTitle = new JLabel("Library");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 36));
        brandTitle.setForeground(TEXT_WHITE);
        brandTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftContent.add(brandTitle);

        JLabel brandSub = new JLabel("Management System");
        brandSub.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        brandSub.setForeground(new Color(199, 210, 254));
        brandSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftContent.add(brandSub);
        leftContent.add(Box.createVerticalStrut(40));

        String[] features = {
                ">  Browse & search library catalog",
                ">  Borrow & return books online",
                ">  Real-time availability tracking",
                ">  Admin management dashboard"
        };
        for (String f : features) {
            JLabel fl = new JLabel(f);
            fl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            fl.setForeground(new Color(199, 210, 254));
            fl.setAlignmentX(Component.LEFT_ALIGNMENT);
            leftContent.add(fl);
            leftContent.add(Box.createVerticalStrut(8));
        }

        left.add(leftContent);

        // ---- RIGHT: login form ----
        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(CARD_BG);

        JPanel formCard = new JPanel();
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBackground(CARD_BG);
        formCard.setBorder(new EmptyBorder(20, 50, 20, 50));

        formCard.setPreferredSize(new Dimension(400, 450));

        JLabel welcomeText = new JLabel("Welcome back");
        welcomeText.setFont(FONT_TITLE);
        welcomeText.setForeground(TEXT_DARK);
        welcomeText.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(welcomeText);
        formCard.add(Box.createVerticalStrut(4));

        JLabel subText = new JLabel("Sign in to your account");
        subText.setFont(FONT_PLAIN);
        subText.setForeground(TEXT_MUTED);
        subText.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(subText);
        formCard.add(Box.createVerticalStrut(32));

        // Username
        formCard.add(formLabel("Username"));
        formCard.add(Box.createVerticalStrut(6));
        JTextField userField = styledTextField();
        formCard.add(userField);
        formCard.add(Box.createVerticalStrut(18));

        // Password
        formCard.add(formLabel("Password"));
        formCard.add(Box.createVerticalStrut(6));
        JPasswordField passField = new JPasswordField();
        passField.setFont(FONT_PLAIN);
        passField.setPreferredSize(new Dimension(0, 42));
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        passField.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(passField);
        formCard.add(Box.createVerticalStrut(10));

        // Error
        JLabel errorLabel = new JLabel(" ");
        errorLabel.setFont(FONT_SMALL);
        errorLabel.setForeground(DANGER);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(errorLabel);
        formCard.add(Box.createVerticalStrut(8));

        // Login button
        JButton loginBtn = primaryButton("Sign In");
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        formCard.add(loginBtn);

        right.add(formCard);

        split.add(left);
        split.add(right);

        // ---- Login action ----
        Runnable doLogin = () -> {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword()).trim();
            if (u.isEmpty() || p.isEmpty()) {
                errorLabel.setText("Please enter both username and password.");
                return;
            }
            errorLabel.setText(" ");
            loginBtn.setEnabled(false);
            loginBtn.setText("Signing in...");

            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() throws Exception {
                    return remote.login(u, p);
                }
                @Override protected void done() {
                    try {
                        String result = get();
                        if (result != null && result.contains("|")) {
                            String[] parts = result.split("\\|");
                            String role = parts[1];
                            try { currentUserId = Integer.parseInt(parts[0].trim()); }
                            catch (NumberFormatException e) { currentUserId = 0; }
                            currentRole = role;
                            currentUsername = u;
                            showDashboard(role);
                        } else {
                            errorLabel.setText("Invalid username or password.");
                        }
                    } catch (Exception ex) {
                        errorLabel.setText("Connection error: " + ex.getMessage());
                    }
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Sign In");
                }
            };
            worker.execute();
        };

        loginBtn.addActionListener(e -> doLogin.run());
        passField.addActionListener(e -> doLogin.run());

        return split;
    }

    // ==================================================================
    //  USER DASHBOARD
    // ==================================================================

    private JPanel buildUserDashboard() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CONTENT_BG);

        root.add(buildSidebar("user"), BorderLayout.WEST);
        root.add(buildDashHeader("User Dashboard", "Browse, search, borrow and return books"), BorderLayout.NORTH);

        JPanel center = new JPanel(new CardLayout());
        center.setOpaque(false);
        center.add(buildUserBooksPanel(),  "books");
        center.add(buildUserSearchPanel(), "search");
        center.add(buildUserBorrowPanel(), "borrow");
        center.add(buildUserReturnPanel(), "return");

        userCenterCards = center;
        userCardLayout  = (CardLayout) center.getLayout();

        root.add(center, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    // ==================================================================
    //  ADMIN DASHBOARD
    // ==================================================================

    private JPanel buildAdminDashboard() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(CONTENT_BG);

        root.add(buildSidebar("admin"), BorderLayout.WEST);
        root.add(buildDashHeader("Admin Dashboard", "Manage library catalog and view transactions"), BorderLayout.NORTH);

        JPanel center = new JPanel(new CardLayout());
        center.setOpaque(false);
        center.add(buildAdminBooksPanel(),   "books");
        center.add(buildAdminAddPanel(),     "add");
        center.add(buildAdminEditPanel(),    "edit");
        center.add(buildAdminDeletePanel(),  "delete");
        center.add(buildAdminTxPanel(),      "tx");

        adminCenterCards = center;
        adminCardLayout  = (CardLayout) center.getLayout();

        root.add(center, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    // ==================================================================
    //  SHARED SIDEBAR
    // ==================================================================

    private JPanel buildSidebar(String role) {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        // ---- Brand ----
        JPanel brandPanel = new JPanel(new BorderLayout(12, 0));
        brandPanel.setOpaque(false);
        brandPanel.setBorder(new EmptyBorder(24, 22, 20, 22));
        brandPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        brandPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel logo = new JLabel(icon("logo.svg", 32, 32));
        JPanel brandText = new JPanel(new GridLayout(2, 1, 0, 2));
        brandText.setOpaque(false);
        JLabel bTitle = new JLabel("Library");
        bTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        bTitle.setForeground(TEXT_WHITE);
        JLabel bSub = new JLabel("Management System");
        bSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        bSub.setForeground(new Color(148, 163, 184));
        brandText.add(bTitle);
        brandText.add(bSub);
        brandPanel.add(logo, BorderLayout.WEST);
        brandPanel.add(brandText, BorderLayout.CENTER);
        sidebar.add(brandPanel);

        // ---- Separator ----
        sidebar.add(createSidebarSeparator());

        // ---- Navigation items ----
        if ("user".equals(role)) {
            sidebar.add(Box.createVerticalStrut(8));
            sidebar.add(sidebarSectionLabel("NAVIGATION"));
            sidebar.add(Box.createVerticalStrut(8));
            sidebar.add(userSideItem("All Books",    "books.svg",  "books"));
            sidebar.add(userSideItem("Search Books",  "search.svg", "search"));
            sidebar.add(userSideItem("Borrow Book",   "borrow.svg", "borrow"));
            sidebar.add(userSideItem("Return Book",   "return.svg", "return"));
        } else {
            sidebar.add(Box.createVerticalStrut(8));
            sidebar.add(sidebarSectionLabel("NAVIGATION"));
            sidebar.add(Box.createVerticalStrut(8));
            sidebar.add(adminSideItem("All Books",       "books.svg",      "books"));
            sidebar.add(adminSideItem("Add New Book",    "add.svg",        "add"));
            sidebar.add(adminSideItem("Edit Book",       "edit.svg",       "edit"));
            sidebar.add(adminSideItem("Delete Book",     "delete.svg",     "delete"));
            sidebar.add(adminSideItem("Transactions",    "transaction.svg","tx"));
        }

        sidebar.add(Box.createVerticalGlue());

        // ---- User info + Logout ----
        sidebar.add(createSidebarSeparator());

        JPanel userInfo = new JPanel(new BorderLayout(10, 0));
        userInfo.setOpaque(false);
        userInfo.setBorder(new EmptyBorder(8, 22, 8, 22));
        userInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        userInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel avatar = new JLabel(icon("user.svg", 20, 20));
        JPanel namePanel = new JPanel(new GridLayout(2, 1, 0, 1));
        namePanel.setOpaque(false);
        JLabel nameLabel = new JLabel("...");
        nameLabel.setFont(FONT_BOLD);
        nameLabel.setForeground(TEXT_WHITE);
        if ("user".equals(role)) sidebarNameLabelUser = nameLabel;
        else sidebarNameLabelAdmin = nameLabel;
        JLabel roleLabel = new JLabel(role.equals("user") ? "User" : "Admin");
        roleLabel.setFont(FONT_SMALL);
        roleLabel.setForeground(new Color(148, 163, 184));
        namePanel.add(nameLabel);
        namePanel.add(roleLabel);
        userInfo.add(avatar, BorderLayout.WEST);
        userInfo.add(namePanel, BorderLayout.CENTER);
        sidebar.add(userInfo);

        sidebar.add(Box.createVerticalStrut(4));

        JButton logoutBtn = sidebarNavItem("Sign Out", "logout.svg");
        logoutBtn.addActionListener(e -> {
            // Reset active-highlight so a future login doesn't show a stale selection
            if (activeUserBtn != null)  { styleSidebarBtn(activeUserBtn, false);  activeUserBtn = null;  }
            if (activeAdminBtn != null) { styleSidebarBtn(activeAdminBtn, false); activeAdminBtn = null; }
            currentRole = null;
            currentUsername = null;
            stopAutoRefresh();
            rootCards.show(rootPanel, "login");
        });
        sidebar.add(logoutBtn);

        sidebar.add(Box.createVerticalStrut(16));

        return sidebar;
    }

    // ==================================================================
    //  SIDEBAR COMPONENT BUILDERS
    // ==================================================================

    private JButton userSideItem(String text, String iconName, String cardName) {
        JButton btn = sidebarNavItem(text, iconName);
        btn.addActionListener(e -> {
            // Always refresh from the server so changes (from this or other clients) appear
            refreshUserBooks();
            if ("search".equals(cardName)) refreshSearchResults();
            if ("borrow".equals(cardName)) refreshUserBorrowed();
            setActiveBtn(btn, "user");
            userCardLayout.show(userCenterCards, cardName);
        });
        return btn;
    }

    private JButton adminSideItem(String text, String iconName, String cardName) {
        JButton btn = sidebarNavItem(text, iconName);
        btn.addActionListener(e -> {
            refreshAdminBooks();
            if ("tx".equals(cardName)) refreshTransactions();
            setActiveBtn(btn, "admin");
            adminCardLayout.show(adminCenterCards, cardName);
        });
        return btn;
    }

    private void setActiveBtn(JButton btn, String role) {
        if ("user".equals(role)) {
            if (activeUserBtn != null) styleSidebarBtn(activeUserBtn, false);
            activeUserBtn = btn;
        } else {
            if (activeAdminBtn != null) styleSidebarBtn(activeAdminBtn, false);
            activeAdminBtn = btn;
        }
        styleSidebarBtn(btn, true);
    }

    private void styleSidebarBtn(JButton btn, boolean active) {
        if (active) {
            btn.setBackground(SIDEBAR_ACTIVE);
            btn.setForeground(TEXT_WHITE);
        } else {
            btn.setBackground(SIDEBAR_BG);
            btn.setForeground(new Color(203, 213, 225));
        }
    }

    private JButton sidebarNavItem(String text, String iconName) {
        JButton btn = new JButton("  " + text, icon(iconName, 18, 18));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setForeground(new Color(203, 213, 225));
        btn.setBackground(SIDEBAR_BG);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(new EmptyBorder(11, 22, 11, 22));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn != activeUserBtn && btn != activeAdminBtn) {
                    btn.setBackground(SIDEBAR_HOVER);
                    btn.setContentAreaFilled(true);
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (btn != activeUserBtn && btn != activeAdminBtn) {
                    btn.setBackground(SIDEBAR_BG);
                    btn.setContentAreaFilled(false);
                }
            }
        });
        return btn;
    }

    private JPanel createSidebarSeparator() {
        JPanel sep = new JPanel();
        sep.setOpaque(false);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setBorder(new EmptyBorder(8, 22, 8, 22));
        return sep;
    }

    private JLabel sidebarSectionLabel(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(new Color(71, 85, 105));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        lbl.setBorder(new EmptyBorder(0, 18, 0, 0));
        return lbl;
    }

    // ==================================================================
    //  SHARED HEADER
    // ==================================================================

    private JPanel buildDashHeader(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(CARD_BG);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        header.setPreferredSize(new Dimension(0, 80));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.setBorder(new EmptyBorder(16, 30, 16, 0));

        JLabel t = new JLabel(title);
        t.setFont(FONT_HEADER);
        t.setForeground(TEXT_DARK);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(t);

        JLabel s = new JLabel(subtitle);
        s.setFont(FONT_SUB);
        s.setForeground(TEXT_MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(s);

        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);

        JButton refresh = accentButton("Refresh", "refresh.svg");
        refresh.addActionListener(e -> {
            if ("Admin".equals(currentRole)) {
                refreshAdminBooks();
                refreshTransactions();
            } else {
                refreshUserBooks();
                refreshUserBorrowed();
                refreshSearchResults();
            }
        });
        right.add(refresh);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    // ==================================================================
    //  USER: BOOKS TABLE
    // ==================================================================

    private JPanel buildUserBooksPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(CONTENT_BG);
        card.setBorder(new EmptyBorder(20, 28, 20, 28));

        // Table
        userBookModel = new DefaultTableModel(
                new Object[]{"ID", "Title", "Author", "Total", "Available"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = styledTable(userBookModel);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(3).setMaxWidth(70);
        table.getColumnModel().getColumn(4).setMaxWidth(80);
        card.add(paddedCard("All Books in Library", table), BorderLayout.CENTER);
        return card;
    }

    // ==================================================================
    //  USER: SEARCH
    // ==================================================================

    private JPanel buildUserSearchPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(CONTENT_BG);
        card.setBorder(new EmptyBorder(20, 28, 20, 28));

        JPanel searchRow = new JPanel(new BorderLayout(12, 0));
        searchRow.setOpaque(false);
        searchRow.setBorder(new EmptyBorder(0, 0, 0, 0));

        searchField = styledTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search by title or author...");
        searchField.setPreferredSize(new Dimension(0, 42));

        JButton searchBtn = accentButton("Search", null);
        searchBtn.setPreferredSize(new Dimension(120, 42));

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchBtn, BorderLayout.EAST);

        searchModel = new DefaultTableModel(
                new Object[]{"ID", "Title", "Author", "Total", "Available"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = styledTable(searchModel);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(3).setMaxWidth(70);
        table.getColumnModel().getColumn(4).setMaxWidth(80);

        searchInfo = new JLabel("Enter a keyword above and click Search.");
        searchInfo.setFont(FONT_SMALL);
        searchInfo.setForeground(TEXT_MUTED);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        JLabel st = new JLabel("Search Results");
        st.setFont(FONT_BOLD);
        st.setForeground(TEXT_DARK);
        headerRow.add(st, BorderLayout.WEST);
        headerRow.add(searchInfo, BorderLayout.EAST);

        JPanel searchContent = new JPanel(new BorderLayout(0, 12));

        searchContent.setBackground(CARD_BG);
        searchContent.setBorder(cardBorder());
        searchContent.add(headerRow, BorderLayout.NORTH);
        searchContent.add(new JScrollPane(table), BorderLayout.CENTER);

        card.add(searchRow, BorderLayout.NORTH);
        card.add(searchContent, BorderLayout.CENTER);

        searchBtn.addActionListener(e -> doSearch(searchField.getText().trim()));
        searchField.addActionListener(e -> searchBtn.doClick());
        return card;
    }
    
    private void doSearch(String keyword) {
        if (searchModel == null) return;
        if (keyword == null || keyword.trim().isEmpty()) {
            lastSearchKeyword = null;
            if (searchInfo != null) searchInfo.setText("Please enter a keyword.");
            return;
        }
        String kw = keyword.trim();
        lastSearchKeyword = kw;
        searchModel.setRowCount(0);
        try {
            String raw = remote.searchBooks(kw);
            if (raw != null && raw.startsWith("ERROR")) {
                if (searchInfo != null) searchInfo.setText(raw);
                return;
            }
            int count = 0;
            if (!"EMPTY".equals(raw)) {
                for (String line : raw.split("\n")) {
                    String[] p = line.split("\\|");
                    if (p.length >= 5) {
                        searchModel.addRow(new Object[]{
                                Integer.parseInt(p[0].trim()), p[1], p[2],
                                Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim())});
                        count++;
                    }
                }
            }
            if (searchInfo != null) searchInfo.setText(count + " result(s) for \"" + kw + "\"");
        } catch (Exception ex) {
            if (searchInfo != null) searchInfo.setText("Error: " + ex.getMessage());
        }
    }


    private void refreshSearchResults() {
        if (lastSearchKeyword != null) doSearch(lastSearchKeyword);
    }

    private void refreshUserBorrowed() {
        if (userBorrowedModel == null || currentUserId <= 0) return;
        try {
            String raw = remote.viewUserBorrowedBooks(currentUserId);
            userBorrowedModel.setRowCount(0);
            if (raw != null && raw.startsWith("ERROR")) {
                System.err.println(raw);
                return;
            }
            if (!"EMPTY".equals(raw)) {
                for (String line : raw.split("\n")) {
                    String[] p = line.split("\\|");
                    if (p.length >= 4) {
                        userBorrowedModel.addRow(new Object[]{p[0], p[1], p[2], p[3]});
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load borrowed books: " + e.getMessage());
        }
    }



    private JPanel buildUserBorrowPanel() {
        JPanel card = new JPanel(new BorderLayout(16, 0));
        card.setBackground(CONTENT_BG);
        card.setBorder(new EmptyBorder(20, 28, 20, 28));

        JPanel formCard = buildActionPanel("Borrow a Book",
                "Enter the Book ID of the book you wish to borrow",
                "Borrow", SUCCESS,
                (bookId) -> remote.borrowBook(currentUserId, bookId),
                this::refreshUserBorrowed);

        card.add(formCard, BorderLayout.WEST);
        card.add(buildUserBorrowedCard(), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildUserReturnPanel() {
        JPanel card = new JPanel(new BorderLayout(16, 0));
        card.setBackground(CONTENT_BG);
        card.setBorder(new EmptyBorder(20, 28, 20, 28));

        // LEFT: return form
        JPanel formCard = buildActionPanel("Return a Book",
                "Enter the Book ID of the book you wish to return",
                "Return", INFO,
                (bookId) -> remote.returnBook(currentUserId, bookId),
                this::refreshUserBorrowed);

        // RIGHT: currently borrowed books for the logged-in user (shared model)
        card.add(formCard, BorderLayout.WEST);
        card.add(buildUserBorrowedCard(), BorderLayout.CENTER);
        return card;
    }


    private JPanel buildUserBorrowedCard() {
        if (userBorrowedModel == null) {
            userBorrowedModel = new DefaultTableModel(
                    new Object[]{"TxID", "Book ID", "Title", "Borrow Date"}, 0) {
                @Override public boolean isCellEditable(int r, int c) { return false; }
            };
        }
        JTable table = styledTable(userBorrowedModel);
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(1).setMaxWidth(90);
        return paddedCard("Currently Borrowed Books", table);
    }

    private JPanel buildActionPanel(String heading, String subtext, String btnText,
                                    Color btnColor, RemoteAction action, Runnable onSuccess) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CONTENT_BG);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(CARD_BG);
        form.setBorder(cardBorder());
        form.setPreferredSize(new Dimension(480, 340));

        form.add(Box.createVerticalStrut(8));

        JLabel h = new JLabel(heading);
        h.setFont(FONT_HEADER);
        h.setForeground(TEXT_DARK);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(h);
        form.add(Box.createVerticalStrut(4));

        JLabel s = new JLabel(subtext);
        s.setFont(FONT_SUB);
        s.setForeground(TEXT_MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(s);
        form.add(Box.createVerticalStrut(24));

        form.add(formLabel("Book ID"));
        form.add(Box.createVerticalStrut(6));
        JTextField idField = styledTextField();
        form.add(idField);
        form.add(Box.createVerticalStrut(12));

        JButton btn = coloredButton(btnText, btnColor);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(btn);
        form.add(Box.createVerticalStrut(10));

        JLabel msg = new JLabel(" ");
        msg.setFont(FONT_SMALL);
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(msg);

        btn.addActionListener(e -> {
            String text = idField.getText().trim();
            if (text.isEmpty()) { msg.setText("Please enter a book ID."); msg.setForeground(DANGER); return; }
            try {
                int bookId = Integer.parseInt(text);
                String result = action.execute(bookId);
                // "Successfully borrowed ..." AND "Book returned successfully." both contain "success"
                boolean ok = result.toLowerCase().contains("success");
                if (ok) {
                    refreshUserBooks();
                    refreshSearchResults();
                    if (onSuccess != null) onSuccess.run();
                    idField.setText("");
                    JOptionPane.showMessageDialog(frame, result, "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    msg.setText(result);
                    msg.setForeground(DANGER);
                }
            } catch (NumberFormatException ex) {
                msg.setText("Invalid ID. Please enter a number.");
                msg.setForeground(DANGER);
            } catch (Exception ex) {
                msg.setText("Error: " + ex.getMessage());
                msg.setForeground(DANGER);
            }
        });
        idField.addActionListener(e -> btn.doClick());

        GridBagConstraints center = new GridBagConstraints();
        center.gridx = 0;
        center.gridy = 0;
        center.weightx = 1;
        center.weighty = 1;
        center.anchor = GridBagConstraints.NORTH;
        card.add(form, center);
        return card;
    }

    // ==================================================================
    //  ADMIN: BOOKS TABLE (with stat cards)
    // ==================================================================

    private JPanel buildAdminBooksPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(CONTENT_BG);
        card.setBorder(new EmptyBorder(20, 28, 20, 28));

        JPanel stats = new JPanel(new GridLayout(1, 3, 16, 0));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(0, 100));
        adminUserCountLabel     = new JLabel("0");
        adminAvailCountLabel    = new JLabel("0");
        adminBorrowedCountLabel = new JLabel("0");
        stats.add(statCard("Total Books",     adminUserCountLabel,     "All books in catalog",  PRIMARY, PRIMARY_LIGHT));
        stats.add(statCard("Available",        adminAvailCountLabel,    "Ready to borrow",       SUCCESS, SUCCESS_BG));
        stats.add(statCard("Currently Borrowed", adminBorrowedCountLabel, "Out on loan",       DANGER,  DANGER_BG));
        card.add(stats, BorderLayout.NORTH);

        adminBookModel = new DefaultTableModel(
                new Object[]{"ID", "Title", "Author", "Total Copies", "Available"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = styledTable(adminBookModel);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(3).setMaxWidth(100);
        table.getColumnModel().getColumn(4).setMaxWidth(100);
        card.add(paddedCard("All Books", table), BorderLayout.CENTER);
        return card;
    }

    // ==================================================================
    //  ADMIN: ADD BOOK
    // ==================================================================

    private JPanel buildAdminAddPanel() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CONTENT_BG);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(CARD_BG);
        form.setBorder(cardBorder());
        form.setPreferredSize(new Dimension(480, 420));

        form.add(Box.createVerticalStrut(8));

        JLabel h = new JLabel("Add a New Book");
        h.setFont(FONT_HEADER);
        h.setForeground(TEXT_DARK);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(h);
        form.add(Box.createVerticalStrut(4));
        JLabel sub = new JLabel("Fill in the details to add a book to the catalog");
        sub.setFont(FONT_SUB);
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(sub);
        form.add(Box.createVerticalStrut(24));

        JTextField titleField  = labelledField("Title", form);
        JTextField authorField = labelledField("Author", form);
        JTextField copiesField = labelledField("Total Copies", form);

        form.add(Box.createVerticalStrut(6));

        JLabel msg = new JLabel(" ");
        msg.setFont(FONT_SMALL);
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(msg);
        form.add(Box.createVerticalStrut(8));

        JButton addBtn = accentButton("Add Book", "add.svg");
        addBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(addBtn);

        addBtn.addActionListener(e -> {
            String title = titleField.getText().trim();
            String author = authorField.getText().trim();
            String copiesStr = copiesField.getText().trim();
            if (title.isEmpty() || author.isEmpty() || copiesStr.isEmpty()) {
                msg.setText("All fields are required."); msg.setForeground(DANGER); return;
            }
            try {
                int copies = Integer.parseInt(copiesStr);
                if (copies <= 0) { msg.setText("Copies must be > 0."); msg.setForeground(DANGER); return; }
                String result = remote.addBook(title, author, copies);
                boolean ok = result.toLowerCase().contains("successfully");
                if (ok) {
                    titleField.setText(""); authorField.setText(""); copiesField.setText("");
                    refreshAdminBooks();
                    JOptionPane.showMessageDialog(frame, result, "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    msg.setText(result);
                    msg.setForeground(DANGER);
                }
            } catch (NumberFormatException ex) {
                msg.setText("Copies must be a valid number."); msg.setForeground(DANGER);
            } catch (Exception ex) {
                msg.setText("Error: " + ex.getMessage()); msg.setForeground(DANGER);
            }
        });

        GridBagConstraints center = new GridBagConstraints();
        center.gridx = 0;
        center.gridy = 0;
        center.weightx = 1;
        center.weighty = 1;
        center.anchor = GridBagConstraints.NORTH;
        card.add(form, center);
        return card;
    }

    // ==================================================================
    //  ADMIN: EDIT BOOK
    // ==================================================================

    private JPanel buildAdminEditPanel() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CONTENT_BG);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(CARD_BG);
        form.setBorder(cardBorder());
        form.setPreferredSize(new Dimension(480, 480));

        form.add(Box.createVerticalStrut(8));

        JLabel h = new JLabel("Edit a Book");
        h.setFont(FONT_HEADER);
        h.setForeground(TEXT_DARK);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(h);
        form.add(Box.createVerticalStrut(4));
        JLabel sub = new JLabel("Enter a Book ID to load, then update its details");
        sub.setFont(FONT_SUB);
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(sub);
        form.add(Box.createVerticalStrut(24));

        // Book ID + Load row
        JPanel idRow = new JPanel(new BorderLayout(8, 0));
        idRow.setOpaque(false);
        idRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        idRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(formLabel("Book ID"));
        form.add(Box.createVerticalStrut(6));
        JTextField idField = styledTextField();
        idField.setPreferredSize(new Dimension(0, 42));
        JButton loadBtn = accentButton("Load", null);
        loadBtn.setPreferredSize(new Dimension(90, 42));
        idRow.add(idField, BorderLayout.CENTER);
        idRow.add(loadBtn, BorderLayout.EAST);
        form.add(idRow);
        form.add(Box.createVerticalStrut(16));

        JTextField titleField  = labelledField("Title", form);
        JTextField authorField = labelledField("Author", form);
        JTextField copiesField = labelledField("Total Copies", form);

        form.add(Box.createVerticalStrut(6));

        JLabel msg = new JLabel(" ");
        msg.setFont(FONT_SMALL);
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(msg);
        form.add(Box.createVerticalStrut(8));

        JButton updateBtn = accentButton("Update Book", "edit.svg");
        updateBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(updateBtn);

        // Load action: fetch book details by ID
        loadBtn.addActionListener(e -> {
            String text = idField.getText().trim();
            if (text.isEmpty()) { msg.setText("Please enter a Book ID."); msg.setForeground(DANGER); return; }
            try {
                int bookId = Integer.parseInt(text);
                String raw = remote.viewAllBooks();
                if (raw == null || "EMPTY".equals(raw)) { msg.setText("No books found."); msg.setForeground(DANGER); return; }
                boolean found = false;
                for (String line : raw.split("\n")) {
                    String[] p = line.split("\\|");
                    if (p.length >= 5 && Integer.parseInt(p[0].trim()) == bookId) {
                        titleField.setText(p[1]);
                        authorField.setText(p[2]);
                        copiesField.setText(p[3]);
                        msg.setText("Book loaded. Edit fields and click Update.");
                        msg.setForeground(SUCCESS);
                        found = true;
                        break;
                    }
                }
                if (!found) { msg.setText("Book ID " + bookId + " not found."); msg.setForeground(DANGER); }
            } catch (NumberFormatException ex) {
                msg.setText("Invalid ID. Please enter a number.");
                msg.setForeground(DANGER);
            } catch (Exception ex) {
                msg.setText("Error: " + ex.getMessage());
                msg.setForeground(DANGER);
            }
        });
        idField.addActionListener(e -> loadBtn.doClick());

        // Update action
        updateBtn.addActionListener(e -> {
            String idText = idField.getText().trim();
            String title = titleField.getText().trim();
            String author = authorField.getText().trim();
            String copiesStr = copiesField.getText().trim();
            if (idText.isEmpty() || title.isEmpty() || author.isEmpty() || copiesStr.isEmpty()) {
                msg.setText("All fields are required."); msg.setForeground(DANGER); return;
            }
            try {
                int bookId = Integer.parseInt(idText);
                int copies = Integer.parseInt(copiesStr);
                if (copies <= 0) { msg.setText("Copies must be > 0."); msg.setForeground(DANGER); return; }
                String result = remote.editBook(bookId, title, author, copies);
                boolean ok = result.toLowerCase().contains("success");
                if (ok) {
                    idField.setText(""); titleField.setText(""); authorField.setText(""); copiesField.setText("");
                    refreshAdminBooks();
                    JOptionPane.showMessageDialog(frame, result, "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    msg.setText(result);
                    msg.setForeground(DANGER);
                }
            } catch (NumberFormatException ex) {
                msg.setText("ID and Copies must be valid numbers.");
                msg.setForeground(DANGER);
            } catch (Exception ex) {
                msg.setText("Error: " + ex.getMessage());
                msg.setForeground(DANGER);
            }
        });

        GridBagConstraints center = new GridBagConstraints();
        center.gridx = 0;
        center.gridy = 0;
        center.weightx = 1;
        center.weighty = 1;
        center.anchor = GridBagConstraints.NORTH;
        card.add(form, center);
        return card;
    }

    // ==================================================================
    //  ADMIN: DELETE BOOK
    // ==================================================================

    private JPanel buildAdminDeletePanel() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(CONTENT_BG);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(CARD_BG);
        form.setBorder(cardBorder());
        form.setPreferredSize(new Dimension(480, 340));

        form.add(Box.createVerticalStrut(8));

        JLabel h = new JLabel("Delete a Book");
        h.setFont(FONT_HEADER);
        h.setForeground(TEXT_DARK);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(h);
        form.add(Box.createVerticalStrut(4));
        JLabel sub = new JLabel("Permanently remove a book from the catalog");
        sub.setFont(FONT_SUB);
        sub.setForeground(TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(sub);
        form.add(Box.createVerticalStrut(24));

        form.add(formLabel("Book ID"));
        form.add(Box.createVerticalStrut(6));
        JTextField idField = styledTextField();
        form.add(idField);
        form.add(Box.createVerticalStrut(12));

        JButton delBtn = coloredButton("Delete Book", DANGER);
        delBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(delBtn);
        form.add(Box.createVerticalStrut(10));

        JLabel msg = new JLabel(" ");
        msg.setFont(FONT_SMALL);
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(msg);

        delBtn.addActionListener(e -> {
            String text = idField.getText().trim();
            if (text.isEmpty()) { msg.setText("Please enter a Book ID."); msg.setForeground(DANGER); return; }
            try {
                int bookId = Integer.parseInt(text);
                int confirm = JOptionPane.showConfirmDialog(frame,
                        "Are you sure you want to delete Book ID " + bookId + "?",
                        "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) return;
                String result = remote.deleteBook(bookId);
                boolean ok = result.toLowerCase().contains("deleted");
                if (ok) {
                    idField.setText("");
                    refreshAdminBooks();
                    JOptionPane.showMessageDialog(frame, result, "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    msg.setText(result);
                    msg.setForeground(DANGER);
                }
            } catch (NumberFormatException ex) {
                msg.setText("Invalid ID."); msg.setForeground(DANGER);
            } catch (Exception ex) {
                msg.setText("Error: " + ex.getMessage()); msg.setForeground(DANGER);
            }
        });
        idField.addActionListener(e -> delBtn.doClick());

        GridBagConstraints center = new GridBagConstraints();
        center.gridx = 0;
        center.gridy = 0;
        center.weightx = 1;
        center.weighty = 1;
        center.anchor = GridBagConstraints.NORTH;
        card.add(form, center);
        return card;
    }

    // ==================================================================
    //  ADMIN: TRANSACTIONS
    // ==================================================================

    private JPanel buildAdminTxPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(CONTENT_BG);
        card.setBorder(new EmptyBorder(20, 28, 20, 28));

        adminTxModel = new DefaultTableModel(
                new Object[]{"TxID", "User", "Book", "Borrow Date", "Return Date", "Status"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = styledTable(adminTxModel);
        table.getColumnModel().getColumn(0).setMaxWidth(55);
        table.getColumnModel().getColumn(5).setMaxWidth(110);
        card.add(paddedCard("Borrowing Transactions", table), BorderLayout.CENTER);
        return card;
    }

    // ==================================================================
    //  STAT CARD
    // ==================================================================

    private JPanel statCard(String label, JLabel value, Color accentColor, Color bgColor) {
        return statCard(label, value, "", accentColor, bgColor);
    }

    private JPanel statCard(String label, JLabel value, String note, Color accentColor, Color bgColor) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(bgColor);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 20)),
                new EmptyBorder(16, 20, 16, 20)));

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbl.setForeground(TEXT_SECONDARY);
        card.add(lbl, BorderLayout.NORTH);

        value.setFont(new Font("Segoe UI", Font.BOLD, 28));
        value.setForeground(accentColor);
        card.add(value, BorderLayout.CENTER);

        if (!note.isEmpty()) {
            JLabel n = new JLabel(note);
            n.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            n.setForeground(TEXT_MUTED);
            card.add(n, BorderLayout.SOUTH);
        }
        return card;
    }

    // ==================================================================
    //  DATA REFRESH
    // ==================================================================

    /**
     * Starts a lightweight Swing timer that periodically re-fetches the
     * visible role's data. This gives both clients (user and admin) a near
     * real-time view of changes made by the other, without pressing Refresh.
     */
    private void startAutoRefresh() {
        if (autoRefreshTimer != null) return;
        autoRefreshTimer = new javax.swing.Timer(AUTO_REFRESH_MS, e -> {
            if (currentRole == null || refreshing) return;   // not logged in / tick in progress
            refreshing = true;
            try {
                if ("Admin".equals(currentRole)) {
                    refreshAdminBooks();
                    refreshTransactions();
                } else {
                    refreshUserBooks();
                    refreshUserBorrowed();
                    refreshSearchResults();
                }
            } finally {
                refreshing = false;
            }
        });
        autoRefreshTimer.setRepeats(true);
        autoRefreshTimer.start();
    }

    private void stopAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
            autoRefreshTimer = null;
        }
    }

    private void refreshUserBooks() {
        if (userBookModel == null) return;
        try {
            String raw = remote.viewAllBooks();
            userBookModel.setRowCount(0);
            if (raw != null && raw.startsWith("ERROR")) {
                System.err.println(raw);
                if (userCountLabel != null)     userCountLabel.setText("0");
                if (availCountLabel != null)    availCountLabel.setText("0");
                if (borrowedCountLabel != null) borrowedCountLabel.setText("0");
                return;
            }
            int total = 0, avail = 0;
            if (!"EMPTY".equals(raw)) {
                for (String line : raw.split("\n")) {
                    String[] p = line.split("\\|");
                    if (p.length >= 5) {
                        int t = Integer.parseInt(p[3].trim());
                        int a = Integer.parseInt(p[4].trim());
                        userBookModel.addRow(new Object[]{
                                Integer.parseInt(p[0].trim()), p[1], p[2], t, a});
                        total += t;
                        avail += a;
                    }
                }
            }
            if (userCountLabel != null)     userCountLabel.setText(String.valueOf(total));
            if (availCountLabel != null)    availCountLabel.setText(String.valueOf(avail));
            if (borrowedCountLabel != null) borrowedCountLabel.setText(String.valueOf(total - avail));
        } catch (Exception e) {
            System.err.println("Failed to load books: " + e.getMessage());
            userBookModel.setRowCount(0);
        }
    }

    private void refreshAdminBooks() {
        if (adminBookModel == null) return;
        try {
            String raw = remote.viewAllBooks();
            adminBookModel.setRowCount(0);
            if (raw != null && raw.startsWith("ERROR")) {
                System.err.println(raw);
                if (adminUserCountLabel != null)     adminUserCountLabel.setText("0");
                if (adminAvailCountLabel != null)    adminAvailCountLabel.setText("0");
                if (adminBorrowedCountLabel != null) adminBorrowedCountLabel.setText("0");
                return;
            }
            int total = 0, avail = 0;
            if (!"EMPTY".equals(raw)) {
                for (String line : raw.split("\n")) {
                    String[] p = line.split("\\|");
                    if (p.length >= 5) {
                        int t = Integer.parseInt(p[3].trim());
                        int a = Integer.parseInt(p[4].trim());
                        adminBookModel.addRow(new Object[]{
                                Integer.parseInt(p[0].trim()), p[1], p[2], t, a});
                        total += t;
                        avail += a;
                    }
                }
            }
            if (adminUserCountLabel != null)     adminUserCountLabel.setText(String.valueOf(total));
            if (adminAvailCountLabel != null)    adminAvailCountLabel.setText(String.valueOf(avail));
            if (adminBorrowedCountLabel != null) adminBorrowedCountLabel.setText(String.valueOf(total - avail));
        } catch (Exception e) {
            System.err.println("Failed to load books: " + e.getMessage());
            adminBookModel.setRowCount(0);
        }
    }

    private void refreshTransactions() {
        if (adminTxModel == null) return;
        try {
            String raw = remote.viewAllTransactions();
            adminTxModel.setRowCount(0);
            if (raw != null && raw.startsWith("ERROR")) {
                System.err.println(raw);
                return;
            }
            if (!"EMPTY".equals(raw)) {
                for (String line : raw.split("\n")) {
                    String[] p = line.split("\\|");
                    if (p.length >= 6) {
                        adminTxModel.addRow(new Object[]{
                                Integer.parseInt(p[0].trim()), p[1], p[2], p[3], p[4], p[5]});
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load transactions: " + e.getMessage());
            adminTxModel.setRowCount(0);
        }
    }

    // ==================================================================
    //  DASHBOARD ROUTER
    // ==================================================================

    private void showDashboard(String role) {
        startAutoRefresh();
        // Sidebar name labels were created at startup (before login), so fill them in now
        if (currentUsername != null) {
            if (sidebarNameLabelUser != null) sidebarNameLabelUser.setText(currentUsername);
            if (sidebarNameLabelAdmin != null) sidebarNameLabelAdmin.setText(currentUsername);
        }
        if ("Admin".equals(role)) {
            refreshAdminBooks();
            adminCardLayout.show(adminCenterCards, "books");
            // Set initial active button
            activeAdminBtn = null;
            rootCards.show(rootPanel, "admin");
        } else {
            refreshUserBooks();
            refreshUserBorrowed();
            userCardLayout.show(userCenterCards, "books");
            activeUserBtn = null;
            rootCards.show(rootPanel, "user");
        }
    }

    // ==================================================================
    //  SHARED UI BUILDERS
    // ==================================================================

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        footer.setBackground(CARD_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        footer.setPreferredSize(new Dimension(0, 36));
        JLabel label = new JLabel("\u25CF  Connected to RMI Server at localhost:1099");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(SUCCESS);
        footer.add(label);
        return footer;
    }

    private JPanel paddedCard(String title, JTable table) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(CARD_BG);
        card.setBorder(cardBorder());

        JLabel t = new JLabel(title);
        t.setFont(FONT_BOLD);
        t.setForeground(TEXT_DARK);
        card.add(t, BorderLayout.NORTH);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (c instanceof JComponent jc) {
                    jc.setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                return c;
            }
        };
        table.setRowHeight(42);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setSelectionBackground(PRIMARY_LIGHT);
        table.setSelectionForeground(TEXT_DARK);
        table.setGridColor(BORDER_LIGHT);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(248, 250, 252));
        table.getTableHeader().setForeground(TEXT_SECONDARY);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER));
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));

        // Alternating row colors
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val,
                                                           boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                if (!sel) {
                    setBackground(row % 2 == 0 ? CARD_BG : new Color(248, 250, 252));
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return this;
            }
        });

        return table;
    }

    // ==================================================================
    //  BUTTON BUILDERS
    // ==================================================================

    private JButton primaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(PRIMARY);
        btn.setForeground(TEXT_WHITE);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 24, 10, 24));
        return btn;
    }

    private JButton accentButton(String text, String iconName) {
        JButton btn = new JButton("  " + text, icon(iconName, 16, 16));
        btn.setBackground(PRIMARY);
        btn.setForeground(TEXT_WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(9, 18, 9, 18));
        return btn;
    }

    private JButton coloredButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(TEXT_WHITE);
        btn.setFont(FONT_BOLD);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 24, 10, 24));
        return btn;
    }

    // ==================================================================
    //  FORM HELPERS
    // ==================================================================

    private JLabel formLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(TEXT_SECONDARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField styledTextField() {
        JTextField field = new JTextField();
        field.setFont(FONT_PLAIN);
        field.setPreferredSize(new Dimension(0, 42));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }

    private JTextField labelledField(String label, JPanel parent) {
        parent.add(formLabel(label));
        parent.add(Box.createVerticalStrut(6));
        JTextField field = styledTextField();
        parent.add(field);
        parent.add(Box.createVerticalStrut(16));
        return field;
    }

    // ==================================================================
    //  ICON HELPER
    // ==================================================================

    private static Icon icon(String name, int w, int h) {
        try {
            // FlatSVGIcon uses ClassLoader.getResource() which must NOT have a leading "/"
            return new FlatSVGIcon("icons/" + name, w, h);
        } catch (Exception e) {
            System.err.println("Icon load failed: " + name + " - " + e.getMessage());
        }
        return null;
    }

    // ==================================================================
    //  BORDER HELPER
    // ==================================================================

    private static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(18, 20, 18, 20));
    }

    // ==================================================================
    //  Functional interface
    // ==================================================================

    @FunctionalInterface
    private interface RemoteAction {
        String execute(int bookId) throws Exception;
    }
}