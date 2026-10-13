package br.jus.stj.importacaotabelas.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConexaoTelesystem {
    @Value("${exportacao.telesystem.url}")
    private String url;
    @Value("${exportacao.telesystem.username}")
    private String username;
    @Value("${exportacao.telesystem.password}")
    private String password;

    public Connection abrir() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
