// THAY TOÀN BỘ Main.java bằng code này:
package com.chat;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("0.0.0.0");
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");

        // Static pages
        ctx.addServlet(new ServletHolder(new StaticServlet("static/index.html")), "");
        ctx.addServlet(new ServletHolder(new StaticServlet("static/index.html")), "/index.html");
        ctx.addServlet(new ServletHolder(new StaticServlet("static/admin.html")), "/admin.html");

        // REST
        ctx.addServlet(AdminServlet.class, "/api/admin/*");

        // WebSocket - dùng LAMBDA, không dùng class reference
        JettyWebSocketServletContainerInitializer.configure(ctx, (context, container) -> {
            container.setMaxTextMessageSize(64 * 1024);
            container.addMapping("/ws/chat",  (req, resp) -> new ChatSocket());
            container.addMapping("/ws/admin", (req, resp) -> new AdminSocket());
        });

        server.setHandler(ctx);
        server.start();
        System.out.println("✅ MultiChat server started on port " + port);
        server.join();
    }

    static class StaticServlet extends HttpServlet {
        private final String path;
        StaticServlet(String path) { this.path = path; }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/html;charset=UTF-8");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
                if (in == null) { resp.sendError(500, "Missing: " + path); return; }
                resp.getOutputStream().write(in.readAllBytes());
            }
        }
    }
}