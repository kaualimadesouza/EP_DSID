package br.usp.agv.usecase;

import br.usp.agv.model.AgvSnapshot;
import br.usp.agv.model.AgvStatus;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre a parte determinística do leilão de lote (Requisito Funcional 4 da especificação:
 * "Alocação Determinística") e a comparação de IDs estáticos usada na eleição Bully
 * (Requisito Funcional 7), sem precisar orquestrar threads, rede ou timers.
 */
class BatchAssignmentUseCaseTest {

    @Test
    void vencedorDoLeilaoEhOAgvMaisPertoDoPickup() {
        Map<String, AgvSnapshot> states = new LinkedHashMap<>();
        states.put("A", new AgvSnapshot("A", new Position(0, 0), AgvStatus.IDLE));
        states.put("B", new AgvSnapshot("B", new Position(5, 5), AgvStatus.IDLE));

        Order order = new Order("ORD-1", new Position(1, 0), new Position(9, 9));

        assertEquals("A", BatchAssignmentUseCase.getBestAgvId(order, states));
    }

    @Test
    void empateNaDistanciaEhDesempatadoPeloMenorId() {
        Map<String, AgvSnapshot> states = new LinkedHashMap<>();
        states.put("B", new AgvSnapshot("B", new Position(0, 1), AgvStatus.IDLE));
        states.put("A", new AgvSnapshot("A", new Position(1, 0), AgvStatus.IDLE));

        Order order = new Order("ORD-2", new Position(0, 0), new Position(9, 9));

        assertEquals("A", BatchAssignmentUseCase.getBestAgvId(order, states));
    }

    @Test
    void agvMovingNoSnapshotNuncaGanhaMesmoSendoMaisPerto() {
        Map<String, AgvSnapshot> states = new LinkedHashMap<>();
        states.put("A", new AgvSnapshot("A", new Position(0, 0), AgvStatus.MOVING));
        states.put("B", new AgvSnapshot("B", new Position(5, 5), AgvStatus.IDLE));

        Order order = new Order("ORD-3", new Position(0, 0), new Position(9, 9));

        assertEquals("B", BatchAssignmentUseCase.getBestAgvId(order, states));
    }

    @Test
    void semNenhumAgvIdleNaoHaVencedor() {
        Map<String, AgvSnapshot> states = new LinkedHashMap<>();
        states.put("A", new AgvSnapshot("A", new Position(0, 0), AgvStatus.MOVING));
        states.put("B", new AgvSnapshot("B", new Position(1, 1), AgvStatus.OFFLINE));

        Order order = new Order("ORD-4", new Position(0, 0), new Position(9, 9));

        assertNull(BatchAssignmentUseCase.getBestAgvId(order, states));
    }

    @Test
    void bullyComparaNumericamenteQuandoMesmoPrefixo() {
        // Regressão do bug: String.compareTo puro fazia "AGV-10" perder de "AGV-9"
        // (compara caractere a caractere: '1' < '9').
        assertTrue(BatchAssignmentUseCase.compareStaticIds("AGV-10", "AGV-9") > 0);
        assertTrue(BatchAssignmentUseCase.compareStaticIds("AGV-2", "AGV-10") < 0);
        assertEquals(0, BatchAssignmentUseCase.compareStaticIds("AGV-9", "AGV-9"));
    }

    @Test
    void bullyCaiParaLexicograficoSemSufixoNumericoOuPrefixoDiferente() {
        // Nomes sem número (ex: cenário de SimulationMain com "Alpha"/"Beta"/"Gamma")
        // continuam se comportando como antes.
        assertTrue(BatchAssignmentUseCase.compareStaticIds("Beta", "Alpha") > 0);

        // Prefixos diferentes: não faz sentido comparar números de séries diferentes,
        // cai para a ordem lexicográfica normal.
        assertTrue(BatchAssignmentUseCase.compareStaticIds("AGV-2", "ROBOT-1") < 0);
    }
}
