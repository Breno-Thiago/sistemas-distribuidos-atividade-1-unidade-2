package br.ufs.transferencia;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

final class Util {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path DATA = Path.of(System.getenv().getOrDefault("DATA_DIR", "/dados"));
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final int BLOCK = 256 * 1024;
    static final long RATE = Long.parseLong(System.getenv().getOrDefault("UPLOAD_BPS", "25000000"));
    interface Handler { Object handle(JsonNode body) throws Exception; }
    static HttpServer web(int port) throws IOException {
        var s = HttpServer.create(new InetSocketAddress(port), 64);
        s.setExecutor(Executors.newCachedThreadPool());
        return s;
    }
    static void route(HttpServer s, String path, Handler h) {
        s.createContext(path, x -> {
            try {
                byte[] raw = x.getRequestBody().readNBytes(1_000_001);
                if (raw.length > 1_000_000) throw new IOException("Requisição muito grande");
                JsonNode input = raw.length == 0 ? JSON.createObjectNode() : JSON.readTree(raw);
                respond(x, 200, h.handle(input));
            } catch (Exception e) { respond(x, 400, Map.of("error", e.toString())); }
        });
    }
    static void respond(HttpExchange x, int code, Object body) throws IOException {
        byte[] b = JSON.writeValueAsBytes(body);
        x.getResponseHeaders().set("Content-Type", "application/json");
        x.sendResponseHeaders(code, b.length);
        try (var out = x.getResponseBody()) { out.write(b); }
    }
    static JsonNode call(String host, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://" + host + ":8080" + path))
            .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build();
        var r = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException(host + path + ": " + r.body());
        return JSON.readTree(r.body());
    }
    static Path safeFile(Path root, String name) {
        if (!name.matches("[A-Za-z0-9_-]+\\.bin")) throw new IllegalArgumentException("Nome inválido: " + name);
        return root.resolve(name);
    }
    static String hash(Path file) throws Exception {
        var d = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) { byte[] b = new byte[BLOCK]; int n; while ((n = in.read(b)) != -1) d.update(b, 0, n); }
        return HexFormat.of().formatHex(d.digest());
    }
    static void check(Path file, long size, String hash) throws Exception {
        if (Files.size(file) != size || !hash(file).equals(hash)) throw new IOException("Integridade inválida: " + file);
    }
    static void clean(Path dir) throws IOException {
        Files.createDirectories(dir);
        try (var files = Files.list(dir)) { for (var p : files.toList()) { if (Files.isRegularFile(p)) Files.delete(p); } }
    }
    static void log(String text) { System.out.println(text); }
}
