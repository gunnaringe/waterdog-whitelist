package xyz.haxxor.waterdogaccess;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Minimal loopback-only HTTP API for the admin scripts. Bound to 127.0.0.1, so it's reachable
 * only from inside the pod (e.g. `kubectl exec ... -- curl localhost:PORT/...`), never through
 * the proxy's own Service/NodePort - no auth beyond "you already have pod-exec access", same
 * trust boundary the console command already relies on.
 */
public class ApiServer {

    private final HttpServer server;

    public ApiServer(int port, AccessOperations operations, Logger logger) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        this.server.setExecutor(Executors.newSingleThreadExecutor());

        this.server.createContext("/grant", exchange -> handle(exchange, logger, params -> {
            String xuid = require(params, "xuid");
            String world = params.getOrDefault("world", AccessDatabase.WILDCARD);
            return operations.grant(xuid, world, params.get("gamertag"));
        }));
        this.server.createContext("/revoke", exchange -> handle(exchange, logger, params -> {
            String xuid = require(params, "xuid");
            String world = params.getOrDefault("world", AccessDatabase.WILDCARD);
            return operations.revoke(xuid, world);
        }));
        this.server.createContext("/list", exchange -> handle(exchange, logger, params -> operations.list()));
        this.server.createContext("/attempts", exchange -> handle(exchange, logger, params -> {
            int limit = params.containsKey("count") ? Integer.parseInt(params.get("count")) : 10;
            return operations.attempts(limit);
        }));
    }

    public void start() {
        this.server.start();
    }

    public void stop() {
        this.server.stop(0);
    }

    private interface Handler {
        String handle(Map<String, String> params) throws SQLException;
    }

    private static void handle(HttpExchange exchange, Logger logger, Handler handler) throws IOException {
        int status = 200;
        String body;
        try {
            body = handler.handle(queryParams(exchange));
        } catch (IllegalArgumentException e) {
            status = 400;
            body = "ERROR: " + e.getMessage();
        } catch (SQLException e) {
            status = 500;
            logger.error("Admin API request to {} failed", exchange.getRequestURI(), e);
            body = "ERROR: database error, see proxy log";
        } catch (RuntimeException e) {
            status = 400;
            body = "ERROR: " + e.getMessage();
        }
        byte[] bytes = (body + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String require(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("missing required parameter '" + key + "'");
        }
        return value;
    }

    private static Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }
}
