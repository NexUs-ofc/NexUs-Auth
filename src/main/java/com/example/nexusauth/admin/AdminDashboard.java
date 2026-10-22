package com.example.nexusauth.admin;

import java.util.List;

/**
 * Contrato de {@code GET /admin/metrics}: as doze visoes da pagina do servico no painel, ja calculadas.
 *
 * <p>Instantes em ISO-8601 (UTC). Valor nulo quer dizer que o servico nao tem o medidor ou que nao houve
 * trafego suficiente na janela para calcular.
 */
public record AdminDashboard(
        String servico,
        Janela janela,
        Http http,
        Jvm jvm,
        Banco banco,
        Logs logs) {

    public record Janela(String inicio, String fim, int minutos, long passoSegundos, int amostras) {
    }

    public record Ponto(String t, Double v) {
    }

    public record Serie(String nome, List<Ponto> pontos) {
    }

    public record Http(
            Requisicoes requisicoes,
            TaxaErro taxaErro,
            StatusHttp status,
            Latencia latencia,
            List<EndpointLento> endpointsMaisLentos) {
    }

    /** Requisicoes por segundo, uma serie por endpoint mais movimentado e o resto em "demais". */
    public record Requisicoes(Double atualPorSegundo, List<Serie> series) {
    }

    /** Percentual de 4xx (cliente) e 5xx (servidor) sobre o total de cada intervalo. */
    public record TaxaErro(Double atualPct, Double clientePct, Double servidorPct, List<Serie> series) {
    }

    public record StatusHttp(long total, List<ClasseStatus> classes) {
    }

    public record ClasseStatus(String classe, long quantidade, Double percentual) {
    }

    /**
     * p50, p95 e p99 do intervalo mais recente. Sem histograma no servico, {@code histograma} e falso e so
     * media e maximo vem preenchidos.
     */
    public record Latencia(
            boolean histograma,
            Double p50Ms,
            Double p95Ms,
            Double p99Ms,
            Double mediaMs,
            Double maximoMs,
            List<Serie> series) {
    }

    public record EndpointLento(String uri, long requisicoes, Double mediaMs, Double maximoMs) {
    }

    public record Jvm(Heap heap, Cpu cpu, Gc gc, Threads threads, Uptime uptime) {
    }

    public record Heap(Double usadoMb, Double maximoMb, List<Serie> series) {
    }

    public record Cpu(Double processoPct, Double sistemaPct, List<Serie> series) {
    }

    /** Segundos de pausa de GC por segundo de relogio. */
    public record Gc(Double pausaSegundosPorSegundo, List<Serie> series) {
    }

    public record Threads(Double atuais, List<Serie> series) {
    }

    /** Instantes em {@code reinicios} sao as amostras em que o uptime voltou a zero. */
    public record Uptime(Double minutos, String iniciadoEm, List<String> reinicios, List<Serie> series) {
    }

    public record Banco(
            boolean disponivel,
            String tipo,
            Double capacidade,
            Double ativas,
            Double pendentes,
            Long timeoutsNaJanela,
            List<Serie> series) {
    }

    public record Logs(Double errosPorSegundo, Long errosNaJanela, List<Serie> series) {
    }
}
