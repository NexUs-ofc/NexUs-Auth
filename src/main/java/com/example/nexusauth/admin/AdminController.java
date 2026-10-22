package com.example.nexusauth.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;

/**
 * Rotas que o painel do administrador consome. Todas exigem o header {@code X-Admin-Key}.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminMetricsService metricsService;
    private final String servico;

    public AdminController(AdminMetricsService metricsService, @Value("${spring.application.name}") String servico) {
        this.metricsService = metricsService;
        this.servico = servico;
    }

    /** Valida a chave no login do painel: 200 com a chave certa, 401 sem ela. */
    @GetMapping("/session")
    public AdminSession session() {
        return new AdminSession(servico, true, Instant.now().toString());
    }

    /** As doze visoes da pagina do servico, na janela pedida (padrao: ultima hora). */
    @GetMapping("/metrics")
    public AdminDashboard metrics(@RequestParam(defaultValue = "60") int minutos) {
        return metricsService.montar(minutos);
    }

    public record AdminSession(String servico, boolean autenticado, String verificadoEm) {
    }
}
