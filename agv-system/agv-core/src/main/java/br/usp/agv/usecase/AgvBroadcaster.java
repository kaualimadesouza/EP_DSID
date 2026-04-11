package br.usp.agv.usecase;

import br.usp.agv.model.Agv;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.Route;
import br.usp.agv.ports.outbound.MessageBusPort;

public class AgvBroadcaster {
    private final MessageBusPort messageBus;
    private final Agv agv;

    public AgvBroadcaster(Agv agv, MessageBusPort messageBus) {
        this.agv = agv;
        this.messageBus = messageBus;
    }

    public void broadcastHeartbeat() {
        messageBus.broadcast(AgvMessage.heartbeat(agv));
    }

    public void broadcastCandidacy(String orderId, int score, Route route) {
        messageBus.broadcast(AgvMessage.candidacy(agv, orderId, score, route));
    }

    public void broadcastConcede(String orderId) {
        messageBus.broadcast(AgvMessage.concede(agv, orderId));
    }

    public void broadcastRouteClaimed(Route route) {
        messageBus.broadcast(AgvMessage.routeClaimed(agv, route));
    }
}
