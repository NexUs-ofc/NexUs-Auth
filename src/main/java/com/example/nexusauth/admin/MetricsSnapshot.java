package com.example.nexusauth.admin;

import java.time.Instant;
import java.util.Map;

/**
 * Leitura do registro do Micrometer num instante. Contadores sao acumulados desde a subida do processo;
 * as taxas do painel saem da diferenca entre duas leituras.
 *
 * <p>Valor indisponivel e {@link Double#NaN}: o servico nao tem aquele medidor.
 */
public record MetricsSnapshot(
        Instant at,
        Map<HttpSeries, HttpStat> http,
        double[] bucketBounds,
        double[] bucketCounts,
        double heapUsedBytes,
        double heapMaxBytes,
        double processCpu,
        double systemCpu,
        double gcPauseSeconds,
        double threads,
        double uptimeSeconds,
        Pool pool,
        double logErrors,
        Map<String, Double> counters) {

    public record HttpSeries(String uri, String status, String outcome) {
    }

    public record HttpStat(double count, double totalSeconds, double maxSeconds) {
    }

    /** Pool de conexoes do banco: Hikari para o Postgres, driver para o MongoDB. */
    public record Pool(String type, double active, double pending, double max, double timeouts) {
    }
}
