package br.usp.agv.usecase;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.PathfinderPort;
import br.usp.agv.ports.outbound.WorldObserverPort;

public class AgvController implements br.usp.agv.ports.inbound.AgvController, BatchAssignmentUseCase.BatchListener {

    private final Agv agv;
    private final BatchAssignmentUseCase batchAssignment;
    private final MovementUseCase movement;
    private final PathfinderPort pathfinder;
    private final AgvBroadcaster broadcaster;
    private Thread heartbeatThread;
    
    private WorldObserverPort observer;

    private final java.util.Map<String, Route> activePeerRoutes = new java.util.concurrent.ConcurrentHashMap<>();

    public AgvController(Agv agv, BatchAssignmentUseCase batchAssignment, MovementUseCase movement, 
                         PathfinderPort pathfinder, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.batchAssignment = batchAssignment;
        this.movement = movement;
        this.pathfinder = pathfinder;
        this.broadcaster = broadcaster;

        this.batchAssignment.setListener(this);
    }

    public void setObserver(WorldObserverPort observer) {
        this.observer = observer;
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
                } catch (Throwable t) {
                    System.err.println("ERRO CRÍTICO NA THREAD DE HEARTBEAT:");
                    t.printStackTrace();
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
        br.usp.agv.logging.SystemLogger.info(agv.getStaticName(), "recebeu atribuição para " + order.orderId(), true);
        
        // Limpa qualquer rota órfã de robôs que já caíram / morreram
        java.util.Set<String> activeIds = batchAssignment.getActivePeerIds();
        activePeerRoutes.keySet().retainAll(activeIds);

        // Coleta todas as posições atualmente reservadas por outros robôs ativos
        java.util.Set<Position> routeObstacles = new java.util.HashSet<>();
        for (java.util.Map.Entry<String, Route> entry : activePeerRoutes.entrySet()) {
            if (!entry.getKey().equals(agv.getAgvId())) {
                routeObstacles.addAll(entry.getValue().waypoints());
            }
        }

        Route toPickup = null;
        try {
            toPickup = pathfinder.calculateRoute(agv.getCurrentPosition(), order.pickup(), routeObstacles, agv.getAgvId());
        } catch (Exception e) {
            // Fallback se estiver bloqueado pelas rotas ativas: calcula sem obstáculos dinâmicos.
            // Isso é aceitar um risco pequeno de esbarrão em vez de travar o pedido para sempre —
            // registrar aqui é o que permite perceber (e explicar) quando isso está acontecendo.
            br.usp.agv.logging.SystemLogger.info("ROTA", "Bloqueado por rotas ativas de outros AGVs ao ir para o pickup de "
                    + order.orderId() + " — recalculando sem desviar deles.", true);
            toPickup = pathfinder.calculateRoute(agv.getCurrentPosition(), order.pickup(), java.util.Set.of(), agv.getAgvId());
        }

        Route toDelivery = null;
        try {
            toDelivery = pathfinder.calculateRoute(order.pickup(), order.delivery(), routeObstacles, agv.getAgvId());
        } catch (Exception e) {
            br.usp.agv.logging.SystemLogger.info("ROTA", "Bloqueado por rotas ativas de outros AGVs ao ir para o delivery de "
                    + order.orderId() + " — recalculando sem desviar deles.", true);
            toDelivery = pathfinder.calculateRoute(order.pickup(), order.delivery(), java.util.Set.of(), agv.getAgvId());
        }

        if (movement != null) {
            movement.executeOrder(order, toPickup, toDelivery);
        }

        if (observer != null) {
            observer.onRouteCalculated(agv.getAgvId(), toPickup);
        }
    }

    @Override
    public void onRouteClaimed(String agvId, String orderId, Route route) {
        if (!agvId.equals(agv.getAgvId())) {
            activePeerRoutes.put(agvId, route);
        }
        if (observer != null) {
            observer.onRouteCalculated(agvId, route);
        }
    }

    @Override
    public void onRouteReleased(String agvId) {
        activePeerRoutes.remove(agvId);
        if (observer != null) {
            observer.onRouteReleased(agvId);
        }
    }

    @Override
    public void onOrderCompleted(String orderId) {
        batchAssignment.onOrderCompleted(orderId);
        // A limpeza da rota do robô que concluiu já acontece via onRouteReleased
        // (Route não carrega o orderId, então não dá pra filtrar por ele aqui).
        if (observer != null) {
            observer.onOrderCompleted(orderId);
        }
    }

    @Override
    public void onElectionReceived(String senderId) {
        batchAssignment.onElectionReceived(senderId);
    }

    @Override
    public void onOkReceived(String senderId) {
        batchAssignment.onOkReceived(senderId);
    }

    @Override
    public void onCoordinatorReceived(String senderId) {
        batchAssignment.onCoordinatorReceived(senderId);
    }

    @Override
    public void updateLamportClock(long receivedTimestamp) {
        agv.updateLamportClock(receivedTimestamp);
    }

    @Override
    public void dumpMemory() {
        batchAssignment.dumpMemory();
    }
}
