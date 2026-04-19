package com.chat;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.URL;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Server server = new Server(port);

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");

        // ── Serve static files from classpath:/static ──
        URL staticUrl = Main.class.getClassLoader().getResource("static");
        if (staticUrl == null) throw new RuntimeException("Cannot find static resources!");
        ctx.setResourceBase(staticUrl.toExternalForm());
        ctx.setWelcomeFiles(new String[]{"index.html"});

        ServletHolder defaultHolder = new ServletHolder("default", DefaultServlet.class);
        defaultHolder.setInitParameter("dirAllowed", "false");
        defaultHolder.setInitParameter("welcomeServlets", "false");
        defaultHolder.setInitParameter("redirectWelcome", "false");
        ctx.addServlet(defaultHolder, "/");

        // ── REST endpoint ──
        ctx.addServlet(AdminServlet.class, "/api/admin/*");

        // ── WebSocket endpoints ──
        JettyWebSocketServletContainerInitializer.configure(ctx, (context, container) -> {
            container.setMaxTextMessageSize(64 * 1024);
            container.addMapping("/ws/chat",  ChatWebSocketCreator.class);
            container.addMapping("/ws/admin", AdminWebSocketCreator.class);
        });

        server.setHandler(ctx);
        server.start();
        System.out.println("✅ MultiChat server started on port " + port);
        server.join();
    }
}