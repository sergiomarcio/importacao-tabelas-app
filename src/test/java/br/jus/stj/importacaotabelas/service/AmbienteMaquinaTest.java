package br.jus.stj.importacaotabelas.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AmbienteMaquinaTest {
    @Test
    void permiteSomenteSTJ220579() {
        AmbienteMaquina ambiente = new AmbienteMaquina("stj220579.stj.jus.br");
        assertTrue(ambiente.exportacaoPermitida());
        assertDoesNotThrow(ambiente::validarExportacao);
    }
    @Test
    void bloqueiaPalmasEMaquinasDesconhecidas() {
        for (String nome : new String[] { "PALMAS", "OUTRA", "", null }) {
            AmbienteMaquina ambiente = new AmbienteMaquina(nome);
            assertFalse(ambiente.exportacaoPermitida());
            ResponseStatusException erro = assertThrows(ResponseStatusException.class, ambiente::validarExportacao);
            assertEquals(403, erro.getRawStatusCode());
        }
    }
}
