# tc-oficina-lambda

Function Serverless de **autenticação por CPF** para a aplicação de oficina mecânica **tc-oficina**, exposta via **API Gateway**.

## Escopo

A Lambda, integrada ao **API Gateway**, recebe o CPF do cliente e:

1. **Valida o CPF** (dígitos verificadores)
2. **Consulta a base de dados** (RDS PostgreSQL) verificando a existência do cliente
3. **Gera um token JWT** (HS256, mesma secret da aplicação) válido para consumo das APIs protegidas

> A aplicação principal vive em [tc-oficina-app](https://github.com/rtmaraujo/tc-oficina-app), a infraestrutura do banco em [tc-oficina-infra-db](https://github.com/rtmaraujo/tc-oficina-infra-db) e o cluster k3s em [tc-oficina-infra-k8s](https://github.com/rtmaraujo/tc-oficina-infra-k8s).

## Endpoints

**Produção** (stack `tc-oficina-auth`):
```
POST https://8rfjx5ofoi.execute-api.us-west-2.amazonaws.com/Prod/auth
```

**Homologação** (stack `tc-oficina-auth-homolog`):
```
POST https://6116yqil7i.execute-api.us-west-2.amazonaws.com/Prod/auth
```

**Container (modo k3s) — entrada canônica via API Gateway Traefik:**
```
POST http://35.84.122.229/auth    (produção)
POST http://35.84.122.229:8081/auth (homologação)
```

> O container auth no k3s é exposto **apenas** pelo API Gateway Traefik (`/auth` e `/health`),
> ponto único de entrada de todas as requisições da aplicação.

**Request:**
```bash
curl -X POST https://8rfjx5ofoi.execute-api.us-west-2.amazonaws.com/Prod/auth \
  -H "Content-Type: application/json" \
  -d '{"cpf":"12345678909"}'
```

**Response:**
```json
{
  "cpf": "12345678909",
  "nome": "Cliente Teste Lambda",
  "status": "ATIVO",
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 86400
}
```

## Estrutura

```
tc-oficina-lambda/
├── src/main/java/br/com/fiap/lambda/
│   ├── AuthHandler.java          # Handler da Lambda (API Gateway Proxy)
│   ├── CpfValidator.java         # Validação de CPF
│   ├── Main.java                 # Modo container (execução local / k3s)
│   └── service/
│       ├── ClienteService.java   # Consulta ao banco (JDBC)
│       └── JwtService.java       # Geração do JWT (jjwt, mesma secret da app)
├── template.yaml                 # AWS SAM: Lambda + API Gateway + VPC
├── Dockerfile                    # Build da imagem container (modo local/k3s)
├── pom.xml                       # Java 21, Maven, shade plugin
└── .github/workflows/ci.yml      # CI/CD (build + deploy SAM + smoke test)
```

## Tecnologias

- Java 21
- AWS Lambda (runtime `java21`)
- AWS API Gateway
- AWS SAM / CloudFormation
- PostgreSQL (JDBC, dentro da VPC do RDS)
- jjwt 0.12.6 (mesma versão da aplicação principal)

## Requisitos / Prerequisitos

- JAR gerado com `mvn clean package` (shade)
- Secrets no GitHub: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`
- Vars no GitHub: `AWS_REGION`, `VPC_ID`, `SUBNET_A`, `SUBNET_B`, `LAMBDA_ROLE_ARN`

## Deploy

O CI/CD em `.github/workflows/ci.yml`:

1. `mvn clean verify` (build + testes) em push/PR
2. `docker-build`: build e push da imagem container para o ECR (modo local/k3s)
3. `deploy-lambda`: empacota o jar via SAM e faz deploy com `aws cloudformation deploy`:
   - Branch `homologacao` → stack `tc-oficina-auth-homolog`
   - Branch `main` → stack `tc-oficina-auth` (produção)
4. `deploy-homologacao` / `deploy-producao`: renderiza e aplica manifestos `auth-*` do
   `tc-oficina-infra-k8s` no k3s (deployment, service ClusterIP, ingressroute do API Gateway Traefik):
   - Branch `homologacao` → namespace `tc-oficina-homolog` (entrypoint `web-homolog`)
   - Branch `main` → namespace `tc-oficina` (entrypoint `web`)
5. Smoke tests: `POST /auth` via API Gateway AWS (espera 200) e `GET /health` via API Gateway Traefik

A Lambda roda dentro da **VPC do RDS** (subnets privadas), acessando o PostgreSQL pelo SG dedicado. Nomes de recursos derivam do `AWS::StackName` para suportar os dois ambientes (`tc-oficina-auth` / `tc-oficina-auth-homolog`).

## Execução local (container)

```bash
mvn clean package
JWT_SECRET=... DB_URL=jdbc:postgresql://... DB_USER=... DB_PASSWORD=... \
  java -cp target/tc-oficina-lambda.jar br.com.fiap.lambda.Main
# POST http://localhost:8080/auth  |  GET http://localhost:8080/health
```

## Diagrama da Arquitetura

```mermaid
flowchart LR
  CLIENTE[Cliente] -->|POST /auth {cpf}| GW[API Gateway]
  GW -->|invoca| LAMBDA[Auth Lambda - Java 21]
  LAMBDA -->|JDBC consulta| DB[(RDS PostgreSQL)]
  LAMBDA -->|gera JWT HS256| GW
  GW -->|200 token| CLIENTE
  CLIENTE -->|Bearer JWT| APP[API tc-oficina-app no k3s]

  style LAMBDA fill:#fff3e0,stroke:#e65100
```
