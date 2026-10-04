package br.ufs.transferencia;

import java.util.Arrays;

record Stats(double min, double mean, double max) {
    static Stats of(double[] values) {
        if (values.length == 0 || Arrays.stream(values).anyMatch(v -> !Double.isFinite(v) || v < 0)) throw new IllegalArgumentException("Amostras inválidas");
        return new Stats(Arrays.stream(values).min().orElseThrow(), Arrays.stream(values).average().orElseThrow(), Arrays.stream(values).max().orElseThrow());
    }
}
