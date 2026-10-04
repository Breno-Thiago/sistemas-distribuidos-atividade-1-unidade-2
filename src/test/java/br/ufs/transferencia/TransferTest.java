package br.ufs.transferencia;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class TransferTest {
    @TempDir Path dir;
    @Test void statistics() {
        assertEquals(new Stats(1, 3, 5), Stats.of(new double[]{1, 3, 5}));
        assertThrows(IllegalArgumentException.class, () -> Stats.of(new double[]{}));
        assertThrows(IllegalArgumentException.class, () -> Stats.of(new double[]{Double.NaN}));
    }
    @Test void partialLastBlock() throws Exception {
        byte[] content = new byte[Util.BLOCK + 123]; new Random(42).nextBytes(content);
        var wire = new ByteArrayOutputStream(); var out = new DataOutputStream(wire); out.writeLong(content.length); out.write(content);
        var dest = dir.resolve("arquivo.bin"); FileClient.receive(new ByteArrayInputStream(wire.toByteArray()), dest);
        assertArrayEquals(content, Files.readAllBytes(dest));
    }
    @Test void disconnectRemovesPartialFile() throws Exception {
        var wire = new ByteArrayOutputStream(); var out = new DataOutputStream(wire); out.writeLong(100); out.write(new byte[10]);
        var dest = dir.resolve("arquivo.bin");
        assertThrows(EOFException.class, () -> FileClient.receive(new ByteArrayInputStream(wire.toByteArray()), dest));
        assertFalse(Files.exists(dest)); assertFalse(Files.exists(dir.resolve("arquivo.bin.part")));
    }
    @Test void stalledConnectionTimesOut() throws Exception {
        try (var listener = new ServerSocket(0); var client = new Socket("localhost", listener.getLocalPort()); var accepted = listener.accept()) {
            client.setSoTimeout(100);
            assertThrows(SocketTimeoutException.class, () -> FileClient.receive(client.getInputStream(), dir.resolve("arquivo.bin")));
            assertFalse(Files.exists(dir.resolve("arquivo.bin.part")));
        }
    }
    @Test void corruptedFileRejected() throws Exception {
        var file = dir.resolve("arquivo.bin"); Files.write(file, new byte[]{1, 2, 3}); String hash = Util.hash(file);
        Files.write(file, new byte[]{1, 2, 4}); assertThrows(IOException.class, () -> Util.check(file, 3, hash));
        assertThrows(IOException.class, () -> Util.check(file, 4, Util.hash(file)));
    }
    @Test void unsafeNamesRejected() { assertThrows(IllegalArgumentException.class, () -> Util.safeFile(dir, "../arquivo.bin")); }
    @Test void trackerPreservesBinaryIdentifiers() {
        assertEquals("\u0000\u00ff+", Tracker.query("info_hash=%00%FF%2B").get("info_hash"));
    }
    @Test void bencodeOrdersDictionaryAndKeepsBytes() throws Exception {
        assertEquals("d1:ai2e1:z1:xe", new String(Bencode.encode(Map.of("z", "x", "a", 2)), StandardCharsets.US_ASCII));
        assertArrayEquals(new byte[]{'2', ':', 0, (byte)255}, Bencode.encode(new byte[]{0, (byte)255}));
    }
    @Test void aggregateRateIsShared() throws Exception {
        var limiter = new RateLimiter(100_000); long start = System.nanoTime();
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { limiter.acquire(10_000); return true; });
            var b = workers.submit(() -> { limiter.acquire(10_000); return true; }); a.get(); b.get();
        }
        assertTrue(System.nanoTime() - start >= 190_000_000);
    }
}
