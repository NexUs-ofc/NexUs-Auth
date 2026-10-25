O cadastro por senha e por Google recebe o endereço sem latitude ou longitude. As coordenadas são obtidas pelo servidor antes de salvar o perfil. Para COMPANY, o endereço é opcional e, quando informado, é salvo sem coordenadas e sem consultar a API.

Configure DISTANCEMATRIX_API_KEY no ambiente com uma chave do produto Geocoding API Accurate da sua conta DistanceMatrix.ai. O arquivo .env.example contém as variáveis da integração. A chave real deve ser configurada nas variáveis de ambiente do deploy ou nos arquivos locais de configuração já utilizados pela aplicação.

O endereço é consultado neste formato:

```http
GET https://api.distancematrix.ai/maps/api/geocode/json?address={rua}, {numero}, {bairro}, {cidade} - {UF}, {CEP}, Brasil&key={DISTANCEMATRIX_API_KEY}
```

Os parâmetros são codificados na URL automaticamente. Rua, número, bairro, cidade, UF e CEP são incluídos na consulta e salvos como dados do endereço. GEOCODING_BASE_URL permite trocar o endpoint; o valor padrão é https://api.distancematrix.ai, correspondente ao produto Accurate.

Quando não existe correspondência para o endereço, o cadastro retorna erro de dados inválidos. Falhas de comunicação, limites da API ou respostas inválidas retornam GEOCODING_UNAVAILABLE, sem expor a chave e sem gravar o cadastro parcialmente.

Exemplo do objeto address das requisições de cadastro:

```json
{
  "neighborhood": "Jardim Henriqueta",
  "street": "Estrada São Francisco",
  "number": "10",
  "cep": "06753000",
  "city": "Taboão da Serra",
  "state": "SP"
}
```
