package br.usp.agv.usecase;

import br.usp.agv.model.*;

import java.util.List;

public class MovementUseCase {

    private final Agv agv;
    private final AgvBroadcaster broadcaster;

    public MovementUseCase(Agv agv, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.broadcaster = broadcaster;
    }

    public void executeOrder(Order order, Route toPickup, Route toDelivery) {
        new Thread(() -> {
            try {
                agv.setStatus(AgvStatus.MOVING);
                
                // vai até o pickup
                System.out.println("AGV " + agv.getAgvId() + " indo para PICKUP de " + order.orderId());
                followRoute(toPickup.waypoints());

                System.out.println("AGV " + agv.getAgvId() + " chegou no PICKUP. Indo para DELIVERY...");
                Thread.sleep(1000); // Simula carregar o lote

                // vai até o delivery
                followRoute(toDelivery.waypoints());

                System.out.println("AGV " + agv.getAgvId() + " concluiu entrega de " + order.orderId());
                agv.setStatus(AgvStatus.IDLE);
                broadcaster.broadcastHeartbeat();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void followRoute(List<Position> waypoints) throws InterruptedException {
        for (Position p : waypoints) {
            Thread.sleep(300);
            agv.setCurrentPosition(p);
            broadcaster.broadcastHeartbeat();
        }
    }

    @Deprecated
    public void executeRoute(Route route) {
        new Thread(() -> {
            try {
                agv.setStatus(AgvStatus.MOVING);
                followRoute(route.waypoints());
                agv.setStatus(AgvStatus.IDLE);
                broadcaster.broadcastHeartbeat();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
