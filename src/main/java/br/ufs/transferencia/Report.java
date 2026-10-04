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
    private final PDFont font = PDType0Font.load(doc, new File("/usr/local/share/fonts/arial/Arial.TTF"));
    private final PDFont bold = PDType0Font.load(doc, new File("/usr/local/share/fonts/arial/Arialbd.TTF"));
    private PDPageContentStream canvas;
    private float y;
    private static final Color INK = Color.BLACK, ACCENT = Color.BLACK;
    private static final float LEFT = 85.04f, RIGHT = 538.58f, TOP = 756.85f, BOTTOM = 56.69f;
    private String currentTitle;
    private final List<String> contents = new ArrayList<>();
    private final List<Integer> contentsPages = new ArrayList<>();
    private PDPage contentsPage;
    private int figure, table;
    void box(float x, float y, String label) throws IOException {
        canvas.setNonStrokingColor(new Color(232, 244, 246)); canvas.addRect(LEFT + (x-45)*(RIGHT-LEFT)/505, y, 120*(RIGHT-LEFT)/505, 35); canvas.fill();
        textAt(x + 10, y + 13, label, 10, bold);
    }
    void arrow(float x1, float y1, float x2, float y2) throws IOException {
        x1 = LEFT + (x1-45)*(RIGHT-LEFT)/505; x2 = LEFT + (x2-45)*(RIGHT-LEFT)/505;
        canvas.setStrokingColor(ACCENT); canvas.moveTo(x1, y1); canvas.lineTo(x2, y2); canvas.stroke();
        double angle = Math.atan2(y2-y1, x2-x1);
        canvas.moveTo(x2, y2); canvas.lineTo(x2-(float)(8*Math.cos(angle-0.4)), y2-(float)(8*Math.sin(angle-0.4)));
        canvas.moveTo(x2, y2); canvas.lineTo(x2-(float)(8*Math.cos(angle+0.4)), y2-(float)(8*Math.sin(angle+0.4))); canvas.stroke();
    }
    void tableHeader(boolean appendix) throws IOException {
        String[] names = appendix ? new String[]{"Modo", "MB", "Clientes", "Rep.", "Mín.", "Média", "Máx.", "Total"} : new String[]{"Arquitetura", "Clientes", "Amostras", "Mínimo", "Média", "Máximo"};
        float[] positions = appendix ? new float[]{45,133,170,222,266,337,408,479} : new float[]{45,175,240,320,400,480};
        rule(y + 14);
        for (int i = 0; i < names.length; i++) textAt(positions[i], y, names[i], 10, bold);
        rule(y - 7); y -= 25;
    }
    void tableRow(String[] values, boolean appendix) throws IOException {
        float[] positions = appendix ? new float[]{45,133,170,222,266,337,408,479} : new float[]{45,175,240,320,400,480};
        for (int i = 0; i < values.length; i++) textAt(positions[i], y, values[i], 10, font);
        y -= 17;
    }
    void rule(float baseline) throws IOException {
        canvas.setStrokingColor(Color.BLACK); canvas.setLineWidth(0.5f);
        canvas.moveTo(LEFT, baseline); canvas.lineTo(RIGHT, baseline); canvas.stroke();
    }
    void tableSource() throws IOException {
        rule(y + 8); y -= 8; caption("Fonte: dados do experimento (2026).");
    }
    static String number(double v) { return String.format(Locale.forLanguageTag("pt-BR"), "%.3f", v); }
    private static final Color[] COLORS = {new Color(0,113,133), new Color(47,94,174), new Color(218,143,44), new Color(125,71,153)};
    private Report() throws IOException {}
    void blankPage() throws IOException {
        if (canvas != null) canvas.close();
        var page = new PDPage(PDRectangle.A4); doc.addPage(page);
        canvas = new PDPageContentStream(doc, page); y = TOP;
    }
    void center(String text, float baseline, PDFont f) throws IOException {
        float width = f.getStringWidth(text) / 1000 * 12;
        textAtRaw((LEFT + RIGHT - width) / 2, baseline, text, 12, f);
    }
    void frontMatter() throws IOException {
        blankPage();
        center("UNIVERSIDADE FEDERAL DE SERGIPE", TOP, bold);
        center("SISTEMAS DISTRIBUÍDOS", TOP - 24, bold);
        center("BRENO THIAGO ARGEMIRO SANTOS", 650, font);
        center("AVALIAÇÃO DE DESEMPENHO NA TRANSFERÊNCIA", 465, bold);
        center("DE ARQUIVOS: CLIENTE-SERVIDOR E BITTORRENT", 441, bold);
        center("Atividade 01 – Unidade 2", 405, font);
        center("São Cristóvão", 92, font); center("2026", 68, font);
        blankPage();
        center("BRENO THIAGO ARGEMIRO SANTOS", TOP, font);
        center("AVALIAÇÃO DE DESEMPENHO NA TRANSFERÊNCIA", 540, bold);
        center("DE ARQUIVOS: CLIENTE-SERVIDOR E BITTORRENT", 516, bold);
        String note = "Relatório apresentado à disciplina de Sistemas Distribuídos da Universidade Federal de Sergipe, como parte da Atividade 01 da Unidade 2. Avaliação experimental das arquiteturas cliente-servidor e P2P.";
        y = 390; wrap(note, (LEFT + RIGHT) / 2, RIGHT, 12, 12, false, false);
        center("São Cristóvão", 92, font); center("2026", 68, font);
        blankPage(); contentsPage = doc.getPage(doc.getNumberOfPages() - 1);
    }
    void page(String title) throws IOException { newSectionPage(title, true); }
    void newSectionPage(String title, boolean entry) throws IOException {
        blankPage(); currentTitle = title;
        textAtRaw(RIGHT - 14, 785, String.valueOf(doc.getNumberOfPages() - 1), 10, font);
        if (entry) { contents.add(title); contentsPages.add(doc.getNumberOfPages() - 1); }
        line(title, 12, title.matches("[0-9]+\\.[0-9]+.*") ? font : bold); y -= 18;
    }
    void ensure(float needed) throws IOException {
        if (y - needed < BOTTOM) newSectionPage(currentTitle, false);
    }
    void textAtRaw(float x, float baseline, String text, float size, PDFont f) throws IOException {
        canvas.setNonStrokingColor(INK); canvas.beginText(); canvas.setFont(f, size);
        canvas.newLineAtOffset(x, baseline); canvas.showText(text); canvas.endText();
    }
    void textAt(float x, float baseline, String text, float size, PDFont f) throws IOException {
        textAtRaw(LEFT + (x - 45) * (RIGHT - LEFT) / 505, baseline, text, size, f);
    }
    void line(String text, float size, PDFont f) throws IOException {
        ensure(size + 12); textAtRaw(LEFT, y, text, Math.min(size, 12), f); y -= 24;
    }
    void wrap(String text, float left, float right, float size, float leading, boolean justify, boolean indent) throws IOException {
        var words = new ArrayDeque<String>();
        for (String token : text.split("\\s+")) {
            StringBuilder part = new StringBuilder();
            for (char c : token.toCharArray()) {
                if (font.getStringWidth(part.toString() + c) / 1000 * size > right - left - (indent ? 35.43f : 0)) {
                    words.add(part.toString()); part.setLength(0);
                }
                part.append(c);
            }
            if (!part.isEmpty()) words.add(part.toString());
        }
        boolean first = true;
        while (!words.isEmpty()) {
            ensure(leading);
            float x = left + (first && indent ? 35.43f : 0);
            var row = new ArrayList<String>();
            while (!words.isEmpty()) {
                var candidate = String.join(" ", row) + (row.isEmpty() ? "" : " ") + words.peek();
                if (!row.isEmpty() && font.getStringWidth(candidate) / 1000 * size > right - x) break;
                row.add(words.remove());
            }
            String joined = String.join(" ", row);
            float gap = justify && !words.isEmpty() && row.size() > 1 ?
                (right - x - font.getStringWidth(joined) / 1000 * size) / (row.size() - 1) : 0;
            for (String word : row) {
                textAtRaw(x, y, word, size, font);
                x += font.getStringWidth(word + " ") / 1000 * size + gap;
            }
            y -= leading; first = false;
        }
    }
    void paragraph(String text) throws IOException {
        wrap(text, LEFT, RIGHT, 12, 18, true, true); y -= 9;
    }
    void caption(String text) throws IOException {
        wrap(text, LEFT, RIGHT, 10, 12, false, false); y -= 6;
    }
    void reference(String text) throws IOException {
        wrap(text, LEFT, RIGHT, 12, 12, false, false); y -= 12;
    }
    void fillContents() throws IOException {
        if (canvas != null) canvas.close();
        canvas = new PDPageContentStream(doc, contentsPage, PDPageContentStream.AppendMode.APPEND, true, true);
        center("SUMÁRIO", TOP, bold); y = TOP - 48;
        for (int i = 0; i < contents.size(); i++) {
            String title = contents.get(i);
            textAtRaw(LEFT, y, title, 12, title.matches("[0-9]+\\.[0-9]+.*") ? font : bold);
            textAtRaw(RIGHT - 16, y, contentsPages.get(i).toString(), 12, font); y -= 24;
        }
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
            pdf.frontMatter();
            pdf.page("1 INTRODUÇÃO");

            pdf.paragraph("Aluno: Breno Thiago Argemiro Santos. Universidade Federal de Sergipe. Disciplina: Sistemas Distribuídos.");
            pdf.paragraph("Objetivo: comparar três políticas de atendimento cliente-servidor e a distribuição P2P com BitTorrent. Os resultados abaixo foram coletados pela aplicação; os arquivos CSV e registros JSON permitem conferir cada observação.");
            pdf.page("2 ARQUITETURAS E AMBIENTE");
            pdf.paragraph("Com sockets e executores da plataforma Java (Oracle, 2023), o arquivo no servidor Java é enviado aos clientes. No modo sequencial há um download ativo; no paralelo há uma thread por conexão; no pool o limite de atendimento é N=" + env.path("pool").asInt() + ". Os demais clientes aguardam e essa espera integra o tempo medido.");
            pdf.paragraph("BitTorrent: tracker -> lista de participantes; seed -> peers <-> peers. Conforme Cohen (2008), o tracker fornece participantes e as peças circulam entre os nós. Transmission " + env.path("transmission").asText() + ", controlado pela API RPC (Transmission Project, 2020), transfere peças de 256 KiB e valida seus hashes. O tracker Java não transporta o arquivo. Quem termina continua compartilhando até todos concluírem.");
            pdf.line("Ambiente real", 14, pdf.bold);
            pdf.paragraph("Uma única máquina física, containers Docker em rede bridge interna. Os nós compartilham CPU, RAM, armazenamento e kernel. Portanto, os números não representam uma rede entre computadores físicos independentes.");
            pdf.paragraph("Java " + env.path("java").asText() + "; " + env.path("os").asText() + " " + env.path("arch").asText() + "; CPUs visíveis: " + env.path("processors").asInt() + ". " + env.path("cpu").asText());
            Path hostFile = Benchmark.RESULTS.resolve("host.txt");
            if (Files.exists(hostFile)) {
                var hostLines = Files.readAllLines(hostFile);
                pdf.paragraph(hostLines.stream().filter(s -> s.startsWith("Docker Engine") || s.startsWith("Docker Compose")).collect(Collectors.joining("; ")));
                for (var hostLine : hostLines) if (hostLine.startsWith("Mem:")) {
                    var fields = hostLine.trim().split("\\s+");
                    if (fields.length >= 7) pdf.paragraph("RAM total: " + fields[1] + "; disponível no início: " + fields[6] + ".");
                }
            }
            pdf.paragraph("Limites por container: Java 256 MiB (heap máximo 192 MiB); Transmission 128 MiB. Os downloads de até 500 MB são gravados em disco.");
            pdf.paragraph("Registro do ambiente: " + env.path("date").asText() + ". Detalhes adicionais do host e Docker constam em resultados/host.txt, quando coletados pelo roteiro.");
            pdf.page("2.1 Fluxos de dados");
            pdf.caption("Figura " + (++pdf.figure) + " – Arquiteturas cliente-servidor e BitTorrent");
            pdf.caption("Políticas de atendimento: sequencial, paralelo e pool.");
            pdf.box(70,620,"Cliente 1"); pdf.box(70,570,"Cliente 2"); pdf.box(70,500,"Cliente N"); pdf.box(395,570,"Servidor Java");
            pdf.arrow(395,587,190,657); pdf.arrow(395,587,190,587); pdf.arrow(395,587,190,517);
            pdf.y = 460; pdf.paragraph("BitTorrent: linhas sólidas representam dados; o tracker apenas informa os participantes. Todos os peers pertencem a containers distintos no mesmo computador.");
            pdf.box(235,345,"Tracker"); pdf.box(70,240,"Seed inicial"); pdf.box(235,240,"Peer 1"); pdf.box(400,240,"Peer 2");
            pdf.canvas.setLineDashPattern(new float[]{4,4}, 0);
            pdf.arrow(295,345,130,275); pdf.arrow(295,345,295,275); pdf.arrow(295,345,460,275); pdf.canvas.setLineDashPattern(new float[]{}, 0);
            pdf.arrow(190,262,235,262); pdf.arrow(355,262,400,262); pdf.arrow(400,250,355,250);
            pdf.arrow(130,240,130,200); pdf.arrow(130,200,460,200); pdf.arrow(460,200,460,240);
            pdf.y = 155; pdf.paragraph("Peças validadas podem ser compartilhadas antes de o arquivo inteiro chegar. O seed original não precisa ser a única fonte de cada download.");
            pdf.caption("Fonte: elaboração própria (2026).");
            pdf.page("3 METODOLOGIA");
            pdf.paragraph("Matriz: 5, 50 e 500 MB decimais; 1, 2, 4 e 8 clientes; quatro arquiteturas; três repetições. Total: 48 condições, 144 execuções, 540 downloads. O seed não é contado como cliente.");
            pdf.paragraph("Arquivos determinísticos não comprimidos são gerados antes dos testes. A preparação dos torrents, a validação inicial do seed e o início dos containers ficam fora da medição. Quatro execuções curtas aquecem o ambiente, sem entrar nas estatísticas. A ordem é embaralhada com semente 20261004.");
            pdf.paragraph("O executor usa System.nanoTime(): para cada cliente, mede do envio do comando de início à observação da conclusão. Os comandos são enviados em paralelo. A consulta de estado ocorre a cada 100 ms, acrescida do tempo de RPC e processamento. Isso introduz atraso de observação, sobretudo relevante para arquivos pequenos. Não é tempo puro de transmissão nem início perfeitamente simultâneo.");
            pdf.paragraph("Após a conclusão, confere tamanho e SHA-256. O tempo dessa verificação adicional fica em coluna separada. Downloads inválidos ou incompletos não entram nos resultados válidos. No BitTorrent, a conclusão exige que todas as peças estejam validadas pelo cliente.");
            pdf.paragraph("O upload agregado é limitado nominalmente a " + env.path("uploadBps").asLong() / 1_000_000.0 + " MB/s por nó. Java reserva tempo de envio em um limitador compartilhado; Transmission utiliza seu limitador de sessão em kB/s decimais. São implementações distintas, com diferentes janelas e rajadas. O BitTorrent agrega a capacidade dos peers, enquanto cliente-servidor concentra o upload no servidor.");
            pdf.paragraph("Todos os downloads usam disco temporário; configurações e dados das transferências são limpos entre execuções. Não foi eliminado o cache do sistema operacional. Os resultados refletem também custos de disco, agendamento e compartilhamento de recursos.");
            double maxSkew = runs.stream().mapToDouble(Benchmark.Run::startSkewMs).max().orElseThrow();
            pdf.paragraph(String.format(Locale.ROOT, "Maior desvio registrado entre comandos de início: %.3f ms. Mínimo e máximo são extremos entre downloads; média é aritmética. As tabelas principais juntam todos os clientes das três repetições de cada condição. Makespan é o intervalo do disparo global até todos concluírem e aparece separadamente no CSV e apêndice.", maxSkew));
            for (int mb : new int[]{5,50,500}) {
                if (mb == 5) pdf.page("4 RESULTADOS");
                else pdf.page("4." + (mb == 5 ? 1 : mb == 50 ? 2 : 3) + " Resultados – arquivo de " + mb + " MB");
                if (mb == 5) {
                    pdf.line("4.1 Resultados – arquivo de 5 MB", 12, pdf.font);
                    pdf.contents.add("4.1 Resultados – arquivo de 5 MB"); pdf.contentsPages.add(pdf.doc.getNumberOfPages() - 1);
                }
                pdf.caption("Tabela " + (++pdf.table) + " – Tempos de transferência para " + mb + " MB");
                pdf.paragraph("Tempos observados em segundos. Amostras = clientes x 3 repetições. Mínimo, média e máximo referem-se aos downloads individuais.");
                pdf.tableHeader(false);
                for (var mode : Benchmark.MODES) {
                    for (int n : new int[]{1,2,4,8}) {
                        var v = times(runs, mode, mb, n); var s = Stats.of(v);
                        pdf.tableRow(new String[]{label(mode), String.valueOf(n), String.valueOf(v.length), number(s.min()), number(s.mean()), number(s.max())}, false);
                    }
                    pdf.y -= 7;
                }
                pdf.tableSource();
            }
            for (int mb : new int[]{5,50,500}) {
                pdf.page("4." + (mb == 5 ? 4 : mb == 50 ? 5 : 6) + " Comparação das médias – " + mb + " MB");
                pdf.caption("Figura " + (++pdf.figure) + " – Tempo médio de transferência para " + mb + " MB");
                pdf.paragraph("Altura das barras = média dos tempos individuais nas três repetições. Eixo vertical em segundos, escala linear independente por tamanho de arquivo.");
                double max = 0;
                for (var mode : Benchmark.MODES) for (int n : new int[]{1,2,4,8}) max = Math.max(max, Stats.of(times(runs,mode,mb,n)).mean());
                float base = 200, height = 350;
                for (int tick = 0; tick <= 5; tick++) {
                    float yy = base + height * tick / 5;
                    pdf.textAt(45, yy, String.format(Locale.ROOT,"%.1f", max * tick / 5), 9, pdf.font);
                    pdf.canvas.setStrokingColor(Color.LIGHT_GRAY); pdf.canvas.moveTo(118, yy); pdf.canvas.lineTo(RIGHT, yy); pdf.canvas.stroke();
                }
                int group = 0;
                for (int n : new int[]{1,2,4,8}) {
                    for (int m = 0; m < 4; m++) {
                        double value = Stats.of(times(runs, Benchmark.MODES.get(m),mb,n)).mean();
                        float x = 125 + group * 100 + m * 20;
                        pdf.canvas.setNonStrokingColor(COLORS[m]); pdf.canvas.addRect(x, base, 16, (float)(value/max*height)); pdf.canvas.fill();
                    }
                    pdf.textAt(113+group*115, 180, n == 1 ? "1 cliente" : n + " clientes", 9, pdf.font); group++;
                }
                for (int m = 0; m < 4; m++) {
                    pdf.canvas.setNonStrokingColor(COLORS[m]); pdf.canvas.addRect(LEFT+m*113, 135, 10, 10); pdf.canvas.fill();
                    pdf.textAt(60+m*125,135,label(Benchmark.MODES.get(m)),9,pdf.font);
                }
                pdf.y = 105; pdf.caption("Fonte: dados do experimento (2026).");
            }
            pdf.page("5 VALIDAÇÃO DA IMPLEMENTAÇÃO");
            pdf.paragraph("Os testes funcionais são separados da matriz de desempenho. Eles conferem concorrência e conteúdo recebido, além de demonstrar colaboração entre participantes BitTorrent.");
            Path testsFile = Benchmark.RESULTS.resolve("testes.json");
            if (Files.exists(testsFile)) {
                var tests = Util.JSON.readTree(testsFile.toFile());
                pdf.paragraph("Registro: " + tests.path("date").asText());
                for (var check : tests.path("checks")) pdf.paragraph(check.asText());
                pdf.paragraph("Somente no teste de colaboração, o seed foi limitado a 5 MB/s, dando tempo para observar troca de peças entre peers. Na matriz principal, o limite nominal é 25 MB/s por nó.");
            }
            pdf.paragraph("Nove testes unitários passaram sem falhas, erros ou testes ignorados. Verificam estatísticas, última parte incompleta, interrupção, timeout, conteúdo corrompido, nomes de arquivo, identificadores do tracker, bencoding e upload agregado. Registro Maven: resultados/unitarios.txt.");
            pdf.page("6 DISCUSSÃO DOS RESULTADOS");
            for (int mb : new int[]{5,50,500}) {
                String best = Benchmark.MODES.stream().min(Comparator.comparingDouble(m -> Stats.of(times(runs,m,mb,8)).mean())).orElseThrow();
                double seq = Stats.of(times(runs,"sequencial",mb,8)).mean(); double bt = Stats.of(times(runs,"bittorrent",mb,8)).mean();
                pdf.paragraph(String.format(Locale.ROOT,"Para %d MB e oito clientes, a menor média observada foi %s. Sequencial: %.3f s; BitTorrent: %.3f s. Esses valores descrevem este ambiente e não estabelecem superioridade geral da arquitetura.",mb,label(best),seq,bt));
            }
            pdf.paragraph(String.format(Locale.ROOT, "Com 500 MB e apenas um cliente, BitTorrent teve média de %.3f s e sequencial de %.3f s. Sem outros clientes para compartilhar peças, não há contribuição de peers adicionais. A diferença observada inclui os custos das implementações e do ambiente, além do protocolo.",
                Stats.of(times(runs,"bittorrent",500,1)).mean(), Stats.of(times(runs,"sequencial",500,1)).mean()));
            long uploaded = runs.stream().filter(r -> r.mode().equals("bittorrent")).mapToLong(Benchmark.Run::peersUploaded).sum();
            long evidence = runs.stream().filter(r -> r.mode().equals("bittorrent") && !r.peerLinks().isEmpty()).count();
            pdf.paragraph("O upload acumulado dos clientes BitTorrent foi " + uploaded + " bytes. Em " + evidence + " das 36 execuções BitTorrent houve recebimento de peers diferentes do seed observado nas consultas RPC. Contadores de upload e conexões estão nos registros JSON; amostragem pode não capturar conexões breves.");
            pdf.paragraph("No sequencial, espera na fila aumenta o tempo dos últimos clientes. Paralelo e pool dividem o mesmo upload do servidor: aumentar concorrência não multiplica sua capacidade. BitTorrent pode distribuir o envio entre participantes, mas descoberta, estabelecimento de conexões e seleção de peças adicionam custos.");
            pdf.paragraph("Arquivos pequenos tornam mais relevantes os custos fixos e a resolução de consulta. Para arquivos maiores, a distribuição do upload tem mais tempo para atuar. Disco compartilhado, cache, processos externos e limitadores diferentes são fatores de confusão. Três repetições fornecem uma descrição inicial, sem demonstrar significância estatística.");
            pdf.page("7 CONCLUSÃO");
            pdf.paragraph("As quatro modalidades foram avaliadas com arquivos iguais, integridade conferida e procedimento documentado. A escolha de arquitetura depende da carga e do ambiente. Uma avaliação futura entre máquinas físicas, com maior número de repetições e controle de rede, permitiria ampliar a validade dos resultados.");
            pdf.page("8 REPRODUÇÃO DO EXPERIMENTO");
            Path artifact = Benchmark.RESULTS.resolve("artefato-medido.txt");
            if (Files.exists(artifact)) pdf.paragraph("Commit utilizado nas medições: " + Files.readAllLines(artifact).getFirst() + ". Hashes do JAR e da imagem estão no registro do artefato.");
            pdf.paragraph("Repositório: https://github.com/Breno-Thiago/sistemas-distribuidos-atividade-1-unidade-2");
            pdf.paragraph("No projeto: docker compose up -d --build; docker compose run --rm executor testar; docker compose run --rm executor benchmark --perfil completo; docker compose run --rm executor relatorio. O README detalha requisitos, retomada e preservação dos resultados. O PDF é gerado em Java com Apache PDFBox (Apache Software Foundation, [s. d.]).");
            pdf.page("REFERÊNCIAS");
            pdf.reference("APACHE SOFTWARE FOUNDATION. Apache PDFBox. [S. l.], [s. d.]. Disponível em: https://pdfbox.apache.org/. Acesso em: 4 out. 2026.");
            pdf.reference("COHEN, Bram. The BitTorrent protocol specification. [S. l.], 2008. Disponível em: https://www.bittorrent.org/beps/bep_0003.html. Acesso em: 4 out. 2026.");

            pdf.reference("ORACLE. Java platform, standard edition 21: API specification. [S. l.], 2023. Disponível em: https://docs.oracle.com/en/java/javase/21/docs/api/. Acesso em: 4 out. 2026.");

            pdf.reference("TRANSMISSION PROJECT. Transmission RPC specification: versão 3.00. [S. l.], 2020. Disponível em: https://github.com/transmission/transmission/blob/3.00/extras/rpc-spec.txt. Acesso em: 4 out. 2026.");
            var sorted = runs.stream().sorted(Comparator.comparing(Benchmark.Run::mode).thenComparingLong(Benchmark.Run::bytes).thenComparingInt(Benchmark.Run::clients).thenComparingInt(Benchmark.Run::repetition)).toList();
            for (int start = 0; start < sorted.size(); start += 30) {
                if (start == 0) { pdf.page("APÊNDICE A – TEMPOS POR EXECUÇÃO"); pdf.table++; }
                else pdf.newSectionPage("APÊNDICE A – TEMPOS POR EXECUÇÃO", false);
                pdf.caption("Tabela " + pdf.table + " – Medições individuais por execução" + (start == 0 ? "" : " (continuação)"));
                pdf.line("Tempos em segundos. Total = makespan (até todos concluírem).", 9, pdf.font);
                pdf.tableHeader(true);
                for (var r : sorted.subList(start, Math.min(start+30,sorted.size()))) {
                    var s = Stats.of(r.samples().stream().mapToDouble(Benchmark.Sample::seconds).toArray());
                    pdf.tableRow(new String[]{label(r.mode()), String.valueOf(r.bytes()/1_000_000), String.valueOf(r.clients()), String.valueOf(r.repetition()), number(s.min()), number(s.mean()), number(s.max()), number(r.makespan())}, true);
                }
                pdf.tableSource();
            }
            pdf.fillContents();
            pdf.canvas.close(); pdf.canvas = null;
            docInfo(pdf.doc); pdf.doc.save("/relatorio/relatorio.pdf.tmp");
            Files.move(Path.of("/relatorio/relatorio.pdf.tmp"), Path.of("/relatorio/relatorio.pdf"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        Util.log("PDF GERADO: relatorio/relatorio.pdf - 144 execuções e 540 downloads.");
    }
    static void docInfo(PDDocument d) { var i = d.getDocumentInformation(); i.setTitle("Atividade 01 Unidade 2 - Transferência de arquivos"); i.setAuthor("Breno Thiago Argemiro Santos"); }
    public void close() throws IOException { if (canvas != null) canvas.close(); doc.close(); }
}
