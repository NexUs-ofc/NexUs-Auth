package com.example.nexusauth.admin;

import com.example.nexusauth.admin.MetricsSnapshot.HttpSeries;
import com.example.nexusauth.admin.MetricsSnapshot.HttpStat;
import com.example.nexusauth.admin.MetricsSnapshot.Pool;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Le o registro do Micrometer a cada passo e guarda a janela recente em memoria.
 *
 * <p>Nao ha servidor Prometheus guardando historico, entao o proprio servico guarda o seu. O custo e
 * perder a janela quando o processo reinicia, que o painel mostra pelo grafico de uptime.
 *
 * <p>{@code @Lazy(false)} porque com inicializacao preguicosa (o Auth sobe assim) o bean nunca seria
 * criado e o agendamento nunca comecaria.
 */
@Component
@Lazy(false)
public class MetricsSampler {

    private static final Logger logger = LoggerFactory.getLogger(MetricsSampler.class);

    private static final double BYTES_POR_MB = 1024.0 * 1024.0;

    private final MeterRegistry registry;
    private final Clock clock;
    private final long passoMs;
    private final int maximoAmostras;
    private final Set<String> contadores;
    private final Deque<MetricsSnapshot> historico = new ArrayDeque<>();

    @Autowired
    public MetricsSampler(
            MeterRegistry registry,
            @Value("${app.admin.metrics.step-ms:60000}") long passoMs,
            @Value("${app.admin.metrics.retention-minutes:720}") int retencaoMinutos,
            @Value("${app.admin.metrics.counters:}") List<String> contadores) {
        this(registry, Clock.systemUTC(), passoMs, retencaoMinutos, contadores);
    }

