package br.com.fiap.lambda.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class ClienteService {

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public ClienteService() {
        this.dbUrl = System.getenv("DB_URL");
        this.dbUser = System.getenv("DB_USER");
        this.dbPassword = System.getenv("DB_PASSWORD");
    }

    public Optional<String> findNomeByCpf(String cpf) {
        if (dbUrl == null) {
            throw new IllegalStateException("DB_URL nao configurado na Lambda");
        }

        String sql = "SELECT nome FROM clientes WHERE value = ?";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cpf);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("nome"));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao consultar cliente na base de dados", e);
        }

        return Optional.empty();
    }
}
