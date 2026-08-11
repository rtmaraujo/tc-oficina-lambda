# ============================================
# Stage 1: Builder - Compilar o jar (sem testes)
# ============================================
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B -q

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -q && \
    cp target/tc-oficina-lambda.jar target/auth.jar

# ============================================
# Stage 2: Runtime - Imagem final otimizada
# ============================================
FROM eclipse-temurin:21-jre-alpine

ARG BUILD_DATE
ARG COMMIT_SHA

LABEL org.opencontainers.image.title="TC Oficina Auth" \
      org.opencontainers.image.description="Servico de autenticacao via CPF (containerizado) - Tech Challenge Fase 3" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${COMMIT_SHA}"

WORKDIR /app

COPY --from=builder /app/target/auth.jar .

# Usuario non-root
RUN addgroup -g 1001 -S appuser && adduser -u 1001 -S appuser -G appuser
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

CMD ["java", "-cp", "auth.jar", "br.com.fiap.lambda.Main"]