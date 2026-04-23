package br.usp.agv.usecase;

import br.usp.agv.model.Agv;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.Batch;
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

    public void broadcastBatchProposal(Batch batch) {
        messageBus.broadcast(AgvMessage.batchProposal(agv.getAgvId(), batch));
    }

    public void broadcastBatchAck(String batchId) {
        messageBus.broadcast(AgvMessage.batchAck(agv.getAgvId(), batchId));
    }

    public void broadcastRouteClaimed(String orderId, Route route) {
        messageBus.broadcast(AgvMessage.routeClaimed(agv, orderId, route));
    }

    public void broadcastRouteReleased() {
        messageBus.broadcast(AgvMessage.routeReleased(agv.getAgvId()));
    }

    public void broadcastOrderCompleted(String orderId) {
        messageBus.broadcast(AgvMessage.orderCompleted(orderId));
    }
}
