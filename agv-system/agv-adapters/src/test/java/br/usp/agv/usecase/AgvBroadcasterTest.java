package br.usp.agv.usecase;

import br.usp.agv.model.Agv;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.AgvStatus;
import br.usp.agv.model.MessageType;
import br.usp.agv.model.Position;
import fakes.FakeMessageBus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cobre o formato das mensagens descrito na especificação (seção VI): tipo, senderId e
 * payload mínimo esperado por cada tipo de mensagem. Usa o FakeMessageBus que já existia
 * no projeto (agv-adapters/src/test/java/fakes) mas nunca tinha sido usado por nenhum teste.
 */
class AgvBroadcasterTest {

    @Test
    void heartbeatCarregaPosicaoEStatusAtuaisDoAgv() {
        Agv agv = new Agv("AGV-1", new Position(3, 4));
        FakeMessageBus bus = new FakeMessageBus();
        AgvBroadcaster broadcaster = new AgvBroadcaster(agv, bus);

        broadcaster.broadcastHeartbeat();

        assertEquals(1, bus.sent.size());
        AgvMessage msg = bus.sent.get(0);
        assertEquals(MessageType.HEARTBEAT, msg.type());
        assertEquals(agv.getAgvId(), msg.senderId());
        assertEquals(new Position(3, 4), msg.payload().get("position"));
        assertEquals(AgvStatus.IDLE, msg.payload().get("status"));
    }

    @Test
    void eleicaoOkECoordinatorUsamOsTiposCorretos() {
        Agv agv = new Agv("AGV-2", new Position(0, 0));
        FakeMessageBus bus = new FakeMessageBus();
        AgvBroadcaster broadcaster = new AgvBroadcaster(agv, bus);

        broadcaster.broadcastElection();
        broadcaster.broadcastOk();
        broadcaster.broadcastCoordinator();

        assertEquals(3, bus.sent.size());
        assertEquals(MessageType.ELECTION, bus.sent.get(0).type());
        assertEquals(MessageType.OK, bus.sent.get(1).type());
        assertEquals(MessageType.COORDINATOR, bus.sent.get(2).type());
    }
}
