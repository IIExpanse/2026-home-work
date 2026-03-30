package company.vk.edu.distrib.compute.ip;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import company.vk.edu.distrib.compute.KVService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PopovIgorKVService implements KVService {
    private static final Logger log = LoggerFactory.getLogger(PopovIgorKVService.class);

    private final HttpServer server;
    private final int port;
    private final PopovIgorKVDaoPersistent dao;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public PopovIgorKVService(int port) {
        this.port = port;
        try {
            this.dao = new PopovIgorKVDaoPersistent("data_" + port);
            // this.dao = new PopovIgorKVDao(); // in mem
            this.server = HttpServer.create(new InetSocketAddress("localhost", this.port), 512);
            initServerContexts();
        } catch (IOException e) {
            log.error("Failed to create HTTP server on port {}", port, e);
            throw new RuntimeException(e);
        }
    }

    private void initServerContexts() {
        server.createContext("/v0/status", this::handleStatus);
        server.createContext("/v0/entity", this::handleEntity);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        try (exchange) {
            int status = "GET".equals(exchange.getRequestMethod()) ? 200 : 503;
            exchange.sendResponseHeaders(status, -1);
        }
    }

    private void handleEntity(HttpExchange exchange) {
        try (exchange) {
            if (!"/v0/entity".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String id = extractId(exchange.getRequestURI().getQuery());
            if (id == null || id.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            switch (method) {
                case "GET" -> {
                    handleGet(exchange, id);
                }
                case "PUT" -> {
                    handlePut(exchange, id);
                }
                case "DELETE" -> {
                    handleDelete(exchange, id);
                }
                default -> {
                    exchange.sendResponseHeaders(405, -1);
                }
            }
        } catch (Exception e) {
            log.error("Internal error during request handling", e);
            sendSafeResponse(exchange, 400);
        }
    }

    private String extractId(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && "id".equals(pair[0])) {
                return pair[1]; // id
            }
        }
        return null;
    }

    private void handleGet(HttpExchange exchange, String id) throws IOException {
        byte[] response = dao.get(id);
        if (response == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void handlePut(HttpExchange exchange, String id) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        dao.upsert(id, body);
        exchange.sendResponseHeaders(201, -1);
    }

    private void handleDelete(HttpExchange exchange, String id) throws IOException {
        dao.delete(id);
        exchange.sendResponseHeaders(202, -1);
    }

    private void sendSafeResponse(HttpExchange exchange, int code) {
        try {
            exchange.sendResponseHeaders(code, -1);
        } catch (IOException e) {
            log.error("Failed to send error response", e);
        }
    }

    @Override
    public synchronized void start() {
        if (isRunning.get()) {
            return;
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        isRunning.set(true);
        log.info("Server started on port {}", port);
    }

    @Override
    public synchronized void stop() {
        if (!isRunning.get()) {
            return;
        }
        log.info("Stopping server on port {}...", port);
        try {
            server.stop(0);
        } catch (Exception e) {
            log.error("Error during HTTP server stop", e);
        }

        try {
            dao.close();
        } catch (IOException e) {
            log.error("Error during DAO closure", e);
        } finally {
            isRunning.set(false);
            log.info("Server stopped.");
        }
    }
}
