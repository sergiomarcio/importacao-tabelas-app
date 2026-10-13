package br.jus.stj.importacaotabelas.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.jus.stj.importacaotabelas.repository.TabelaServidorSciRepository;

@Service
public class ImportacaoExcelService {
    @Autowired
    private TabelaServidorSciRepository tabelaServidorSciRepository;
    private AmbienteMaquina ambienteMaquina = new AmbienteMaquina();
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${importacao.excel.diretorio}")
    private String diretorio;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importarTabela(String nomeTabela) {
        if (nomeTabela == null || !nomeTabela.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw invalido("Nome de tabela invalido");
        }
        String bancoPadrao = ambienteMaquina.bancoImportacao(nomeTabela + ".xlsx");
        br.jus.stj.importacaotabelas.entidade.TabelaServidorSci cadastro =
                tabelaServidorSciRepository.findAllByNomeTabela(nomeTabela);
        String banco = cadastro != null && cadastro.getGrupo() != null
                && "integra".equalsIgnoreCase(cadastro.getGrupo().trim()) ? "sgp" : bancoPadrao;
        Path caminho = Paths.get(diretorio).toAbsolutePath().normalize().resolve(nomeTabela + ".xlsx");
        if (!Files.isRegularFile(caminho)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Planilha nao encontrada: " + caminho);
        }
        try (Workbook workbook = abrirStreaming(caminho)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw invalido("Planilha sem abas");
            }
            List<String> cabecalho = lerCabecalho(workbook.getSheetAt(0));
            int numeroAba = 0;
            for (Sheet aba : workbook) {
                if (numeroAba++ > 0 && !cabecalho.equals(lerCabecalho(aba))) {
                    throw invalido("As abas devem possuir os mesmos cabecalhos na mesma ordem");
                }
                for (Row linha : aba) {
                    if (linha.getRowNum() == 0) continue;
                    for (Cell celula : linha) {
                        if (celula.getCellType() == CellType.FORMULA || celula.getCellType() == CellType.ERROR) {
                            throw invalido("Formula ou erro na aba " + aba.getSheetName()
                                    + ", linha " + (linha.getRowNum() + 1));
                        }
                        if (celula.getColumnIndex() >= cabecalho.size()
                                && celula.getCellType() != CellType.BLANK) {
                            throw invalido("Dados sem cabecalho na aba " + aba.getSheetName());
                        }
                    }

                }
            }
            try (Workbook dados = abrirStreaming(caminho)) {
            Map<String, Object> resultado = entityManager.unwrap(Session.class).doReturningWork(connection -> {
                String tabela = "[" + banco + "].[dbo].[" + nomeTabela + "]";
                Map<String, Boolean> gravaveis = new HashMap<>();
                Map<String, Boolean> identidades = new HashMap<>();
                List<String> obrigatorias = new ArrayList<>();
                String sql = "SELECT c.name, c.is_identity, c.is_computed, c.system_type_id, "
                        + "c.is_nullable, c.default_object_id FROM [" + banco + "].sys.columns c "
                        + "JOIN [" + banco + "].sys.tables t ON t.object_id = c.object_id "
                        + "JOIN [" + banco + "].sys.schemas s ON s.schema_id = t.schema_id "
                        + "WHERE s.name = 'dbo' AND t.name = ?";
                try (PreparedStatement consulta = connection.prepareStatement(sql)) {
                    consulta.setString(1, nomeTabela);
                    try (ResultSet colunas = consulta.executeQuery()) {
                        while (colunas.next()) {
                            String nome = colunas.getString(1).toLowerCase(Locale.ROOT);
                            boolean identity = colunas.getBoolean(2);
                            boolean gravavel = !colunas.getBoolean(3) && colunas.getInt(4) != 189;
                            gravaveis.put(nome, gravavel);
                            identidades.put(nome, identity);
                            if (gravavel && !identity && !colunas.getBoolean(5) && colunas.getInt(6) == 0) {
                                obrigatorias.add(nome);
                            }
                        }
                    }
                }
                if (gravaveis.isEmpty()) throw invalido("Tabela de destino nao encontrada");
                List<Integer> indices = new ArrayList<>();
                List<String> nomes = new ArrayList<>();
                boolean identity = false;
                for (int i = 0; i < cabecalho.size(); i++) {
                    String nome = cabecalho.get(i).toLowerCase(Locale.ROOT);
                    if (!gravaveis.containsKey(nome)) throw invalido("Coluna inexistente em " + banco + ": " + cabecalho.get(i));
                    // Campos calculados e rowversion sao gerados pelo SQL Server.
                    if (gravaveis.get(nome)) {
                        indices.add(i);
                        nomes.add("[" + cabecalho.get(i).replace("]", "]]") + "]");
                        identity |= identidades.get(nome);
                    }
                }
                for (String nome : obrigatorias) {
                    if (cabecalho.stream().noneMatch(c -> c.equalsIgnoreCase(nome))) {
                        throw invalido("Coluna obrigatoria ausente na planilha: " + nome);
                    }
                }
                if (indices.isEmpty()) throw invalido("Nenhuma coluna importavel");
                String lista = String.join(", ", nomes);
                int[] tipos = new int[indices.size()];
                try (Statement consulta = connection.createStatement();
                        ResultSet colunas = consulta.executeQuery("SELECT TOP (0) " + lista + " FROM " + tabela)) {
                    for (int i = 0; i < tipos.length; i++) tipos[i] = colunas.getMetaData().getColumnType(i + 1);
                }
                try (Statement comando = connection.createStatement()) {
                    comando.executeUpdate("TRUNCATE TABLE " + tabela);
                    if (identity) comando.execute("SET IDENTITY_INSERT " + tabela + " ON");
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + tabela
                            + " (" + lista + ") VALUES ("
                            + String.join(", ", java.util.Collections.nCopies(indices.size(), "?")) + ")")) {
                        long quantidade = 0;
                        int pendentes = 0;
                        for (Sheet aba : dados) {
                            for (Row linha : aba) {
                                if (linha.getRowNum() == 0) continue;
                                boolean possuiCelulas = false;
                                for (int indice : indices) {
                                    Cell cell = linha.getCell(indice);
                                    possuiCelulas |= cell != null && cell.getCellType() != CellType.BLANK;
                                }
                                if (!possuiCelulas) continue;
                                for (int i = 0; i < indices.size(); i++) {
                                    preencher(insert, i + 1, linha.getCell(indices.get(i)), tipos[i]);
                                }
                                insert.addBatch();
                                quantidade++;
                                if (++pendentes == 500) {
                                    insert.executeBatch();
                                    insert.clearBatch();
                                    pendentes = 0;
                                }
                            }
                        }
                        if (pendentes > 0) insert.executeBatch();
                        return Map.of("nomeTabela", nomeTabela, "registrosImportados", quantidade,
                                "arquivo", caminho.toString());
                    } finally {
                        if (identity) comando.execute("SET IDENTITY_INSERT " + tabela + " OFF");
                    }
                }
            });
            LocalDateTime dataAtualizacao = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
            if (tabelaServidorSciRepository.atualizarDataImportacao(nomeTabela, dataAtualizacao) != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Nao foi possivel atualizar a data da tabela no cadastro");
            }
            Map<String, Object> resposta = new HashMap<>(resultado);
            resposta.put("dataAtualizacao", dataAtualizacao.toString());
            return resposta;
            }
        } catch (IOException erro) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nao foi possivel ler a planilha", erro);
        }
    }

    private List<String> lerCabecalho(Sheet aba) {
        java.util.Iterator<Row> linhas = aba.iterator();
        Row linha = linhas.hasNext() ? linhas.next() : null;
        if (linha != null && linha.getRowNum() != 0) throw invalido("Aba sem cabecalho: " + aba.getSheetName());
        if (linha == null || linha.getLastCellNum() <= 0) throw invalido("Aba sem cabecalho: " + aba.getSheetName());
        List<String> nomes = new ArrayList<>();
        HashSet<String> encontrados = new HashSet<>();
        for (int i = 0; i < linha.getLastCellNum(); i++) {
            Cell celula = linha.getCell(i);
            if (celula == null || celula.getCellType() != CellType.STRING) throw invalido("Cabecalho invalido");
            String nome = celula.getStringCellValue().trim();
            if (nome.isEmpty() || !encontrados.add(nome.toLowerCase(Locale.ROOT))) throw invalido("Cabecalho vazio ou duplicado");
            nomes.add(nome);
        }
        return nomes;
    }

    private void preencher(PreparedStatement insert, int indice, Cell celula, int tipo) throws java.sql.SQLException {
        if (celula == null || celula.getCellType() == CellType.BLANK) {
            insert.setNull(indice, tipo);
            return;
        }
        String valor;
        switch (celula.getCellType()) {
            case STRING: valor = celula.getStringCellValue(); break;
            case BOOLEAN: valor = celula.getBooleanCellValue() ? "1" : "0"; break;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(celula)) {
                    insert.setTimestamp(indice, java.sql.Timestamp.valueOf(celula.getLocalDateTimeCellValue()));
                    return;
                }
                valor = NumberToTextConverter.toText(celula.getNumericCellValue());
                break;
            default: throw invalido("Tipo de celula nao suportado");
        }
        if (tipo == Types.BINARY || tipo == Types.VARBINARY || tipo == Types.LONGVARBINARY) {
            try {
                insert.setBytes(indice, Base64.getDecoder().decode(valor));
            } catch (IllegalArgumentException erro) {
                throw invalido("Conteudo binario invalido: esperado Base64");
            }
        } else {
            // O SQL Server converte o texto para o tipo da coluna de destino.
            insert.setString(indice, valor);
        }
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }

    private Workbook abrirStreaming(Path caminho) {
        return com.github.pjfanning.xlsx.StreamingReader.builder()
                .rowCacheSize(100).bufferSize(65536).setUseSstTempFile(true).open(caminho.toFile());
    }
}
