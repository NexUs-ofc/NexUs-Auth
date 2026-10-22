package com.example.nexusauth.admin;

import com.example.nexusauth.admin.AdminDashboard.Banco;
import com.example.nexusauth.admin.AdminDashboard.ClasseStatus;
import com.example.nexusauth.admin.AdminDashboard.Cpu;
import com.example.nexusauth.admin.AdminDashboard.EndpointLento;
import com.example.nexusauth.admin.AdminDashboard.Gc;
import com.example.nexusauth.admin.AdminDashboard.Heap;
import com.example.nexusauth.admin.AdminDashboard.Http;
import com.example.nexusauth.admin.AdminDashboard.Janela;
import com.example.nexusauth.admin.AdminDashboard.Jvm;
import com.example.nexusauth.admin.AdminDashboard.Latencia;
import com.example.nexusauth.admin.AdminDashboard.Logs;
import com.example.nexusauth.admin.AdminDashboard.Ponto;
import com.example.nexusauth.admin.AdminDashboard.Requisicoes;
import com.example.nexusauth.admin.AdminDashboard.Serie;
import com.example.nexusauth.admin.AdminDashboard.StatusHttp;
import com.example.nexusauth.admin.AdminDashboard.TaxaErro;
import com.example.nexusauth.admin.AdminDashboard.Threads;
import com.example.nexusauth.admin.AdminDashboard.Uptime;
import com.example.nexusauth.admin.MetricsSnapshot.HttpSeries;
import com.example.nexusauth.admin.MetricsSnapshot.HttpStat;
import com.example.nexusauth.admin.MetricsSnapshot.Pool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Transforma as amostras do {@link MetricsSampler} nas visoes do painel.
 *
 * <p>As contas espelham as consultas PromQL equivalentes: {@code rate()} e a diferenca entre duas amostras
 * dividida pelo tempo entre elas, e os percentis seguem o {@code histogram_quantile}, interpolando dentro do
 * bucket. Contador que volta para tras e reinicio do processo: conta do zero, como o Prometheus faz.
 */
@Service
public class AdminMetricsService {

    static final int ENDPOINTS_EM_DESTAQUE = 3;
    static final int ENDPOINTS_MAIS_LENTOS = 10;
    static final String DEMAIS = "demais";

    private static final List<String> CLASSES_STATUS = List.of("2xx", "3xx", "4xx", "5xx");

    private final MetricsSampler sampler;
    private final Clock clock;
    private final String servico;

    @Autowired
    public AdminMetricsService(MetricsSampler sampler, @Value("${spring.application.name}") String servico) {
        this(sampler, Clock.systemUTC(), servico);
    }

    public AdminMetricsService(MetricsSampler sampler, Clock clock, String servico) {
        this.sampler = sampler;
        this.clock = clock;
        this.servico = servico;
    }

    public int minutosValidos(int minutos) {
        return Math.max(1, Math.min(minutos, sampler.retencaoMinutos()));
    }

    public AdminDashboard montar(int minutos) {
        int janelaMinutos = minutosValidos(minutos);
        Instant fim = clock.instant();
        Instant inicio = fim.minus(Duration.ofMinutes(janelaMinutos));

        List<MetricsSnapshot> amostras = new ArrayList<>();
        for (MetricsSnapshot amostra : sampler.historico()) {
            if (!amostra.at().isBefore(inicio)) {
                amostras.add(amostra);
            }
        }

        // O ponto de agora entra sempre: sem ele o painel ficaria um passo atrasado.
        MetricsSnapshot agora = sampler.capturar();
        if (amostras.isEmpty() || agora.at().isAfter(amostras.get(amostras.size() - 1).at())) {
            amostras.add(agora);
        }

        return montar(amostras, inicio, fim, janelaMinutos);
    }

