package br.jus.stj.importacaotabelas.controller;

import java.util.List;
import java.util.Map;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.jus.stj.importacaotabelas.dto.EntidadeTabelaServidorSciDTO;
import br.jus.stj.importacaotabelas.entidade.TabelaServidorSci;
import br.jus.stj.importacaotabelas.service.ExportacaoExcelService;
import br.jus.stj.importacaotabelas.service.AmbienteMaquina;
import br.jus.stj.importacaotabelas.service.ImportacaoExcelService;
import br.jus.stj.importacaotabelas.service.TabelasServidorSciService;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping({"/importacao-tabelas"})
public class TabelasServidorSciController {
    
    @Autowired
    TabelasServidorSciService tabelasServidorSciService;

    @Autowired
    private ExportacaoExcelService exportacaoExcelService;

    @Autowired
    private ImportacaoExcelService importacaoExcelService;

    final ModelMapper modelMapper = new ModelMapper();

    @ApiOperation(value = "Consultar Lista de Tabelas para Importação", response = TabelaServidorSci.class)
    @GetMapping("/lista-tabelas")
    public ResponseEntity<List<TabelaServidorSci>> getListaTabelasImportacao() throws Exception {
        List<TabelaServidorSci> lista = tabelasServidorSciService.getListaTabelas();
        return new ResponseEntity<>(lista, HttpStatus.OK);
    }

    @ApiOperation(value = "Consultar Lista de Tabelas para Importação - Por Grupo", response = TabelaServidorSci.class)
    @GetMapping("/lista-tabelas/grupo/{grupo}")
    public ResponseEntity<List<TabelaServidorSci>> getTabelaPorGrupo(@PathVariable("grupo") String grupo) throws Exception {
        List<TabelaServidorSci> lista = tabelasServidorSciService.getTabelaPorGrupo(grupo);
        return new ResponseEntity<>(lista, HttpStatus.OK);
    }

    @ApiOperation(value = "Consultar Lista de Tabelas para Importação - Por Nome da Tabela", response = TabelaServidorSci.class)
    @GetMapping("/tabela/{nometabela}")
    public ResponseEntity<TabelaServidorSci> getTabelaPorNomeTabela(@PathVariable("nometabela") String nometabela) throws Exception {
        TabelaServidorSci tabela = tabelasServidorSciService.getTabelaPorNomeTabela(nometabela);
        return new ResponseEntity<>(tabela, HttpStatus.OK);
    }

    @ApiOperation(value = "Tabelas para Importação - Incluir", response = TabelaServidorSci.class)
    @PostMapping("")
    public ResponseEntity<TabelaServidorSci> incluirTabelaImportacao(@RequestBody EntidadeTabelaServidorSciDTO registro) throws Exception {
    	if (registro.getNome_tabela() == null || !registro.getNome_tabela().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")
                || registro.getGrupo() == null || registro.getGrupo().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe nome da tabela e grupo validos");
        }
        if (tabelasServidorSciService.getTabelaPorNomeTabela(registro.getNome_tabela()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tabela ja cadastrada");
        }
        TabelaServidorSci novoregistro = new TabelaServidorSci();
        novoregistro.setNomeTabela(registro.getNome_tabela());
        novoregistro.setGrupo(registro.getGrupo().trim());
    	tabelasServidorSciService.salvarTabela(novoregistro);
        return new ResponseEntity<>(novoregistro, HttpStatus.CREATED);
    }

    @ApiOperation(value = "Tabelas para Importação - Excluir")
    @DeleteMapping("/{id:[0-9]+}")
    public ResponseEntity<TabelaServidorSci> excluirTabelaImportacao(@PathVariable("id") Integer id) throws Exception {
    	tabelasServidorSciService.excluirTabela(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    
    @ApiOperation(value = "Limpar tabela e importar Excel: SGP em STJ220579, servidor_sci em PALMAS")
    @PostMapping("/importar-do-excel")
    public ResponseEntity<Map<String, Object>> importarExcel(@RequestParam("nomeTabela") String nomeTabela) {
        return ResponseEntity.ok(importacaoExcelService.importarTabela(nomeTabela));
    }

    @ApiOperation(value = "Consultar permissao de exportacao da maquina")
    @GetMapping("/permissao-exportacao")
    public ResponseEntity<Map<String, Object>> permissaoExportacao() {
        AmbienteMaquina ambiente = new AmbienteMaquina();
        return ResponseEntity.ok(Map.of("maquina", ambiente.getNome(),
                "permitida", ambiente.exportacaoPermitida()));
    }

    @ApiOperation(value = "Exportar tabela para Excel somente em STJ220579")
    @PostMapping("/exportar-para-excel")
    public ResponseEntity<Map<String, Object>> exportarExcel(@RequestParam("nomeTabela") String nomeTabela) {
        return ResponseEntity.ok(exportacaoExcelService.exportarTabela(nomeTabela));
    }

}
