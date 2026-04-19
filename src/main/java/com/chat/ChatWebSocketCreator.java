package com.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.websocket.server.JettyWebSocketCreator;

public class ChatWebSocketCreator implements JettyWebSocketCreator {
    @Override
    public Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp) {
        return new ChatSocket();
    }
}

class ChatSocket extends WebSocketAdapter {
    private final Gson gson = new Gson();
    private String username = null;

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        sendRaw("{\"type\":\"need_join\"}");
    }

    @Override
    public void onWebSocketText(String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            if ("join".equals(type)) {
                String name = json.has("username") ? json.get("username").getAsString() : "";
                String pass = json.has("password") ? json.get("password").getAsString() : "";
                ChatRoom.JoinResult result = ChatRoom.get().tryJoin(getSession(), name, pass);
                if (result.ok()) { this.username = result.username(); }
                else { sendRaw("{\"type\":\"error\",\"text\":\"" + esc(result.error()) + "\"}"); getSession().close(); }
                return;
            }
            if ("chat".equals(type) && username != null)
                ChatRoom.get().handleMessage(username, json.has("text") ? json.get("text").getAsString() : "");
        } catch (Exception e) { System.err.println("ChatSocket error: " + e.getMessage()); }
    }

    @Override public void onWebSocketClose(int s, String r) { ChatRoom.get().leave(username); username = null; super.onWebSocketClose(s, r); }
    @Override public void onWebSocketError(Throwable c) { ChatRoom.get().leave(username); username = null; }

    private void sendRaw(String msg) {
        try { Session s = getSession(); if (s != null && s.isOpen()) s.getRemote().sendString(msg); } catch (Exception ignored) {}
    }
    private String esc(String s) { return s == null ? "" : s.replace("\"", "\\\""); }
}