package br.usp.agv.messaging;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.MessageBusPort;
import br.usp.agv.ports.outbound.WorldObserverPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador que encaminha eventos do observador para a rede via MessageBus.
 * Permite que um processo de UI remoto visualize o que está acontecendo no nó.
 */
public class NetworkObserverAdapter implements WorldObserverPort {

    private final String agvId;
    private final MessageBusPort messageBus;

    public NetworkObserverAdapter(String agvId, MessageBusPort messageBus) {
        this.agvId = agvId;
        this.messageBus = messageBus;
    }

    @Override
    public void onAgvMoved(String agvId, Position newPosition) {
        // Já tratado pelo Heartbeat em muitos casos, mas podemos reforçar
    }

    @Override
    public void onOrderCreated(Order order) {
        // O nó geralmente não cria pedidos, ele os recebe. 
        // Mas se criasse, dispararíamos NEW_ORDER.
    }

    @Override
    public void onRouteCalculated(String agvId, Route route) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("agvId", agvId);
        payload.put("route", route);

        AgvMessage msg = new AgvMessage(this.agvId, MessageType.ROUTE_CLAIMED, payload);
        messageBus.broadcast(msg);
    }

    @Override
    public void onSystemStateChanged(List<Agv> allAgvs, List<Order> pendingOrders) {
        // No modelo P2P, cada nó cuida do seu estado.
    }
}
