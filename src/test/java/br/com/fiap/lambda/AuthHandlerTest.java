package br.com.fiap.lambda;

import br.com.fiap.lambda.service.ClienteService;
import br.com.fiap.lambda.service.JwtService;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthHandlerTest {

    private static final String SECRET = "teste-secret-para-jwt-com-mais-de-32-caracteres";
    private static final String CPF_VALIDO = "12345678909";

    private AuthHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthHandler(new StubClienteService(Optional.of("Joao da Silva")), new JwtService(SECRET));
    }

    @Test
    @DisplayName("deve retornar 400 para CPF invalido")
    void deveRetornar400ParaCpfInvalido() {
        APIGatewayProxyRequestEvent request = request("{\"cpf\":\"123\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, new ContextStub());

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("CPF invalido"));
    }

    @Test
    @DisplayName("deve retornar 400 para body ausente")
    void deveRetornar400ParaBodyAusente() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        APIGatewayProxyResponseEvent response = handler.handleRequest(request, new ContextStub());

        assertEquals(400, response.getStatusCode());
    }

    @Test
    @DisplayName("deve retornar 400 para CPF repetido")
    void deveRetornar400ParaCpfRepetido() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(request("{\"cpf\":\"11111111111\"}"), new ContextStub());

        assertEquals(400, response.getStatusCode());
    }

    @Test
    @DisplayName("deve retornar 404 quando cliente nao existe na base")
    void deveRetornar404QuandoClienteNaoEncontrado() {
        AuthHandler handler404 = new AuthHandler(new StubClienteService(Optional.empty()), new JwtService(SECRET));
        APIGatewayProxyResponseEvent response = handler404.handleRequest(request("{\"cpf\":\"" + CPF_VALIDO + "\"}"), new ContextStub());

        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("nao encontrado"));
    }

    @Test
    @DisplayName("deve retornar 500 quando a consulta ao banco falha")
    void deveRetornar500QuandoBancoFalha() {
        AuthHandler handler500 = new AuthHandler(
                new StubClienteService(new IllegalStateException("timeout")),
                new JwtService(SECRET));

        APIGatewayProxyResponseEvent response = handler500.handleRequest(request("{\"cpf\":\"" + CPF_VALIDO + "\"}"), new ContextStub());

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Erro interno"));
    }

    @Test
    @DisplayName("deve retornar 200 com token JWT valido")
    void deveRetornar200ComTokenValido() {
        APIGatewayProxyResponseEvent response = handler.handleRequest(request("{\"cpf\":\"" + CPF_VALIDO + "\"}"), new ContextStub());

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"cpf\":\"" + CPF_VALIDO + "\""));
        assertTrue(response.getBody().contains("Joao da Silva"));
        assertTrue(response.getBody().contains("\"status\":\"ATIVO\""));
        assertTrue(response.getBody().contains("\"token_type\":\"Bearer\""));
        assertTrue(response.getBody().contains("\"expires_in\":86400"));

        String token = extractToken(response.getBody());
        assertNotNull(token);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        assertEquals(CPF_VALIDO, subject);
    }

    private String extractToken(String body) {
        Matcher matcher = Pattern.compile("\"access_token\":\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "corpo da resposta deveria conter access_token");
        return matcher.group(1);
    }

    private APIGatewayProxyRequestEvent request(String body) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setBody(body);
        return event;
    }

    private static final class StubClienteService extends ClienteService {
        private final Optional<String> nome;
        private final RuntimeException failure;

        private StubClienteService(Optional<String> nome) {
            this.nome = nome;
            this.failure = null;
        }

        private StubClienteService(RuntimeException failure) {
            this.nome = null;
            this.failure = failure;
        }

        @Override
        public Optional<String> findNomeByCpf(String cpf) {
            if (failure != null) {
                throw failure;
            }
            return nome;
        }
    }

    private static final class ContextStub implements Context {
        @Override
        public String getAwsRequestId() {
            return "test-request-id";
        }

        @Override
        public String getLogGroupName() {
            return "/test/auth";
        }

        @Override
        public String getLogStreamName() {
            return "test";
        }

        @Override
        public String getFunctionName() {
            return "tc-oficina-auth";
        }

        @Override
        public String getFunctionVersion() {
            return "test";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "test";
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
                }

                @Override
                public void log(byte[] message) {
                }
            };
        }
    }
}
