package br.ufs.transferencia;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

final class Transmission {
    final String host;
    private String token = "";
    Transmission(String host) { this.host = host; }
    synchronized JsonNode rpc(String method, Object args) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            var req = HttpRequest.newBuilder(URI.create("http://" + host + ":9091/transmission/rpc"))
                .timeout(Duration.ofSeconds(15)).header("X-Transmission-Session-Id", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(Util.JSON.writeValueAsBytes(Map.of("method", method, "arguments", args)))).build();
            var r = Util.HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 409) { token = r.headers().firstValue("X-Transmission-Session-Id").orElseThrow(); continue; }
            if (r.statusCode() != 200) throw new IOException(host + ": HTTP " + r.statusCode());
            var body = Util.JSON.readTree(r.body());
            if (!body.path("result").asText().equals("success")) throw new IOException(host + ": " + body);
            return body.path("arguments");
        }
        throw new IOException("Falha de sessão RPC: " + host);
    }
    void config() throws Exception {
        var session = rpc("session-get", Map.of());
        if (!session.path("version").asText().startsWith("3.00")) throw new IOException("Transmission inesperado: " + session);
        // A sessão usa kB decimais; 25.000 kB/s correspondem a 25 MB/s.
        if (session.path("units").path("speed-bytes").asInt() != 1000) throw new IOException("Unidade de velocidade inesperada");
        rpc("session-set", Map.of("speed-limit-up", Util.RATE / 1000, "speed-limit-up-enabled", true,
            "dht-enabled", false, "pex-enabled", false, "lpd-enabled", false, "seedRatioLimited", false));
    }
    void remove() throws Exception { rpc("torrent-remove", Map.of("delete-local-data", true)); }
    void add(byte[] torrent, String path) throws Exception {
        var r = rpc("torrent-add", Map.of("metainfo", Base64.getEncoder().encodeToString(torrent), "download-dir", path, "paused", true));
        if (!r.has("torrent-added")) throw new IOException("Torrent duplicado: " + host);
    }
    void start() throws Exception { rpc("torrent-start-now", Map.of()); }
    JsonNode status() throws Exception {
        var items = rpc("torrent-get", Map.of("fields", List.of("id", "percentDone", "leftUntilDone", "haveValid", "haveUnchecked", "status", "error", "errorString", "peers", "uploadedEver", "downloadedEver", "corruptEver", "totalSize", "isPrivate", "pieceSize"))).path("torrents");
        if (items.size() != 1) throw new IOException("Esperado um torrent em " + host + ": " + items.size());
        var t = items.get(0);
        if (t.path("error").asInt() != 0) throw new IOException(host + ": " + t.path("errorString"));
        return t;
    }
}
