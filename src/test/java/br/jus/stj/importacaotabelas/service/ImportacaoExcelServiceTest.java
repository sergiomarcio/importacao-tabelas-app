package br.jus.stj.importacaotabelas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Map;
import br.jus.stj.importacaotabelas.repository.TabelaServidorSciRepository;

import javax.persistence.EntityManager;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.web.server.ResponseStatusException;

class ImportacaoExcelServiceTest {
    @TempDir Path pasta;
    ImportacaoExcelService service;
    Connection connection;
    Statement comando;
    PreparedStatement insert;
    TabelaServidorSciRepository cadastro;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() throws Exception {
        service = new ImportacaoExcelService();
        cadastro = mock(TabelaServidorSciRepository.class);
        configurar("tabelaServidorSciRepository", cadastro);
        when(cadastro.atualizarDataImportacao(anyString(), any())).thenReturn(1);
        configurar("ambienteMaquina", new AmbienteMaquina("STJ220579"));
        EntityManager manager = mock(EntityManager.class);
        Session session = mock(Session.class);
        connection = mock(Connection.class);
        comando = mock(Statement.class);
        insert = mock(PreparedStatement.class);
        PreparedStatement consulta = mock(PreparedStatement.class);
        ResultSet colunas = mock(ResultSet.class);
        ResultSet tipos = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        configurar("entityManager", manager);
        configurar("diretorio", pasta.toString());
        when(manager.unwrap(Session.class)).thenReturn(session);
        when(session.doReturningWork(any())).thenAnswer(invocation ->
                ((ReturningWork<Map<String, Object>>) invocation.getArgument(0)).execute(connection));
        when(connection.prepareStatement(startsWith("SELECT c.name"))).thenReturn(consulta);
        when(connection.prepareStatement(startsWith("INSERT INTO"))).thenReturn(insert);
        when(consulta.executeQuery()).thenReturn(colunas);
        when(colunas.next()).thenReturn(true, true, false);
        when(colunas.getString(1)).thenReturn("id", "codigo");
        when(colunas.getBoolean(2)).thenReturn(true, false);
        when(colunas.getBoolean(5)).thenReturn(false);
        when(connection.createStatement()).thenReturn(comando);
        when(comando.executeQuery(anyString())).thenReturn(tipos);
        when(tipos.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnType(anyInt())).thenReturn(Types.VARCHAR);
    }

    @Test
    void importaTodasAsAbasPreservandoTextoEIdentity() throws Exception {
        criarArquivo("codigo");
        Map<String, Object> resultado = service.importarTabela("pessoas");
        assertEquals(2L, resultado.get("registrosImportados"));
        assertNotNull(resultado.get("dataAtualizacao"));
        verify(cadastro).atualizarDataImportacao(org.mockito.ArgumentMatchers.eq("pessoas"), any());
        verify(insert, times(2)).setString(2, "00123");
        verify(insert, times(2)).addBatch();
        InOrder ordem = inOrder(comando, insert);
        ordem.verify(comando).executeQuery(anyString());
        ordem.verify(comando).executeUpdate("TRUNCATE TABLE [sgp].[dbo].[pessoas]");
        ordem.verify(comando).execute("SET IDENTITY_INSERT [sgp].[dbo].[pessoas] ON");
        ordem.verify(insert).executeBatch();
        ordem.verify(comando).execute("SET IDENTITY_INSERT [sgp].[dbo].[pessoas] OFF");
    }

    @Test
    void rejeitaColunaDesconhecidaSemTruncate() throws Exception {
        criarArquivo("inexistente");
        assertThrows(ResponseStatusException.class, () -> service.importarTabela("pessoas"));
        verify(comando, never()).executeUpdate(anyString());
    }

