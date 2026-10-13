package br.jus.stj.importacaotabelas.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConexaoIntegra {
    @Value("${exportacao.integra.url}")
    private String url;
    @Value("${exportacao.integra.username}")
    private String username;
    @Value("${exportacao.integra.password}")
    private String password;

    public Connection abrir() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
