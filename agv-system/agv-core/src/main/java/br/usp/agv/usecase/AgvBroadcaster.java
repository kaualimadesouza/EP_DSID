package br.usp.agv.usecase;

import br.usp.agv.model.Agv;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.Batch;
import br.usp.agv.model.Route;
import br.usp.agv.ports.outbound.MessageBusPort;

public class AgvBroadcaster {
    private final MessageBusPort messageBus;
    private final Agv agv;
    private int nextSeq = 1;

    public AgvBroadcaster(Agv agv, MessageBusPort messageBus) {
        this.agv = agv;
        this.messageBus = messageBus;
    }

    private synchronized int nextSequenceNumber() {
        return nextSeq++;
    }

    public void broadcastHeartbeat() {
        System.out.println("[" + agv.getAgvId() + "] Enviando Heartbeat...");
        agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.heartbeat(agv, 0));
    }

    public void broadcastBatchProposal(Batch batch) {
        int seq = nextSequenceNumber();
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.batchProposal(agv.getAgvId(), seq, clock, batch));
    }

    public void broadcastBatchAck(String batchId) {
        int seq = nextSequenceNumber();
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.batchAck(agv.getAgvId(), seq, clock, batchId));
    }

    public void broadcastRouteClaimed(String orderId, Route route) {
        agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.routeClaimed(agv, 0, orderId, route));
    }

    public void broadcastRouteReleased() {
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.routeReleased(agv.getAgvId(), 0, clock));
    }

    public void broadcastOrderCompleted(String orderId) {
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.orderCompleted(agv.getAgvId(), 0, clock, orderId));
    }

    public void broadcastElection() {
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.election(agv.getAgvId(), 0, clock));
    }

    public void broadcastOk() {
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.ok(agv.getAgvId(), 0, clock));
    }

    public void broadcastCoordinator() {
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.coordinator(agv.getAgvId(), 0, clock));
    }

    public void broadcastNackRequest(String targetSenderId, int targetSeq) {
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.nackRequest(agv.getAgvId(), 0, clock, targetSenderId, targetSeq));
    }

    public void broadcastNackResponse(AgvMessage lostMessage) {
        long clock = agv.incrementAndGetLamportClock();
        messageBus.broadcast(AgvMessage.nackResponse(agv.getAgvId(), 0, clock, lostMessage));
    }
}
