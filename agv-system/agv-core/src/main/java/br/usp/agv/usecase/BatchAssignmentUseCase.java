package br.usp.agv.usecase;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.PathfinderPort;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BatchAssignmentUseCase {

    private final Agv agv;
    private final PathfinderPort pathfinder;
    private final AgvBroadcaster broadcaster;

    private final Map<String, AgvSnapshot> activePeers = new ConcurrentHashMap<>();
    private final List<Order> pendingOrders = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> processedOrders = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Batch> proposedBatches = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> receivedAcks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean timerRunning = false;

    private BatchListener listener;

    public interface BatchListener {
        void onOrderAssigned(Order order);
    }

    public void setListener(BatchListener listener) {
        this.listener = listener;
    }

    public BatchAssignmentUseCase(Agv agv, PathfinderPort pathfinder, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.pathfinder = pathfinder;
        this.broadcaster = broadcaster;
    }

    public void onHeartbeatReceived(String peerId, Position position, AgvStatus status) {
        activePeers.put(peerId, new AgvSnapshot(peerId, position, status));
    }

    private void cleanDeadPeers() {
        // Assume heartbeats are at 1Hz, timeout after 5s
    }

    public boolean isLeader() {
        String myId = agv.getAgvId();
        String minId = myId;
        for (String id : activePeers.keySet()) {
            if (id.compareTo(minId) < 0) {
                minId = id;
            }
        }
        return myId.equals(minId);
    }

    public void onNewOrder(Order order) {
        // Ignora se já estamos processando ou processamos esse pedido
        if (processedOrders.contains(order.orderId())) return;
        
        // Verifica se já está na fila de pendentes (evita duplicação por retransmissão)
        synchronized (pendingOrders) {
            boolean alreadyPending = pendingOrders.stream().anyMatch(o -> o.orderId().equals(order.orderId()));
            if (alreadyPending) return;
        }

        if (!isLeader()) return;

        pendingOrders.add(order);
        if (!timerRunning) {
            timerRunning = true;
            scheduler.schedule(this::proposeBatch, 2, TimeUnit.SECONDS);
        }
    }

    private void proposeBatch() {
        timerRunning = false;
        List<Order> batchOrders;
        synchronized (pendingOrders) {
            if (pendingOrders.isEmpty()) return;
            batchOrders = new ArrayList<>(pendingOrders);
            pendingOrders.clear();
        }

        Map<String, AgvSnapshot> states = new HashMap<>(activePeers);
        states.put(agv.getAgvId(), new AgvSnapshot(agv.getAgvId(), agv.getCurrentPosition(), agv.getStatus()));

        Batch batch = new Batch("BATCH-" + System.currentTimeMillis(), batchOrders, states);
        broadcaster.broadcastBatchProposal(batch);
    }

    public void onBatchProposal(Batch batch) {
        proposedBatches.put(batch.batchId(), batch);
        receivedAcks.put(batch.batchId(), Collections.synchronizedSet(new HashSet<>()));
        
        // Marca pedidos do lote como "em processamento" para evitar entrar em novos lotes
        for (Order o : batch.orders()) {
            processedOrders.add(o.orderId());
        }

        broadcaster.broadcastBatchAck(batch.batchId());
        // Auto-ack for self if leader
        onBatchAck(agv.getAgvId(), batch.batchId());
    }

    public void onBatchAck(String senderId, String batchId) {
        Set<String> acks = receivedAcks.get(batchId);
        if (acks == null) return;

        acks.add(senderId);
        Batch batch = proposedBatches.get(batchId);
        if (batch != null) {
            Set<String> requiredPeers = batch.agvStates().keySet();
            if (acks.containsAll(requiredPeers)) {
                executeBatch(batch);
            }
        }
    }

    private synchronized void executeBatch(Batch batch) {
        // Remova para evitar processamento duplo
        if (proposedBatches.remove(batch.batchId()) == null) return;

        System.out.println("Executando lote: " + batch.batchId() + " com " + batch.orders().size() + " pedidos");

        List<Order> orders = new ArrayList<>(batch.orders());
        orders.sort(Comparator.comparing(Order::orderId));

        Map<String, AgvSnapshot> states = new HashMap<>(batch.agvStates());

        for (Order order : orders) {
            String bestAgvId = null;
            int minDistance = Integer.MAX_VALUE;

            for (AgvSnapshot state : states.values()) {
                // Um AGV só é elegível se estiver IDLE no snapshot DO LOTE
                if (state.status() == AgvStatus.IDLE) {
                    int dist = state.position().manhattanDistanceTo(order.pickup());
                    if (dist < minDistance) {
                        minDistance = dist;
                        bestAgvId = state.agvId();
                    } else if (dist == minDistance) {
                        // Desempate determinístico por ID
                        if (bestAgvId == null || state.agvId().compareTo(bestAgvId) < 0) {
                            bestAgvId = state.agvId();
                        }
                    }
                }
            }

            if (bestAgvId != null) {
                // Atribui pedido
                if (bestAgvId.equals(agv.getAgvId())) {
                    System.out.println("[TOTAL ORDERING] Atribuído a mim: " + order.orderId());
                    if (listener != null) {
                        listener.onOrderAssigned(order);
                    }
                }
                // Atualiza estado local para próxima iteração do loop de pedidos no mesmo lote
                AgvSnapshot old = states.get(bestAgvId);
                states.put(bestAgvId, new AgvSnapshot(old.agvId(), old.position(), AgvStatus.MOVING));
            } else {
                System.out.println("Nenhum AGV disponível para o pedido " + order.orderId() + ". Liberando para futura retransmissão.");
                processedOrders.remove(order.orderId());
            }
        }
    }
}