    AdminDashboard montar(List<MetricsSnapshot> amostras, Instant inicio, Instant fim, int minutos) {
        List<Intervalo> intervalos = intervalos(amostras);
        MetricsSnapshot ultima = amostras.get(amostras.size() - 1);

        return new AdminDashboard(
                servico,
                new Janela(inicio.toString(), fim.toString(), minutos, sampler.passoSegundos(), amostras.size()),
                http(amostras, intervalos),
                jvm(amostras, intervalos, ultima),
                banco(amostras, intervalos, ultima),
                logs(intervalos));
    }

    record Intervalo(MetricsSnapshot antes, MetricsSnapshot depois, double segundos) {

        String t() {
            return depois.at().toString();
        }
    }

    static List<Intervalo> intervalos(List<MetricsSnapshot> amostras) {
        List<Intervalo> intervalos = new ArrayList<>();
        for (int i = 1; i < amostras.size(); i++) {
            MetricsSnapshot antes = amostras.get(i - 1);
            MetricsSnapshot depois = amostras.get(i);
            double segundos = Duration.between(antes.at(), depois.at()).toMillis() / 1000.0;
            if (segundos > 0) {
                intervalos.add(new Intervalo(antes, depois, segundos));
            }
        }
        return intervalos;
    }

    /** Diferenca de um contador entre duas leituras. Se voltou para tras, o processo reiniciou. */
    static double delta(double antes, double depois) {
        if (Double.isNaN(depois)) {
            return 0.0;
        }
        if (Double.isNaN(antes) || depois < antes) {
            return depois;
        }
        return depois - antes;
    }

