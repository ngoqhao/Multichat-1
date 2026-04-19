package com.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;

public class AdminWebSocketCreator implements JettyWebSocketCreator {
    @Override
    public Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp) {
        return new AdminSocket();
    }
}

class AdminSocket extends WebSocketAdapter {
    private final Gson gson = new Gson();

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        ChatRoom.get().addAdminSession(session);
    }

    @Override
    public void onWebSocketText(String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String action = json.get("action").getAsString();
            ChatRoom room = ChatRoom.get();

            switch (action) {
                case "configure" -> {
                    String name    = json.get("roomName").getAsString();
                    String pass    = json.has("password")   ? json.get("password").getAsString()   : "";
                    String welcome = json.has("welcomeMsg") ? json.get("welcomeMsg").getAsString() : "Chào mừng!";
                    int max = json.has("maxUsers") ? json.get("maxUsers").getAsInt() : 50;
                    room.configure(name, pass, welcome, max);
                }
                case "close_room" -> room.closeRoom();
                case "kick" -> {
                    String uname = json.get("username").getAsString();
                    room.kickUser(uname);
                }
                case "broadcast" -> {
                    String text = json.get("text").getAsString();
                    room.adminBroadcast(text);
                }
                case "ping" -> room.notifyAdmins();
            }
        } catch (Exception e) {
            System.err.println("Admin WS error: " + e.getMessage());
        }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        ChatRoom.get().removeAdminSession(getSession());
        super.onWebSocketClose(statusCode, reason);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        ChatRoom.get().removeAdminSession(getSession());
    }
}
