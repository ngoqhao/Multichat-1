package com.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

public class ChatSocket implements WebSocketListener {
    private final Gson gson = new Gson();
    private Session session;
    private String username = null;

    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session;
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
                ChatRoom.JoinResult result = ChatRoom.get().tryJoin(session, name, pass);
                if (result.ok()) {
                    this.username = result.username();
                } else {
                    sendRaw("{\"type\":\"error\",\"text\":\"" + esc(result.error()) + "\"}");
                    session.close();
                }
                return;
            }
            if ("chat".equals(type) && username != null)
                ChatRoom.get().handleMessage(username,
                    json.has("text") ? json.get("text").getAsString() : "");
        } catch (Exception e) {
            System.err.println("ChatSocket error: " + e.getMessage());
        }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        ChatRoom.get().leave(username);
        username = null;
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        ChatRoom.get().leave(username);
        username = null;
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {}

    private void sendRaw(String msg) {
        try {
            if (session != null && session.isOpen())
                session.getRemote().sendString(msg);
        } catch (Exception ignored) {}
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
