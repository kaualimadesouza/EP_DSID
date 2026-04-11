package br.usp.agv.usecase;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.MessageBusPort;

import java.util.HashMap;
import java.util.Map;

public class AgvController implements br.usp.agv.ports.inbound.AgvController, ElectionUseCase.ElectionListener {

    private final Agv agv;
    private final ElectionUseCase election;
    private final MovementUseCase movement;
    private final MessageBusPort messageBus;
    private Thread heartbeatThread;

    public AgvController(Agv agv, ElectionUseCase election, MovementUseCase movement, MessageBusPort messageBus) {
        this.agv = agv;
        this.election = election;
        this.movement = movement;
        this.messageBus = messageBus;

        this.election.setElectionListener(this);
    }

    public void start() {
        if (heartbeatThread != null) return;
        heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    broadcastHeartbeat();
                    Thread.sleep(1000); // Heartbeat a cada 1Hz
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.start();
    }

    private void broadcastHeartbeat() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("position", agv.getCurrentPosition());
        payload.put("status", agv.getStatus());

        AgvMessage msg = new AgvMessage(agv.getAgvId(), MessageType.HEARTBEAT, payload);
        messageBus.broadcast(msg);
    }

    @Override
    public void onNewOrder(Order order) {
        election.startElection(order);
    }

    @Override
    public void onElectionWon(String orderId, br.usp.agv.model.Route wonRoute) {
        if (agv.getStatus() != br.usp.agv.model.AgvStatus.ELECTING) return;
        
        System.out.println("AGV " + agv.getAgvId() + " venceu a eleição reativamente para " + orderId);
        if (movement != null) movement.executeRoute(wonRoute);

        broadcastRouteClaimed(wonRoute);
    }

    @Override
    public void onMessageReceived(AgvMessage message) {
        if (message.senderId().equals(agv.getAgvId())) return;

        switch (message.type()) {
            case ELECTION_REQUEST -> election.onElectionRequest(message);
            case ELECTION_CONCEDE -> {
                String orderId = (String) message.payload().get("orderId");
                System.out.println("ELECTION_CONCEDE" + message.payload());
                election.onConcedeReceived(message.senderId(), orderId);
            }
            case HEARTBEAT -> election.onHeartbeatReceived(message.senderId());
        }
    }

    private void broadcastRouteClaimed(Route wonRoute) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agvId", agv.getAgvId());
        payload.put("route", wonRoute);

        AgvMessage msg = new AgvMessage(agv.getAgvId(), MessageType.ROUTE_CLAIMED, payload);
        messageBus.broadcast(msg);
    }
}
