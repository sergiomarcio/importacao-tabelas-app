package br.jus.stj.importacaotabelas.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EntidadeTabelaServidorSciDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private Integer id;    
    private String nome_tabela;
    private String grupo;
    private LocalDateTime dataAtualizacao;
    
}