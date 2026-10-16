package com.teamb.scheduler.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import com.teamb.scheduler.conflict.ConflictAnalytics;
import com.teamb.scheduler.db.SlotRepository;
import com.teamb.scheduler.index.IntervalIndex;
import com.teamb.scheduler.index.VersionRegistry;
import com.teamb.scheduler.result.ResultBuilder;
import com.teamb.scheduler.search.SearchRanking;
import com.teamb.scheduler.validator.RequestValidator;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class AvailabilityServer {
    private final int port;
    private final RequestValidator validator;
    private final IntervalIndex index;
    private final SearchRanking ranking;
    private final ConflictAnalytics conflicts;
    private final ResultBuilder builder;
    private final VersionRegistry versions;
    private final ObjectMapper mapper;
    private final SlotRepository repository;
    private final ApiKeyManager apiKeys;

    public AvailabilityServer(int port, RequestValidator validator, IntervalIndex index,
            SearchRanking ranking, ConflictAnalytics conflicts, ResultBuilder builder,
            VersionRegistry versions, ObjectMapper mapper, SlotRepository repository, ApiKeyManager apiKeys) {
        this.port = port; this.validator = validator; this.index = index;
        this.ranking = ranking; this.conflicts = conflicts; this.builder = builder;
        this.versions = versions; this.mapper = mapper; this.repository = repository; this.apiKeys = apiKeys;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleHome);
        server.createContext("/availability", this::handleAvailability);
        server.createContext("/health", exchange -> send(exchange, 200, Map.of("status","UP")));
        server.setExecutor(null);
        server.start();
    }

    private void handleHome(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "GET only"));
            return;
        }
        try (InputStream in = AvailabilityServer.class.getResourceAsStream("/index.html")) {
            if (in == null) { send(exchange, 500, Map.of("error", "Web UI resource not found")); return; }
            byte[] html = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(html); }
        }
    }

    private void handleAvailability(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error","GET only"));
            return;
        }

        String suppliedKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (!apiKeys.matches(suppliedKey)) {
            send(exchange, 401, Map.of("error", "Missing or invalid API key. Enter the key printed in the server terminal."));
            return;
        }

        long start = System.nanoTime();
        Map<String,String> q = parse(exchange.getRequestURI().getRawQuery());
        String provider = q.get("provider");
        String date = q.get("date");
        String duration = q.get("duration");

        try {
            validator.validate(provider, date, duration);
            var bookings = index.lookup(provider, LocalDate.parse(date));
            var slots = ranking.findFreeSlots(provider, LocalDate.parse(date),
                    Integer.parseInt(duration), bookings);
            var conflictData = conflicts.analyze(bookings);

            long runtimeMs = (System.nanoTime() - start) / 1_000_000;
            var result = builder.build(slots, conflictData, runtimeMs,
                    versions.currentVersion(), "SortedIntervalIndex");

            send(exchange, 200, result);
        } catch (RuntimeException e) {
            send(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            send(exchange, 500, Map.of("error", "Internal server error"));
        }
    }

    private Map<String,String> parse(String raw) {
        Map<String,String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                map.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private void send(HttpExchange exchange, int code, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
}
