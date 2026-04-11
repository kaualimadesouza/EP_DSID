package integration;

import br.usp.agv.model.*;
import br.usp.agv.pathfinder.AStarPathfinderAdapter;
import br.usp.agv.pathfinder.GridGraphAdapter;
import br.usp.agv.usecase.AgvController;
import br.usp.agv.usecase.ElectionUseCase;
import fakes.FakeMessageBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;


class ElectionIntegrationTest {

    @Test
    @DisplayName("AGV calcula score e faz broadcast ao receber order")
    void agv_calcula_score_e_faz_broadcast_ao_receber_order() {
        // Arrange
        GridGraphAdapter gridGraphAdapter = new GridGraphAdapter(10, 10, Collections.emptySet());
        AStarPathfinderAdapter pathfinder = new AStarPathfinderAdapter(gridGraphAdapter);
        FakeMessageBus bus = new FakeMessageBus();

        Agv agv = new Agv("agv-alpha", new Position(0, 0));

        ElectionUseCase election = new ElectionUseCase(agv, pathfinder, bus);
        // Passamos null para o movement para simplificar o teste
        AgvController controller = new AgvController(agv, election, null, bus);

        Order order = new Order("ORD-1", new Position(3, 4), new Position(8, 8));

        // Act
        controller.start();
        controller.onNewOrder(order);

        // Assert
        // A eleição deve ter iniciado
        assertTrue(agv.getStatus() == AgvStatus.ELECTING || agv.getStatus() == AgvStatus.IDLE);

        // Verificamos se as mensagens foram enviadas (Heartbeat do loop + Election Request)
        assertFalse(bus.sent.isEmpty(), "Deveria ter enviado ao menos a mensagem de eleição");
        
        AgvMessage electionMsg = bus.sent.stream()
                .filter(m -> m.type() == MessageType.ELECTION_REQUEST)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Mensagem ELECTION_REQUEST não encontrada"));

        assertEquals("agv-alpha", electionMsg.senderId());
        assertEquals("ORD-1", electionMsg.payload().get("orderId"));
        assertEquals(7, electionMsg.payload().get("score"));
        
        // Verifica se houve ao menos um heartbeat (pode haver mais de um dependendo do tempo)
        assertTrue(bus.sent.stream().anyMatch(m -> m.type() == MessageType.HEARTBEAT));
    }
}
