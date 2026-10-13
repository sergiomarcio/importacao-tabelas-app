package br.jus.stj.importacaotabelas.service;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import br.jus.stj.importacaotabelas.entidade.TabelaServidorSci;
import br.jus.stj.importacaotabelas.repository.TabelaServidorSciRepository;

@Service
public class TabelasServidorSciService {
	  
    @Autowired
    private TabelaServidorSciRepository tabelaServidorSciRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    public List<TabelaServidorSci> getListaTabelas() {
        return tabelaServidorSciRepository.findAllBy();
    }

    public List<TabelaServidorSci> getTabelaPorGrupo(String grupo) {
        return tabelaServidorSciRepository.findAllByGrupo(grupo);
    }

    public TabelaServidorSci getTabelaPorNomeTabela(String nometabela) {
        return tabelaServidorSciRepository.findAllByNomeTabela(nometabela);
    }
    
    public TabelaServidorSci salvarTabela(TabelaServidorSci registro) {
        return tabelaServidorSciRepository.save(registro);
    }

    public void excluirTabela(Integer id) {
    	tabelaServidorSciRepository.deleteById(id);
    }

}