    @Test
    void desativaIdentityEPropagaFalhaDeInsercao() throws Exception {
        criarArquivo("codigo");
        when(insert.executeBatch()).thenThrow(new SQLException("Falha de conversao"));
        assertThrows(SQLException.class, () -> service.importarTabela("pessoas"));
        verify(cadastro, never()).atualizarDataImportacao(anyString(), any());
        verify(comando).execute("SET IDENTITY_INSERT [sgp].[dbo].[pessoas] OFF");
        assertNotNull(ImportacaoExcelService.class.getMethod("importarTabela", String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void rejeitaArquivoAusenteENomeInvalido() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.importarTabela("pessoas")).getStatus().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.importarTabela("../pessoas")).getStatus().value());
        verifyNoInteractions(connection);
    }

    @Test
    void importaParaServidorSciEmPalmas() throws Exception {
        configurar("ambienteMaquina", new AmbienteMaquina("palmas"));
        criarArquivo("codigo");
        service.importarTabela("pessoas");
        verify(comando).executeUpdate("TRUNCATE TABLE [servidor_sci].[dbo].[pessoas]");
        verify(connection).prepareStatement(contains("FROM [servidor_sci].sys.columns"));
        verify(connection).prepareStatement("INSERT INTO [servidor_sci].[dbo].[pessoas] ([id], [codigo]) VALUES (?, ?)");
        verify(comando).execute("SET IDENTITY_INSERT [servidor_sci].[dbo].[pessoas] OFF");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"STJ220579, Integra", "PALMAS, Integra", "PALMAS, iNtEgRa"})
    void importaGrupoIntegraParaSgp(String maquina, String grupo) throws Exception {
        configurar("ambienteMaquina", new AmbienteMaquina(maquina));
        br.jus.stj.importacaotabelas.entidade.TabelaServidorSci tabela =
                new br.jus.stj.importacaotabelas.entidade.TabelaServidorSci();
        tabela.setGrupo(" " + grupo + " ");
        when(cadastro.findAllByNomeTabela("pessoas")).thenReturn(tabela);
        criarArquivo("codigo");

        assertEquals(2L, service.importarTabela("pessoas").get("registrosImportados"));
        verify(comando).executeUpdate("TRUNCATE TABLE [sgp].[dbo].[pessoas]");
        verify(connection).prepareStatement(contains("FROM [sgp].sys.columns"));
        verify(connection).prepareStatement("INSERT INTO [sgp].[dbo].[pessoas] ([id], [codigo]) VALUES (?, ?)");
        verify(comando).execute("SET IDENTITY_INSERT [sgp].[dbo].[pessoas] OFF");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"STJ220579", "PALMAS"})
    void efeitoFinanceiroRemoveSomenteFolhasAbertasEInsereAusentes(String maquina) throws Exception {
        configurar("ambienteMaquina", new AmbienteMaquina(maquina));
        String banco = "PALMAS".equals(maquina) ? "servidor_sci" : "sgp";
        PreparedStatement consulta = connection.prepareStatement("SELECT c.name");
        ResultSet colunas = consulta.executeQuery();
        when(colunas.getString(1)).thenReturn("seq_efeito_fin", "codigo");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet aba = workbook.createSheet();
            org.apache.poi.ss.usermodel.Row header = aba.createRow(0);
            header.createCell(0).setCellValue("seq_efeito_fin");
            header.createCell(1).setCellValue("codigo");
            for (int i = 1; i <= 3; i++) {
                org.apache.poi.ss.usermodel.Row linha = aba.createRow(i);
                linha.createCell(0).setCellValue(i == 1 ? "1" : "2");
                linha.createCell(1).setCellValue("00123");
            }
            try (java.io.OutputStream saida = Files.newOutputStream(pasta.resolve("efeito_financeiro.xlsx"))) {
                workbook.write(saida);
            }
        }
        ResultSet existentes = mock(ResultSet.class);
        String tabela = "[" + banco + "].[dbo].[efeito_financeiro]";
        PreparedStatement verificar = mock(PreparedStatement.class);
        String sqlVerificar = "SELECT seq_efeito_fin FROM " + tabela + " WITH (UPDLOCK, HOLDLOCK) WHERE seq_efeito_fin = ?";
        when(connection.prepareStatement(sqlVerificar)).thenReturn(verificar);
        when(verificar.executeQuery()).thenReturn(existentes);
        when(existentes.next()).thenReturn(true, false, false);
        when(existentes.getString(1)).thenReturn("1");
        String delete = "DELETE ef FROM " + tabela + " ef "
                + "WHERE EXISTS (SELECT 1 FROM [" + banco + "].[dbo].[folha_pagamento] fp "
                + "WHERE fp.seq_folha_pgmto = ef.seq_folha_assoc AND fp.ind_st_folha <> '2')";
        when(comando.executeUpdate(delete)).thenReturn(3);

        Map<String, Object> resposta = service.importarTabela("efeito_financeiro");

        assertEquals(1L, resposta.get("registrosImportados"));
        assertEquals(3, resposta.get("registrosRemovidos"));
        verify(comando, never()).executeUpdate(startsWith("TRUNCATE"));
        verify(insert).setString(1, "2");
        verify(insert, times(1)).addBatch();
        InOrder ordem = inOrder(comando, insert);
        ordem.verify(comando).executeUpdate(delete);
        verify(verificar).setString(1, "1");
        verify(verificar).setString(1, "2");
        ordem.verify(insert).executeBatch();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"STJ220579", "PALMAS"})
    void importaArquivoTeletrabalhoParaSgpNasDuasMaquinas(String maquina) throws Exception {
        configurar("ambienteMaquina", new AmbienteMaquina(maquina));
        criarArquivo("codigo");
        String nomeTabela = "historico_TBL_TELETRABALHO_servidor";
        Files.move(pasta.resolve("pessoas.xlsx"), pasta.resolve(nomeTabela + ".xlsx"));

        assertEquals(2L, service.importarTabela(nomeTabela).get("registrosImportados"));
        verify(comando).executeUpdate("TRUNCATE TABLE [sgp].[dbo].[" + nomeTabela + "]");
        verify(connection).prepareStatement(contains("FROM [sgp].sys.columns"));
        verify(connection).prepareStatement("INSERT INTO [sgp].[dbo].[" + nomeTabela
                + "] ([id], [codigo]) VALUES (?, ?)");
    }

    @Test
    void importaMaisLinhasQueOCacheEmLotesLimitados() throws Exception {
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook workbook = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
            org.apache.poi.ss.usermodel.Sheet aba = workbook.createSheet();
            org.apache.poi.ss.usermodel.Row cabecalho = aba.createRow(0);
            cabecalho.createCell(0).setCellValue("id");
            cabecalho.createCell(1).setCellValue("codigo");
            for (int i = 1; i <= 1200; i++) {
                org.apache.poi.ss.usermodel.Row linha = aba.createRow(i);
                linha.createCell(0).setCellValue(Integer.toString(i));
                linha.createCell(1).setCellValue("00123");
            }
            try (java.io.OutputStream saida = Files.newOutputStream(pasta.resolve("pessoas.xlsx"))) {
                workbook.write(saida);
            } finally { workbook.dispose(); }
        }
        assertEquals(1200L, service.importarTabela("pessoas").get("registrosImportados"));
        verify(insert, times(1200)).addBatch();
        verify(insert, times(3)).executeBatch();
        verify(insert, times(2)).clearBatch();
    }

    private void criarArquivo(String segundaColuna) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            for (int i = 0; i < 2; i++) {
                org.apache.poi.ss.usermodel.Sheet aba = workbook.createSheet("Registros " + i);
                org.apache.poi.ss.usermodel.Row cabecalho = aba.createRow(0);
                cabecalho.createCell(0).setCellValue("id");
                cabecalho.createCell(1).setCellValue(segundaColuna);
                org.apache.poi.ss.usermodel.Row linha = aba.createRow(1);
                linha.createCell(0).setCellValue(Integer.toString(i + 1));
                linha.createCell(1).setCellValue("00123");
            }
            try (java.io.OutputStream saida = Files.newOutputStream(pasta.resolve("pessoas.xlsx"))) {
                workbook.write(saida);
            }
        }
    }

    private void configurar(String nome, Object valor) throws Exception {
        Field field = ImportacaoExcelService.class.getDeclaredField(nome);
        field.setAccessible(true);
        field.set(service, valor);
    }
}
