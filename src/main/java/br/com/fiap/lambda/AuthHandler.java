package br.com.fiap.lambda;

import br.com.fiap.lambda.service.ClienteService;
import br.com.fiap.lambda.service.JwtService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(AuthHandler.class);

    private final ClienteService clienteService;
    private final JwtService jwtService;

    public AuthHandler() {
        this(new ClienteService(), new JwtService(System.getenv("JWT_SECRET")));
    }

    AuthHandler(ClienteService clienteService, JwtService jwtService) {
        this.clienteService = clienteService;
        this.jwtService = jwtService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String cpf = extractCpf(input);
        if (cpf == null || !CpfValidator.isValidCpf(cpf)) {
            return error(400, "CPF invalido ou ausente");
        }

        Optional<String> status;
        try {
            status = clienteService.findNomeByCpf(cpf);
        } catch (Exception e) {
            log.error("Erro ao consultar cliente", e);
            return error(500, "Erro interno ao consultar cliente");
        }

        if (status.isEmpty()) {
            return error(404, "Cliente nao encontrado para o CPF informado");
        }

        String token = jwtService.generateToken(cpf, status.orElse("ATIVO"));

        Map<String, Object> body = new HashMap<>();
        body.put("cpf", cpf);
        body.put("nome", status.get());
        body.put("status", "ATIVO");
        body.put("access_token", token);
        body.put("token_type", "Bearer");
        body.put("expires_in", 86400);

        return json(200, body);
    }

    private String extractCpf(APIGatewayProxyRequestEvent input) {
        String body = input.getBody();
        if (body == null || body.isBlank()) {
            return null;
        }

        int start = body.indexOf("\"cpf\"");
        if (start < 0) {
            return null;
        }

        start = body.indexOf(':', start) + 1;
        int end = body.indexOf(',', start);
        if (end < 0) {
            end = body.indexOf('}', start);
        }
        if (end < 0) {
            return null;
        }

        return body.substring(start, end).replaceAll("[\\\"\\s]", "");
    }

    private APIGatewayProxyResponseEvent error(int statusCode, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", statusCode);
        body.put("erro", message);
        return json(statusCode, body);
    }

    private APIGatewayProxyResponseEvent json(int statusCode, Object body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setBody(toJson(body));
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        response.setHeaders(headers);
        return response;
    }

    private String toJson(Object obj) {
        if (obj instanceof String) {
            return (String) obj;
        }
        StringBuilder sb = new StringBuilder("{");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) obj;
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object value = e.getValue();
            if (value instanceof Number) {
                sb.append(value);
            } else {
                sb.append("\"").append(value).append("\"");
            }
        }
        return sb.append("}").toString();
    }
}
