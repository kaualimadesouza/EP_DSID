package br.usp.agv.ports.outbound;

import br.usp.agv.model.Agv;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;

import java.util.List;

// Porta para observadores externos que desejam visualizar o estado do sistema
public interface WorldObserverPort {
    void onAgvMoved(String agvId, Position newPosition);
    void onOrderCreated(Order order);
    void onRouteCalculated(String agvId, br.usp.agv.model.Route route);
    void onRouteReleased(String agvId);
    void onOrderCompleted(String orderId);
    void onSystemStateChanged(List<Agv> allAgvs, List<Order> pendingOrders);
    default void onLeaderChanged(String leaderId) {}
}
