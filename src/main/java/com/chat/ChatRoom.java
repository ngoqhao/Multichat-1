package com.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatRoom {
    private static final ChatRoom INSTANCE = new ChatRoom();
    public static ChatRoom get() { return INSTANCE; }

    private final Gson gson = new Gson();
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Config (set by admin)
    private String roomName    = "Phòng Chat Chung";
    private String password    = "";
    private String welcomeMsg  = "Chào mừng bạn đến phòng chat!";
    private int    maxUsers    = 50;
    private boolean open       = true;

    // Connected users: username -> UserSession
    private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

    // Admin WebSocket sessions
    private final CopyOnWriteArrayList<Session> adminSessions = new CopyOnWriteArrayList<>();

    // Stats
    private int totalJoined   = 0;
    private int totalMessages = 0;
    private final List<String> logHistory = Collections.synchronizedList(new ArrayList<>());

    // ── Config ──────────────────────────────────────────────────
    public String getRoomName()   { return roomName; }
    public String getPassword()   { return password; }
    public String getWelcomeMsg() { return welcomeMsg; }
    public int    getMaxUsers()   { return maxUsers; }
    public boolean isOpen()       { return open; }

    public synchronized void configure(String name, String pass, String welcome, int max) {
        this.roomName   = name;
        this.password   = pass;
        this.welcomeMsg = welcome;
        this.maxUsers   = max;
        this.open       = true;
        this.totalJoined   = 0;
        this.totalMessages = 0;
        logHistory.clear();
        sysLog("Phòng \"" + roomName + "\" đã được cấu hình lại.");
        notifyAdmins();
    }

    public synchronized void closeRoom() {
        this.open = false;
        broadcastSystem("Server đang đóng...");
        for (UserSession s : users.values()) s.closeSession();
        users.clear();
        notifyAdmins();
        sysLog("Server đã đóng.");
    }

    // ── User join / leave ────────────────────────────────────────
    public synchronized JoinResult tryJoin(Session session, String username, String pass) {
        if (!open) return JoinResult.error("Phòng chat chưa được mở.");
        if (!password.isEmpty() && !password.equals(pass))
            return JoinResult.error("Sai mật khẩu phòng!");
        if (users.size() >= maxUsers)
            return JoinResult.error("Phòng đã đầy (" + maxUsers + " người).");

        String uname = username.trim().isEmpty() ? "Guest_" + rnd() : username.trim();
        if (users.containsKey(uname)) uname = uname + "_" + rnd();

        UserSession us = new UserSession(uname, session, now());
        users.put(uname, us);
        totalJoined++;

        // Send room info to the new user
        send(session, buildMsg("room_info", Map.of(
            "roomName",   roomName,
            "username",   uname,
            "welcome",    welcomeMsg
        )));

        broadcastSystem(uname + " đã tham gia! " + onlineList());
        sysLog("[VÀO] " + uname + " (" + ipOf(session) + ")");
        notifyAdmins();
        return JoinResult.ok(uname);
    }

    public synchronized void leave(String username) {
        if (username == null) return;
        users.remove(username);
        broadcastSystem(username + " đã rời phòng. " + onlineList());
        sysLog("[RA] " + username + " đã rời phòng.");
        notifyAdmins();
    }

    // ── Messaging ────────────────────────────────────────────────
    public void handleMessage(String username, String text) {
        text = text.trim();
        if (text.isEmpty()) return;
        UserSession us = users.get(username);
        if (us == null) return;

        if (text.startsWith("/")) { handleCommand(us, text); return; }

        String ts = now();
        totalMessages++;
        String payload = buildMsg("chat", Map.of(
            "ts", ts, "sender", username, "text", text
        ));
        for (UserSession s : users.values()) send(s.session, payload);
        sysLog("[MSG] " + username + ": " + text);
        notifyAdmins();
    }

    private void handleCommand(UserSession us, String cmd) {
        String[] parts = cmd.split("\\s+", 2);
        switch (parts[0].toLowerCase()) {
            case "/list" -> send(us.session, buildSys(onlineList()));
            case "/quit", "/exit" -> {
                send(us.session, buildSys("Tạm biệt " + us.username + "!"));
                us.closeSession();
            }
            case "/pm" -> {
                if (parts.length < 2) { send(us.session, buildSys("Dùng: /pm <tên> <tin>")); break; }
                String[] pm = parts[1].split("\\s+", 2);
                if (pm.length < 2) { send(us.session, buildSys("Dùng: /pm <tên> <tin>")); break; }
                UserSession target = users.get(pm[0]);
                if (target == null) { send(us.session, buildSys("Không tìm thấy: " + pm[0])); break; }
                String ts = now();
                send(target.session, buildMsg("pm", Map.of("ts",ts,"from",us.username,"text",pm[1],"dir","in")));
                send(us.session, buildMsg("pm", Map.of("ts",ts,"to",pm[0],"text",pm[1],"dir","out")));
            }
            case "/help" -> send(us.session, buildSys("/list  /pm <tên> <tin>  /quit  /help"));
            default -> send(us.session, buildSys("Lệnh không hợp lệ. Gõ /help."));
        }
    }

    // ── Admin actions ────────────────────────────────────────────
    public synchronized void kickUser(String username) {
        UserSession us = users.get(username);
        if (us == null) return;
        send(us.session, buildSys("Bạn đã bị kick bởi Admin."));
        send(us.session, buildMsg("kicked", Map.of()));
        us.closeSession();
        users.remove(username);
        broadcastSystem(username + " đã bị Admin kick khỏi phòng.");
        sysLog("[KICK] " + username + " đã bị kick.");
        notifyAdmins();
    }

    public void adminBroadcast(String text) {
        broadcastSystem("Admin: " + text);
        sysLog("[ADMIN] Broadcast: " + text);
    }

    public synchronized void addAdminSession(Session s)    { adminSessions.add(s); sendAdminState(s); }
    public synchronized void removeAdminSession(Session s) { adminSessions.remove(s); }

    // ── Admin state push ─────────────────────────────────────────
    public void notifyAdmins() {
        String payload = buildAdminState();
        for (Session s : adminSessions) send(s, payload);
    }

    private void sendAdminState(Session s) { send(s, buildAdminState()); }

    private String buildAdminState() {
        List<Map<String,String>> userList = new ArrayList<>();
        for (UserSession us : users.values())
            userList.add(Map.of("username", us.username, "ip", us.ip, "joinedAt", us.joinedAt));

        return gson.toJson(Map.of(
            "type",          "admin_state",
            "roomName",      roomName,
            "open",          open,
            "onlineCount",   users.size(),
            "totalJoined",   totalJoined,
            "totalMessages", totalMessages,
            "users",         userList,
            "logs",          new ArrayList<>(logHistory).subList(
                               Math.max(0, logHistory.size() - 200), logHistory.size())
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────
    private void broadcastSystem(String msg) {
        String payload = buildSys(msg);
        for (UserSession s : users.values()) send(s.session, payload);
    }

    private String onlineList() {
        if (users.isEmpty()) return "Online (0): (trống)";
        return "Online (" + users.size() + "): " + String.join(", ", users.keySet());
    }

    private void sysLog(String msg) {
        String line = "[" + now() + "] " + msg;
        logHistory.add(line);
        if (logHistory.size() > 1000) logHistory.remove(0);
    }

    private String buildMsg(String type, Map<String,?> extra) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.putAll(extra);
        return gson.toJson(m);
    }

    private String buildSys(String text) {
        return buildMsg("system", Map.of("text", text));
    }

    private void send(Session s, String msg) {
        try { if (s != null && s.isOpen()) s.getRemote().sendString(msg); }
        catch (IOException ignored) {}
    }

    private String now() { return LocalTime.now().format(dtf); }
    private int rnd()    { return (int)(Math.random() * 9000 + 1000); }
    private static String ipOf(Session s) {
    try {
        InetSocketAddress addr = (InetSocketAddress) s.getRemoteAddress();
        return addr.getAddress().getHostAddress();
    } catch (Exception e) {
        return "unknown";
    }
}

    // ── Inner classes ────────────────────────────────────────────
    public static class UserSession {
        public final String username;
        public final Session session;
        public final String ip;
        public final String joinedAt;

        UserSession(String username, Session session, String joinedAt) {
            this.username = username;
            this.session  = session;
            this.ip       = ipOf(session);
            this.joinedAt = joinedAt;
        }

        private static String ipOf(Session s) {
            try {
                InetSocketAddress addr = (InetSocketAddress) s.getRemoteAddress();
                return addr.getAddress().getHostAddress();
            } catch (Exception e) {
                return "unknown";
            }
        }

        void closeSession() {
            try { if (session.isOpen()) session.close(); } catch (Exception ignored) {}
        }
    }

    public record JoinResult(boolean ok, String username, String error) {
        static JoinResult ok(String u)  { return new JoinResult(true, u, null); }
        static JoinResult error(String e) { return new JoinResult(false, null, e); }
    }
}
