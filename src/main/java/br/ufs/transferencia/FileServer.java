package br.ufs.transferencia;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

final class FileServer {
    private ExecutorService workers = Executors.newSingleThreadExecutor();
    private volatile RateLimiter limiter = new RateLimiter(Util.RATE);
    private final AtomicInteger pending = new AtomicInteger(), active = new AtomicInteger(), peak = new AtomicInteger();
    private volatile String mode = "sequencial";
    void run() throws Exception {
        Files.createDirectories(Util.DATA.resolve("fonte"));
        var web = Util.web(8080);
        Util.route(web, "/setup", body -> {
            if (pending.get() != 0) throw new IOException("Há downloads pendentes");
            String nextMode = body.path("mode").asText(); int n = body.path("pool").asInt(2);
            var nextWorkers = switch (nextMode) {
                case "sequencial" -> Executors.newSingleThreadExecutor();
                case "paralelo" -> Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory());
                case "pool" -> Executors.newFixedThreadPool(n);
                default -> throw new IllegalArgumentException("Modo inválido");
            };
            workers.shutdown(); workers = nextWorkers; mode = nextMode;
            peak.set(0); limiter = new RateLimiter(body.path("rate").asLong(Util.RATE));
            return stats();
        });
        Util.route(web, "/status", b -> stats()); web.start();
        try (var listener = new ServerSocket(9000)) {
            Util.log("Servidor TCP pronto: 9000; controle: 8080");
            while (true) {
                var socket = listener.accept(); socket.setSoTimeout(10000);
                pending.incrementAndGet(); workers.submit(() -> send(socket));
            }
        }
    }
    private Map<String, Object> stats() { return Map.of("mode", mode, "active", active.get(), "peak", peak.get(), "pending", pending.get()); }
    private void send(Socket socket) {
        int count = active.incrementAndGet(); peak.accumulateAndGet(count, Math::max);
        try (socket; var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            var file = Util.safeFile(Util.DATA.resolve("fonte"), in.readUTF());
            out.writeLong(Files.size(file));
            try (var source = Files.newInputStream(file)) {
                byte[] buffer = new byte[Util.BLOCK]; int read;
                while ((read = source.read(buffer)) != -1) { limiter.acquire(read); out.write(buffer, 0, read); out.flush(); }
            }
        } catch (Exception e) { Util.log("Falha de envio: " + e); }
        finally { active.decrementAndGet(); pending.decrementAndGet(); }
    }
}
