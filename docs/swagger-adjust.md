# Contrato OpenAPI do Auth

O Auth usa Spring Boot 4.1 e springdoc 3.1.1. A documentação pública fica em /swagger-ui/index.html e /v3/api-docs.

As operações descrevem entradas, modelos de resposta e os status efetivamente usados pelo controller:

- 202 para início de cadastro por senha e solicitação de recuperação.
- 200 para emissão de sessão e confirmação de OTP.
- 428 no login Google quando ainda é necessário completar o cadastro.
- 204 para logout, redefinição de senha e vinculação de credenciais.

Erros de validação documentam os formatos AuthError e AuthValidationError. As respostas também indicam conflitos de identidade, limites de requisição e indisponibilidade de dependências. Exemplos usam valores fictícios.

## Credenciais no Swagger

As operações /api/auth usam X-API-KEY. Logout e vinculação de Google ou senha usam API key e BearerAuth no mesmo requisito OpenAPI, representando a combinação das credenciais.

BearerAuth recebe o access token. O refresh token é enviado no corpo das operações que o utilizam.

As operações administrativas usam X-Admin-Key, sem herdar o requisito de API key do aplicativo.

## Verificação

Execute com JDK 17:

```text
./mvnw verify checkstyle:check
```

OpenApiContractTest acessa o contrato gerado, verifica descrições das operações e parâmetros, resolve as referências aos schemas e confere os status e combinações de segurança. As suítes existentes continuam verificando os fluxos dos controllers e serviços.

No Windows, a execução iniciada pelo aplicativo desktop pode apresentar Unable to establish loopback connection na criação de clientes HTTP Java. Foi verificado que apontar jdk.net.unixdomain.tmpdir para .cache/nexus-test-sockets dentro do perfil do usuário, fora de AppData, permite abrir o seletor. Passe essa propriedade à JVM dos testes por JAVA_TOOL_OPTIONS no terminal da execução. Nenhuma configuração de produção é alterada.

Exemplo no PowerShell, com JAVA_HOME já apontando para um JDK 17:

```powershell
$socketDirectory = Join-Path $env:USERPROFILE '.cache/nexus-test-sockets'
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir="' + $socketDirectory + '"'
.\mvnw.cmd verify checkstyle:check
```

Esta alteração não muda a política de expiração ou revogação dos tokens.

## Resultado em 02/11/2026

verify e checkstyle:check concluídos com JDK 17.0.20.1: 107 testes aprovados, zero falhas, zero erros e zero ignorados. O pacote JAR foi gerado. O contrato foi verificado sem avisos de processamento JSON.