    private Http http(List<MetricsSnapshot> amostras, List<Intervalo> intervalos) {
        Map<String, Double> requisicoesPorUri = new HashMap<>();
        Map<String, Double> segundosPorUri = new HashMap<>();
        Map<String, Double> maximoPorUri = new HashMap<>();
        Map<String, Long> porClasse = new LinkedHashMap<>();
        CLASSES_STATUS.forEach(classe -> porClasse.put(classe, 0L));

        List<Map<String, Double>> uriPorIntervalo = new ArrayList<>();
        List<Ponto> cliente = new ArrayList<>();
        List<Ponto> servidor = new ArrayList<>();
        List<Ponto> p50 = new ArrayList<>();
        List<Ponto> p95 = new ArrayList<>();
        List<Ponto> p99 = new ArrayList<>();
        List<Ponto> media = new ArrayList<>();
        List<Ponto> maximo = new ArrayList<>();
        boolean histograma = false;
        Double[] percentisAtuais = {null, null, null};

        for (Intervalo intervalo : intervalos) {
            Map<String, Double> porUri = new HashMap<>();
            double total = 0;
            double erros4xx = 0;
            double erros5xx = 0;
            double segundos = 0;
            double maximoIntervalo = 0;

            for (Map.Entry<HttpSeries, HttpStat> entrada : intervalo.depois().http().entrySet()) {
                HttpSeries serie = entrada.getKey();
                HttpStat depois = entrada.getValue();
                HttpStat antes = intervalo.antes().http().get(serie);
                double requisicoes = delta(antes == null ? 0 : antes.count(), depois.count());
                double tempo = delta(antes == null ? 0 : antes.totalSeconds(), depois.totalSeconds());

                porUri.merge(serie.uri(), requisicoes, Double::sum);
                segundosPorUri.merge(serie.uri(), tempo, Double::sum);
                total += requisicoes;
                segundos += tempo;
                if ("CLIENT_ERROR".equals(serie.outcome())) {
                    erros4xx += requisicoes;
                } else if ("SERVER_ERROR".equals(serie.outcome())) {
                    erros5xx += requisicoes;
                }
                porClasse.merge(classe(serie.status()), Math.round(requisicoes), Long::sum);
                if (requisicoes > 0) {
                    maximoIntervalo = Math.max(maximoIntervalo, depois.maxSeconds());
                }
            }

            porUri.forEach((uri, quantidade) -> requisicoesPorUri.merge(uri, quantidade, Double::sum));
            uriPorIntervalo.add(porUri);
            cliente.add(ponto(intervalo, total > 0 ? erros4xx / total * 100 : null));
            servidor.add(ponto(intervalo, total > 0 ? erros5xx / total * 100 : null));
            media.add(ponto(intervalo, total > 0 ? segundos / total * 1000 : null));
            maximo.add(ponto(intervalo, total > 0 ? maximoIntervalo * 1000 : null));

            double[] limites = intervalo.depois().bucketBounds();
            if (limites.length > 0 && MetricsSampler.mesmosLimites(limites, intervalo.antes().bucketBounds())) {
                histograma = true;
                double[] cumulativos = new double[limites.length];
                for (int i = 0; i < limites.length; i++) {
                    cumulativos[i] = delta(intervalo.antes().bucketCounts()[i], intervalo.depois().bucketCounts()[i]);
                }
                percentisAtuais = new Double[] {
                    quantil(0.50, limites, cumulativos, total),
                    quantil(0.95, limites, cumulativos, total),
                    quantil(0.99, limites, cumulativos, total)};
                p50.add(ponto(intervalo, percentisAtuais[0]));
                p95.add(ponto(intervalo, percentisAtuais[1]));
                p99.add(ponto(intervalo, percentisAtuais[2]));
            }
        }

        for (MetricsSnapshot amostra : amostras) {
            amostra.http().forEach((serie, stat) ->
                    maximoPorUri.merge(serie.uri(), stat.maxSeconds() * 1000, Math::max));
        }

        List<String> destaques = requisicoesPorUri.entrySet().stream()
                .filter(entrada -> entrada.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(ENDPOINTS_EM_DESTAQUE)
                .map(Map.Entry::getKey)
                .toList();

        List<Serie> seriesRequisicoes = new ArrayList<>();
        for (String uri : destaques) {
            seriesRequisicoes.add(serieTaxa(uri, intervalos, uriPorIntervalo, porUri -> porUri.getOrDefault(uri, 0.0)));
        }
        boolean temDemais = requisicoesPorUri.entrySet().stream()
                .anyMatch(entrada -> entrada.getValue() > 0 && !destaques.contains(entrada.getKey()));
        if (temDemais) {
            seriesRequisicoes.add(serieTaxa(DEMAIS, intervalos, uriPorIntervalo, porUri -> porUri.entrySet().stream()
                    .filter(entrada -> !destaques.contains(entrada.getKey()))
                    .mapToDouble(Map.Entry::getValue)
                    .sum()));
        }

        Double atualPorSegundo = null;
        if (!intervalos.isEmpty()) {
            Intervalo ultimo = intervalos.get(intervalos.size() - 1);
            double soma = uriPorIntervalo.get(uriPorIntervalo.size() - 1).values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            atualPorSegundo = arredondar(soma / ultimo.segundos());
        }

        Double clienteAtual = ultimoValor(cliente);
        Double servidorAtual = ultimoValor(servidor);
        Double erroAtual = clienteAtual == null || servidorAtual == null ? null
                : arredondar(clienteAtual + servidorAtual);

        long totalJanela = porClasse.values().stream().mapToLong(Long::longValue).sum();
        List<ClasseStatus> classes = porClasse.entrySet().stream()
                .filter(entrada -> CLASSES_STATUS.contains(entrada.getKey()) || entrada.getValue() > 0)
                .map(entrada -> new ClasseStatus(entrada.getKey(), entrada.getValue(),
                        totalJanela > 0 ? arredondar(entrada.getValue() * 100.0 / totalJanela) : null))
                .toList();

        List<EndpointLento> lentos = requisicoesPorUri.entrySet().stream()
                .filter(entrada -> entrada.getValue() > 0)
                .map(entrada -> new EndpointLento(
                        entrada.getKey(),
                        Math.round(entrada.getValue()),
                        arredondar(segundosPorUri.getOrDefault(entrada.getKey(), 0.0) / entrada.getValue() * 1000),
                        arredondar(maximoPorUri.getOrDefault(entrada.getKey(), Double.NaN))))
                .sorted(Comparator.comparing(EndpointLento::mediaMs,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ENDPOINTS_MAIS_LENTOS)
                .toList();

        List<Serie> seriesLatencia = histograma
                ? List.of(new Serie("p50", p50), new Serie("p95", p95), new Serie("p99", p99))
                : List.of(new Serie("media", media), new Serie("maximo", maximo));

        return new Http(
                new Requisicoes(atualPorSegundo, seriesRequisicoes),
                new TaxaErro(erroAtual, clienteAtual, servidorAtual,
                        List.of(new Serie("4xx", cliente), new Serie("5xx", servidor))),
                new StatusHttp(totalJanela, classes),
                new Latencia(histograma, percentisAtuais[0], percentisAtuais[1], percentisAtuais[2],
                        ultimoValor(media), ultimoValor(maximo), seriesLatencia),
                lentos);
    }

    private Jvm jvm(List<MetricsSnapshot> amostras, List<Intervalo> intervalos, MetricsSnapshot ultima) {
        List<Ponto> heap = new ArrayList<>();
        List<Ponto> processo = new ArrayList<>();
        List<Ponto> sistema = new ArrayList<>();
        List<Ponto> threads = new ArrayList<>();
        List<Ponto> uptime = new ArrayList<>();
        List<String> reinicios = new ArrayList<>();

        MetricsSnapshot anterior = null;
        for (MetricsSnapshot amostra : amostras) {
            String t = amostra.at().toString();
            heap.add(new Ponto(t, arredondar(MetricsSampler.megabytes(amostra.heapUsedBytes()))));
            processo.add(new Ponto(t, arredondar(amostra.processCpu() * 100)));
            sistema.add(new Ponto(t, arredondar(amostra.systemCpu() * 100)));
            threads.add(new Ponto(t, arredondar(amostra.threads())));
            uptime.add(new Ponto(t, arredondar(amostra.uptimeSeconds() / 60)));
            if (anterior != null && amostra.uptimeSeconds() < anterior.uptimeSeconds()) {
                reinicios.add(t);
            }
            anterior = amostra;
        }

        List<Ponto> gc = new ArrayList<>();
        for (Intervalo intervalo : intervalos) {
            double pausa = delta(intervalo.antes().gcPauseSeconds(), intervalo.depois().gcPauseSeconds());
            gc.add(ponto(intervalo, pausa / intervalo.segundos()));
        }

        String iniciadoEm = Double.isFinite(ultima.uptimeSeconds())
                ? ultima.at().minusMillis(Math.round(ultima.uptimeSeconds() * 1000)).toString()
                : null;

        return new Jvm(
                new Heap(ultimoValor(heap), arredondar(MetricsSampler.megabytes(ultima.heapMaxBytes())),
                        List.of(new Serie("usado", heap))),
                new Cpu(ultimoValor(processo), ultimoValor(sistema),
                        List.of(new Serie("processo", processo), new Serie("sistema", sistema))),
                new Gc(ultimoValor(gc), List.of(new Serie("gc", gc))),
                new Threads(ultimoValor(threads), List.of(new Serie("threads", threads))),
                new Uptime(ultimoValor(uptime), iniciadoEm, reinicios, List.of(new Serie("uptime", uptime))));
    }

    private Banco banco(List<MetricsSnapshot> amostras, List<Intervalo> intervalos, MetricsSnapshot ultima) {
        Pool pool = ultima.pool();
        if (pool == null) {
            return new Banco(false, null, null, null, null, null, List.of());
        }

        List<Ponto> ativas = new ArrayList<>();
        List<Ponto> pendentes = new ArrayList<>();
        for (MetricsSnapshot amostra : amostras) {
            if (amostra.pool() != null) {
                String t = amostra.at().toString();
                ativas.add(new Ponto(t, arredondar(amostra.pool().active())));
                pendentes.add(new Ponto(t, arredondar(amostra.pool().pending())));
            }
        }

        Long timeouts = null;
        if (Double.isFinite(pool.timeouts())) {
            double soma = 0;
            for (Intervalo intervalo : intervalos) {
                if (intervalo.antes().pool() != null && intervalo.depois().pool() != null) {
                    soma += delta(intervalo.antes().pool().timeouts(), intervalo.depois().pool().timeouts());
                }
            }
            timeouts = Math.round(soma);
        }

        return new Banco(true, pool.type(), arredondar(pool.max()), arredondar(pool.active()),
                arredondar(pool.pending()), timeouts,
                List.of(new Serie("ativas", ativas), new Serie("pendentes", pendentes)));
    }

    private Logs logs(List<Intervalo> intervalos) {
        List<Ponto> erros = new ArrayList<>();
        double total = 0;
        boolean medido = false;
        for (Intervalo intervalo : intervalos) {
            if (Double.isNaN(intervalo.depois().logErrors())) {
                continue;
            }
            medido = true;
            double quantidade = delta(intervalo.antes().logErrors(), intervalo.depois().logErrors());
            total += quantidade;
            erros.add(ponto(intervalo, quantidade / intervalo.segundos()));
        }
        return new Logs(ultimoValor(erros), medido ? Math.round(total) : null, List.of(new Serie("error", erros)));
    }

    /**
     * Percentil a partir de contagens acumuladas por limite ({@code le}), interpolando dentro do bucket como o
     * {@code histogram_quantile}. Resultado em milissegundos.
     */
    static Double quantil(double q, double[] limites, double[] cumulativos, double total) {
        if (total <= 0 || limites.length == 0) {
            return null;
        }
        double alvo = q * total;
        double limiteAnterior = 0;
        double contagemAnterior = 0;
        for (int i = 0; i < limites.length; i++) {
            if (cumulativos[i] >= alvo) {
                double dentro = cumulativos[i] - contagemAnterior;
                double fracao = dentro <= 0 ? 1.0 : (alvo - contagemAnterior) / dentro;
                return arredondar((limiteAnterior + (limites[i] - limiteAnterior) * fracao) * 1000);
            }
            limiteAnterior = limites[i];
            contagemAnterior = cumulativos[i];
        }
        return arredondar(limites[limites.length - 1] * 1000);
    }

    static String classe(String status) {
        if (status != null && status.length() == 3 && Character.isDigit(status.charAt(0))) {
            return status.charAt(0) + "xx";
        }
        return "outros";
    }

    private static Serie serieTaxa(String nome, List<Intervalo> intervalos, List<Map<String, Double>> porIntervalo,
                                   ToDoubleFunction<Map<String, Double>> quantidade) {
        List<Ponto> pontos = new ArrayList<>();
        for (int i = 0; i < intervalos.size(); i++) {
            Intervalo intervalo = intervalos.get(i);
            pontos.add(ponto(intervalo, quantidade.applyAsDouble(porIntervalo.get(i)) / intervalo.segundos()));
        }
        return new Serie(nome, pontos);
    }

    private static Ponto ponto(Intervalo intervalo, Double valor) {
        return new Ponto(intervalo.t(), valor == null ? null : arredondar(valor));
    }

    private static Double ultimoValor(List<Ponto> pontos) {
        for (int i = pontos.size() - 1; i >= 0; i--) {
            if (pontos.get(i).v() != null) {
                return pontos.get(i).v();
            }
        }
        return null;
    }

    static Double arredondar(double valor) {
        if (!Double.isFinite(valor)) {
            return null;
        }
        return Math.round(valor * 1000.0) / 1000.0;
    }
}
