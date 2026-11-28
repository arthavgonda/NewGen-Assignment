package com.newgen.los;

import com.newgen.los.config.AppConfig;
import com.newgen.los.console.ConsoleApp;
import com.newgen.los.web.WebServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class App {

    public static void main(String[] args) {
        List<String> options = Arrays.asList(args);
        boolean webOnly = options.contains("--web-only");
        boolean noWeb = options.contains("--no-web");
        setupLogging(webOnly);

        AppConfig config = AppConfig.load();
        AppContext ctx = new AppContext(config);

        if (!ctx.database().isReachable()) {
            System.err.println("Cannot connect to MySQL at " + config.dbUrl());
            System.err.println("Check db.url / db.user / db.password in config/application.properties");
            System.exit(1);
        }
        ctx.database().initSchema();
        ctx.scheduler().start();

        WebServer web = null;
        if (config.webEnabled() && !noWeb) {
            try {
                web = new WebServer(ctx, config.webPort());
                web.start();
                System.out.println("Web interface running at http://localhost:" + config.webPort());
            } catch (IOException e) {
                System.err.println("Web interface could not start on port " + config.webPort() + ": " + e.getMessage());
            }
        }

        WebServer server = web;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.stop();
            }
            ctx.scheduler().stop();
        }));

        if (webOnly) {
            System.out.println("Running without console menu, press Ctrl+C to stop.");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        new ConsoleApp(ctx).run();
        System.exit(0);
    }

    private static void setupLogging(boolean keepConsole) {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$-7s %3$s - %5$s%6$s%n");
        Logger root = Logger.getLogger("");
        if (!keepConsole) {
            for (Handler h : root.getHandlers()) {
                root.removeHandler(h);
            }
        }
        try {
            Files.createDirectories(Path.of("logs"));
            FileHandler file = new FileHandler("logs/application.log", true);
            file.setFormatter(new SimpleFormatter());
            root.addHandler(file);
        } catch (IOException e) {
            System.err.println("Logging to file disabled: " + e.getMessage());
        }
        root.setLevel(Level.INFO);
        Logger.getLogger("com.mysql").setLevel(Level.WARNING);
    }
}
