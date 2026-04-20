package com.chat;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Timer;

/**
 * Swing GUI quản lý server — chạy cùng tiến trình với Jetty.
 * Gọi thẳng ChatRoom.get() thay vì qua network.
 */
public class ChatServer extends JFrame {

    // ══ THEME ══════════════════════════════════════════════
    private static final Color BG_MAIN   = new Color(245, 245, 248);
    private static final Color BG_CARD   = Color.WHITE;
    private static final Color BG_ACCENT = new Color(235, 233, 255);
    private static final Color C_PRIMARY = new Color(60, 52, 137);
    private static final Color C_SUCCESS = new Color(15, 110, 86);
    private static final Color C_DANGER  = new Color(163, 45, 45);
    private static final Color C_MUTED   = new Color(120, 120, 130);
    private static final Color C_BORDER  = new Color(220, 218, 235);
    private static final Color C_LOGBG   = new Color(22, 22, 32);
    private static final Color C_LOG_SYS  = new Color(170, 160, 255);
    private static final Color C_LOG_JOIN = new Color(80, 210, 150);
    private static final Color C_LOG_KICK = new Color(255, 120, 120);
    private static final Color C_LOG_MSG  = new Color(190, 190, 205);

    private static final Font F_TITLE = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font F_BODY  = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font F_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font F_MONO  = new Font("Consolas", Font.PLAIN, 12);
    private static final Font F_STAT  = new Font("Segoe UI", Font.BOLD, 28);

    // ══ STATE ══════════════════════════════════════════════
    private boolean serverRunning = false;
    private int serverPort = 8080;
    private Timer refreshTimer;

    // ══ GUI ════════════════════════════════════════════════
    // Tab 1
    private JTextField tfRoomName, tfPort, tfMaxConn, tfPassword, tfWelcome;
    private JButton    btnStart, btnStop;
    private JLabel     lblOpenStatus;
    private JPanel     dotOpen;
    private JTextArea  ipBox;

    // Tab 2
    private JLabel     lblOnline, lblTotal, lblMessages, lblManageStatus;
    private JPanel     dotManage;
    private JTable     userTable;
    private DefaultTableModel userModel;
    private JTextPane  logPane;
    private JButton    btnKick, btnBroadcast;
    private JTextField tfBroadcast;

