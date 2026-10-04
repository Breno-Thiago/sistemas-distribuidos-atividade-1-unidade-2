package br.ufs.transferencia;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

final class Torrent {
    static byte[] create(Path file) throws Exception {
        var pieces = new ByteArrayOutputStream(); var digest = MessageDigest.getInstance("SHA-1");
        try (var in = Files.newInputStream(file)) {
            byte[] block;
            while ((block = in.readNBytes(Util.BLOCK)).length != 0) pieces.write(digest.digest(block));
        }
        var info = Map.of("name", file.getFileName().toString(), "length", Files.size(file),
            "piece length", Util.BLOCK, "pieces", pieces.toByteArray(), "private", 1);
        return Bencode.encode(Map.of("announce", "http://tracker:8080/announce", "info", info,
            "comment", "UFS - Atividade 01 Unidade 2"));
    }
}
