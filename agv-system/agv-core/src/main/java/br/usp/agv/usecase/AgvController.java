package br.usp.agv.usecase;

import br.usp.agv.model.*;

public class AgvController implements br.usp.agv.ports.inbound.AgvController, ElectionUseCase.ElectionListener {

    private final Agv agv;
    private final ElectionUseCase election;
    private final MovementUseCase movement;
    private final AgvBroadcaster broadcaster;
    private Thread heartbeatThread;

    public AgvController(Agv agv, ElectionUseCase election, MovementUseCase movement, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.election = election;
        this.movement = movement;
        this.broadcaster = broadcaster;

        this.election.setElectionListener(this);
    }

    public void start() {
        if (heartbeatThread != null) return;
        heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    broadcaster.broadcastHeartbeat();
                    Thread.sleep(1000); // Heartbeat a cada 1Hz
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        heartbeatThread.start();
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

        broadcaster.broadcastRouteClaimed(wonRoute);
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
}
