package com.chat;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.URL;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Server server = new Server(port);

        // ── Handler 1: WebSocket + REST ──
        ServletContextHandler wsCtx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        wsCtx.setContextPath("/");

        wsCtx.addServlet(AdminServlet.class, "/api/admin/*");

        JettyWebSocketServletContainerInitializer.configure(wsCtx, (context, container) -> {
            container.setMaxTextMessageSize(64 * 1024);
            container.addMapping("/ws/chat",  ChatWebSocketCreator.class);
            container.addMapping("/ws/admin", AdminWebSocketCreator.class);
        });

        // ── Handler 2: Static files ──
        URL staticUrl = Main.class.getClassLoader().getResource("static");
        if (staticUrl == null) throw new RuntimeException("Cannot find static resources!");

        ResourceHandler staticHandler = new ResourceHandler();
        staticHandler.setDirectoriesListed(false);
        staticHandler.setWelcomeFiles(new String[]{"index.html"});
        staticHandler.setResourceBase(staticUrl.toExternalForm());

        HandlerList handlers = new HandlerList();
        handlers.addHandler(wsCtx);
        handlers.addHandler(staticHandler);

        server.setHandler(handlers);
        server.start();
        System.out.println("✅ MultiChat server started on port " + port);
        server.join();
    }
}