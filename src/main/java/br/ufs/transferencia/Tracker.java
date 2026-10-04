package br.ufs.transferencia;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

final class Tracker {
    private record Peer(String id, String ip, int port, long seen) {}
    private final Map<String, Map<String, Peer>> swarms = new ConcurrentHashMap<>();
    void run() throws Exception {
        var web = Util.web(8080);
        Util.route(web, "/reset", b -> { swarms.clear(); return Map.of("ok", true); });
        Util.route(web, "/status", b -> Map.of("swarms", swarms.size(), "peers", swarms.values().stream().mapToInt(Map::size).sum()));
        web.createContext("/announce", x -> {
            try {
                var q = query(x.getRequestURI().getRawQuery());
                String hash = q.get("info_hash"), id = q.get("peer_id");
                if (hash == null || hash.length() != 20 || id == null || id.length() != 20) throw new IllegalArgumentException("Identificador inválido");
                int port = Integer.parseInt(q.get("port")); if (port < 1 || port > 65535) throw new IllegalArgumentException("Porta inválida");
                String ip = x.getRemoteAddress().getAddress().getHostAddress();
                boolean stopped = "stopped".equals(q.get("event"));
                var peers = stopped ? swarms.getOrDefault(hash, new ConcurrentHashMap<>())
                    : swarms.computeIfAbsent(hash, k -> new ConcurrentHashMap<>());
                long now = System.nanoTime(); peers.values().removeIf(p -> now - p.seen > 120_000_000_000L);
                if (stopped) {
                    peers.remove(id);
                    if (peers.isEmpty()) swarms.remove(hash, peers);
                }
                else peers.put(id, new Peer(id, ip, port, now));
                var others = peers.values().stream().filter(p -> !p.id.equals(id))
                    .map(p -> Map.<String, Object>of("peer id", p.id.getBytes(StandardCharsets.ISO_8859_1), "ip", p.ip, "port", p.port)).toList();
                byte[] result = Bencode.encode(Map.of("interval", 2, "min interval", 1, "peers", others));
                x.getResponseHeaders().set("Content-Type", "text/plain"); x.sendResponseHeaders(200, result.length);
                try (var out = x.getResponseBody()) { out.write(result); }
            } catch (Exception e) {
                byte[] result = Bencode.encode(Map.of("failure reason", e.toString()));
                x.sendResponseHeaders(200, result.length); try (var out = x.getResponseBody()) { out.write(result); }
            }
        }); web.start();
    }
    static Map<String, String> query(String raw) {
        var result = new HashMap<String, String>();
        if (raw != null) for (String field : raw.split("&")) {
            var parts = field.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.ISO_8859_1), parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.ISO_8859_1) : "");
        }
        return result;
    }
}
