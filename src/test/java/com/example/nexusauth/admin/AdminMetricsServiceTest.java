package com.example.nexusauth.admin;

import com.example.nexusauth.admin.AdminDashboard.ClasseStatus;
import com.example.nexusauth.admin.AdminDashboard.Serie;
import com.example.nexusauth.admin.MetricsSnapshot.HttpSeries;
import com.example.nexusauth.admin.MetricsSnapshot.HttpStat;
import com.example.nexusauth.admin.MetricsSnapshot.Pool;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdminMetricsServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");
    private static final double[] LIMITES = {0.1, 0.5, 1.0};

    private final MetricsSampler sampler = new MetricsSampler(new SimpleMeterRegistry(),
            Clock.fixed(T0, ZoneOffset.UTC), 60_000, 60, List.of());
    private final AdminMetricsService service = new AdminMetricsService(sampler, Clock.fixed(T0, ZoneOffset.UTC),
            "NexUs-Auth");

    @Test
    void deltaTrataContadorQueVoltouComoReinicio() {
        assertThat(AdminMetricsService.delta(10, 25)).isEqualTo(15);
        assertThat(AdminMetricsService.delta(25, 4)).isEqualTo(4);
        assertThat(AdminMetricsService.delta(Double.NaN, 7)).isEqualTo(7);
        assertThat(AdminMetricsService.delta(3, Double.NaN)).isZero();
    }

    @Test
    void quantilInterpolaDentroDoBucketComoOHistogramQuantile() {
        double[] cumulativos = {50, 90, 100};

        assertThat(AdminMetricsService.quantil(0.5, LIMITES, cumulativos, 100)).isCloseTo(100.0, within(0.001));
        assertThat(AdminMetricsService.quantil(0.7, LIMITES, cumulativos, 100)).isCloseTo(300.0, within(0.001));
        assertThat(AdminMetricsService.quantil(0.95, LIMITES, cumulativos, 100)).isCloseTo(750.0, within(0.001));
    }

    @Test
    void quantilSemTrafegoENulo() {
        assertThat(AdminMetricsService.quantil(0.5, LIMITES, new double[] {0, 0, 0}, 0)).isNull();
    }

    @Test
    void quantilAcimaDoMaiorLimiteDevolveOMaiorLimite() {
        assertThat(AdminMetricsService.quantil(0.99, LIMITES, new double[] {0, 0, 10}, 20)).isEqualTo(1000.0);
    }

    @Test
    void classificaOStatusPelaCentena() {
        assertThat(AdminMetricsService.classe("201")).isEqualTo("2xx");
        assertThat(AdminMetricsService.classe("404")).isEqualTo("4xx");
        assertThat(AdminMetricsService.classe("UNKNOWN")).isEqualTo("outros");
    }

    @Test
    void montaTaxaErroStatusELatenciaEntreDuasAmostras() {
        MetricsSnapshot antes = amostra(T0, 0, Map.of(
                new HttpSeries("/api/auth/login", "200", "SUCCESS"), new HttpStat(100, 10, 0.2),
                new HttpSeries("/api/auth/login", "401", "CLIENT_ERROR"), new HttpStat(10, 1, 0.1),
                new HttpSeries("/health", "200", "SUCCESS"), new HttpStat(50, 0.5, 0.01)),
                new double[] {100, 150, 160});
        MetricsSnapshot depois = amostra(T0.plusSeconds(60), 60, Map.of(
                new HttpSeries("/api/auth/login", "200", "SUCCESS"), new HttpStat(160, 22, 0.9),
                new HttpSeries("/api/auth/login", "401", "CLIENT_ERROR"), new HttpStat(16, 1.6, 0.1),
                new HttpSeries("/api/auth/login", "500", "SERVER_ERROR"), new HttpStat(4, 2, 0.8),
                new HttpSeries("/health", "200", "SUCCESS"), new HttpStat(110, 1.1, 0.01)),
                new double[] {150, 240, 290});

        AdminDashboard painel = service.montar(List.of(antes, depois), T0, T0.plusSeconds(60), 60);

        // 60 + 6 + 4 + 60 = 130 requisicoes em 60s
        assertThat(painel.http().requisicoes().atualPorSegundo()).isCloseTo(130 / 60.0, within(0.001));
        assertThat(painel.http().taxaErro().clientePct()).isCloseTo(6 * 100.0 / 130, within(0.001));
        assertThat(painel.http().taxaErro().servidorPct()).isCloseTo(4 * 100.0 / 130, within(0.001));

        assertThat(painel.http().status().total()).isEqualTo(130);
        assertThat(painel.http().status().classes()).extracting(ClasseStatus::classe)
                .containsExactly("2xx", "3xx", "4xx", "5xx");

        assertThat(painel.http().latencia().histograma()).isTrue();
        assertThat(painel.http().latencia().p50Ms()).isNotNull();
        assertThat(painel.http().latencia().p99Ms()).isGreaterThanOrEqualTo(painel.http().latencia().p50Ms());

        assertThat(painel.http().endpointsMaisLentos().get(0).uri()).isEqualTo("/api/auth/login");
        assertThat(painel.http().endpointsMaisLentos().get(0).requisicoes()).isEqualTo(70);
        assertThat(painel.http().requisicoes().series()).extracting(Serie::nome)
                .containsExactly("/api/auth/login", "/health");
    }

    @Test
    void semHistogramaUsaMediaEMaximo() {
        Map<HttpSeries, HttpStat> antes = Map.of(new HttpSeries("/x", "200", "SUCCESS"), new HttpStat(0, 0, 0));
        Map<HttpSeries, HttpStat> depois = Map.of(new HttpSeries("/x", "200", "SUCCESS"), new HttpStat(10, 2, 0.5));

        AdminDashboard painel = service.montar(List.of(
                amostra(T0, 0, antes, new double[0]), amostra(T0.plusSeconds(60), 60, depois, new double[0])),
                T0, T0.plusSeconds(60), 60);

        assertThat(painel.http().latencia().histograma()).isFalse();
        assertThat(painel.http().latencia().mediaMs()).isCloseTo(200.0, within(0.001));
        assertThat(painel.http().latencia().maximoMs()).isCloseTo(500.0, within(0.001));
        assertThat(painel.http().latencia().series()).extracting(Serie::nome).containsExactly("media", "maximo");
    }

    @Test
    void marcaReinicioQuandoOUptimeVoltaAZero() {
        Map<HttpSeries, HttpStat> vazio = Map.of();
        AdminDashboard painel = service.montar(List.of(
                amostra(T0, 600, vazio, new double[0]),
                amostra(T0.plusSeconds(60), 660, vazio, new double[0]),
                amostra(T0.plusSeconds(120), 30, vazio, new double[0])),
                T0, T0.plusSeconds(120), 60);

        assertThat(painel.jvm().uptime().reinicios()).containsExactly(T0.plusSeconds(120).toString());
        assertThat(painel.jvm().uptime().minutos()).isEqualTo(0.5);
    }

    @Test
    void somaTimeoutsDoPoolNaJanela() {
        Map<HttpSeries, HttpStat> vazio = Map.of();
        MetricsSnapshot antes = comPool(amostra(T0, 0, vazio, new double[0]), new Pool("postgres", 1, 0, 3, 2));
        MetricsSnapshot depois = comPool(amostra(T0.plusSeconds(60), 60, vazio, new double[0]),
                new Pool("postgres", 3, 1, 3, 5));

        AdminDashboard painel = service.montar(List.of(antes, depois), T0, T0.plusSeconds(60), 60);

        assertThat(painel.banco().disponivel()).isTrue();
        assertThat(painel.banco().capacidade()).isEqualTo(3.0);
        assertThat(painel.banco().timeoutsNaJanela()).isEqualTo(3);
        assertThat(painel.banco().ativas()).isEqualTo(3.0);
    }

    @Test
    void semPoolOBancoVemIndisponivel() {
        Map<HttpSeries, HttpStat> vazio = Map.of();
        AdminDashboard painel = service.montar(List.of(amostra(T0, 0, vazio, new double[0])), T0, T0, 60);

        assertThat(painel.banco().disponivel()).isFalse();
        assertThat(painel.http().requisicoes().atualPorSegundo()).isNull();
    }

    @Test
    void samplerLeOsMedidoresDoRegistro() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer.builder("http.server.requests").tags("uri", "/x", "status", "200", "outcome", "SUCCESS",
                "method", "GET").register(registry).record(Duration.ofMillis(120));
        Timer.builder("http.server.requests").tags("uri", "/x", "status", "200", "outcome", "SUCCESS",
                "method", "POST").register(registry).record(Duration.ofMillis(80));
        Counter.builder("logback.events").tag("level", "error").register(registry).increment(2);
        Counter.builder("notify.push.deliveries").tag("result", "accepted").register(registry).increment(3);

        MetricsSampler leitor = new MetricsSampler(registry, Clock.fixed(T0, ZoneOffset.UTC), 60_000, 60,
                List.of("notify.push.deliveries"));
        MetricsSnapshot amostra = leitor.capturar();

        HttpStat stat = amostra.http().get(new HttpSeries("/x", "200", "SUCCESS"));
        assertThat(stat.count()).isEqualTo(2);
        assertThat(stat.totalSeconds()).isCloseTo(0.2, within(0.0001));
        assertThat(amostra.logErrors()).isEqualTo(2);
        assertThat(amostra.counters()).containsEntry("notify.push.deliveries{result=accepted}", 3.0);
        assertThat(amostra.pool()).isNull();
    }

    @Test
    void historicoDescartaAsAmostrasMaisAntigas() {
        MetricsSampler leitor = new MetricsSampler(new SimpleMeterRegistry(), Clock.fixed(T0, ZoneOffset.UTC),
                60_000, 3, List.of());
        for (int i = 0; i < 5; i++) {
            leitor.registrar(amostra(T0.plusSeconds(i * 60L), i, Map.of(), new double[0]));
        }

        assertThat(leitor.historico()).hasSize(3);
        assertThat(leitor.historico().get(0).at()).isEqualTo(T0.plusSeconds(120));
    }

    private static MetricsSnapshot amostra(Instant at, double uptime, Map<HttpSeries, HttpStat> http,
                                           double[] contagens) {
        double[] limites = contagens.length == 0 ? new double[0] : LIMITES;
        return new MetricsSnapshot(at, http, limites, contagens, 200 * 1024 * 1024, 512 * 1024 * 1024,
                0.2, 0.3, uptime / 1000, 40, uptime, null, 0, Map.of());
    }

    private static MetricsSnapshot comPool(MetricsSnapshot base, Pool pool) {
        return new MetricsSnapshot(base.at(), base.http(), base.bucketBounds(), base.bucketCounts(),
                base.heapUsedBytes(), base.heapMaxBytes(), base.processCpu(), base.systemCpu(),
                base.gcPauseSeconds(), base.threads(), base.uptimeSeconds(), pool, base.logErrors(),
                base.counters());
    }
}
