# tc-oficina-lambda

Function Serverless de **autenticação por CPF** para a aplicação de oficina mecânica **tc-oficina**.

## Escopo

A Lambda exposta via **API Gateway** recebe o CPF do cliente e:

1. **Valida o CPF** (dígitos verificadores)
2. **Consulta a base de dados** (RDS PostgreSQL) verificando a existência e o status do cliente
3. **Gera um token JWT** (HS256) válido para consumo das APIs protegidas da aplicação

> A aplicação principal vive em [tc-oficina-app](../tc-oficina-app), o banco em [tc-oficina-infra-db](../tc-oficina-infra-db).

## Estrutura

```
tc-oficina-lambda/
├── src/main/java/br/com/fiap/lambda/
│   ├── AuthHandler.java          # Handler da Lambda (API Gateway)
│   ├── CpfValidator.java         # Validação de CPF
│   └── service/
│       ├── ClienteService.java   # Consulta ao banco (JDBC)
│       └── JwtService.java       # Geração do JWT (jjwt, mesma secret da app)
├── template.yaml                 # SAM: Lambda + API Gateway
├── pom.xml                       # Java 21, Maven, shade plugin
└── .github/workflows/ci.yml      # CI/CD (build + SAM deploy)
```

## Tecnologias

- Java 21
- AWS Lambda (runtime `java21`)
- AWS API Gateway
- AWS SAM CLI
- PostgreSQL (JDBC)
- jjwt 0.12.6 (mesma versão da aplicação principal)

## Pré-requisitos

- JAR gerado com `mvn clean package`
- Secrets no GitHub: `JWT_SECRET` (mesma da aplicação), `DB_URL`, `DB_USER`, `DB_PASSWORD`
- Vars no GitHub: `AWS_REGION`

## Como executar localmente

```bash
mvn clean package

# Invocar com evento de exemplo (requer env vars configuradas)
sam local start-api \
  --parameter-overrides JwtSecret=... DbUrl=... DbUser=... DbPassword=...
```

## Teste com curl (após deploy)

```bash
curl -X POST https://<api-id>.execute-api.<region>.amazonaws.com/Prod/auth \
  -H "Content-Type: application/json" \
  -d '{"cpf":"17861341011"}'
```

**Response:**
```json
{
  "cpf": "17861341011",
  "status": "ATIVO",
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

## CI/CD

O workflow em `.github/workflows/ci.yml`:

1. `mvn clean verify` (build + testes) em qualquer push/PR para `main`
2. Deploy via **SAM** automático na branch `homologacao`
3. Deploy via **SAM** automático na branch `main` (produção)

## Diagrama da Arquitetura

```mermaid
flowchart LR
  CLIENTE[Cliente] -->|POST /auth {cpf}| GW[API Gateway]
  GW -->|evento| LAMBDA[Auth Lambda]
  LAMBDA -->|consulta status| DB[(RDS PostgreSQL)]
  LAMBDA -->|gera JWT| GW
  GW -->|200 token| CLIENTE
  CLIENTE -->|Bearer JWT| APP[API tc-oficina-app]

  style LAMBDA fill:#fff3e0,stroke:#e65100
```
