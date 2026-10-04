package br.ufs.transferencia;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

final class Benchmark {
    static final Path RESULTS = Path.of("/resultados");
    static final List<String> MODES = List.of("sequencial", "paralelo", "pool", "bittorrent");
    static final String SCHEMA = "v1-observado-100ms";
    static final int POOL = Integer.parseInt(System.getenv().getOrDefault("POOL_N", "2"));
    private final Transmission seed = new Transmission("seed");
    private final List<Transmission> peers = java.util.stream.IntStream.rangeClosed(1, 8).mapToObj(i -> new Transmission("peer" + i)).toList();
    record Sample(int client, double seconds, double verificationSeconds, long startOffsetNanos, String sha256) {}
    record Run(String schema, String mode, long bytes, int clients, int repetition, long uploadBps, int pool,
               String date, List<Sample> samples, double makespan, double startSkewMs, int serverPeak,
               long seedUploaded, long peersUploaded, List<String> peerLinks, String sourceSha256) {}
    record Condition(String mode, int mb, int clients, int repetition) {
        String id() { return mode + "-" + mb + "MB-" + clients + "c-r" + repetition; }
    }
    void ready() throws Exception {
        Files.createDirectories(RESULTS); Files.createDirectories(Util.DATA.resolve("fonte"));
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (true) {
            try {
                Util.call("servidor", "/status", Map.of()); Util.call("tracker", "/status", Map.of());
                for (int i = 1; i <= 8; i++) Util.call("cliente" + i, "/status", Map.of());
                seed.config(); for (var p : peers) p.config(); break;
            } catch (Exception e) { if (System.nanoTime() > limit) throw e; Thread.sleep(1000); }
        }
        // Uma interrupção do executor pode deixar os nós trabalhando na execução antiga.
        for (int i = 1; i <= 8; i++) Util.call("cliente" + i, "/cancel", Map.of());
        seed.remove(); for (var p : peers) p.remove();
        Util.call("tracker", "/reset", Map.of());
    }
    Path source(long bytes) throws Exception {
        Path file = Util.DATA.resolve("fonte/arquivo-" + bytes + ".bin");
        if (!Files.exists(file) || Files.size(file) != bytes) {
            var random = new Random(20261004L + bytes); byte[] b = new byte[Util.BLOCK];
            try (var out = Files.newOutputStream(file)) {
                for (long remaining = bytes; remaining > 0;) { random.nextBytes(b); int n = (int) Math.min(b.length, remaining); out.write(b, 0, n); remaining -= n; }
            }
        }
        return file;
    }
    void run(String[] args) throws Exception {
        String profile = "completo";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--perfil") && i + 1 < args.length) profile = args[++i];
            else throw new IllegalArgumentException("Uso: benchmark --perfil completo|rapido");
        }
        if (!List.of("completo", "rapido").contains(profile)) throw new IllegalArgumentException("Perfil inválido");
        Files.createDirectories(RESULTS);
        try (var channel = FileChannel.open(RESULTS.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Outro executor está usando os resultados");
            ready(); environment();
            for (int mb : profile.equals("completo") ? new int[]{5, 50, 500} : new int[]{5}) source(mb * 1_000_000L);
            Util.log("Aquecimento: quatro modalidades, fora das estatísticas");
            for (var mode : MODES) experiment(mode, 1_000_123, 2, 0);
            var conditions = new ArrayList<Condition>();
            for (var mode : MODES) for (int mb : profile.equals("completo") ? new int[]{5, 50, 500} : new int[]{5})
                for (int clients : profile.equals("completo") ? new int[]{1, 2, 4, 8} : new int[]{1, 2})
                    for (int r = 1; r <= (profile.equals("completo") ? 3 : 1); r++) conditions.add(new Condition(mode, mb, clients, r));
            Collections.shuffle(conditions, new Random(20261004));
            var sourceHashes = new HashMap<Integer, String>();
            int done = 0;
            for (var c : conditions) {
                Path target = RESULTS.resolve(c.id() + ".json");
                if (Files.exists(target)) {
                    var existing = Util.JSON.readValue(target.toFile(), Run.class);
                    validate(existing, c);
                    if (!sourceHashes.containsKey(c.mb)) sourceHashes.put(c.mb, Util.hash(source(c.mb * 1_000_000L)));
                    if (!existing.sourceSha256.equals(sourceHashes.get(c.mb))) throw new IOException("Original diferente do resultado salvo: " + c.id());
                    done++; Util.log("RETOMADA " + c.id()); continue;
                }
                Util.log("[" + (done + 1) + "/" + conditions.size() + "] " + c.id());
                try {
                    var result = experiment(c.mode, c.mb * 1_000_000L, c.clients, c.repetition);
                    Path temp = target.resolveSibling(target.getFileName() + ".tmp");
                    Util.JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), result);
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                    done++;
                    var stats = Stats.of(result.samples.stream().mapToDouble(Sample::seconds).toArray());
                    Util.log(String.format(Locale.ROOT, "OK min=%.3fs média=%.3fs max=%.3fs; upload peers=%d bytes", stats.min(), stats.mean(), stats.max(), result.peersUploaded));
                    export();
                } catch (Exception e) {
                    Files.writeString(RESULTS.resolve("falhas.jsonl"), Util.JSON.writeValueAsString(Map.of("condition", c.id(), "date", Instant.now().toString(), "error", e.toString())) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    throw e;
                }
            }
            export(); Util.log("BENCHMARK CONCLUÍDO: " + done + " execuções. Resultados preservados em resultados/.");
        }
    }
    static void validate(Run r, Condition c) throws Exception {
        if (!r.schema.equals(SCHEMA) || !r.mode.equals(c.mode) || r.bytes != c.mb * 1_000_000L || r.clients != c.clients || r.repetition != c.repetition || r.uploadBps != Util.RATE || r.pool != POOL || r.samples.size() != c.clients)
            throw new IOException("Resultado incompatível; use uma pasta de resultados separada");
        for (var s : r.samples) if (!Double.isFinite(s.seconds) || s.seconds <= 0 || !s.sha256.equals(r.sourceSha256)) throw new IOException("Amostra inválida");
    }
    Run experiment(String mode, long bytes, int clients, int repetition) throws Exception {
        Path file = source(bytes); String name = file.getFileName().toString(), hash = Util.hash(file);
        boolean torrent = mode.equals("bittorrent");
        var links = new TreeSet<String>(); long uploadedSeed = 0, uploadedPeers = 0;
        try {
            if (torrent) {
                seed.remove(); for (var p : peers) p.remove();
                Util.call("tracker", "/reset", Map.of());
                Util.clean(Util.DATA.resolve("seed")); Files.copy(file, Util.DATA.resolve("seed").resolve(name));
                byte[] metainfo = Torrent.create(file);
                seed.add(metainfo, "/dados/seed"); seed.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
                while (seed.status().path("haveValid").asLong() != bytes) {
                    if (System.nanoTime() > deadline) throw new IOException("Seed não ficou pronto"); Thread.sleep(100);
                }
                for (int i = 0; i < clients; i++) {
                    Util.clean(Util.DATA.resolve("peer" + (i + 1))); peers.get(i).add(metainfo, "/dados/peer" + (i + 1));
                }
            } else {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
                while (Util.call("servidor", "/status", Map.of()).path("pending").asInt() != 0) {
                    if (System.nanoTime() > deadline) throw new IOException("Servidor ainda ocupado"); Thread.sleep(100);
                }
                Util.call("servidor", "/setup", Map.of("mode", mode, "pool", POOL, "rate", Util.RATE));
                for (int i = 1; i <= clients; i++) Util.call("cliente" + i, "/prepare", Map.of("file", name));
            }
            long[] starts = new long[clients], ends = new long[clients];
            var barrier = new CountDownLatch(1);
            long epoch;
            try (var tasks = Executors.newFixedThreadPool(clients)) {
                var futures = new ArrayList<Future<?>>();
                for (int i = 0; i < clients; i++) {
                    final int index = i;
                    futures.add(tasks.submit(() -> {
                        try { barrier.await(); starts[index] = System.nanoTime();
                            if (torrent) peers.get(index).start(); else Util.call("cliente" + (index + 1), "/start", Map.of());
                        } catch (Exception e) { throw new CompletionException(e); }
                    }));
                }
                epoch = System.nanoTime(); barrier.countDown(); for (var f : futures) f.get();
            }
            long deadline = epoch + TimeUnit.MINUTES.toNanos(15);
            String seedIp = InetAddress.getByName("seed").getHostAddress();
            try (var polling = Executors.newFixedThreadPool(clients)) {
                while (Arrays.stream(ends).anyMatch(v -> v == 0)) {
                    if (System.nanoTime() > deadline) throw new IOException("Prazo de 15 minutos excedido");
                    var queries = new ArrayList<Future<JsonNode>>();
                    for (int i = 0; i < clients; i++) {
                        final int index = i;
                        queries.add(polling.submit(() -> torrent ? peers.get(index).status() : Util.call("cliente" + (index + 1), "/status", Map.of())));
                    }
                    for (int i = 0; i < clients; i++) {
                        var status = queries.get(i).get();
                        if (torrent) {
                            if (status.path("leftUntilDone").asLong() == 0 && status.path("haveValid").asLong() == bytes && ends[i] == 0) ends[i] = System.nanoTime();
                            for (var p : status.path("peers")) if (!p.path("address").asText().equals(seedIp) && p.path("rateToClient").asLong() > 0)
                                links.add("peer" + (i + 1) + " <- " + p.path("address").asText());
                        } else {
                            if (status.path("state").asText().equals("failed")) throw new IOException(status.path("error").asText());
                            if (status.path("state").asText().equals("done") && ends[i] == 0) ends[i] = System.nanoTime();
                        }
                    }
                    if (Arrays.stream(ends).anyMatch(v -> v == 0)) Thread.sleep(100);
                }
            }
            int peak = torrent ? 0 : Util.call("servidor", "/status", Map.of()).path("peak").asInt();
            if (torrent) {
                uploadedSeed = seed.status().path("uploadedEver").asLong();
                for (int i = 0; i < clients; i++) uploadedPeers += peers.get(i).status().path("uploadedEver").asLong();
                seed.rpc("torrent-stop", Map.of()); for (int i = 0; i < clients; i++) peers.get(i).rpc("torrent-stop", Map.of());
            }
            var samples = new ArrayList<Sample>();
            for (int i = 0; i < clients; i++) {
                long verifyStart = System.nanoTime();
                Path output = Util.DATA.resolve((torrent ? "peer" : "cs-") + (i + 1)).resolve(name);
                Util.check(output, bytes, hash);
                samples.add(new Sample(i + 1, (ends[i] - starts[i]) / 1e9, (System.nanoTime() - verifyStart) / 1e9, starts[i] - epoch, hash));
            }
            return new Run(SCHEMA, mode, bytes, clients, repetition, Util.RATE, POOL, Instant.now().toString(), samples,
                (Arrays.stream(ends).max().orElseThrow() - epoch) / 1e9,
                (Arrays.stream(starts).max().orElseThrow() - Arrays.stream(starts).min().orElseThrow()) / 1e6,
                peak, uploadedSeed, uploadedPeers, List.copyOf(links), hash);
        } finally {
            if (torrent) { seed.remove(); for (var p : peers) p.remove(); Util.call("tracker", "/reset", Map.of()); }
            else { for (int i = 1; i <= clients; i++) Util.call("cliente" + i, "/cancel", Map.of()); }
            for (int i = 1; i <= clients; i++) Util.clean(Util.DATA.resolve((torrent ? "peer" : "cs-") + i));
            if (torrent) Util.clean(Util.DATA.resolve("seed"));
        }
    }
    void test() throws Exception {
        Files.createDirectories(RESULTS);
        try (var channel = FileChannel.open(RESULTS.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Executor ocupado");
            ready(); var checks = new ArrayList<String>();
            var stoppedRequest = java.net.http.HttpRequest.newBuilder(URI.create("http://tracker:8080/announce?info_hash="
                + "h".repeat(20) + "&peer_id=" + "i".repeat(20) + "&port=51413&event=stopped")).GET().build();
            Util.HTTP.send(stoppedRequest, java.net.http.HttpResponse.BodyHandlers.discarding());
            if (Util.call("tracker", "/status", Map.of()).path("swarms").asInt() != 0)
                throw new IOException("Parada tardia recriou um enxame vazio");
            checks.add("Tracker: notificação tardia de parada não recria enxame removido");
            var interruptedFile = source(5_000_123);
            Util.call("servidor", "/setup", Map.of("mode", "paralelo", "pool", POOL, "rate", 1_000_000));
            for (int i = 1; i <= 4; i++) {
                Util.call("cliente" + i, "/prepare", Map.of("file", interruptedFile.getFileName().toString()));
                Util.call("cliente" + i, "/start", Map.of());
            }
            Thread.sleep(600);
            ready();
            for (int i = 1; i <= 4; i++) {
                if (Util.call("cliente" + i, "/status", Map.of()).path("state").asText().equals("running"))
                    throw new IOException("Transferência antiga continua ativa");
                if (Files.exists(Util.DATA.resolve("cs-" + i).resolve(interruptedFile.getFileName() + ".part")))
                    throw new IOException("Arquivo parcial não foi removido");
            }
            checks.add("Quatro transferências interrompidas: cancelamento, limpeza dos parciais e nova execução conferidos");
            for (var mode : List.of("sequencial", "paralelo", "pool")) {
                var r = experiment(mode, 5_000_123, 4, 0);
                int expected = mode.equals("sequencial") ? 1 : mode.equals("pool") ? Math.min(POOL, 4) : 4;
                if (r.serverPeak != expected) throw new IOException("Concorrência " + mode + ": " + r.serverPeak + " esperado " + expected);
                checks.add(mode + ": 4 downloads íntegros; pico=" + r.serverPeak); Util.log("OK " + checks.getLast());
            }
            Run p2p;
            // Seed mais lento neste teste dá tempo para observar a colaboração.
            seed.rpc("session-set", Map.of("speed-limit-up", 5000));
            try { p2p = experiment("bittorrent", 50_000_123, 4, 0); } finally { seed.config(); }
            Util.log("Evidência BitTorrent: upload peers=" + p2p.peersUploaded + "; conexões=" + p2p.peerLinks);
            if (p2p.peersUploaded <= 0 || p2p.peerLinks.isEmpty()) throw new IOException("Não houve evidência de compartilhamento entre peers");
            checks.add("BitTorrent: 4 downloads íntegros; upload dos peers=" + p2p.peersUploaded + " bytes; " + p2p.peerLinks.size() + " conexões com recebimento observado");
            if (Util.call("tracker", "/status", Map.of()).path("swarms").asInt() != 0) throw new IOException("Tracker não foi limpo");
            for (int i = 1; i <= 4; i++) {
                try (var paths = Files.list(Util.DATA.resolve("peer" + i))) { if (paths.findAny().isPresent()) throw new IOException("Resíduo de peer"); }
            }
            checks.add("Última peça parcial, limpeza dos downloads e reset do tracker conferidos");
            Util.JSON.writerWithDefaultPrettyPrinter().writeValue(RESULTS.resolve("testes.json").toFile(), Map.of("date", Instant.now().toString(), "checks", checks, "p2p", p2p, "seedUploadBpsDuringTest", 5000000));
            checks.forEach(Util::log); Util.log("TESTES DE INTEGRAÇÃO OK. Testes unitários de falha são executados na construção da imagem.");
        }
    }
    void environment() throws Exception {
        var env = Map.of("date", Instant.now().toString(), "java", System.getProperty("java.version"), "os", System.getProperty("os.name"),
            "arch", System.getProperty("os.arch"), "processors", Runtime.getRuntime().availableProcessors(), "transmission", seed.rpc("session-get", Map.of()).path("version").asText(),
            "uploadBps", Util.RATE, "pollMs", 100, "pool", POOL, "cpu", Files.readString(Path.of("/proc/cpuinfo")).lines().filter(s -> s.startsWith("model name")).findFirst().orElse("não disponível"));
        if (!Files.exists(RESULTS.resolve("ambiente.json"))) Util.JSON.writerWithDefaultPrettyPrinter().writeValue(RESULTS.resolve("ambiente.json").toFile(), env);
    }
    static List<Run> results() throws Exception {
        try (var files = Files.list(RESULTS)) {
            var runs = new ArrayList<Run>();
            for (var f : files.filter(p -> p.getFileName().toString().matches("(sequencial|paralelo|pool|bittorrent)-.*\\.json")).sorted().toList()) runs.add(Util.JSON.readValue(f.toFile(), Run.class));
            return runs;
        }
    }
    static void export() throws Exception {
        var individual = new StringBuilder("arquitetura,bytes,clientes,repeticao,cliente,tempo_s,verificacao_s,inicio_offset_ns,sha256\n");
        var perRun = new StringBuilder("arquitetura,bytes,clientes,repeticao,min_s,media_s,max_s,makespan_s,desvio_inicio_ms,pico_servidor,upload_seed_bytes,upload_peers_bytes\n");
        var grouped = new TreeMap<String, List<Double>>();
        for (var r : results()) {
            var times = r.samples.stream().mapToDouble(Sample::seconds).toArray(); var s = Stats.of(times);
            String key = r.mode + "," + r.bytes + "," + r.clients;
            for (var sample : r.samples) {
                individual.append(String.format(Locale.ROOT, "%s,%d,%d,%.6f,%.6f,%d,%s%n", key, r.repetition, sample.client, sample.seconds, sample.verificationSeconds, sample.startOffsetNanos, sample.sha256));
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(sample.seconds);
            }
            perRun.append(String.format(Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f,%.6f,%.3f,%d,%d,%d%n", key, r.repetition, s.min(), s.mean(), s.max(), r.makespan, r.startSkewMs, r.serverPeak, r.seedUploaded, r.peersUploaded));
        }
        var summary = new StringBuilder("arquitetura,bytes,clientes,amostras,min_s,media_s,max_s\n");
        for (var entry : grouped.entrySet()) {
            var s = Stats.of(entry.getValue().stream().mapToDouble(Double::doubleValue).toArray());
            summary.append(String.format(Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f%n", entry.getKey(), entry.getValue().size(), s.min(), s.mean(), s.max()));
        }
        Files.writeString(RESULTS.resolve("downloads.csv"), individual); Files.writeString(RESULTS.resolve("execucoes.csv"), perRun); Files.writeString(RESULTS.resolve("resumo.csv"), summary);
    }
}
