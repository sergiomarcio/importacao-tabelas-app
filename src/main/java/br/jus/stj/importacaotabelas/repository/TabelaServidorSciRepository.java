
package br.jus.stj.importacaotabelas.repository;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.jus.stj.importacaotabelas.entidade.TabelaServidorSci;

@Repository
public interface TabelaServidorSciRepository extends JpaRepository<TabelaServidorSci, Integer>  {
	
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TabelaServidorSci t set t.dataAtualizacao = :data where t.nomeTabela = :nome")
    int atualizarDataImportacao(@Param("nome") String nome, @Param("data") LocalDateTime data);

    List<TabelaServidorSci> findAllBy();

    TabelaServidorSci findAllByNomeTabela(String nometabela);

    List<TabelaServidorSci> findAllByGrupo(String grupo);

}