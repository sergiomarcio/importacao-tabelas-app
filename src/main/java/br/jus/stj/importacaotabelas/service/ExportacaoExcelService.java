package br.jus.stj.importacaotabelas.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.jus.stj.importacaotabelas.entidade.TabelaServidorSci;
import br.jus.stj.importacaotabelas.repository.TabelaServidorSciRepository;

@Service
public class ExportacaoExcelService {
    private AmbienteMaquina ambienteMaquina = new AmbienteMaquina();
    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TabelaServidorSciRepository tabelaServidorSciRepository;

    @Autowired
    private ConexaoTelesystem conexaoTelesystem;

    @Autowired
    private ConexaoIntegra conexaoIntegra;

    @Value("${exportacao.excel.diretorio}")
    private String diretorio;

    @Transactional(readOnly = true)
    public Map<String, Object> exportarTabela(String nomeTabela) {
        ambienteMaquina.validarExportacao();
        if (nomeTabela == null || !nomeTabela.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de tabela invalido");
        }
        TabelaServidorSci tabela = tabelaServidorSciRepository.findAllByNomeTabela(nomeTabela);
        if (tabela != null && tabela.getGrupo() != null && "telesystem".equalsIgnoreCase(tabela.getGrupo().trim())) {
            try (Connection connection = conexaoTelesystem.abrir()) {
                return exportar(connection, nomeTabela, "sgp");
            } catch (SQLException erro) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Nao foi possivel exportar a tabela do grupo telesystem", erro);
            }
        }
        if (tabela != null && tabela.getGrupo() != null && "integra".equalsIgnoreCase(tabela.getGrupo().trim())) {
            try (Connection connection = conexaoIntegra.abrir()) {
                return exportar(connection, nomeTabela, "sgp");
            } catch (SQLException erro) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Nao foi possivel exportar a tabela do grupo integra", erro);
            }
        }
        return entityManager.unwrap(Session.class).doReturningWork(
                connection -> exportar(connection, nomeTabela, "servidor_sci"));
    }

    private Map<String, Object> exportar(Connection connection, String nomeTabela, String banco) throws SQLException {
            Path pasta = Paths.get(diretorio).toAbsolutePath().normalize();
            Path destino = pasta.resolve(nomeTabela + ".xlsx");
            Path temporario = null;
            SXSSFWorkbook workbook = new SXSSFWorkbook(100);
            workbook.setCompressTempFiles(true);
            try (SXSSFWorkbook arquivo = workbook;
                    Statement consulta = connection.createStatement()) {
                consulta.setFetchSize(1000);
                String sql = "SELECT * FROM [" + banco + "].[dbo].[" + nomeTabela + "]";
                try (ResultSet registros = consulta.executeQuery(sql)) {
                    ResultSetMetaData metadados = registros.getMetaData();
                    int colunas = metadados.getColumnCount();
                    if (colunas > SpreadsheetVersion.EXCEL2007.getMaxColumns()) {
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "Tabela excede o limite de colunas do Excel");
                    }
                    int numeroPlanilha = 1;
                    Sheet planilha = criarPlanilha(arquivo, metadados, numeroPlanilha);
                    int linha = 1;
                    long quantidade = 0;
                    while (registros.next()) {
                        if (linha == SpreadsheetVersion.EXCEL2007.getMaxRows()) {
                            planilha = criarPlanilha(arquivo, metadados, ++numeroPlanilha);
                            linha = 1;
                        }
                        Row registro = planilha.createRow(linha++);
                        for (int coluna = 1; coluna <= colunas; coluna++) {
                            // Texto preserva IDs, zeros a esquerda e precisao dos valores.
                            String valor = metadados.getColumnType(coluna) == java.sql.Types.BINARY
                                    || metadados.getColumnType(coluna) == java.sql.Types.VARBINARY
                                    || metadados.getColumnType(coluna) == java.sql.Types.LONGVARBINARY
                                    ? codificarBinario(registros.getBytes(coluna)) : registros.getString(coluna);
                            if (valor != null) {
                                if (valor.length() > SpreadsheetVersion.EXCEL2007.getMaxTextLength()) {
                                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                            "Valor excede o limite de texto do Excel na coluna "
                                                    + metadados.getColumnLabel(coluna));
                                }
                                registro.createCell(coluna - 1).setCellValue(valor);
                            }
                        }
                        quantidade++;
                    }
                    Files.createDirectories(pasta);
                    temporario = Files.createTempFile(pasta, "exportacao-", ".xlsx");
                    try (OutputStream saida = Files.newOutputStream(temporario)) {
                        arquivo.write(saida);
                    }
                    // Substitui a versao anterior apenas depois de gerar o arquivo completo.
                    Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
                    return Map.of("nomeTabela", nomeTabela, "registrosExportados", quantidade,
                            "arquivo", destino.toString());
                }
            } catch (IOException erro) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Nao foi possivel salvar a planilha Excel", erro);
            } finally {
                workbook.dispose();
                if (temporario != null) {
                    try {
                        Files.deleteIfExists(temporario);
                    } catch (IOException erro) {
                        org.slf4j.LoggerFactory.getLogger(ExportacaoExcelService.class)
                                .warn("Nao foi possivel remover o arquivo temporario {}", temporario, erro);
                    }
                }
            }
    }

    private Sheet criarPlanilha(SXSSFWorkbook arquivo, ResultSetMetaData metadados, int numero)
            throws java.sql.SQLException {
        Sheet planilha = arquivo.createSheet("Registros " + numero);
        planilha.createFreezePane(0, 1);
        Row cabecalho = planilha.createRow(0);
        for (int coluna = 1; coluna <= metadados.getColumnCount(); coluna++) {
            cabecalho.createCell(coluna - 1).setCellValue(metadados.getColumnLabel(coluna));
        }
        return planilha;
    }

    private String codificarBinario(byte[] valor) {
        return valor == null ? null : Base64.getEncoder().encodeToString(valor);
    }
}
