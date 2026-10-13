package br.jus.stj.importacaotabelas.entidade;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@Entity
@Table(name = "tbl_tabela_servidor_sci")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TabelaServidorSci implements Serializable {
    
	private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)

    @Column(name = "id")
    private Integer id;

    @Column(name = "nome_tabela")
    private String nomeTabela;

    @Column(name = "grupo")
    private String grupo;

    @Column(name = "data_atualizacao")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSS]")
    private LocalDateTime dataAtualizacao;
    
}