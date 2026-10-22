# Painel do administrador

Rotas que o painel do administrador (React) consome para montar a página deste serviço. São só dados em
JSON: a apresentação é do painel.

## Autenticação

Uma chave só, a mesma em todos os serviços do NexUs (Auth, Core, Notify, IA e BI-IA). O painel pede a
chave no login e a manda em todas as requisições:

```
X-Admin-Key: <ADMIN_API_KEY>
```

| Situação | Resposta |
| --- | --- |
| Chave certa | `200` |
| Chave ausente ou errada | `401` `{"error": "..."}` |
| `ADMIN_API_KEY` não configurada no servidor | `503` `{"error": "..."}` |

A chave do painel não autentica rotas do aplicativo, e a API key ou o JWT do aplicativo não abrem as rotas
do painel. Para o navegador chamar a API, a origem do painel precisa estar em `CORS_ALLOWED_ORIGINS`.

## Rotas

| Método | Rota | O que devolve |
| --- | --- | --- |
| `GET` | `/admin/session` | Confirma a chave no login: `{"servico", "autenticado", "verificadoEm"}` |
| `GET` | `/admin/metrics?minutos=60` | As visões da página, calculadas sobre os últimos `minutos` |
| `GET` | `/actuator/prometheus` | Formato de exposição do Prometheus, para um coletor externo (aceita também `?key=`) |

`minutos` vai de 1 até a retenção configurada (`ADMIN_METRICS_RETENTION_MINUTES`, padrão 720). Fora disso,
o valor é ajustado para o limite mais próximo.

## De onde vêm os números

São as métricas padrão do Spring Boot com Micrometer. Não há servidor Prometheus guardando histórico, então
o próprio serviço lê o registro a cada `ADMIN_METRICS_STEP_MS` (padrão 60s) e guarda a janela em memória.
Cada consulta junta essas amostras com uma leitura feita na hora.

As contas espelham o PromQL que daria o mesmo gráfico:

| Visão | Medidor | Equivalente PromQL |
| --- | --- | --- |
| Requisições por segundo, por endpoint | `http_server_requests_seconds_count` | `sum by (uri) (rate(...[passo]))` |
| Taxa de erro (%) | idem, por `outcome` | `CLIENT_ERROR` e `SERVER_ERROR` sobre o total |
| Distribuição de status | idem, por `status` | `sum by (status) (increase(...[janela]))` |
| Latência p50/p95/p99 | `http_server_requests_seconds_bucket` | `histogram_quantile(q, sum by (le) (rate(...)))` |
| Top 10 endpoints mais lentos | `_sum`, `_count` e `_max` por `uri` | média = soma ÷ contagem |
| Heap usada × máxima | `jvm_memory_used_bytes{area="heap"}`, `jvm_memory_max_bytes` | — |
| CPU | `process_cpu_usage`, `system_cpu_usage` | ×100 |
| Pausas de GC | `jvm_gc_pause_seconds_sum` | `rate(...)` |
| Threads ativas | `jvm_threads_live_threads` | — |
| Uptime | `process_uptime_seconds` | queda para zero marca reinício |
| Pool do banco | `hikaricp_connections_active`, `_pending`, `_max`, `_timeout_total` | timeouts = `increase(...)` |
| Erros no log | `logback_events_total{level="error"}` | `rate(...)` |

O histórico recomeça quando o processo reinicia: depois de subir, o painel mostra só o que veio desde então.
O instante do reinício aparece em `jvm.uptime.iniciadoEm`.

## Formato de `/admin/metrics`

Instantes em ISO-8601 (UTC). Valor `null` quer dizer que o serviço não tem aquele medidor ou que não houve
tráfego suficiente na janela para calcular. Toda série tem o mesmo formato:

```json
{"nome": "/api/auth/login", "pontos": [{"t": "2026-10-20T12:01:00Z", "v": 1.25}]}
```

```json
{
  "servico": "NexUs-Auth",
  "janela": {"inicio": "...", "fim": "...", "minutos": 60, "passoSegundos": 60, "amostras": 61},
  "http": {
    "requisicoes": {"atualPorSegundo": 12.4, "series": ["3 endpoints mais movimentados + \"demais\""]},
    "taxaErro": {"atualPct": 1.2, "clientePct": 0.8, "servidorPct": 0.4, "series": ["4xx", "5xx"]},
    "status": {"total": 44640, "classes": [{"classe": "2xx", "quantidade": 43613, "percentual": 97.7}]},
    "latencia": {
      "histograma": true, "p50Ms": 142, "p95Ms": 340, "p99Ms": 620, "mediaMs": 160, "maximoMs": 980,
      "series": ["p50", "p95", "p99"]
    },
    "endpointsMaisLentos": [{"uri": "/api/auth/login", "requisicoes": 320, "mediaMs": 480, "maximoMs": 980}]
  },
  "jvm": {
    "heap": {"usadoMb": 248, "maximoMb": 512, "series": ["usado"]},
    "cpu": {"processoPct": 18, "sistemaPct": 27, "series": ["processo", "sistema"]},
    "gc": {"pausaSegundosPorSegundo": 0.012, "series": ["gc"]},
    "threads": {"atuais": 42, "series": ["threads"]},
    "uptime": {"minutos": 30, "iniciadoEm": "...", "reinicios": [], "series": ["uptime"]}
  },
  "banco": {
    "disponivel": true, "tipo": "postgres", "capacidade": 3, "ativas": 1, "pendentes": 0,
    "timeoutsNaJanela": 0, "series": ["ativas", "pendentes"]
  },
  "logs": {"errosPorSegundo": 0.04, "errosNaJanela": 2, "series": ["error"]}
}
```

Nos exemplos acima, `"series"` mostra só o nome de cada série; na resposta real cada item é o objeto
`{"nome", "pontos"}`.

Sem o histograma de latência ligado, `latencia.histograma` vem `false`, os percentis vêm `null` e as séries
passam a ser `media` e `maximo`. O histograma está ligado em `application` por
`management.metrics.distribution.percentiles-histogram.http.server.requests=true`.

## Variáveis

| Variável | Padrão | Uso |
| --- | --- | --- |
| `ADMIN_API_KEY` | vazio | Chave do painel. `TRACING_ACESS` continua aceita como alternativa. |
| `ADMIN_METRICS_STEP_MS` | `60000` | Intervalo entre amostras. |
| `ADMIN_METRICS_RETENTION_MINUTES` | `720` | Histórico guardado em memória. |
| `CORS_ALLOWED_ORIGINS` | vazio | Inclua a origem do painel. |

```sh
curl -H 'X-Admin-Key: <ADMIN_API_KEY>' 'http://localhost:8080/admin/metrics?minutos=60'
```
