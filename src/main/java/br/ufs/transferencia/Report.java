package br.ufs.transferencia;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

final class Report implements AutoCloseable {
    private final PDDocument doc = new PDDocument();
    private final PDFont font = PDType0Font.load(doc, new File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
    private final PDFont bold = PDType0Font.load(doc, new File("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"));
    private PDPageContentStream canvas;
    private float y;
    private static final Color INK = new Color(25, 48, 67), ACCENT = new Color(0, 113, 133);
    void box(float x, float y, String label) throws IOException {
        canvas.setNonStrokingColor(new Color(232, 244, 246)); canvas.addRect(x, y, 120, 35); canvas.fill();
        textAt(x + 10, y + 13, label, 10, bold);
    }
    void arrow(float x1, float y1, float x2, float y2) throws IOException {
        canvas.setStrokingColor(ACCENT); canvas.moveTo(x1, y1); canvas.lineTo(x2, y2); canvas.stroke();
        double angle = Math.atan2(y2-y1, x2-x1);
        canvas.moveTo(x2, y2); canvas.lineTo(x2-(float)(8*Math.cos(angle-0.4)), y2-(float)(8*Math.sin(angle-0.4)));
        canvas.moveTo(x2, y2); canvas.lineTo(x2-(float)(8*Math.cos(angle+0.4)), y2-(float)(8*Math.sin(angle+0.4))); canvas.stroke();
    }
    void tableHeader(boolean appendix) throws IOException {
        String[] names = appendix ? new String[]{"Modo", "MB", "Clientes", "Rep.", "Mín.", "Média", "Máx.", "Total"} : new String[]{"Arquitetura", "Clientes", "Amostras", "Mínimo", "Média", "Máximo"};
        float[] positions = appendix ? new float[]{45,133,170,222,266,337,408,479} : new float[]{45,175,240,320,400,480};
        for (int i = 0; i < names.length; i++) textAt(positions[i], y, names[i], 9, bold);
        y -= 25;
    }
    void tableRow(String[] values, boolean appendix) throws IOException {
        float[] positions = appendix ? new float[]{45,133,170,222,266,337,408,479} : new float[]{45,175,240,320,400,480};
        for (int i = 0; i < values.length; i++) textAt(positions[i], y, values[i], 9, font);
        y -= 17;
    }
    static String number(double v) { return String.format(Locale.ROOT, "%.3f", v); }
    private static final Color[] COLORS = {new Color(0,113,133), new Color(47,94,174), new Color(218,143,44), new Color(125,71,153)};
    private Report() throws IOException {}
    void page(String title) throws IOException {
        if (canvas != null) canvas.close();
        var page = new PDPage(PDRectangle.A4); doc.addPage(page); canvas = new PDPageContentStream(doc, page);
        canvas.setNonStrokingColor(ACCENT); canvas.addRect(0, 817, 595, 25); canvas.fill();
        textAt(45, 790, "UFS | SISTEMAS DISTRIBUÍDOS | UNIDADE 2", 10, bold);
        textAt(45, 45, "Breno Thiago Argemiro Santos", 9, font);
        textAt(510, 45, String.valueOf(doc.getNumberOfPages()), 9, font);
        y = 750; line(title, 19, bold); y -= 10;
    }
    void textAt(float x, float y, String text, float size, PDFont f) throws IOException {
        canvas.setNonStrokingColor(INK); canvas.beginText(); canvas.setFont(f, size); canvas.newLineAtOffset(x, y); canvas.showText(text); canvas.endText();
    }
    void line(String text, float size, PDFont f) throws IOException { textAt(45, y, text, size, f); y -= size + 8; }
    void paragraph(String text) throws IOException {
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * 10.5 > 505) { line(line.toString(), 10.5f, font); line = new StringBuilder(word); }
            else line = new StringBuilder(candidate);
        }
        if (!line.isEmpty()) line(line.toString(), 10.5f, font); y -= 6;
    }
    static String label(String mode) { return switch (mode) { case "sequencial" -> "Sequencial"; case "paralelo" -> "Paralelo"; case "pool" -> "Pool"; default -> "BitTorrent"; }; }
    static double[] times(List<Benchmark.Run> runs, String mode, int mb, int clients) {
        return runs.stream().filter(r -> r.mode().equals(mode) && r.bytes() == mb * 1_000_000L && r.clients() == clients)
            .flatMap(r -> r.samples().stream()).mapToDouble(Benchmark.Sample::seconds).toArray();
    }
    static void generate() throws Exception {
        var runs = Benchmark.results();
        if (runs.size() != 144) throw new IOException("PDF final exige 144 execuções; encontradas " + runs.size());
        for (var mode : Benchmark.MODES) for (int mb : new int[]{5,50,500}) for (int n : new int[]{1,2,4,8}) for (int rep = 1; rep <= 3; rep++) {
            final int repetition = rep;
            var r = runs.stream().filter(v -> v.mode().equals(mode) && v.bytes() == mb * 1_000_000L && v.clients() == n && v.repetition() == repetition).findFirst().orElseThrow();
            Benchmark.validate(r, new Benchmark.Condition(mode, mb, n, rep));
        }
        Benchmark.export(); Files.createDirectories(Path.of("/relatorio"));
        var env = Util.JSON.readTree(Benchmark.RESULTS.resolve("ambiente.json").toFile());
        try (var pdf = new Report()) {
            pdf.page("Avaliação de transferência de arquivos");
            pdf.line("Atividade 01 - Unidade 2", 14, pdf.bold);
            pdf.paragraph("Aluno: Breno Thiago Argemiro Santos. Universidade Federal de Sergipe. Disciplina: Sistemas Distribuídos.");
            pdf.paragraph("Objetivo: comparar três políticas de atendimento cliente-servidor e a distribuição P2P com BitTorrent. Os resultados abaixo foram coletados pela aplicação; os arquivos CSV e registros JSON permitem conferir cada observação.");
            pdf.line("Arquiteturas", 14, pdf.bold);
            pdf.paragraph("Cliente-servidor: clientes -> servidor Java -> arquivo. No modo sequencial há um download ativo; no paralelo há uma thread por conexão; no pool o limite de atendimento é N=" + env.path("pool").asInt() + ". Os demais clientes aguardam e essa espera integra o tempo medido.");
            pdf.paragraph("BitTorrent: tracker -> lista de participantes; seed <-> peers <-> peers. Transmission " + env.path("transmission").asText() + " transfere peças de 256 KiB e valida seus hashes. O tracker Java não transporta o arquivo. Quem termina continua compartilhando até todos concluírem.");
            pdf.line("Ambiente real", 14, pdf.bold);
            pdf.paragraph("Uma única máquina física, containers Docker em rede bridge interna. Os nós compartilham CPU, RAM, armazenamento e kernel. Portanto, os números não representam uma rede entre computadores físicos independentes.");
            pdf.paragraph("Java " + env.path("java").asText() + "; " + env.path("os").asText() + " " + env.path("arch").asText() + "; CPUs visíveis: " + env.path("processors").asInt() + ". " + env.path("cpu").asText());
            pdf.paragraph("Registro do ambiente: " + env.path("date").asText() + ". Detalhes adicionais do host e Docker constam em resultados/host.txt, quando coletados pelo roteiro.");
            pdf.page("Fluxos de dados");
            pdf.paragraph("Cliente-servidor: o servidor concentra o envio. Sequencial, paralelo e pool definem quantos downloads ele atende ao mesmo tempo.");
            pdf.box(70,640,"Cliente 1"); pdf.box(70,570,"Cliente 2"); pdf.box(70,500,"Cliente N"); pdf.box(395,570,"Servidor Java");
            pdf.arrow(395,587,190,657); pdf.arrow(395,587,190,587); pdf.arrow(395,587,190,517);
            pdf.y = 460; pdf.paragraph("BitTorrent: linhas sólidas representam dados; o tracker apenas informa os participantes. Todos os peers pertencem a containers distintos no mesmo computador.");
            pdf.box(235,345,"Tracker"); pdf.box(70,240,"Seed inicial"); pdf.box(235,240,"Peer 1"); pdf.box(400,240,"Peer 2");
            pdf.canvas.setLineDashPattern(new float[]{4,4}, 0);
            pdf.arrow(295,345,130,275); pdf.arrow(295,345,295,275); pdf.arrow(295,345,460,275); pdf.canvas.setLineDashPattern(new float[]{}, 0);
            pdf.arrow(190,262,235,262); pdf.arrow(235,250,190,250); pdf.arrow(355,262,400,262); pdf.arrow(400,250,355,250);
            pdf.arrow(130,240,130,200); pdf.arrow(130,200,460,200); pdf.arrow(460,200,460,240);
            pdf.y = 155; pdf.paragraph("Peças validadas podem ser compartilhadas antes de o arquivo inteiro chegar. O seed original não precisa ser a única fonte de cada download.");
            pdf.page("Metodologia e interpretação");
            pdf.paragraph("Matriz: 5, 50 e 500 MB decimais; 1, 2, 4 e 8 clientes; quatro arquiteturas; três repetições. Total: 48 condições, 144 execuções, 540 downloads. O seed não é contado como cliente.");
            pdf.paragraph("Arquivos determinísticos não comprimidos são gerados antes dos testes. A preparação dos torrents, a validação inicial do seed e o início dos containers ficam fora da medição. Quatro execuções curtas aquecem o ambiente, sem entrar nas estatísticas. A ordem é embaralhada com semente 20261004.");
            pdf.paragraph("O executor usa System.nanoTime(): para cada cliente, mede do envio do comando de início à observação da conclusão. Os comandos são enviados em paralelo. A consulta de estado ocorre a cada 100 ms, acrescida do tempo de RPC e processamento. Isso introduz atraso de observação, sobretudo relevante para arquivos pequenos. Não é tempo puro de transmissão nem início perfeitamente simultâneo.");
            pdf.paragraph("Após a conclusão, confere tamanho e SHA-256. O tempo dessa verificação adicional fica em coluna separada. Downloads inválidos ou incompletos não entram nos resultados válidos. No BitTorrent, a conclusão exige que todas as peças estejam validadas pelo cliente.");
            pdf.paragraph("O upload agregado é limitado nominalmente a " + env.path("uploadBps").asLong() / 1_000_000.0 + " MB/s por nó. Java reserva tempo de envio em um limitador compartilhado; Transmission utiliza seu limitador de sessão em kB/s decimais. São implementações distintas, com diferentes janelas e rajadas. O BitTorrent agrega a capacidade dos peers, enquanto cliente-servidor concentra o upload no servidor.");
            pdf.paragraph("Todos os downloads usam disco temporário; configurações e dados das transferências são limpos entre execuções. Não foi eliminado o cache do sistema operacional. Os resultados refletem também custos de disco, agendamento e compartilhamento de recursos.");
            double maxSkew = runs.stream().mapToDouble(Benchmark.Run::startSkewMs).max().orElseThrow();
            pdf.paragraph(String.format(Locale.ROOT, "Maior desvio registrado entre comandos de início: %.3f ms. Mínimo e máximo são extremos entre downloads; média é aritmética. As tabelas principais juntam todos os clientes das três repetições de cada condição. Makespan é o intervalo do disparo global até todos concluírem e aparece separadamente no CSV e apêndice.", maxSkew));
            for (int mb : new int[]{5,50,500}) {
                pdf.page("Resultados - arquivo de " + mb + " MB");
                pdf.paragraph("Tempos observados em segundos. Amostras = clientes x 3 repetições. Mínimo, média e máximo referem-se aos downloads individuais.");
                pdf.tableHeader(false);
                for (var mode : Benchmark.MODES) {
                    for (int n : new int[]{1,2,4,8}) {
                        var v = times(runs, mode, mb, n); var s = Stats.of(v);
                        pdf.tableRow(new String[]{label(mode), String.valueOf(n), String.valueOf(v.length), number(s.min()), number(s.mean()), number(s.max())}, false);
                    }
                    pdf.y -= 7;
                }
            }
            for (int mb : new int[]{5,50,500}) {
                pdf.page("Comparação das médias - " + mb + " MB");
                pdf.paragraph("Altura das barras = média dos tempos individuais nas três repetições. Eixo vertical em segundos, escala linear independente por tamanho de arquivo.");
                double max = 0;
                for (var mode : Benchmark.MODES) for (int n : new int[]{1,2,4,8}) max = Math.max(max, Stats.of(times(runs,mode,mb,n)).mean());
                float base = 200, height = 350;
                for (int tick = 0; tick <= 5; tick++) {
                    float yy = base + height * tick / 5;
                    pdf.textAt(45, yy, String.format(Locale.ROOT,"%.1f", max * tick / 5), 9, pdf.font);
                    pdf.canvas.setStrokingColor(Color.LIGHT_GRAY); pdf.canvas.moveTo(82, yy); pdf.canvas.lineTo(550, yy); pdf.canvas.stroke();
                }
                int group = 0;
                for (int n : new int[]{1,2,4,8}) {
                    for (int m = 0; m < 4; m++) {
                        double value = Stats.of(times(runs, Benchmark.MODES.get(m),mb,n)).mean();
                        float x = 90 + group * 115 + m * 23;
                        pdf.canvas.setNonStrokingColor(COLORS[m]); pdf.canvas.addRect(x, base, 18, (float)(value/max*height)); pdf.canvas.fill();
                    }
                    pdf.textAt(113+group*115, 180, n + " clientes", 9, pdf.font); group++;
                }
                for (int m = 0; m < 4; m++) {
                    pdf.canvas.setNonStrokingColor(COLORS[m]); pdf.canvas.addRect(45+m*130, 135, 10, 10); pdf.canvas.fill();
                    pdf.textAt(60+m*130,135,label(Benchmark.MODES.get(m)),9,pdf.font);
                }
            }
            pdf.page("Discussão dos resultados");
            for (int mb : new int[]{5,50,500}) {
                String best = Benchmark.MODES.stream().min(Comparator.comparingDouble(m -> Stats.of(times(runs,m,mb,8)).mean())).orElseThrow();
                double seq = Stats.of(times(runs,"sequencial",mb,8)).mean(); double bt = Stats.of(times(runs,"bittorrent",mb,8)).mean();
                pdf.paragraph(String.format(Locale.ROOT,"Para %d MB e oito clientes, a menor média observada foi %s. Sequencial: %.3f s; BitTorrent: %.3f s. Esses valores descrevem este ambiente e não estabelecem superioridade geral da arquitetura.",mb,label(best),seq,bt));
            }
            long uploaded = runs.stream().filter(r -> r.mode().equals("bittorrent")).mapToLong(Benchmark.Run::peersUploaded).sum();
            long evidence = runs.stream().filter(r -> r.mode().equals("bittorrent") && !r.peerLinks().isEmpty()).count();
            pdf.paragraph("O upload acumulado dos clientes BitTorrent foi " + uploaded + " bytes. Em " + evidence + " das 36 execuções BitTorrent houve recebimento de peers diferentes do seed observado nas consultas RPC. Contadores de upload e conexões estão nos registros JSON; amostragem pode não capturar conexões breves.");
            pdf.paragraph("No sequencial, espera na fila aumenta o tempo dos últimos clientes. Paralelo e pool dividem o mesmo upload do servidor: aumentar concorrência não multiplica sua capacidade. BitTorrent pode distribuir o envio entre participantes, mas descoberta, estabelecimento de conexões e seleção de peças adicionam custos.");
            pdf.paragraph("Arquivos pequenos tornam mais relevantes os custos fixos e a resolução de consulta. Para arquivos maiores, a distribuição do upload tem mais tempo para atuar. Disco compartilhado, cache, processos externos e limitadores diferentes são fatores de confusão. Três repetições fornecem uma descrição inicial, sem demonstrar significância estatística.");
            pdf.paragraph("Conclusão: as quatro modalidades foram avaliadas com arquivos iguais, integridade conferida e tempos reproduzíveis. A escolha de arquitetura depende da carga e do ambiente. Uma avaliação futura entre máquinas físicas, com maior número de repetições e controle de rede, permitiria ampliar a validade dos resultados.");
            var sorted = runs.stream().sorted(Comparator.comparing(Benchmark.Run::mode).thenComparingLong(Benchmark.Run::bytes).thenComparingInt(Benchmark.Run::clients).thenComparingInt(Benchmark.Run::repetition)).toList();
            for (int start = 0; start < sorted.size(); start += 36) {
                pdf.page("Apêndice - tempos por execução");
                pdf.line("Tempos em segundos. Total = makespan (até todos concluírem).", 9, pdf.font);
                pdf.tableHeader(true);
                for (var r : sorted.subList(start, Math.min(start+36,sorted.size()))) {
                    var s = Stats.of(r.samples().stream().mapToDouble(Benchmark.Sample::seconds).toArray());
                    pdf.tableRow(new String[]{label(r.mode()), String.valueOf(r.bytes()/1_000_000), String.valueOf(r.clients()), String.valueOf(r.repetition()), number(s.min()), number(s.mean()), number(s.max()), number(r.makespan())}, true);
                }
            }
            pdf.page("Referências e reprodução");
            pdf.paragraph("BitTorrent BEP 3: https://www.bittorrent.org/beps/bep_0003.html - protocolo, metainfo, peças e tracker.");
            pdf.paragraph("Transmission 3.00 RPC: https://github.com/transmission/transmission/blob/3.00/extras/rpc-spec.txt - controle e estatísticas.");
            pdf.paragraph("Java 21: https://docs.oracle.com/en/java/javase/21/docs/api/ - sockets, executores e relógio monotônico.");
            pdf.paragraph("Apache PDFBox: https://pdfbox.apache.org/ - geração deste relatório em Java.");
            pdf.paragraph("Repositório: https://github.com/Breno-Thiago/sistemas-distribuidos-atividade-1-unidade-2");
            pdf.paragraph("No projeto: docker compose up -d --build; docker compose run --rm executor testar; docker compose run --rm executor benchmark --perfil completo; docker compose run --rm executor relatorio. O README detalha requisitos, retomada e preservação dos resultados.");
            pdf.canvas.close(); pdf.canvas = null;
            docInfo(pdf.doc); pdf.doc.save("/relatorio/relatorio.pdf");
        }
        Util.log("PDF GERADO: relatorio/relatorio.pdf - 144 execuções e 540 downloads.");
    }
    static void docInfo(PDDocument d) { var i = d.getDocumentInformation(); i.setTitle("Atividade 01 Unidade 2 - Transferência de arquivos"); i.setAuthor("Breno Thiago Argemiro Santos"); }
    public void close() throws IOException { if (canvas != null) canvas.close(); doc.close(); }
}
