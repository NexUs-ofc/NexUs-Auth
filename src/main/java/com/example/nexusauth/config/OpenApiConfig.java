package com.example.nexusauth.config;

import com.example.nexusauth.admin.AdminKeyFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        Schema<?> error = new ObjectSchema()
                .addProperty("code", new StringSchema().example("INVALID_AUTHENTICATION"))
                .addProperty("message", new StringSchema().example("Credencial inválida ou expirada"))
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .required(List.of("code", "message"));
        Schema<?> validation = new ObjectSchema()
                .addProperty("code", new StringSchema().example("VALIDATION_ERROR"))
                .addProperty("fields", new ObjectSchema().additionalProperties(true)
                        .description("Mapa dos nomes dos campos rejeitados para suas mensagens de validação"))
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .required(List.of("code", "fields", "timestamp"));
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))
                .info(new Info().title("NexUs Auth API").version("1.0.0")
                        .description("Autenticação por senha e Google, cadastro com OTP e recuperação de senha. "
                                + "As rotas /api/auth exigem X-API-KEY. Logout e vinculação de credenciais "
                                + "também usam o JWT do perfil. Nunca informe refresh token no campo Bearer."))
                .components(new Components()
                        .addSchemas("AuthError", error)
                        .addSchemas("AuthValidationError", validation)
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-API-KEY"))
                        .addSecuritySchemes("BearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("AdminKeyAuth", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name(AdminKeyFilter.HEADER)))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"));
    }

    @Bean
    public OperationCustomizer documentAuthContract() {
        Set<String> conflicts = Set.of("startPasswordRegistration", "registerGoogle", "linkGoogle", "linkPassword");
        return (operation, handler) -> {
            if (handler.getBeanType().getPackageName().endsWith(".admin")) {
                if (operation.getParameters() != null) {
                    operation.getParameters().forEach(parameter -> parameter.setDescription(
                            "Janela em minutos, limitada à retenção configurada (720 por padrão)."));
                }
                operation.getResponses().addApiResponse("401",
                        new ApiResponse().description("Chave administrativa ausente ou inválida"));
                operation.getResponses().addApiResponse("503",
                        new ApiResponse().description("Chave administrativa não configurada"));
                return operation;
            }
            if (!handler.getBeanType().getPackageName().endsWith(".controller")) {
                return operation;
            }
            SecurityRequirement requirement = new SecurityRequirement().addList("ApiKeyAuth");
            if (handler.hasMethodAnnotation(PreAuthorize.class)) {
                requirement.addList("BearerAuth");
            }
            operation.setSecurity(List.of(requirement));
            operation.getResponses().addApiResponse("400", new ApiResponse().description("Dados de entrada inválidos")
                    .content(json(new Schema<>().oneOf(List.of(
                            new Schema<>().$ref("#/components/schemas/AuthError"),
                            new Schema<>().$ref("#/components/schemas/AuthValidationError"))))));
            operation.getResponses().addApiResponse("401", new ApiResponse()
                    .description("API key, senha, OTP ou token inválido ou expirado; "
                            + "o filtro pode retornar corpo vazio"));
            operation.getResponses().addApiResponse("403", error("Perfil sem acesso ao canal ou operação"));
            if (conflicts.contains(handler.getMethod().getName())) {
                operation.getResponses().addApiResponse("409", error("E-mail, CNPJ ou identidade já vinculada"));
            }
            operation.getResponses().addApiResponse("429",
                    error("Limite atingido; aguarde os segundos de Retry-After"));
            operation.getResponses().addApiResponse("500", new ApiResponse().description("Falha interna do serviço"));
            operation.getResponses().addApiResponse("503",
                    error("Autenticação Google ou geocodificação indisponível, conforme a operação"));
            return operation;
        };
    }

    private ApiResponse error(String description) {
        return new ApiResponse().description(description)
                .content(json(new Schema<>().$ref("#/components/schemas/AuthError")));
    }

    private Content json(Schema<?> schema) {
        return new Content().addMediaType("application/json",
                new io.swagger.v3.oas.models.media.MediaType().schema(schema));
    }
}

