package br.ufs.transferencia;

import java.util.Arrays;

public final class Main {
    public static void main(String[] args) {
        try {
            if (args.length == 0) throw new IllegalArgumentException("Informe servidor, cliente, tracker, testar, benchmark ou relatorio");
            switch (args[0]) {
                case "servidor" -> new FileServer().run();
                case "cliente" -> new FileClient(args[1]).run();
                case "tracker" -> new Tracker().run();
                case "testar" -> new Benchmark().test();
                case "benchmark" -> new Benchmark().run(Arrays.copyOfRange(args, 1, args.length));
                case "relatorio" -> Report.generate();
                default -> throw new IllegalArgumentException("Comando desconhecido: " + args[0]);
            }
        } catch (Exception e) { System.err.println("FALHOU: " + e); e.printStackTrace(); System.exit(1); }
    }
}