    public MetricsSampler(MeterRegistry registry, Clock clock, long passoMs, int retencaoMinutos,
                          List<String> contadores) {
        this.registry = registry;
        this.clock = clock;
        this.passoMs = Math.max(1000L, passoMs);
        this.maximoAmostras = (int) Math.max(2L, retencaoMinutos * 60_000L / this.passoMs);
        this.contadores = contadores == null ? Set.of() : contadores.stream()
                .map(String::trim)
                .filter(nome -> !nome.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Scheduled(initialDelay = 0, fixedRateString = "${app.admin.metrics.step-ms:60000}")
    public void amostrar() {
        try {
            registrar(capturar());
        } catch (RuntimeException exception) {
            logger.warn("Falha ao amostrar metricas do painel error_type={}", exception.getClass().getSimpleName());
        }
    }

    void registrar(MetricsSnapshot amostra) {
        synchronized (historico) {
            historico.addLast(amostra);
            while (historico.size() > maximoAmostras) {
                historico.removeFirst();
            }
        }
    }

    public List<MetricsSnapshot> historico() {
        synchronized (historico) {
            return new ArrayList<>(historico);
        }
    }

    public long passoSegundos() {
        return passoMs / 1000L;
    }

    public int retencaoMinutos() {
        return (int) (maximoAmostras * passoMs / 60_000L);
    }

    public MetricsSnapshot capturar() {
        Map<HttpSeries, HttpStat> http = new HashMap<>();
        TreeMap<Double, Double> buckets = new TreeMap<>();

        for (Timer timer : registry.find("http.server.requests").timers()) {
            HttpSeries serie = new HttpSeries(tag(timer, "uri"), tag(timer, "status"), tag(timer, "outcome"));
            HttpStat atual = new HttpStat(timer.count(), timer.totalTime(TimeUnit.SECONDS),
                    timer.max(TimeUnit.SECONDS));
            http.merge(serie, atual, (a, b) -> new HttpStat(a.count() + b.count(),
                    a.totalSeconds() + b.totalSeconds(), Math.max(a.maxSeconds(), b.maxSeconds())));

            for (CountAtBucket bucket : timer.takeSnapshot().histogramCounts()) {
                buckets.merge(bucket.bucket(TimeUnit.SECONDS), bucket.count(), Double::sum);
            }
        }

        double[] limites = buckets.keySet().stream().mapToDouble(Double::doubleValue).toArray();
        double[] contagens = buckets.values().stream().mapToDouble(Double::doubleValue).toArray();

        return new MetricsSnapshot(
                clock.instant(),
                Map.copyOf(http),
                limites,
                contagens,
                somaGauges("jvm.memory.used", "area", "heap"),
                somaGaugesPositivos("jvm.memory.max", "area", "heap"),
                gauge("process.cpu.usage"),
                gauge("system.cpu.usage"),
                somaTempoTimers("jvm.gc.pause"),
                gauge("jvm.threads.live"),
                uptimeSegundos(),
                pool(),
                somaContadores("logback.events", "level", "error"),
                contadoresConfigurados());
    }

    static double megabytes(double bytes) {
        return bytes / BYTES_POR_MB;
    }

    private Pool pool() {
        if (!registry.find("hikaricp.connections.active").gauges().isEmpty()) {
            return new Pool("postgres",
                    somaGauges("hikaricp.connections.active", null, null),
                    somaGauges("hikaricp.connections.pending", null, null),
                    somaGauges("hikaricp.connections.max", null, null),
                    somaContadores("hikaricp.connections.timeout", null, null));
        }
        if (!registry.find("mongodb.driver.pool.checkedout").gauges().isEmpty()) {
            return new Pool("mongodb",
                    somaGauges("mongodb.driver.pool.checkedout", null, null),
                    somaGauges("mongodb.driver.pool.waitqueuesize", null, null),
                    somaGauges("mongodb.driver.pool.size", null, null),
                    Double.NaN);
        }
        return null;
    }

    private Map<String, Double> contadoresConfigurados() {
        if (contadores.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> valores = new HashMap<>();
        for (Meter meter : registry.getMeters()) {
            String nome = meter.getId().getName();
            if (meter instanceof Counter counter && contadores.contains(nome)) {
                valores.merge(chave(meter), counter.count(), Double::sum);
            }
        }
        return Map.copyOf(valores);
    }

    /** Nome do contador mais as tags, sem a tag {@code application}, que e igual em todos. */
    static String chave(Meter meter) {
        String tags = meter.getId().getTags().stream()
                .filter(tag -> !"application".equals(tag.getKey()))
                .map(tag -> tag.getKey() + "=" + tag.getValue())
                .sorted()
                .collect(Collectors.joining(","));
        return tags.isEmpty() ? meter.getId().getName() : meter.getId().getName() + "{" + tags + "}";
    }

    private double uptimeSegundos() {
        TimeGauge uptime = registry.find("process.uptime").timeGauge();
        return uptime == null ? Double.NaN : uptime.value(TimeUnit.SECONDS);
    }

    private double gauge(String nome) {
        Gauge gauge = registry.find(nome).gauge();
        return gauge == null ? Double.NaN : gauge.value();
    }

    private double somaGauges(String nome, String tagChave, String tagValor) {
        Collection<Gauge> gauges = tagChave == null
                ? registry.find(nome).gauges()
                : registry.find(nome).tag(tagChave, tagValor).gauges();
        if (gauges.isEmpty()) {
            return Double.NaN;
        }
        return gauges.stream().mapToDouble(Gauge::value).filter(Double::isFinite).sum();
    }

    private double somaGaugesPositivos(String nome, String tagChave, String tagValor) {
        Collection<Gauge> gauges = registry.find(nome).tag(tagChave, tagValor).gauges();
        double soma = gauges.stream().mapToDouble(Gauge::value).filter(valor -> valor > 0).sum();
        return soma > 0 ? soma : Double.NaN;
    }

    private double somaContadores(String nome, String tagChave, String tagValor) {
        Collection<Counter> counters = tagChave == null
                ? registry.find(nome).counters()
                : registry.find(nome).tag(tagChave, tagValor).counters();
        if (counters.isEmpty()) {
            return Double.NaN;
        }
        return counters.stream().mapToDouble(Counter::count).sum();
    }

    private double somaTempoTimers(String nome) {
        Collection<Timer> timers = registry.find(nome).timers();
        if (timers.isEmpty()) {
            return 0.0;
        }
        return timers.stream().mapToDouble(timer -> timer.totalTime(TimeUnit.SECONDS)).sum();
    }

    private static String tag(Meter meter, String chave) {
        return meter.getId().getTags().stream()
                .filter(tag -> chave.equals(tag.getKey()))
                .map(Tag::getValue)
                .findFirst()
                .orElse("UNKNOWN");
    }

    static boolean mesmosLimites(double[] a, double[] b) {
        return Arrays.equals(a, b);
    }
}
