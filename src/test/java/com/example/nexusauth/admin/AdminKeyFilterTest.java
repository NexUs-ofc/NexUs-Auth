package com.example.nexusauth.admin;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminKeyFilterTest {

    private static final String CHAVE = "chave-do-painel";

    @Test
    void aceitaAChaveNoHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/metrics");
        request.addHeader(AdminKeyFilter.HEADER, CHAVE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new AdminKeyFilter(CHAVE).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"chave-errada", "outra"})
    void recusaChaveAusenteOuErradaSemExporAChave(String enviada) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/metrics");
        if (enviada != null) {
            request.addHeader(AdminKeyFilter.HEADER, enviada);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new AdminKeyFilter(CHAVE).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"error\":").doesNotContain(CHAVE);
        verifyNoInteractions(chain);
    }

    @Test
    void parametroKeySoValeNoActuator() throws Exception {
        MockHttpServletRequest prometheus = new MockHttpServletRequest("GET", "/actuator/prometheus");
        prometheus.addParameter("key", CHAVE);
        MockHttpServletResponse respostaPrometheus = new MockHttpServletResponse();
        FilterChain chainPrometheus = mock(FilterChain.class);

        new AdminKeyFilter(CHAVE).doFilter(prometheus, respostaPrometheus, chainPrometheus);

        verify(chainPrometheus).doFilter(prometheus, respostaPrometheus);

        MockHttpServletRequest painel = new MockHttpServletRequest("GET", "/admin/metrics");
        painel.addParameter("key", CHAVE);
        MockHttpServletResponse respostaPainel = new MockHttpServletResponse();
        FilterChain chainPainel = mock(FilterChain.class);

        new AdminKeyFilter(CHAVE).doFilter(painel, respostaPainel, chainPainel);

        assertThat(respostaPainel.getStatus()).isEqualTo(401);
        verifyNoInteractions(chainPainel);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void bloqueiaQuandoAChaveNaoEstaConfigurada(String configurada) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/session");
        request.addHeader(AdminKeyFilter.HEADER, "qualquer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new AdminKeyFilter(configurada).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(chain);
    }

    @Test
    void deixaPassarOPreflightDoNavegador() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/admin/metrics");
        request.addHeader("Origin", "https://painel.example.com");
        request.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new AdminKeyFilter(CHAVE).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
