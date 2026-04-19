package com.chat;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Server server = new Server(port);

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");

        // Serve index.html
        ctx.addServlet(new ServletHolder(new StaticServlet("static/index.html", "text/html")), "/");
        // Serve admin.html
        ctx.addServlet(new ServletHolder(new StaticServlet("static/admin.html", "text/html")), "/admin.html");
        // REST
        ctx.addServlet(AdminServlet.class, "/api/admin/*");

        // WebSocket
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

    // Servlet đọc file từ classpath và trả về
    static class StaticServlet extends HttpServlet {
        private final String resourcePath;
        private final String contentType;

        StaticServlet(String resourcePath, String contentType) {
            this.resourcePath = resourcePath;
            this.contentType  = contentType;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType(contentType + ";charset=UTF-8");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    resp.sendError(500, "Resource not found: " + resourcePath);
                    return;
                }
                resp.getOutputStream().write(in.readAllBytes());
            }
        }
    }
}