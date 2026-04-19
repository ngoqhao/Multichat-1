package com.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

public class AdminSocket extends WebSocketAdapter {
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
                case "configure" -> room.configure(
                    json.get("roomName").getAsString(),
                    json.has("password")   ? json.get("password").getAsString()   : "",
                    json.has("welcomeMsg") ? json.get("welcomeMsg").getAsString() : "Chào mừng!",
                    json.has("maxUsers")   ? json.get("maxUsers").getAsInt()       : 50);
                case "close_room" -> room.closeRoom();
                case "kick"      -> room.kickUser(json.get("username").getAsString());
                case "broadcast" -> room.adminBroadcast(json.get("text").getAsString());
                case "ping"      -> room.notifyAdmins();
            }
        } catch (Exception e) {
            System.err.println("AdminSocket error: " + e.getMessage());
        }
    }

    @Override
    public void onWebSocketClose(int s, String r) {
        ChatRoom.get().removeAdminSession(getSession());
        super.onWebSocketClose(s, r);
    }

    @Override
    public void onWebSocketError(Throwable c) {
        ChatRoom.get().removeAdminSession(getSession());
    }
}