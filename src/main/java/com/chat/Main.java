package com.chat;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Server server = new Server(port);

        // Static files handler
        ResourceHandler staticHandler = new ResourceHandler();
        staticHandler.setDirectoriesListed(false);
        staticHandler.setWelcomeFiles(new String[]{"index.html"});
        staticHandler.setResourceBase(
            Main.class.getClassLoader().getResource("static").toExternalForm()
        );

        // WebSocket + REST handler
        ServletContextHandler wsHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        wsHandler.setContextPath("/");
        wsHandler.addServlet(AdminServlet.class, "/api/admin/*");
        JettyWebSocketServletContainerInitializer.configure(wsHandler, (context, container) -> {
            container.setMaxTextMessageSize(64 * 1024);
            container.addMapping("/ws/chat", ChatWebSocketCreator.class);
            container.addMapping("/ws/admin", AdminWebSocketCreator.class);
        });

        HandlerList handlers = new HandlerList();
        handlers.addHandler(wsHandler);
        handlers.addHandler(staticHandler);

        server.setHandler(handlers);
        server.start();
        System.out.println("✅ MultiChat server started on port " + port);
        server.join();
    }
}
