package br.usp.agv.usecase;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.PathfinderPort;

public class AgvController implements br.usp.agv.ports.inbound.AgvController, BatchAssignmentUseCase.BatchListener {

    private final Agv agv;
    private final BatchAssignmentUseCase batchAssignment;
    private final MovementUseCase movement;
    private final PathfinderPort pathfinder;
    private final AgvBroadcaster broadcaster;
    private Thread heartbeatThread;

    public AgvController(Agv agv, BatchAssignmentUseCase batchAssignment, MovementUseCase movement, 
                         PathfinderPort pathfinder, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.batchAssignment = batchAssignment;
        this.movement = movement;
        this.pathfinder = pathfinder;
        this.broadcaster = broadcaster;

        this.batchAssignment.setListener(this);
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
        batchAssignment.onNewOrder(order);
    }

    @Override
    public void onBatchProposal(Batch batch) {
        batchAssignment.onBatchProposal(batch);
    }

    @Override
    public void onBatchAck(String senderId, String batchId) {
        batchAssignment.onBatchAck(senderId, batchId);
    }

    @Override
    public void onHeartbeatReceived(String senderId, Position position, AgvStatus status) {
        batchAssignment.onHeartbeatReceived(senderId, position, status);
    }

    @Override
    public void onOrderAssigned(Order order) {
        System.out.println("AGV " + agv.getAgvId() + " recebeu atribuição para " + order.orderId());
        
        // Calcula as duas partes da viagem
        Route toPickup = pathfinder.calculateRoute(agv.getCurrentPosition(), order.pickup(), java.util.Set.of(), agv.getAgvId());
        Route toDelivery = pathfinder.calculateRoute(order.pickup(), order.delivery(), java.util.Set.of(), agv.getAgvId());

        if (movement != null) {
            movement.executeOrder(order, toPickup, toDelivery);
        }

        broadcaster.broadcastRouteClaimed(order.orderId(), toPickup);
    }
}
