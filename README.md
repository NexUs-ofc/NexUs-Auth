# NexUs Auth

Serviço de autenticação do ecossistema **NexUs / Ceris**, responsável por cadastro, validação de e-mail, login, integração com Google, emissão e renovação de tokens, recuperação de senha e vinculação de métodos de autenticação.

O Auth centraliza a identidade dos usuários. As permissões sobre empresas, lojas, estoques e demais recursos de negócio devem ser aplicadas pelos serviços responsáveis por esses recursos.

## Sumário

- [Responsabilidades](#responsabilidades)
- [Tecnologias e dependências](#tecnologias-e-dependências)
- [Perfis e políticas de acesso](#perfis-e-políticas-de-acesso)
- [Autenticação das requisições](#autenticação-das-requisições)
- [Endpoints](#endpoints)
- [Fluxos de autenticação](#fluxos-de-autenticação)
- [Tokens e sessões](#tokens-e-sessões)
- [Validação de dados](#validação-de-dados)
- [Configuração e execução](#configuração-e-execução)
- [Persistência e Redis](#persistência-e-redis)
- [Segurança e tratamento de dados](#segurança-e-tratamento-de-dados)
- [Limitação de requisições](#limitação-de-requisições)
- [Observabilidade e administração](#observabilidade-e-administração)
- [Testes, build e implantação](#testes-build-e-implantação)
- [Diagnóstico de problemas](#diagnóstico-de-problemas)
- [Limitações conhecidas que não esbarram no cumprimento de requisitos](#limitações-conhecidas-que-não-esbarram-no-cumprimento-de-requisitos)
- [Licença](#licença)

## Responsabilidades

O serviço oferece:

- Cadastro público de pessoas e empresas.
- Confirmação de cadastro por código enviado ao e-mail.
- Login com e-mail e senha.
- Login e cadastro com identidade Google.
- Vinculação automática do Google a uma conta existente com o mesmo e-mail verificado.
- Vinculação explícita de Google ou senha a uma conta autenticada.
- Emissão de access tokens JWT e refresh tokens opacos.
- Rotação de refresh tokens e detecção de reutilização.
- Logout com revogação do access token apresentado e do refresh token informado.
- Recuperação de senha por e-mail.
- Limitação de requisições por IP, login, solicitação de OTP e renovação de tokens.
- Geocodificação de endereço durante o cadastro de pessoas.
- Endpoints administrativos de métricas e monitoramento.

O Auth não oferece, na implementação atual, endpoints de gerenciamento de perfil, exclusão de conta, alteração de e-mail ou administração completa de usuários.

## Tecnologias e dependências

| Componente | Tecnologia |
| --- | --- |
| Linguagem | Java 17 |
| Framework declarado no projeto | Spring Boot 4.1.0 |
| API HTTP | Spring Web MVC |
| Segurança | Spring Security e OAuth2 Resource Server |
| Persistência relacional | Spring Data JPA e PostgreSQL |
| Estado temporário de autenticação | Redis |
| Limitação de requisições | Contadores em memória por instância |
| Access tokens | JWT assinado com HS256 |
| Senhas e códigos OTP | BCrypt, custo 12 |
| Identidade externa | Google ID Token |
| E-mails transacionais | API HTTP da Brevo |
| Geocodificação | DistanceMatrix.ai |
| Documentação da API | Springdoc OpenAPI 3.1.1 e Swagger UI |
| Métricas | Actuator, Micrometer e Prometheus |
| Build | Maven, com Maven Wrapper |
| Verificação de estilo | Checkstyle |

### Dependências de execução

- PostgreSQL com o schema do NexUs previamente aplicado.
- Redis acessível pelo serviço.
- Credenciais da Brevo e remetente autorizado.
- Chave da DistanceMatrix.ai.
- Client IDs do Google para os clientes que utilizarem esse login.

O Hibernate está configurado com `ddl-auto: validate`: o Auth valida o schema existente e não cria nem atualiza automaticamente as tabelas.

As migrações do banco são mantidas no projeto **NexUs-DB**. Não há execução de migrações Flyway implementada no código atual do Auth.

## Perfis e políticas de acesso

### Tipos de perfil

| Tipo | Finalidade | Cadastro público |
| --- | --- | --- |
| `HOUSEHOLD` | Pessoa ou unidade doméstica no aplicativo | Sim |
| `COMPANY` | Empresa na plataforma e no aplicativo | Sim |
| `ADMIN` | Perfil administrativo do produto | Não |
| `STORE` | Loja ou filial no domínio do NexUs | Não |

A existência de um tipo no modelo não significa que ele possa ser criado pelos endpoints públicos.

### Canais de login

O campo `channel` é obrigatório no login por senha e na autenticação Google.

O perfil precisa estar com status `ACTIVE`.

Perfis `INACTIVE` ou `BLOCKED` não podem iniciar login. A renovação de sessão também consulta o perfil e exige status `ACTIVE`.

> O canal é validado no login, mas não é incluído no JWT. Os serviços consumidores devem aplicar suas próprias regras de autorização sobre as operações e os recursos acessados.

### Regras de cadastro

- Apenas `HOUSEHOLD` e `COMPANY` aceitam cadastro público.
- O e-mail deve estar disponível.
- No cadastro, o e-mail é normalizado com remoção de espaços nas extremidades e conversão para minúsculas.
- `HOUSEHOLD` exige endereço.
- `COMPANY` exige CNPJ e plano existente e ativo.
- O CNPJ não pode estar vinculado a outra empresa.
- Endereço é opcional para `COMPANY`.
- CNPJ e plano não se aplicam a `HOUSEHOLD`.
- Novos perfis são criados com status `ACTIVE`.

## Autenticação das requisições

O serviço utiliza três credenciais diferentes:

| Credencial | Header ou campo | Finalidade |
| --- | --- | --- |
| API key da aplicação | `X-API-KEY` | Controle de acesso às rotas do aplicativo |
| Access token do usuário | `Authorization: Bearer <accessToken>` | Identidade do perfil autenticado |
| Chave administrativa | `X-Admin-Key` | Acesso ao painel e às métricas administrativas |

Essas credenciais têm funções distintas. O tipo de perfil `ADMIN` também é diferente da chave administrativa do painel.

### Headers das rotas do aplicativo

```http
Content-Type: application/json
X-API-KEY: <API_KEY>
```

Nas operações que dependem de uma sessão de usuário, envie também:

```http
Authorization: Bearer <accessToken>
```

O refresh token deve ser enviado no corpo das operações correspondentes. Ele não deve ser utilizado como Bearer token.

### Rotas que dispensam API key

O filtro de API key dispensa:

- Swagger UI.
- Especificação OpenAPI.
- Caminhos de Actuator cobertos pela exceção do filtro.
- Caminhos administrativos cobertos pela exceção do filtro.
- Requisições de preflight CORS.

As rotas `/admin/**` e `/actuator/prometheus` possuem uma cadeia de segurança própria e exigem chave administrativa.

### Exigência de JWT nas rotas protegidas

A API key é validada sem criar uma autenticação de usuário. Logout e vinculação de métodos exigem, além da API key, uma autenticação JWT validada pelo servidor.

As rotas `/api/auth/logout`, `/api/auth/google/link` e `/api/auth/password/link` retornam `401` quando o Bearer token está ausente, inválido, expirado ou revogado. O perfil utilizado na operação é obtido do JWT validado.

As operações públicas de cadastro, login, recuperação de senha e refresh são liberadas explicitamente por método e caminho. Elas continuam exigindo API key e dispensam uma sessão prévia de usuário.

## Endpoints

Base das rotas de autenticação:

```text
/api/auth
```

Todas as rotas abaixo utilizam `POST`.

| Rota | Credenciais a enviar | Sucesso |
| --- | --- | --- |
| `/registrations/password/start` | API key | `202` com `registrationId` |
| `/registrations/verify` | API key | `200` com sessão |
| `/login/password` | API key | `200` com sessão |
| `/google/authenticate` | API key | `200` com sessão ou `428` para completar cadastro |
| `/registrations/google` | API key e `googleTicket` no corpo | `200` com sessão |
| `/token/refresh` | API key e refresh token no corpo | `200` com nova sessão |
| `/logout` | API key, Bearer e refresh token no corpo | `204` |
| `/password/forgot` | API key | `202` com `resetId` |
| `/password/reset/verify` | API key | `200` com `resetTicket` |
| `/password/reset` | API key e `resetTicket` no corpo | `204` |
| `/google/link` | API key e Bearer | `204` |
| `/password/link` | API key e Bearer | `204` |

A coluna de credenciais descreve o contrato que os clientes devem seguir. As rotas de logout e vinculação exigem API key e JWT válido em conjunto.

As rotas estão sujeitas ao limite geral de requisições por IP e, quando aplicável, aos limites específicos da operação. Ao exceder uma cota, a API retorna `429 Too Many Requests` com `Retry-After`.

### Resposta de sessão

Cadastro concluído, login e renovação retornam:

```json
{
  "accessToken": "<JWT>",
  "accessTokenExpiresAt": "<instante ISO-8601>",
  "refreshToken": "<token opaco>",
  "refreshTokenExpiresAt": "<instante ISO-8601>"
}
```

Na autenticação Google, a sessão aparece dentro do campo `session`.

O cliente deve utilizar as datas retornadas pela API, sem presumir que os prazos padrão estarão ativos em todos os ambientes.

## Fluxos de autenticação

### Cadastro com senha

#### 1. Iniciar o cadastro

```http
POST /api/auth/registrations/password/start
```

Exemplo para `HOUSEHOLD`:

```json
{
  "type": "HOUSEHOLD",
  "email": "pessoa@example.com",
  "password": "<senha de 8 a 72 caracteres>",
  "name": "Pessoa Exemplo",
  "phones": ["+5511999999999"],
  "address": {
    "neighborhood": "Centro",
    "street": "Rua Exemplo",
    "number": "100",
    "cep": "01001000",
    "city": "São Paulo",
    "state": "SP"
  }
}
```

Resposta `202 Accepted`:

```json
{
  "registrationId": "<identificador do cadastro>"
}
```

Nesse momento:

1. O serviço verifica as cotas de requisição e solicitação de OTP.
2. Valida os dados iniciais.
3. A senha é convertida em hash BCrypt.
4. O cadastro pendente é armazenado no Redis.
5. Um código de seis dígitos é enviado por e-mail.

O perfil definitivo ainda não foi criado.

Para `COMPANY`, envie também:

```json
{
  "cnpj": "<14 dígitos>",
  "planId": 1
}
```

O `planId` precisa corresponder a um plano ativo no banco. O endereço pode ser omitido no cadastro de empresa.

#### 2. Confirmar o e-mail

```http
POST /api/auth/registrations/verify
```

```json
{
  "registrationId": "<identificador do cadastro>",
  "otp": "123456"
}
```

Após validar o código, o serviço verifica novamente a disponibilidade do e-mail e os dados de empresa, persiste o cadastro e retorna uma sessão.

Para `HOUSEHOLD`, o endereço precisa ser localizado pelo serviço de geocodificação antes de concluir o cadastro.

**Validade padrão:** 15 minutos, controlada por `REGISTRATION_TTL`.

O código de cadastro acompanha a validade do cadastro pendente. Ele não utiliza `OTP_TTL`.

### Login com senha

```http
POST /api/auth/login/password
```

```json
{
  "email": "pessoa@example.com",
  "password": "<senha>",
  "channel": "MOBILE"
}
```

O serviço verifica:

- Cota geral por IP.
- Cota de login por combinação de e-mail normalizado e IP.
- Existência do perfil.
- Existência do método `PASSWORD`.
- Correspondência da senha com o hash armazenado.
- Status ativo.
- Permissão do tipo de perfil no canal informado.

O sucesso retorna uma sessão.

E-mail inexistente, ausência de método por senha e senha incorreta utilizam a mesma resposta de credencial inválida.

O limite padrão é de 12 tentativas em cinco minutos por e-mail e IP. A contagem ocorre antes da verificação das credenciais e inclui logins bem-sucedidos e malsucedidos que chegam a essa etapa.

### Login e cadastro com Google

O cliente obtém um **Google ID Token** e o envia ao Auth:

```http
POST /api/auth/google/authenticate
```

```json
{
  "idToken": "<Google ID Token>",
  "channel": "MOBILE"
}
```

O serviço utiliza `GoogleIdTokenVerifier`, com as audiências configuradas, e exige e-mail presente e verificado.

Um access token do Google não substitui o ID Token esperado por essa rota.

A autenticação Google está sujeita à cota geral por IP. O limite específico de 12 tentativas em cinco minutos aplica-se ao login por senha.

#### Identidade já vinculada

Retorna `200 OK`:

```json
{
  "registrationRequired": false,
  "session": {
    "accessToken": "<JWT>",
    "accessTokenExpiresAt": "<instante ISO-8601>",
    "refreshToken": "<token opaco>",
    "refreshTokenExpiresAt": "<instante ISO-8601>"
  },
  "registration": null
}
```

#### E-mail já cadastrado por outro método

Quando a identidade Google possui o mesmo e-mail verificado de um perfil existente, o serviço vincula o Google automaticamente e aplica as validações de login.

Essa vinculação automática faz parte do comportamento atual e deve ser considerada na política de identidade do produto.

#### Pessoa ainda não cadastrada

Retorna `428 Precondition Required`:

```json
{
  "registrationRequired": true,
  "session": null,
  "registration": {
    "googleTicket": "<ticket temporário>",
    "email": "pessoa@example.com",
    "name": "Pessoa Exemplo",
    "profileImageUrl": "<URL fornecida pelo Google>",
    "requiredFields": [
      "type",
      "phones",
      "address",
      "cnpj/company",
      "planId/company"
    ]
  }
}
```

O cliente deve completar o cadastro:

```http
POST /api/auth/registrations/google
```

```json
{
  "googleTicket": "<ticket temporário>",
  "type": "HOUSEHOLD",
  "phones": ["+5511999999999"],
  "address": {
    "neighborhood": "Centro",
    "street": "Rua Exemplo",
    "number": "100",
    "cep": "01001000",
    "city": "São Paulo",
    "state": "SP"
  }
}
```

Nome, e-mail e imagem vêm da identidade Google armazenada no ticket.

O cadastro Google é concluído diretamente, sem um segundo OTP de confirmação de e-mail. O serviço exige que o Google informe o e-mail como verificado.

O `googleTicket` utiliza `REGISTRATION_TTL` e é removido após a conclusão bem-sucedida.

> A lista `requiredFields` é genérica. As regras efetivas dependem do tipo: telefone é opcional nos DTOs atuais, endereço é obrigatório para `HOUSEHOLD`, e CNPJ/plano são obrigatórios apenas para `COMPANY`.

### Recuperação de senha

O fluxo possui três chamadas.

#### 1. Solicitar o código

```http
POST /api/auth/password/forgot
```

```json
{
  "email": "pessoa@example.com"
}
```

Resposta `202 Accepted`:

```json
{
  "resetId": "<identificador da recuperação>"
}
```

Para e-mail não cadastrado, o serviço também retorna `202` com um identificador, mas não envia mensagem.

Essa uniformização reduz a exposição direta da existência de contas. Ela não representa uma garantia de tempos de resposta indistinguíveis.

A solicitação está sujeita ao limite de um pedido por minuto e cinco por 15 minutos por e-mail, compartilhado com o início de cadastro por senha. A cota é verificada antes da consulta de existência do perfil.

#### 2. Validar o código

```http
POST /api/auth/password/reset/verify
```

```json
{
  "resetId": "<identificador da recuperação>",
  "otp": "123456"
}
```

Resposta `200 OK`:

```json
{
  "resetTicket": "<ticket de redefinição>"
}
```

O OTP de recuperação utiliza `OTP_TTL`, com padrão de 10 minutos.

Após a validação, o desafio é removido e o serviço emite um ticket com validade de cinco minutos.

#### 3. Definir a nova senha

```http
POST /api/auth/password/reset
```

```json
{
  "resetTicket": "<ticket de redefinição>",
  "newPassword": "<nova senha de 8 a 72 caracteres>"
}
```

Resposta `204 No Content`.

Ao concluir:

- A senha é armazenada como hash BCrypt.
- O método `PASSWORD` é criado caso ainda não exista.
- Os refresh tokens associados ao perfil são revogados.
- Nenhuma sessão nova é retornada; o cliente deve encaminhar ao login.

Os access tokens já emitidos não são todos adicionados à blacklist por essa operação. Eles podem permanecer utilizáveis até expirar, conforme as validações dos serviços consumidores.

### Vincular métodos de autenticação

As duas operações exigem uma sessão de usuário conforme o contrato de integração.

#### Adicionar senha

```http
POST /api/auth/password/link
```

```json
{
  "newPassword": "<senha de 8 a 72 caracteres>"
}
```

Disponível para perfil ativo que ainda não possui método `PASSWORD`.

A rota adiciona um método; ela não substitui uma senha existente.

#### Adicionar Google

```http
POST /api/auth/google/link
```

```json
{
  "idToken": "<Google ID Token>"
}
```

Requisitos:

- Perfil ativo.
- Identidade Google válida.
- E-mail Google igual ao e-mail do perfil, desconsiderando maiúsculas e minúsculas.
- Identidade externa ainda não vinculada.

As duas rotas retornam `204 No Content` em caso de sucesso.

## Tokens e sessões

### Access token

O access token é um JWT assinado com **HS256**.

| Claim | Conteúdo |
| --- | --- |
| `iss` | Emissor configurado |
| `sub` | ID do perfil representado como texto |
| `iat` | Instante de emissão |
| `exp` | Instante de expiração |
| `jti` | Identificador único do token |
| `profile_type` | Tipo do perfil |
| `email` | E-mail do perfil |

Configurações:

| Variável | Padrão |
| --- | --- |
| `JWT_ISSUER` | `nexus-auth` |
| `ACCESS_TOKEN_TTL` | `15m` |
| `JWT_SECRET_BASE64` | Obrigatória, sem padrão |

O segredo deve ser Base64 válido e representar pelo menos 32 bytes.

**Base64 é uma codificação, não uma proteção criptográfica do segredo.**

O JWT é assinado, sem criptografia do conteúdo. Quem possui o token pode ler suas claims.

Na implementação atual:

- Não há claim `aud`.
- Não há claim de canal.
- Não há identificador de empresa ou loja.
- Não há publicação de JWKS.
- Não há rotação de chaves com `kid`.

Como HS256 utiliza segredo compartilhado, qualquer serviço que recebe esse segredo para verificar tokens também possui capacidade criptográfica de assiná-los.

### Refresh token

O refresh token:

- É opaco, sem claims acessíveis ao cliente.
- É gerado com 32 bytes aleatórios usando `SecureRandom`.
- É codificado em Base64 URL-safe.
- Possui padrão de validade de 30 dias.
- É indexado no Redis pelo hash SHA-256, sem armazenamento do valor bruto.

Cada login cria uma família de refresh tokens.

#### Renovação

```http
POST /api/auth/token/refresh
```

```json
{
  "refreshToken": "<refresh token atual>"
}
```

A rota não exige access token válido.

Na renovação:

1. As cotas geral e específica de refresh são verificadas.
2. O refresh token atual é consumido.
3. Um sucessor é criado na mesma família.
4. O perfil é consultado e precisa estar ativo.
5. Um novo access token é emitido.
6. O cliente recebe os dois novos tokens e suas expirações.

Cada sucessor recebe uma nova validade completa de `REFRESH_TOKEN_TTL`. Portanto, o prazo é renovável; não existe um limite absoluto de duração da família implementado.

O limite padrão de renovação é de 30 requisições por minuto por IP. Ele é compartilhado entre as sessões que utilizam o mesmo IP e se acumula com a cota geral.

#### Reutilização

O uso de um refresh token já consumido, enquanto seu registro de uso estiver disponível, provoca tentativa de revogação dos tokens ativos da mesma família e resposta de autenticação inválida.

Essa revogação não invalida automaticamente os access tokens da família.

### Política de integração dos clientes

Para respeitar a rotação:

1. Mantenha apenas uma renovação em andamento por sessão.
2. Compartilhe seu resultado entre chamadas concorrentes.
3. Coordene a renovação entre abas quando elas compartilham a sessão.
4. Salve o novo par de tokens assim que a renovação tiver sucesso.
5. Utilize o novo refresh token em todas as renovações seguintes.
6. Evite repetir indefinidamente requisições que retornam `401`.
7. Diferencie credenciais inválidas de falhas temporárias de rede ou indisponibilidade.
8. Ao receber `429`, respeite `Retry-After` antes de repetir a operação.

Uma resposta `429` na renovação não indica, por si só, que o refresh token é inválido. A verificação da cota ocorre antes de seu consumo.

O Auth retorna tokens no corpo JSON. Ele não cria cookies de sessão nem define, por conta própria, o armazenamento usado pelo navegador ou aplicativo.

A perda da resposta de uma renovação pode deixar o cliente sem o refresh token sucessor. O endpoint não oferece recuperação idempotente dessa resposta.

### Logout

```http
POST /api/auth/logout
Authorization: Bearer <accessToken>
```

```json
{
  "refreshToken": "<refresh token atual>"
}
```

Resposta `204 No Content`.

O logout:

- Adiciona o `jti` do access token apresentado à blacklist pelo tempo restante de validade.
- Revoga o refresh token informado.

Ele não representa logout global de todos os dispositivos e não revoga automaticamente todos os tokens da família.

A invalidação imediata do JWT em outros serviços depende de esses serviços também consultarem a blacklist compartilhada.

## Validação de dados

### Senhas

Cadastro, vinculação e redefinição exigem:

- Valor não vazio.
- Entre 8 e 72 caracteres nos DTOs.
- Armazenamento com BCrypt, custo 12.

Não há regra implementada de obrigatoriedade de maiúsculas, números ou símbolos, histórico de senhas ou consulta a senhas comprometidas.

O limite dos DTOs em caracteres não deve ser confundido com o limite em bytes do BCrypt. Senhas com caracteres multibyte precisam de validação compatível.

### Telefones

Os DTOs públicos de cadastro aceitam lista opcional de telefones no formato:

```text
+[código do país][número]
```

Exemplo:

```text
+5511999999999
```

A expressão validada é:

```regex
\+[1-9][0-9]{7,14}
```

Isso valida o formato, sem comprovar a existência do número.

### Endereço

Quando informado, exige todos os campos:

| Campo | Regra |
| --- | --- |
| `neighborhood` | Obrigatório, até 100 caracteres |
| `street` | Obrigatório, até 150 caracteres |
| `number` | Obrigatório, até 10 caracteres |
| `cep` | Exatamente oito dígitos, sem pontuação |
| `city` | Obrigatório, até 100 caracteres |
| `state` | Duas letras |

Para `HOUSEHOLD`, o endereço passa por geocodificação e recebe latitude e longitude.

Para `COMPANY`, o endereço informado é persistido sem geocodificação.

A integração possui timeout de conexão de três segundos, leitura de cinco segundos e cache local limitado a 256 endereços encontrados.

### CNPJ

O contrato público exige 14 dígitos sem pontuação, além de disponibilidade no banco.


### OTP

- Exatamente seis dígitos.
- Gerado com `SecureRandom`.
- Armazenado como hash BCrypt.
- Até cinco verificações por desafio.
- Na requisição que ultrapassa o limite, o desafio é removido e rejeitado.
- O prazo é definido pelo tipo de fluxo.

O limite de verificações por desafio é diferente do limite de solicitações de envio de código. Os dois controles coexistem.

## Configuração e execução

### Variáveis obrigatórias

| Variável | Finalidade |
| --- | --- |
| `DB_URL` | URL JDBC do PostgreSQL |
| `DB_USERNAME` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco |
| `REDIS_HOST` | Host do Redis |
| `API_KEY` | Chave das rotas do aplicativo |
| `JWT_SECRET_BASE64` | Segredo de assinatura JWT em Base64 |
| `BREVO_API_KEY` | Credencial de envio de e-mails |
| `MAIL_FROM` | Endereço do remetente |
| `DISTANCEMATRIX_API_KEY` | Credencial de geocodificação |

A chave de geocodificação possui validação de configuração não vazia, mesmo que o ambiente utilize apenas cadastros de empresa.

### Variáveis opcionais

| Variável | Padrão | Finalidade |
| --- | --- | --- |
| `PORT` | `8080` | Porta HTTP |
| `DB_POOL_MAX_SIZE` | `3` | Máximo de conexões |
| `DB_POOL_MIN_IDLE` | `1` | Mínimo de conexões ociosas |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | `20000` | Espera por conexão |
| `REDIS_PORT` | `6379` | Porta Redis |
| `REDIS_PASSWORD` | Vazio | Autenticação Redis |
| `REDIS_SSL` | `false` | TLS na conexão Redis |
| `JWT_ISSUER` | `nexus-auth` | Emissor JWT |
| `ACCESS_TOKEN_TTL` | `15m` | Validade do access token |
| `REFRESH_TOKEN_TTL` | `30d` | Validade de cada refresh token |
| `REGISTRATION_TTL` | `15m` | Cadastro pendente, OTP de cadastro e ticket Google |
| `OTP_TTL` | `10m` | OTP de recuperação |
| `GOOGLE_WEB_CLIENT_ID` | Vazio | Audiência Google aceita |
| `GOOGLE_ANDROID_CLIENT_ID` | Vazio | Audiência Google aceita |
| `MAIL_FROM_NAME` | `NexUs` | Nome do remetente |
| `CORS_ALLOWED_ORIGINS` | Vazio | Origens permitidas, separadas por vírgula |
| `GEOCODING_BASE_URL` | `https://api.distancematrix.ai` | Base da geocodificação |
| `ADMIN_API_KEY` | Vazio | Chave administrativa |
| `ADMIN_METRICS_STEP_MS` | `60000` | Intervalo das amostras administrativas |
| `ADMIN_METRICS_RETENTION_MINUTES` | `720` | Histórico em memória |

As variáveis `RATE_LIMIT_*` estão descritas na seção [Limitação de requisições](#limitação-de-requisições).

`TRACING_ACESS` é aceito como alternativa legada quando `ADMIN_API_KEY` não está definida. Prefira `ADMIN_API_KEY`.

O ticket de redefinição de senha possui TTL fixo de cinco minutos no código, sem variável própria.

### Configuração local

Prefira fornecer as variáveis pelo ambiente da IDE, terminal ou gerenciador de execução.

O arquivo `.env.example` atual contém somente parâmetros de geocodificação e não representa toda a configuração necessária.

Um arquivo `.env` comum não é carregado automaticamente pelo serviço.

Há declarações de importação de `.env.properties` nos arquivos de configuração. Antes de utilizar arquivos locais de credenciais, confira o caminho efetivamente importado e sua exclusão do versionamento. A exclusão atual de `src/main/resources/.env.properties` não deve ser presumida para arquivos de mesmo nome em outros caminhos.

### Executar no Windows

Com Java 17, banco preparado, Redis e variáveis configuradas:

```powershell
.\mvnw.cmd spring-boot:run
```

### Executar no Linux ou macOS

```bash
./mvnw spring-boot:run
```

Endereço local padrão:

```text
http://localhost:8080
```

### Swagger e OpenAPI

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

A especificação declara os esquemas:

| Esquema | Credencial |
| --- | --- |
| `ApiKeyAuth` | Header `X-API-KEY` |
| `BearerAuth` | Access token JWT no header `Authorization` |
| `AdminKeyAuth` | Header `X-Admin-Key` |

As operações de logout e vinculação documentam API key e Bearer em conjunto. As operações administrativas documentam a chave do painel.

A especificação inclui descrições dos fluxos, respostas de validação, autenticação, conflito, rate limit e indisponibilidade. As senhas dos DTOs de login e cadastro estão marcadas como campos somente de escrita.

A declaração no OpenAPI descreve o contrato e não substitui a aplicação das regras no servidor. As rotas protegidas exigem autenticação JWT na configuração de segurança da API.

## Persistência e Redis

### PostgreSQL

| Estrutura | Uso |
| --- | --- |
| `profile` | Identidade, tipo, status e dados do perfil |
| `profile_phone` | Telefones |
| `auth_method` | Métodos de autenticação |
| `address` | Endereço e coordenadas quando aplicáveis |
| `company` | Associação entre empresa, perfil e plano |
| `plan` | Consulta de existência e disponibilidade do plano |

Em `auth_method.credential`:

- `PASSWORD` armazena o hash BCrypt.
- `GOOGLE` armazena o identificador da identidade no provedor.

### Redis

Redis mantém estado necessário à autenticação; sua função vai além de cache.

| Padrão de chave | Conteúdo | Prazo |
| --- | --- | --- |
| `auth:registration:<id>` | Cadastro pendente e hash do OTP | `REGISTRATION_TTL` |
| `auth:registration-attempts:<id>` | Contagem de verificações | TTL atualizado nas tentativas |
| `auth:google-ticket:<id>` | Identidade Google para completar cadastro | `REGISTRATION_TTL` |
| `auth:password-reset:<id>` | Perfil e hash do OTP | `OTP_TTL` |
| `auth:reset-attempts:<id>` | Contagem de verificações | TTL atualizado nas tentativas |
| `auth:password-reset-ticket:<id>` | Autorização para redefinir senha | 5 minutos |
| `auth:refresh:<hash>` | Sessão de refresh | `REFRESH_TOKEN_TTL` |
| `auth:refresh-used:<hash>` | Associação de token consumido à família | `REFRESH_TOKEN_TTL` após consumo |
| `auth:profile-refresh:<profileId>` | Índice de tokens do perfil | TTL renovado na emissão |
| `auth:refresh-family:<familyId>` | Índice de tokens da família | TTL renovado na emissão |
| `auth:blacklist:<jti>` | Revogação de access token | Validade restante do JWT |

A expiração das chaves de tentativas não prolonga a validade do cadastro ou do desafio principal.

Os contadores de rate limit são mantidos na memória da aplicação e não utilizam essas chaves Redis.


## Segurança e tratamento de dados

### Controles implementados

- Hash BCrypt para senhas e códigos OTP.
- Aleatoriedade criptográfica para OTP e refresh tokens.
- Assinatura HS256 dos access tokens.
- Validação de emissor e validade temporal dos JWTs pelo decoder configurado.
- Separação entre validação da API key e autenticação do usuário por JWT.
- Exigência de autenticação JWT nas rotas de logout e vinculação de métodos.
- Consulta de blacklist por `jti`.
- Indexação de refresh tokens por hash SHA-256.
- Rotação e registro de reutilização de refresh tokens.
- Expiração de dados temporários no Redis.
- Limite de verificações por desafio OTP.
- Rate limit geral por IP.
- Limites específicos para login por senha, solicitação de OTP e renovação.
- Identificação de IP encaminhado com lista de proxies confiáveis.
- Verificação de e-mail na identidade Google.
- Validação de status e canal no login.
- Validação de status na renovação.
- Lista configurável de origens CORS.
- Chave administrativa separada das credenciais do aplicativo.
- Comparação da chave administrativa com `MessageDigest.isEqual`.
- Ausência de sessão HTTP de usuário no servidor.

A confirmação de cadastro por OTP não representa MFA obrigatório no login. O serviço não exige um segundo fator a cada autenticação.

### CORS

`CORS_ALLOWED_ORIGINS` recebe origens completas, separadas por vírgula:

```text
https://app.example.com,http://localhost:5173
```

A configuração:

- Permite apenas as origens informadas.
- Permite todos os métodos e headers.
- Expõe todos os headers.
- Define cache de preflight de uma hora.
- Não habilita explicitamente credenciais de cookies.

Com a lista vazia, chamadas de outras origens no navegador não são autorizadas por CORS.

CORS não autentica clientes fora do navegador e não substitui a API key nem o JWT.

### Segredos e transporte

Diretrizes operacionais:

- Utilizar HTTPS no acesso externo.
- Proteger PostgreSQL e Redis por rede e credenciais.
- Habilitar TLS nas conexões externas conforme a infraestrutura.
- Manter segredos fora do código, de imagens e de arquivos publicados.
- Separar credenciais entre desenvolvimento, homologação e produção.
- Restringir acesso às chaves de assinatura e administração.
- Rotacionar credenciais em caso de exposição.

Uma API key distribuída em um frontend ou aplicativo pode ser extraída. Ela não deve ser tratada como prova suficiente da identidade do usuário.

A troca de `JWT_SECRET_BASE64` exige coordenação entre emissores e validadores. O serviço não implementa aceitação simultânea de múltiplas chaves para uma transição gradual.

### Dados pessoais e terceiros

O serviço processa:

- E-mail e nome.
- Telefones.
- Endereço.
- Coordenadas geográficas quando aplicáveis.
- URL de imagem.
- Identificador de identidade Google.
- Dados empresariais, incluindo CNPJ.
- Identificadores de perfil, sessões e eventos em logs.
- IP de origem para aplicação de cotas de requisição.

Integrações externas:

| Serviço | Dados utilizados |
| --- | --- |
| Google | ID Token e dados da identidade |
| Brevo | Destinatário, código e conteúdo do e-mail |
| DistanceMatrix.ai | Endereço para geocodificação |
| Cloudinary, na imagem do e-mail | A logo remota é carregada pelo cliente de e-mail, quando permitido |

Os identificadores de e-mail usados nos contadores de rate limit são normalizados e convertidos em hash SHA-256. Esse procedimento não torna o dado necessariamente anônimo.

### Retenção e privacidade

O código define TTLs para dados temporários de autenticação e janelas de expiração para os contadores de rate limit em memória.

Não há política automatizada de retenção e exclusão dos dados permanentes de perfil, nem prazo de retenção de logs implementado pelo Auth.

## Limitação de requisições

O Auth aplica rate limit em memória, por instância, nas rotas de autenticação.

### Regras padrão

| Operação | Padrão | Identificação |
| --- | --- | --- |
| Requisições em `/api/auth/**` | 120 por minuto | IP |
| Login com senha | 12 tentativas por 5 minutos | E-mail normalizado + IP |
| Solicitação de envio de OTP | 1 por minuto e 5 por 15 minutos | E-mail normalizado |
| Renovação de token | 30 por minuto | IP |
| Verificação de OTP | Até 5 verificações durante a validade do desafio | ID do desafio, mantido no Redis |

O limite de verificação de OTP é um controle do desafio e utiliza Redis. As demais cotas da tabela utilizam o limitador em memória.

### Aplicação das cotas

A cota geral cobre os caminhos sob `/api/auth/`, incluindo login, cadastro, recuperação, renovação, logout e vinculação de métodos.

Os limites específicos são aplicados em:

| Rota | Cota adicional |
| --- | --- |
| `/api/auth/login/password` | Login por e-mail + IP |
| `/api/auth/registrations/password/start` | Solicitação de OTP por e-mail |
| `/api/auth/password/forgot` | Solicitação de OTP por e-mail |
| `/api/auth/token/refresh` | Renovação por IP |

A cota de solicitação de OTP é compartilhada entre início de cadastro por senha e recuperação de senha.

Por exemplo, iniciar um cadastro para determinado e-mail pode impedir uma solicitação de recuperação para esse mesmo e-mail durante o próximo minuto.

Os limites se acumulam. Uma renovação precisa respeitar tanto a cota geral de 120 por minuto quanto a específica de 30 por minuto.

A cota geral pode ser consumida mesmo quando uma verificação posterior rejeita a requisição.

As cotas específicas são verificadas antes da autenticação por senha, do início dos fluxos de e-mail ou da rotação do refresh token. A contagem não depende do sucesso dessas operações.

### Funcionamento das janelas

O limitador utiliza janelas fixas por chave, iniciadas quando a respectiva cota é utilizada pela primeira vez.

- As cotas são liberadas após a expiração da janela.
- Não há bloqueio permanente de conta.
- As operações sobre os contadores são sincronizadas dentro da instância.
- Entradas expiradas são removidas durante o processamento de novas verificações.
- Há um teto configurável para a quantidade de entradas.
- Se não houver capacidade para criar as janelas necessárias, a requisição também pode receber `429`.

O limite de entradas representa a quantidade de contadores, não a quantidade de usuários.

### Configuração

| Variável | Padrão | Finalidade |
| --- | --- | --- |
| `RATE_LIMIT_MAX_ENTRIES` | `100000` | Máximo de entradas no limitador |
| `RATE_LIMIT_REQUESTS_PER_MINUTE` | `120` | Cota geral por IP |
| `RATE_LIMIT_LOGIN_ATTEMPTS` | `12` | Tentativas por e-mail + IP |
| `RATE_LIMIT_LOGIN_WINDOW` | `5m` | Janela do login por senha |
| `RATE_LIMIT_OTP_PER_MINUTE` | `1` | Solicitações de OTP por e-mail por minuto |
| `RATE_LIMIT_OTP_PER_QUARTER_HOUR` | `5` | Solicitações de OTP por e-mail por 15 minutos |
| `RATE_LIMIT_REFRESH_PER_MINUTE` | `30` | Renovações por IP por minuto |
| `RATE_LIMIT_TRUSTED_PROXIES` | Vazio | IPs ou redes de proxies confiáveis, separados por vírgula |

Os limites numéricos e a duração da janela de login devem ser positivos.

### Resposta ao exceder a cota

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
Content-Type: application/json
```

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Muitas requisicoes. Tente novamente mais tarde."
}
```

`Retry-After` informa quantos segundos esperar antes de tentar novamente.

Quando a rejeição ocorre no controller, a resposta também contém `timestamp`. Rejeições feitas diretamente pelo filtro retornam `code` e `message`, sem esse campo.

O cliente deve respeitar o header e evitar repetição imediata ou ciclos automáticos de tentativas.

### Proxies e IP de origem

Por padrão, o limitador utiliza o endereço remoto da conexão.

Quando a aplicação está atrás de um proxy, `RATE_LIMIT_TRUSTED_PROXIES` deve conter somente os IPs ou redes da infraestrutura confiável.

O resolvedor:

- Examina `X-Forwarded-For` apenas enquanto o remetente de cada salto é confiável.
- Percorre a cadeia de encaminhamento da direita para a esquerda.
- Descarta valores inválidos.
- Não utiliza o header como prova de origem quando o remetente não está na lista confiável.

Sem configuração adequada, usuários diferentes podem compartilhar a cota do IP do proxy. Uma lista excessivamente ampla pode permitir que cabeçalhos enviados pelo cliente influenciem a identificação.

### Escopo e limitações

- Requisições `OPTIONS` não passam pelo limitador.
- Swagger, Actuator e rotas administrativas ficam fora desse filtro.
- Cada instância mantém seus próprios contadores.
- Reiniciar o processo apaga as cotas em memória.
- Múltiplas réplicas não compartilham uma cota global.
- Usuários que compartilham IP também compartilham as cotas geral e de renovação.
- Reiniciar o Auth não apaga, por si só, os desafios e contadores de OTP mantidos no Redis.


## Observabilidade e administração

### Saúde

```http
GET /actuator/health
```

O manifesto Kubernetes referencia também:

```text
/actuator/health/liveness
/actuator/health/readiness
```

As probes devem ser verificadas no ambiente de implantação.

### Administração

| Método | Rota | Finalidade |
| --- | --- | --- |
| `GET` | `/admin/session` | Validar a chave administrativa |
| `GET` | `/admin/metrics?minutos=60` | Consultar métricas agregadas |
| `GET` | `/actuator/prometheus` | Exposição para coleta externa |

Header:

```http
X-Admin-Key: <ADMIN_API_KEY>
```

- Chave inválida ou ausente: `401`.
- Chave administrativa não configurada: `503`.
- Erros administrativos utilizam corpo `{"error":"..."}`.

Em `/actuator/prometheus`, a implementação também aceita `?key=...`. Prefira o header quando o coletor permitir, pois parâmetros de URL podem aparecer em registros de infraestrutura.

### Histórico das métricas

O serviço coleta amostras do Micrometer:

- Intervalo padrão de 60 segundos.
- Retenção padrão de 720 minutos.
- Histórico em memória da instância.
- Histórico reiniciado após reinício do processo.

As visões incluem tráfego, erros HTTP, latência, memória, CPU, GC, threads, uptime, conexões do banco e eventos de erro.

A consulta `minutos` é ajustada ao intervalo entre um minuto e a retenção configurada.

A exposição de métricas não cria, sozinha, um servidor Prometheus com armazenamento persistente.

Detalhes adicionais estão em `docs/painel-admin.md`. O exemplo de coleta está em `src/main/resources/prometeus.yml`.

## Testes, build e implantação

### Validação local

Windows:

```powershell
.\mvnw.cmd test
.\mvnw.cmd checkstyle:check
.\mvnw.cmd clean package
```

Linux ou macOS:

```bash
./mvnw test
./mvnw checkstyle:check
./mvnw clean package
```

O projeto possui testes para autenticação, cadastros pendentes, recuperação de senha, e-mails, geocodificação, configuração de segurança, contrato OpenAPI, rate limit e métricas administrativas.

Os testes de segurança verificam a exigência conjunta de API key e JWT nas rotas protegidas, a rejeição de tokens inválidos, expirados ou revogados, o uso da identidade validada e o funcionamento das rotas públicas, CORS e administração.

Os testes de rate limit incluem cotas de login, compartilhamento da cota de OTP, renovação, expiração de janelas, concorrência e identificação de IP por proxies confiáveis.

A configuração de testes não equivale a uma validação completa contra os serviços reais de produção.

### Artefato executável

```text
target/NexUs-Auth-0.0.1-SNAPSHOT.jar
```

Execução:

```bash
java -jar target/NexUs-Auth-0.0.1-SNAPSHOT.jar
```

### Integração contínua

A pipeline configura jobs de:

- Testes.
- Checkstyle.
- Build do JAR.

Eles são disparados em pushes para `main` e pull requests destinados à `main`.

### Discloud

O repositório contém:

- `discloud.config`.
- Workflow de implantação.
- Build e renomeação do artefato para `app.jar`.

O workflow valida o build nas pull requests e executa o upload em eventos que não sejam pull request, incluindo push para `main` e execução manual.

A implantação utiliza o segredo `DISCLOUD_TOKEN`. As configurações do serviço precisam ser fornecidas no ambiente de execução.

Em ambientes com proxy, configure `RATE_LIMIT_TRUSTED_PROXIES` conforme os endereços efetivamente utilizados pela infraestrutura.

### Docker

O arquivo disponível é `Dockerfile.vercel`.

```bash
docker build -f Dockerfile.vercel -t nexus-auth .
docker run --rm -p 8080:8080 --env-file /caminho/seguro/auth.env nexus-auth
```

Nesse exemplo, `--env-file` é processado pelo Docker, que injeta as variáveis no processo.

A imagem utiliza Java 17 e executa com usuário sem privilégios de root.

### Kubernetes

O manifesto em `k8s/deployment.yaml` declara:

- Duas réplicas.
- Atualização gradual.
- Variáveis obtidas de `nexus-auth-secret`.
- Probes de inicialização, prontidão e vida.
- Limites de CPU e memória.
- Service do tipo `LoadBalancer`.

Imagem, namespace, segredos, TLS e conectividade precisam corresponder ao ambiente utilizado.

## Diagnóstico de problemas

| Sintoma | Verificações |
| --- | --- |
| Login retorna credencial inválida | E-mail, senha e existência do método `PASSWORD` |
| Login retorna acesso negado | Status do perfil e combinação de tipo/canal |
| Login retorna `429` | Cota geral por IP, cota de e-mail + IP e `Retry-After` |
| Solicitação de código retorna `429` | Cota compartilhada entre cadastro e recuperação |
| Google retorna `401` | ID Token, validade e audiência configurada |
| Google retorna `503` | Client IDs e disponibilidade do verificador |
| Cadastro Google retorna `428` | Completar cadastro com o `googleTicket` |
| OTP é rejeitado | ID correto, prazo do fluxo e número de tentativas |
| Cadastro falha ao localizar endereço | Dados informados e resposta da geocodificação |
| Geocodificação retorna `503` | Credencial, conectividade e fornecedor |
| Refresh falha após chamadas simultâneas | Reutilização do mesmo token e coordenação entre clientes/abas |
| Refresh retorna `429` | Cota de renovação por IP e cota geral |
| Muitos usuários recebem `429` juntos | IP compartilhado ou configuração dos proxies confiáveis |
| Sessão deixa de renovar após reinício do Redis | Persistência e integridade das chaves de sessão |
| Navegador bloqueia a chamada | Origem exata em `CORS_ALLOWED_ORIGINS` e preflight |
| Administração retorna `503` | Configuração de `ADMIN_API_KEY` |
| Inicialização falha na validação JPA | Schema e migrações do NexUs-DB |
| Código não chega por e-mail | Brevo, remetente, conectividade e pasta de spam |

### Formato de erros da aplicação

Exemplo:

```json
{
  "code": "INVALID_AUTHENTICATION",
  "message": "Credencial inválida ou expirada",
  "timestamp": "<instante ISO-8601>"
}
```

| HTTP | Código | Situação |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | Dados de negócio inválidos |
| `400` | `VALIDATION_ERROR` | Validação dos campos |
| `401` | `INVALID_AUTHENTICATION` | Credencial, OTP, refresh ou ticket de reset inválido |
| `403` | `ACCESS_DENIED` | Perfil indisponível ou canal não permitido |
| `409` | `RESOURCE_CONFLICT` | E-mail, CNPJ ou identidade em uso |
| `429` | `RATE_LIMIT_EXCEEDED` | Limite de requisições atingido; observar `Retry-After` |
| `503` | `GOOGLE_AUTH_UNAVAILABLE` | Autenticação Google não configurada |
| `503` | `GEOCODING_UNAVAILABLE` | Falha no serviço de localização |

Erros de validação retornam `fields` em lugar de uma mensagem única.

Nem todas as falhas passam pelo mesmo tratamento: rejeições em filtros de segurança, erros administrativos e exceções sem handler próprio podem utilizar outro formato.

As respostas de rate limit produzidas pelo filtro não contêm `timestamp`; as produzidas pelo handler do controller contêm esse campo.

Falhas de envio de e-mail não possuem um mapeamento específico no `ApiExceptionHandler`.

## Limitações conhecidas que não esbarram no cumprimento de requisitos

As seguintes limitações são consideradas na operação e evolução do serviço:

1. **Revogação após redefinição de senha:** os refresh tokens são revogados, mas os access tokens existentes não são todos invalidados imediatamente, mesmo que tenham expiração curta.

2. **Autorização dos consumidores:** mudanças de status e revogações não são consideradas pelos serviços que recebem os JWTs; validar apenas a assinatura não verifica todo o estado atual da conta.

3. **Rate limit por instância:** as cotas são locais, desaparecem após reinício e não são compartilhadas entre réplicas. Usuários no mesmo IP compartilham os limites por IP.

## Licença

Distribuído sob a licença **MIT**, conforme o arquivo `LICENSE`.

Copyright © 2026 NexUs.
