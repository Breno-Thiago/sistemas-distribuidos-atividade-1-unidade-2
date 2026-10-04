package br.ufs.transferencia;

import java.util.concurrent.locks.LockSupport;

final class RateLimiter {
    private final long bytesPerSecond;
    private long next;
    RateLimiter(long rate) { if (rate <= 0) throw new IllegalArgumentException("Taxa deve ser positiva"); bytesPerSecond = rate; }
    void acquire(int bytes) throws InterruptedException {
        long deadline;
        // Todas as conexões reservam tempo no mesmo limitador de upload.
        synchronized (this) {
            long now = System.nanoTime();
            next = Math.max(next, now) + (long) (bytes * 1_000_000_000.0 / bytesPerSecond);
            deadline = next;
        }
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0) {
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) throw new InterruptedException();
        }
    }
}
