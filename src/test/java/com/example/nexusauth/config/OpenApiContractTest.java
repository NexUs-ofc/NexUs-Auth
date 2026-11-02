package com.example.nexusauth.config;

import com.example.nexusauth.ratelimit.AuthRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.api-key=auth-docs-test", "spring.config.import="})
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthRateLimitService rateLimits;

    @Test
    void exposesSwaggerAndDescribesAllOperationsWithoutRequiringCredentials() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        JsonNode document = document();
        document.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            JsonNode operation = method.getValue();
            assertThat(operation.path("summary").asText()).as(path.getKey()).isNotBlank();
            assertThat(operation.path("description").asText()).as(path.getKey()).isNotBlank();
            operation.path("parameters").forEach(parameter ->
                    assertThat(parameter.path("description").asText())
                            .as(parameter.path("name").asText()).isNotBlank());
        }));
        assertReferencesResolve(document, document);
    }

    @Test
    void documentsApiKeyAndBearerAsCombinedRequirementsOnlyWhereNeeded() throws Exception {
        JsonNode document = document();
        JsonNode login = document.at("/paths/~1api~1auth~1login~1password/post/security");
        assertThat(login.size()).isEqualTo(1);
        assertThat(login.get(0).has("ApiKeyAuth")).isTrue();
        assertThat(login.get(0).has("BearerAuth")).isFalse();
        for (String route : new String[] {"logout", "google~1link", "password~1link"}) {
            JsonNode security = document.at("/paths/~1api~1auth~1" + route + "/post/security");
            assertThat(security.size()).isEqualTo(1);
            assertThat(security.get(0).has("ApiKeyAuth")).isTrue();
            assertThat(security.get(0).has("BearerAuth")).isTrue();
            assertThat(document.at("/paths/~1api~1auth~1" + route + "/post/parameters").isMissingNode()).isTrue();
        }
        JsonNode admin = document.at("/paths/~1admin~1session/get/security");
        assertThat(admin.get(0).has("AdminKeyAuth")).isTrue();
        assertThat(admin.get(0).has("ApiKeyAuth")).isFalse();
    }

    @Test
    void documentsActualSuccessStatusesAndStructuredErrors() throws Exception {
        JsonNode document = document();
        JsonNode registration = document.at("/paths/~1api~1auth~1registrations~1password~1start/post/responses");
        assertThat(registration.has("202")).isTrue();
        assertThat(registration.has("200")).isFalse();
        assertThat(registration.has("409")).isTrue();
        JsonNode google = document.at("/paths/~1api~1auth~1google~1authenticate/post/responses");
        assertThat(google.has("200")).isTrue();
        assertThat(google.has("428")).isTrue();
        JsonNode logout = document.at("/paths/~1api~1auth~1logout/post/responses");
        assertThat(logout.has("204")).isTrue();
        assertThat(logout.has("200")).isFalse();
        assertThat(registration.at("/400/content/application~1json/schema/oneOf").size()).isEqualTo(2);
        assertThat(document.at("/components/schemas/AuthValidationError/properties/fields").isMissingNode()).isFalse();
        assertThat(document.at("/components/schemas/PasswordLoginRequest/properties/password/writeOnly").asBoolean())
                .isTrue();
    }

    private JsonNode document() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body);
    }

    private void assertReferencesResolve(JsonNode root, JsonNode node) {
        if (node.has("$ref")) {
            String reference = node.path("$ref").asText();
            if (reference.startsWith("#/")) {
                assertThat(root.at(reference.substring(1)).isMissingNode()).as(reference).isFalse();
            }
        }
        node.forEach(child -> assertReferencesResolve(root, child));
    }
}
