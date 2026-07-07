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
                br.usp.agv.logging.SystemLogger.info(agv.getStaticName(), "indo para PICKUP de " + order.orderId(), true);
                broadcaster.broadcastRouteClaimed(order.orderId(), toPickup);
                followRoute(toPickup.waypoints());

                br.usp.agv.logging.SystemLogger.info(agv.getStaticName(), "chegou no PICKUP. Indo para DELIVERY...", true);
                Thread.sleep(1000); // Simula carregar o lote
                
                // vai até o delivery
                broadcaster.broadcastRouteClaimed(order.orderId(), toDelivery);
                followRoute(toDelivery.waypoints());

                br.usp.agv.logging.SystemLogger.info(agv.getStaticName(), "concluiu entrega de " + order.orderId(), true);
                agv.setStatus(AgvStatus.IDLE);
                
                broadcaster.broadcastOrderCompleted(order.orderId());
                broadcaster.broadcastRouteReleased();
                broadcaster.broadcastHeartbeat();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void followRoute(List<Position> waypoints) throws InterruptedException {
        for (Position p : waypoints) {
            // Modo Fail-Safe: Se perder rede, pausa a movimentação até que saia do estado FAIL_SAFE
            while (agv.getStatus() == AgvStatus.FAIL_SAFE) {
                Thread.sleep(500);
            }
            
            Thread.sleep(300);
            agv.setCurrentPosition(p);
            broadcaster.broadcastHeartbeat();
        }
    }
}
