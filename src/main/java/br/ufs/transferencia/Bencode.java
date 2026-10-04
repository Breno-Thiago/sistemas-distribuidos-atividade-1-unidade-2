package br.ufs.transferencia;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Bencode {
    static byte[] encode(Object value) throws IOException {
        var out = new ByteArrayOutputStream(); write(out, value); return out.toByteArray();
    }
    private static void write(OutputStream out, Object value) throws IOException {
        if (value instanceof Number n) out.write(("i" + n.longValue() + "e").getBytes(StandardCharsets.US_ASCII));
        else if (value instanceof Map<?, ?> map) {
            out.write('d');
            for (var key : map.keySet().stream().map(Object::toString).sorted().toList()) { write(out, key); write(out, map.get(key)); }
            out.write('e');
        } else if (value instanceof List<?> list) {
            out.write('l'); for (var v : list) write(out, v); out.write('e');
        } else {
            byte[] b = value instanceof byte[] bytes ? bytes : value.toString().getBytes(StandardCharsets.UTF_8);
            out.write((b.length + ":").getBytes(StandardCharsets.US_ASCII)); out.write(b);
        }
    }
}
