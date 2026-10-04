package br.ufs.transferencia;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

final class FileClient {
    private volatile String state = "idle", error = "";
    private String name;
    private volatile Socket activeSocket;
    private Thread transfer;
    private final Path dir;
    private final String host = System.getenv().getOrDefault("SERVER_HOST", "servidor");
    FileClient(String id) { dir = Util.DATA.resolve("cs-" + id); }
    void run() throws Exception {
        Files.createDirectories(dir); var web = Util.web(8080);
        Util.route(web, "/prepare", b -> {
            if (state.equals("running")) throw new IOException("Cliente ocupado");
            Util.clean(dir); name = b.path("file").asText(); Util.safeFile(dir, name);
            state = "ready"; error = ""; return status();
        });
        Util.route(web, "/start", b -> {
            synchronized (this) {
                if (!state.equals("ready")) throw new IOException("Cliente não preparado");
                state = "running";
                transfer = Thread.ofPlatform().unstarted(this::download);
                transfer.start();
            }
            return status();
        });
        Util.route(web, "/cancel", b -> {
            Thread previous;
            synchronized (this) {
                state = "cancelled";
                if (activeSocket != null) activeSocket.close();
                previous = transfer;
            }
            if (previous != null) {
                previous.join(6000);
                if (previous.isAlive()) throw new IOException("Transferência não encerrou após cancelamento");
            }
            return status();
        });
        Util.route(web, "/status", b -> status()); web.start();
    }
    private Map<String, Object> status() { return Map.of("state", state, "error", error); }
    private void download() {
        try (var socket = new Socket()) {
            synchronized (this) { if (!state.equals("running")) return; activeSocket = socket; }
            socket.connect(new InetSocketAddress(host, 9000), 5000); socket.setSoTimeout(900000);
            var out = new DataOutputStream(socket.getOutputStream()); out.writeUTF(name); out.flush();
            receive(socket.getInputStream(), Util.safeFile(dir, name)); state = "done";
        } catch (Exception e) { error = e.toString(); state = "failed"; }
        finally { activeSocket = null; }
    }
    static void receive(InputStream input, Path destination) throws IOException {
        Path temp = destination.resolveSibling(destination.getFileName() + ".part");
        try {
            var in = new DataInputStream(input); long remaining = in.readLong();
            if (remaining < 0 || remaining > 1_000_000_000L) throw new IOException("Tamanho inválido");
            try (var file = Files.newOutputStream(temp)) {
                byte[] b = new byte[Util.BLOCK];
                while (remaining > 0) {
                    int n = in.read(b, 0, (int) Math.min(remaining, b.length));
                    if (n < 0) throw new EOFException("Transferência interrompida");
                    file.write(b, 0, n); remaining -= n;
                }
            }
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { Files.deleteIfExists(temp); throw e; }
    }
}
