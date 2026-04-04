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

import static org.junit.jupiter.api.Assertions.assertEquals;


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
        AgvController controller = new AgvController(agv, election);

        Order order = new Order("ORD-1", new Position(3, 4), new Position(8, 8));

        // Act
        controller.onNewOrder(order);

        // Assert
        assertEquals(AgvStatus.ELECTING, agv.getStatus());

        assertEquals(1, bus.sent.size());
        AgvMessage sent = bus.sent.getFirst();
        assertEquals(MessageType.ELECTION_REQUEST, sent.type());
        assertEquals("agv-alpha", sent.senderId());
        assertEquals("ORD-1", sent.payload().get("orderId"));

        // score = distância(0,0 a 3,4) = 7
        assertEquals(7, sent.payload().get("score"));
    }
}