    // ══ CONSTRUCTOR ════════════════════════════════════════
    public ChatServer(int port) {
        super("Chat Server Manager");
        this.serverPort = port;
        this.serverRunning = true; // Jetty đã chạy sẵn từ Main

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 680);
        setMinimumSize(new Dimension(700, 560));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_MAIN);
        buildUI();

        // Khởi tạo UI với trạng thái đang chạy
        SwingUtilities.invokeLater(() -> setRunningUI(true));

        // Auto refresh UI mỗi 2 giây
        refreshTimer = new Timer(true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(() -> refreshAll());
            }
        }, 2000, 2000);

        // Đăng ký log callback với ChatRoom
        ChatRoom.get().setLogCallback((msg, type) ->
            SwingUtilities.invokeLater(() -> appendLog(msg, colorForType(type)))
        );

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (refreshTimer != null) refreshTimer.cancel();
            }
        });
    }

    // ══ BUILD UI ═══════════════════════════════════════════
    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(F_TITLE);
        tabs.setBackground(BG_MAIN);
        tabs.addTab("  Mở phòng  ", buildOpenTab());
        tabs.addTab("  Quản lý phòng  ", buildManageTab());
        tabs.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_PRIMARY);
        p.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
        JLabel t1 = new JLabel("Chat Server Manager");
        t1.setFont(new Font("Segoe UI", Font.BOLD, 18));
        t1.setForeground(Color.WHITE);
        JLabel t2 = new JLabel("Quản lý phòng chat TCP/IP · Port " + serverPort);
        t2.setFont(F_SMALL);
        t2.setForeground(new Color(200, 190, 255));
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
        left.setOpaque(false);
        left.add(t1); left.add(t2);
        p.add(left, BorderLayout.WEST);
        return p;
    }

    // ── TAB 1: MỞ PHÒNG ────────────────────────────────────
    private JPanel buildOpenTab() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BG_MAIN);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        statusBar.setBackground(new Color(240, 238, 250));
        statusBar.setBorder(new LineBorder(C_BORDER, 1, true));
        statusBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        dotOpen = makeDot();
        lblOpenStatus = new JLabel("Đang khởi động...");
        lblOpenStatus.setFont(F_BODY); lblOpenStatus.setForeground(C_MUTED);
        statusBar.add(dotOpen); statusBar.add(lblOpenStatus);
        root.add(statusBar);
        root.add(Box.createVerticalStrut(12));

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createTitledBorder(
            new LineBorder(C_BORDER, 1, true), "  Cấu hình phòng chat  ",
            TitledBorder.LEFT, TitledBorder.TOP, F_TITLE, C_PRIMARY));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));

        GridBagConstraints g = gbc();

        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        card.add(lbl("Tên phòng *"), g);
        g.gridy = 1;
        tfRoomName = field("VD: Phòng kỹ thuật...");
        tfRoomName.setText(ChatRoom.get().getRoomName());
        card.add(tfRoomName, g);

        g.gridy = 2; g.gridwidth = 1;
        card.add(lbl("Cổng (Port)"), g);
        g.gridx = 1; card.add(lbl("Số kết nối tối đa"), g);
        g.gridy = 3; g.gridx = 0;
        tfPort = field("8080");
        tfPort.setText(String.valueOf(serverPort));
        tfPort.setEnabled(false); // port không thể đổi khi đang chạy
        card.add(tfPort, g);
        g.gridx = 1;
        tfMaxConn = field("50");
        tfMaxConn.setText(String.valueOf(ChatRoom.get().getMaxUsers()));
        card.add(tfMaxConn, g);

        g.gridy = 4; g.gridx = 0; g.gridwidth = 2;
        card.add(lbl("Mật khẩu phòng (để trống nếu không cần)"), g);
        g.gridy = 5;
        tfPassword = field("Nhập mật khẩu...");
        tfPassword.setText(ChatRoom.get().getPassword());
        card.add(tfPassword, g);

        g.gridy = 6; card.add(lbl("Tin nhắn chào mừng"), g);
        g.gridy = 7;
        tfWelcome = field("VD: Chào mừng đến phòng chat!");
        tfWelcome.setText(ChatRoom.get().getWelcomeMsg());
        card.add(tfWelcome, g);

        g.gridy = 8; card.add(lbl("Địa chỉ IP máy chủ (người dùng truy cập)"), g);
        g.gridy = 9;
        ipBox = new JTextArea(getLocalIPs());
        ipBox.setEditable(false);
        ipBox.setFont(F_MONO);
        ipBox.setBackground(BG_ACCENT);
        ipBox.setForeground(C_PRIMARY);
        ipBox.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        ipBox.setRows(2);
        card.add(ipBox, g);

        root.add(card);
        root.add(Box.createVerticalStrut(14));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        btnRow.setOpaque(false);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        btnStart = actionBtn("▶   Áp dụng cấu hình", C_PRIMARY, Color.WHITE);
        btnStart.setPreferredSize(new Dimension(210, 40));
        btnStart.addActionListener(e -> doApply());

        btnStop = actionBtn("■   Dừng Server", C_DANGER, Color.WHITE);
        btnStop.setPreferredSize(new Dimension(210, 40));
        btnStop.addActionListener(e -> doStop());

        btnRow.add(btnStart); btnRow.add(btnStop);
        root.add(btnRow);
        return root;
    }

    // ── TAB 2: QUẢN LÝ ─────────────────────────────────────
    private JPanel buildManageTab() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(BG_MAIN);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JPanel mStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        mStatus.setBackground(new Color(240, 238, 250));
        mStatus.setBorder(new LineBorder(C_BORDER, 1, true));
        mStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        dotManage = makeDot();
        lblManageStatus = new JLabel("Đang khởi động...");
        lblManageStatus.setFont(F_BODY); lblManageStatus.setForeground(C_MUTED);
        mStatus.add(dotManage); mStatus.add(lblManageStatus);

        top.add(mStatus);
        top.add(Box.createVerticalStrut(10));
        top.add(buildStatCards());
        root.add(top, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildUserPanel(), buildLogPanel());
        split.setDividerLocation(360);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);
        root.add(buildBroadcastBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildStatCards() {
        JPanel p = new JPanel(new GridLayout(1, 3, 12, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
        lblOnline   = new JLabel("0", SwingConstants.CENTER);
        lblTotal    = new JLabel("0", SwingConstants.CENTER);
        lblMessages = new JLabel("0", SwingConstants.CENTER);
        p.add(statCard("Đang online",   lblOnline,   C_SUCCESS));
        p.add(statCard("Tổng lượt vào", lblTotal,    C_PRIMARY));
        p.add(statCard("Tin nhắn",      lblMessages, new Color(160, 100, 0)));
        return p;
    }

    private JPanel statCard(String label, JLabel val, Color color) {
        JPanel c = new JPanel(new BorderLayout(0, 4));
        c.setBackground(BG_CARD);
        c.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        val.setFont(F_STAT); val.setForeground(color);
        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setFont(F_SMALL); l.setForeground(C_MUTED);
        c.add(l, BorderLayout.NORTH);
        c.add(val, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildUserPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        JLabel title = new JLabel("Người tham gia");
        title.setFont(F_TITLE); title.setForeground(C_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        userModel = new DefaultTableModel(
            new String[]{"Tên người dùng", "Địa chỉ IP", "Vào lúc"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        userTable = new JTable(userModel);
        userTable.setFont(F_BODY); userTable.setRowHeight(28);
        userTable.setSelectionBackground(BG_ACCENT);
        userTable.setSelectionForeground(C_PRIMARY);
        userTable.setGridColor(C_BORDER);
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        userTable.getTableHeader().setBackground(new Color(235, 233, 250));
        userTable.getTableHeader().setForeground(C_PRIMARY);
        userTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        userTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        userTable.getColumnModel().getColumn(2).setPreferredWidth(70);

        JScrollPane scroll = new JScrollPane(userTable);
        scroll.setBorder(new LineBorder(C_BORDER, 1, true));

        btnKick = actionBtn("Kick người dùng", C_DANGER, Color.WHITE);
        btnKick.setEnabled(false);
        btnKick.addActionListener(e -> doKick());
        userTable.getSelectionModel().addListSelectionListener(e ->
            btnKick.setEnabled(userTable.getSelectedRow() >= 0));

        p.add(title, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(btnKick, BorderLayout.SOUTH);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        return p;
    }

    private JPanel buildLogPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        JLabel title = new JLabel("Nhật ký hệ thống");
        title.setFont(F_TITLE); title.setForeground(C_PRIMARY);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(C_LOGBG);
        logPane.setFont(F_MONO);
        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(new LineBorder(C_BORDER, 1, true));

        JButton btnClear = actionBtn("Xoá log", new Color(60, 60, 75), Color.WHITE);
        btnClear.setFont(F_SMALL);
        btnClear.addActionListener(e -> logPane.setText(""));

        p.add(title, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(btnClear, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildBroadcastBar() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setBackground(BG_CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel lbl = new JLabel("Thông báo toàn phòng:");
        lbl.setFont(F_BODY); lbl.setForeground(C_MUTED);
        tfBroadcast = new JTextField();
        tfBroadcast.setFont(F_BODY);
        tfBroadcast.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        tfBroadcast.addActionListener(e -> doBroadcast());
        btnBroadcast = actionBtn("Gửi thông báo", C_PRIMARY, Color.WHITE);
        btnBroadcast.addActionListener(e -> doBroadcast());
        p.add(lbl, BorderLayout.WEST);
        p.add(tfBroadcast, BorderLayout.CENTER);
        p.add(btnBroadcast, BorderLayout.EAST);
        return p;
    }

    // ══ ACTIONS ════════════════════════════════════════════
    private void doApply() {
        String name = tfRoomName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên phòng!",
                "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int max;
        try { max = Integer.parseInt(tfMaxConn.getText().trim()); }
        catch (Exception e) { max = 50; }
        String welcome = tfWelcome.getText().trim();
        if (welcome.isEmpty()) welcome = "Chào mừng đến " + name + "!";

        ChatRoom.get().configure(name, tfPassword.getText().trim(), welcome, max);
        setRunningUI(true);
        JOptionPane.showMessageDialog(this, "Đã áp dụng cấu hình!", "Thành công",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void doStop() {
        int ok = JOptionPane.showConfirmDialog(this,
            "Dừng server sẽ ngắt kết nối tất cả người dùng.\nBạn có chắc?",
            "Xác nhận dừng", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        ChatRoom.get().closeRoom();
        setRunningUI(false);
    }

    private void doKick() {
        int row = userTable.getSelectedRow();
        if (row < 0) return;
        String uname = (String) userModel.getValueAt(row, 0);
        int ok = JOptionPane.showConfirmDialog(this,
            "Kick \"" + uname + "\" khỏi phòng?",
            "Xác nhận Kick", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;
        ChatRoom.get().kickUser(uname);
    }

    private void doBroadcast() {
        String msg = tfBroadcast.getText().trim();
        if (!msg.isEmpty()) {
            ChatRoom.get().adminBroadcast(msg);
            tfBroadcast.setText("");
        }
    }

    // ══ UI REFRESH ═════════════════════════════════════════
    private void refreshAll() {
        ChatRoom room = ChatRoom.get();

        // Stats
        lblOnline.setText(String.valueOf(room.getOnlineCount()));
        lblTotal.setText(String.valueOf(room.getTotalJoined()));
        lblMessages.setText(String.valueOf(room.getTotalMessages()));

        // User table
        java.util.List<ChatRoom.UserInfo> users = room.getUserList();
        userModel.setRowCount(0);
        for (ChatRoom.UserInfo u : users)
            userModel.addRow(new Object[]{ u.username(), u.ip(), u.joinedAt() });
        btnKick.setEnabled(userTable.getSelectedRow() >= 0);
    }

    private void setRunningUI(boolean on) {
        ChatRoom room = ChatRoom.get();
        String statusText = on
            ? "\"" + room.getRoomName() + "\" đang chạy · Port " + serverPort
            : "Server đã dừng";
        Color col = on ? C_SUCCESS : C_MUTED;
        setDot(dotOpen,   on); lblOpenStatus.setText(statusText);   lblOpenStatus.setForeground(col);
        setDot(dotManage, on); lblManageStatus.setText(statusText); lblManageStatus.setForeground(col);
        serverRunning = on;
    }

    // ══ LOG ════════════════════════════════════════════════
    private void appendLog(String text, Color color) {
        StyledDocument doc = logPane.getStyledDocument();
        Style style = logPane.addStyle("s", null);
        StyleConstants.setForeground(style, color);
        try {
            doc.insertString(doc.getLength(), text + "\n", style);
            logPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private Color colorForType(String type) {
        return switch (type) {
            case "join" -> C_LOG_JOIN;
            case "kick" -> C_LOG_KICK;
            case "sys"  -> C_LOG_SYS;
            default     -> C_LOG_MSG;
        };
    }

    // ══ UI HELPERS ═════════════════════════════════════════
    private JPanel makeDot() {
        JPanel dot = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Boolean.TRUE.equals(getClientProperty("on")) ? C_SUCCESS : C_MUTED);
                g.fillOval(0, 0, 10, 10);
            }
        };
        dot.setPreferredSize(new Dimension(10, 10));
        dot.setOpaque(false);
        dot.putClientProperty("on", false);
        return dot;
    }

    private void setDot(JPanel dot, boolean on) {
        dot.putClientProperty("on", on); dot.repaint();
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(F_SMALL); l.setForeground(C_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        return l;
    }

    private JTextField field(String tip) {
        JTextField f = new JTextField();
        f.setFont(F_BODY); f.setToolTipText(tip);
        f.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(C_BORDER, 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return f;
    }

    private JButton actionBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setBackground(bg); b.setForeground(fg);
        b.setFocusPainted(false); b.setBorderPainted(false); b.setOpaque(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        Color hover = bg.brighter();
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(hover); }
            public void mouseExited(MouseEvent e)  { b.setBackground(bg); }
        });
        return b;
    }

    private GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(2, 8, 2, 8);
        g.weightx = 1.0;
        return g;
    }

    private String getLocalIPs() {
        StringBuilder sb = new StringBuilder();
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                java.net.NetworkInterface ni = ifaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address)
                        sb.append("http://").append(a.getHostAddress())
                          .append(":").append(serverPort)
                          .append("   (").append(ni.getDisplayName()).append(")\n");
                }
            }
        } catch (Exception e) { sb.append("Không lấy được IP"); }
        return sb.toString().trim();
    }
}
