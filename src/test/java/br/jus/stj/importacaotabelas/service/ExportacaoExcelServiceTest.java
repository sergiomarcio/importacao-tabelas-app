package br.jus.stj.importacaotabelas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Map;

import javax.persistence.EntityManager;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class ExportacaoExcelServiceTest {
    @TempDir
    Path pasta;

    @Test
    void rejeitaNomeInvalidoAntesDeAcessarBancoOuArquivo() throws Exception {
        ExportacaoExcelService service = new ExportacaoExcelService();
        configurar(service, "ambienteMaquina", new AmbienteMaquina("STJ220579"));
        assertThrows(ResponseStatusException.class, () -> service.exportarTabela("../tabela"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void substituiArquivoComCabecalhoEPreservaZerosEPrecisao() throws Exception {
        ExportacaoExcelService service = new ExportacaoExcelService();
        configurar(service, "ambienteMaquina", new AmbienteMaquina("STJ220579"));
        EntityManager manager = mock(EntityManager.class);
        Session session = mock(Session.class);
        Connection connection = mock(Connection.class);
        Statement consulta = mock(Statement.class);
        ResultSet registros = mock(ResultSet.class);
        ResultSetMetaData metadados = mock(ResultSetMetaData.class);
        configurar(service, "entityManager", manager);
        configurar(service, "diretorio", pasta.toString());
        when(manager.unwrap(Session.class)).thenReturn(session);
        when(session.doReturningWork(any())).thenAnswer(invocation ->
                ((ReturningWork<Map<String, Object>>) invocation.getArgument(0)).execute(connection));
        when(connection.createStatement()).thenReturn(consulta);
        when(consulta.executeQuery("SELECT * FROM [servidor_sci].[dbo].[pessoas]")).thenReturn(registros);
        when(registros.getMetaData()).thenReturn(metadados);
        when(metadados.getColumnCount()).thenReturn(2);
        when(metadados.getColumnLabel(1)).thenReturn("codigo");
        when(metadados.getColumnLabel(2)).thenReturn("valor");
        when(registros.next()).thenReturn(true, false);
        when(registros.getString(1)).thenReturn("00123");
        when(registros.getString(2)).thenReturn("12345678901234567890.12");
        Path destino = pasta.resolve("pessoas.xlsx");
        Files.writeString(destino, "arquivo antigo");

        Map<String, Object> resposta = service.exportarTabela("pessoas");

        assertEquals(1L, resposta.get("registrosExportados"));
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(destino))) {
            assertEquals("codigo", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("00123", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("12345678901234567890.12",
                    workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
        try (java.util.stream.Stream<Path> arquivos = Files.list(pasta)) {
            assertEquals(1, arquivos.count());
        }
    }

    @Test
    void bloqueiaExportacaoEmPalmasAntesDeAcessarBancoOuArquivo() throws Exception {
        ExportacaoExcelService service = new ExportacaoExcelService();
        configurar(service, "ambienteMaquina", new AmbienteMaquina("STJ220579"));
        configurar(service, "ambienteMaquina", new AmbienteMaquina("PALMAS.stj"));
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.exportarTabela("efeito_financeiro")).getStatus().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.exportarTabela("pessoas")).getStatus().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Telesystem", "Integra", " integra "})
    void exportaGrupoPelaConexaoSeparadaNoBancoSgp(String grupo) throws Exception {
        ExportacaoExcelService service = new ExportacaoExcelService();
        configurar(service, "ambienteMaquina", new AmbienteMaquina("STJ220579"));
        EntityManager manager = mock(EntityManager.class);
        configurar(service, "entityManager", manager);
        configurar(service, "diretorio", pasta.toString());
        br.jus.stj.importacaotabelas.repository.TabelaServidorSciRepository repository =
                mock(br.jus.stj.importacaotabelas.repository.TabelaServidorSciRepository.class);
        br.jus.stj.importacaotabelas.entidade.TabelaServidorSci tabela =
                new br.jus.stj.importacaotabelas.entidade.TabelaServidorSci();
        tabela.setGrupo(grupo);
        when(repository.findAllByNomeTabela("pessoas")).thenReturn(tabela);
        configurar(service, "tabelaServidorSciRepository", repository);
        ConexaoTelesystem conexao = mock(ConexaoTelesystem.class);
        Connection connection = mock(Connection.class);
        Statement consulta = mock(Statement.class);
        ResultSet registros = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        configurar(service, "conexaoTelesystem", conexao);
        ConexaoIntegra integra = mock(ConexaoIntegra.class);
        configurar(service, "conexaoIntegra", integra);
        if ("telesystem".equalsIgnoreCase(grupo.trim())) {
            when(conexao.abrir()).thenReturn(connection);
        } else {
            when(integra.abrir()).thenReturn(connection);
        }
        when(connection.createStatement()).thenReturn(consulta);
        when(consulta.executeQuery("SELECT * FROM [sgp].[dbo].[pessoas]")).thenReturn(registros);
        when(registros.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("codigo");
        when(registros.next()).thenReturn(true, false);
        when(registros.getString(1)).thenReturn("123");

        assertEquals(1L, service.exportarTabela("pessoas").get("registrosExportados"));
        verifyNoInteractions(manager);
        if ("telesystem".equalsIgnoreCase(grupo.trim())) {
            verifyNoInteractions(integra);
        } else {
            verifyNoInteractions(conexao);
        }
        verify(connection).close();
        verify(consulta).executeQuery("SELECT * FROM [sgp].[dbo].[pessoas]");
    }

    private void configurar(Object objeto, String nome, Object valor) throws Exception {
        if ("entityManager".equals(nome)) {
            Field repository = objeto.getClass().getDeclaredField("tabelaServidorSciRepository");
            repository.setAccessible(true);
            repository.set(objeto, mock(br.jus.stj.importacaotabelas.repository.TabelaServidorSciRepository.class));
        }
        Field field = objeto.getClass().getDeclaredField(nome);
        field.setAccessible(true);
        field.set(objeto, valor);
    }

    @Test
    @SuppressWarnings("unchecked")
    void criaPlanilhaEfeitoFinanceiroComFiltroDeVigencia() throws Exception {
        ExportacaoExcelService service = new ExportacaoExcelService();
        configurar(service, "ambienteMaquina", new AmbienteMaquina("STJ220579"));
        EntityManager manager = mock(EntityManager.class);
        Session session = mock(Session.class);
        Connection connection = mock(Connection.class);
        Statement consulta = mock(Statement.class);
        ResultSet registros = mock(ResultSet.class);
        ResultSetMetaData metadados = mock(ResultSetMetaData.class);
        configurar(service, "entityManager", manager);
        configurar(service, "diretorio", pasta.toString());
        when(manager.unwrap(Session.class)).thenReturn(session);
        when(session.doReturningWork(any())).thenAnswer(invocation ->
                ((ReturningWork<Map<String, Object>>) invocation.getArgument(0)).execute(connection));
        when(connection.createStatement()).thenReturn(consulta);
        String sql = "SELECT ef.* FROM [servidor_sci].[dbo].[EFEITO_FINANCEIRO] ef "
                + "WHERE EXISTS (SELECT 1 FROM [servidor_sci].[dbo].[folha_pagamento] fp "
                + "WHERE fp.seq_folha_pgmto = ef.seq_folha_assoc "
                + "AND fp.dt_inicio_vig >= CONVERT(date, '20250101', 112))";
        ResultSet folhas = mock(ResultSet.class);
        when(consulta.executeQuery("SELECT seq_folha_pgmto FROM [servidor_sci].[dbo].[folha_pagamento] WHERE ind_st_folha <> '2'"))
                .thenReturn(folhas);
        when(consulta.executeQuery(sql)).thenReturn(registros);
        when(registros.getMetaData()).thenReturn(metadados);
        when(metadados.getColumnCount()).thenReturn(2);
        when(metadados.getColumnLabel(1)).thenReturn("seq_efeito_fin");
        when(metadados.getColumnLabel(2)).thenReturn("seq_folha_assoc");
        when(registros.next()).thenReturn(true, false);
        when(registros.getString(1)).thenReturn("42");

        assertEquals(1L, service.exportarTabela("EFEITO_FINANCEIRO").get("registrosExportados"));
        verify(consulta).executeQuery(sql);
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                Files.newInputStream(pasta.resolve("EFEITO_FINANCEIRO.xlsx")))) {
            assertEquals("42", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
        }
    }
}
