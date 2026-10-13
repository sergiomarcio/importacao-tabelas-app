package br.jus.stj.importacaotabelas.service;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AmbienteMaquina {
    private final String nome;

    public AmbienteMaquina() {
        this(nomeLocal());
    }

    AmbienteMaquina(String nome) {
        this.nome = nome == null ? "" : nome.trim().split("\\.", 2)[0];
    }

    String bancoImportacao() {
        if ("STJ220579".equalsIgnoreCase(nome)) return "sgp";
        if ("PALMAS".equalsIgnoreCase(nome)) return "servidor_sci";
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Importacao nao configurada para a maquina " + nome);
    }

    String bancoImportacao(String nomeArquivo) {
        String banco = bancoImportacao();
        if (nomeArquivo != null && nomeArquivo.toLowerCase(java.util.Locale.ROOT).contains("tbl_teletrabalho")) {
            return "sgp";
        }
        return banco;
    }

    public String getNome() { return nome; }

    public boolean exportacaoPermitida() {
        return "STJ220579".equalsIgnoreCase(nome);
    }

    void validarExportacao() {
        if (!exportacaoPermitida()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Exportacao para Excel permitida somente na maquina STJ220579. Maquina atual: " + nome);
        }
    }
    private static String nomeLocal() {
        String computador = System.getenv("COMPUTERNAME");
        if (computador != null && !computador.trim().isEmpty()) return computador;
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException erro) {
            throw new IllegalStateException("Nao foi possivel identificar a maquina", erro);
        }
    }
}

