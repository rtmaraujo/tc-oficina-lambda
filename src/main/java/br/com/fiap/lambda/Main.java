package br.com.fiap.lambda;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static AuthHandler newAuthHandler() {
        final String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET nao configurado no ambiente");
        }
        return new AuthHandler();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/health", Main::health);
        server.createContext("/auth", Main::auth);
        server.start();

        log.info("TC Oficina Auth service listening on port {}", port);
    }

    private static void health(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"status\":\"UP\"}");
    }

    private static void auth(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"erro\":\"Metodo nao permitido\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
            request.setBody(body);

            APIGatewayProxyResponseEvent response = newAuthHandler().handleRequest(request, new NoopContext());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            respond(exchange, response.getStatusCode(), response.getBody());
        } catch (Throwable t) {
            log.error("Falha interna no servico de auth", t);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            respond(exchange, 500, "{\"erro\":\"Falha interna no servico de auth\"}");
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final class NoopContext implements Context {

        @Override
        public String getAwsRequestId() {
            return "local";
        }

        @Override
        public String getLogGroupName() {
            return "/local/auth";
        }

        @Override
        public String getLogStreamName() {
            return "local";
        }

        @Override
        public String getFunctionName() {
            return "tc-oficina-auth";
        }

        @Override
        public String getFunctionVersion() {
            return "container";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "local";
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 10000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 512;
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                    System.out.println(message);
                }

                @Override
                public void log(byte[] message) {
                    System.out.println(new String(message, StandardCharsets.UTF_8));
                }
            };
        }
    }
}