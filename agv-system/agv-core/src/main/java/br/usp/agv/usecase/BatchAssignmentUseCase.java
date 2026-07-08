package br.usp.agv.usecase;

import br.usp.agv.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BatchAssignmentUseCase {

    private final Agv agv;
    private final AgvBroadcaster broadcaster;

    private final Map<String, AgvSnapshot> activePeers = new ConcurrentHashMap<>();
    private final List<Order> pendingOrders = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> processedOrders = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Batch> proposedBatches = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> receivedAcks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean timerRunning = false;

    private BatchListener listener;

    // Lógica de Liderança, Eleição Bully e Modo Fail-Safe
    private String currentLeaderId = null;
    private long lastLeaderHeartbeatSeen = System.currentTimeMillis();
    private long lastAnyPeerMessageTime = System.currentTimeMillis();
    private boolean isElecting = false;
    private volatile boolean okReceived = false;
    private java.util.concurrent.ScheduledFuture<?> electionTimeoutFuture = null;

    // Rastreamento de tarefas ativas para recuperação de órfãs
    private final Map<String, Order> activeAssignments = new ConcurrentHashMap<>();

    public interface BatchListener {
        void onOrderAssigned(Order order);
    }

    public void setListener(BatchListener listener) {
        this.listener = listener;
    }

    public BatchAssignmentUseCase(Agv agv, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.broadcaster = broadcaster;
        
        // Inicia limpeza periódica de peers inativos e monitoramento de rede/líder
        this.scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanDeadPeers();
                monitorLeader();
            } catch (Throwable t) {
                br.usp.agv.logging.SystemLogger.error("MONITOR", "Erro no monitoramento periódico", t);
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    public void onHeartbeatReceived(String peerId, Position position, AgvStatus status) {
        activePeers.put(peerId, new AgvSnapshot(peerId, position, status));
        lastAnyPeerMessageTime = System.currentTimeMillis();
        
        if (peerId.equals(currentLeaderId)) {
            lastLeaderHeartbeatSeen = System.currentTimeMillis();
        }
    }

    private void cleanDeadPeers() {
        long timeout = 10000; // 10 segundos sem heartbeat
        long now = System.currentTimeMillis();
        
        List<String> deadPeers = new ArrayList<>();
        for (Map.Entry<String, AgvSnapshot> entry : activePeers.entrySet()) {
            if ((now - entry.getValue().lastSeen()) > timeout) {
                deadPeers.add(entry.getKey());
            }
        }
        
        for (String deadPeer : deadPeers) {
            activePeers.remove(deadPeer);
            br.usp.agv.logging.SystemLogger.info("P2P", "Peer " + Agv.getStaticNameFromId(deadPeer) + " removido por inatividade.", true);
            
            // Se nós somos o líder, recuperamos a tarefa dele se houver
            if (isLeader()) {
                handleOrphanTasksFor(deadPeer);
            }
        }
    }

    private void monitorLeader() {
        long timeout = 10000; // 10s
        long now = System.currentTimeMillis();
        
        // Fail-Safe: Se não ouvirmos nada na rede nos últimos 6s e houver outros nós ativos,
        // entra em modo Fail-Safe para evitar colisões
        if (now - lastAnyPeerMessageTime > 6000 && !activePeers.isEmpty()) {
            if (agv.getStatus() == AgvStatus.MOVING) {
                br.usp.agv.logging.SystemLogger.info("FAIL-SAFE", "Perda de rede detectada (silêncio de 6s). Parando AGV!", true);
                agv.setStatus(AgvStatus.FAIL_SAFE);
            }
        } else {
            // Rede recuperada ou nenhum outro nó ativo
            if (agv.getStatus() == AgvStatus.FAIL_SAFE) {
                br.usp.agv.logging.SystemLogger.info("FAIL-SAFE", "Conectividade restabelecida. Retomando movimentação.", true);
                agv.setStatus(AgvStatus.MOVING);
            }
        }

        if (currentLeaderId != null) {
            if (!isLeader()) {
                if (now - lastLeaderHeartbeatSeen > timeout) {
                    br.usp.agv.logging.SystemLogger.info("LÍDER", "Líder " + Agv.getStaticNameFromId(currentLeaderId) + " caiu (sem heartbeat por 10s). Iniciando eleição...", true);
                    currentLeaderId = null;
                    startElection();
                }
            }
        } else {
            // Sem líder estabelecido há mais de 5s desde o último contato
            if (now - lastLeaderHeartbeatSeen > 5000 && !isElecting) {
                br.usp.agv.logging.SystemLogger.info("LÍDER", "Nenhum líder conhecido. Iniciando eleição...", true);
                startElection();
            }
        }
    }

    private synchronized void startElection() {
        if (isElecting) return;
        isElecting = true;
        okReceived = false;
        
        String myStaticName = agv.getStaticName();
        br.usp.agv.logging.SystemLogger.info("ELEIÇÃO", "Iniciando algoritmo Bully por " + agv.getStaticName(), true);
        
        List<String> higherIds = new ArrayList<>();
        for (String peerId : activePeers.keySet()) {
            String peerStatic = Agv.getStaticNameFromId(peerId);
            if (peerStatic.compareTo(myStaticName) > 0) {
                higherIds.add(peerId);
            }
        }
        
        if (higherIds.isEmpty()) {
            // Nenhum nó superior ativo, declaramos vitória
            becomeLeader();
        } else {
            // Broadcast ELECTION para a rede
            broadcaster.broadcastElection();
            
            // Aguarda OK por 2 segundos
            if (electionTimeoutFuture != null) {
                electionTimeoutFuture.cancel(false);
            }
            electionTimeoutFuture = scheduler.schedule(() -> {
                synchronized (this) {
                    try {
                        if (!okReceived) {
                            br.usp.agv.logging.SystemLogger.info("ELEIÇÃO", "Nenhum OK recebido de nós superiores. Assumindo coordenação.", true);
                            becomeLeader();
                        } else {
                            br.usp.agv.logging.SystemLogger.info("ELEIÇÃO", "OK recebido. Aguardando COORDINATOR...", false);
                            // Timeout secundário para aguardar mensagem do novo líder
                            scheduler.schedule(() -> {
                                try {
                                    if (currentLeaderId == null && isElecting) {
                                        br.usp.agv.logging.SystemLogger.info("ELEIÇÃO", "COORDINATOR não recebido. Reiniciando eleição...", true);
                                        isElecting = false;
                                        startElection();
                                    }
                                } catch (Throwable t) {
                                    br.usp.agv.logging.SystemLogger.error("ELEIÇÃO", "Erro no timeout secundário de eleição", t);
                                }
                            }, 5, TimeUnit.SECONDS);
                        }
                    } catch (Throwable t) {
                        br.usp.agv.logging.SystemLogger.error("ELEIÇÃO", "Erro no timeout de eleição", t);
                    }
                }
            }, 2000, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void becomeLeader() {
        isElecting = false;
        currentLeaderId = agv.getAgvId();
        lastLeaderHeartbeatSeen = System.currentTimeMillis();
        br.usp.agv.logging.SystemLogger.info("LÍDER", "Eu (" + agv.getStaticName() + ") assumi como Líder Coordenador (Primário).", true);
        broadcaster.broadcastCoordinator();
        
        // Recupera tarefas órfãs de qualquer nó que tenha morrido antes
        recoverOrphanTasks();
    }

    public void onElectionReceived(String senderId) {
        lastAnyPeerMessageTime = System.currentTimeMillis();
        String myStatic = agv.getStaticName();
        String senderStatic = Agv.getStaticNameFromId(senderId);
        
        br.usp.agv.logging.SystemLogger.info("ELEIÇÃO", "ELECTION recebida de " + Agv.getStaticNameFromId(senderId), false);
        if (myStatic.compareTo(senderStatic) > 0) {
            br.usp.agv.logging.SystemLogger.info("ELEIÇÃO", "Enviando OK para " + Agv.getStaticNameFromId(senderId), false);
            broadcaster.broadcastOk();
            startElection();
        }
    }

    public void onOkReceived(String senderId) {
        lastAnyPeerMessageTime = System.currentTimeMillis();
        String myStatic = agv.getStaticName();
        String senderStatic = Agv.getStaticNameFromId(senderId);
        
        if (senderStatic.compareTo(myStatic) > 0) {
            br.usp.agv.logging.SystemLogger.info("ELEIÇÃO", "OK recebido de " + Agv.getStaticNameFromId(senderId), false);
            okReceived = true;
        }
    }

    public void onCoordinatorReceived(String senderId) {
        lastAnyPeerMessageTime = System.currentTimeMillis();
        String myStatic = agv.getStaticName();
        String senderStatic = Agv.getStaticNameFromId(senderId);
        
        if (senderStatic.compareTo(myStatic) < 0) {
            br.usp.agv.logging.SystemLogger.info("LÍDER", "COORDINATOR ignorado de " + senderStatic + " (ID inferior ao meu). Iniciando eleição...", true);
            startElection();
            return;
        }
        
        br.usp.agv.logging.SystemLogger.info("LÍDER", "COORDINATOR recebido de " + senderStatic + ". Atualizando líder.", true);
        currentLeaderId = senderId;
        lastLeaderHeartbeatSeen = System.currentTimeMillis();
        isElecting = false;
    }

    public boolean isLeader() {
        return agv.getAgvId().equals(currentLeaderId);
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
        try {
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
            
            // Inicializa localmente antes do broadcast para evitar condições de corrida com ACKs rápidos
            proposedBatches.put(batch.batchId(), batch);
            Set<String> acks = Collections.synchronizedSet(new HashSet<>());
            acks.add(agv.getAgvId()); // O próprio líder já dá ACK localmente
            receivedAcks.put(batch.batchId(), acks);
            for (Order o : batch.orders()) {
                processedOrders.add(o.orderId());
            }

            broadcaster.broadcastBatchProposal(batch);
            broadcaster.broadcastBatchAck(batch.batchId()); // Envia o ACK do líder para os backups na rede
            
            int required = batch.agvStates().size();
            if (acks.size() >= required) {
                executeBatch(batch);
            }
        } catch (Throwable t) {
            br.usp.agv.logging.SystemLogger.error("LOTE", "ERRO CRÍTICO EM PROPOSE_BATCH", t);
        }
    }

    public void onBatchProposal(Batch batch) {
        lastAnyPeerMessageTime = System.currentTimeMillis();
        
        // Usa putIfAbsent para não sobrescrever o conjunto se o líder já começou a coletar ACKs
        proposedBatches.putIfAbsent(batch.batchId(), batch);
        receivedAcks.putIfAbsent(batch.batchId(), Collections.synchronizedSet(new HashSet<>()));
        
        for (Order o : batch.orders()) {
            processedOrders.add(o.orderId());
        }

        broadcaster.broadcastBatchAck(batch.batchId());
        onBatchAck(agv.getAgvId(), batch.batchId());
    }

    public void onBatchAck(String senderId, String batchId) {
        lastAnyPeerMessageTime = System.currentTimeMillis();
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

        br.usp.agv.logging.SystemLogger.info("REPLICAÇÃO", "Replicação total concluída para o lote: " + batch.batchId(), true);
        br.usp.agv.logging.SystemLogger.info("LOTE", "Executando lote: " + batch.batchId() + " com " + batch.orders().size() + " pedidos", true);

        List<Order> orders = new ArrayList<>(batch.orders());
        orders.sort(Comparator.comparing(Order::orderId));

        Map<String, AgvSnapshot> states = new HashMap<>(batch.agvStates());

        for (Order order : orders) {
            String bestAgvId = getBestAgvId(order, states);

            if (bestAgvId != null) {
                // Rastreia atribuição ativa
                activeAssignments.put(bestAgvId, order);

                // Atribui pedido
                if (bestAgvId.equals(agv.getAgvId())) {
                    br.usp.agv.logging.SystemLogger.info("TOTAL ORDERING", "Atribuído a mim: " + order.orderId(), true);
                    if (listener != null) {
                        listener.onOrderAssigned(order);
                    }
                }
                // Atualiza estado local para próxima iteração do loop de pedidos no mesmo lote
                states.computeIfPresent(bestAgvId, (k, old) -> new AgvSnapshot(old.agvId(), old.position(), AgvStatus.MOVING, old.lastSeen()));
            } else {
                br.usp.agv.logging.SystemLogger.info("TOTAL ORDERING", "Nenhum AGV disponível para o pedido " + order.orderId() + ". Liberando para futura retransmissão.", true);
                processedOrders.remove(order.orderId());
            }
        }
    }

    private static String getBestAgvId(Order order, Map<String, AgvSnapshot> states) {
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
        return bestAgvId;
    }

    private synchronized void recoverOrphanTasks() {
        br.usp.agv.logging.SystemLogger.info("ÓRFÃOS", "Líder verificando se há tarefas órfãs para recuperar...", false);
        for (Map.Entry<String, Order> entry : activeAssignments.entrySet()) {
            String peerId = entry.getKey();
            if (!activePeers.containsKey(peerId) && !peerId.equals(agv.getAgvId())) {
                handleOrphanTasksFor(peerId);
            }
        }
    }

    private synchronized void handleOrphanTasksFor(String deadPeerId) {
        Order orphanOrder = activeAssignments.remove(deadPeerId);
        if (orphanOrder != null) {
            br.usp.agv.logging.SystemLogger.info("ÓRFÃOS", "Recuperando tarefa órfã " + orphanOrder.orderId() + " de " + Agv.getStaticNameFromId(deadPeerId) + " para retransmissão.", true);
            processedOrders.remove(orphanOrder.orderId());
            synchronized (pendingOrders) {
                pendingOrders.add(orphanOrder);
            }
            
            if (!timerRunning) {
                timerRunning = true;
                scheduler.schedule(this::proposeBatch, 2, TimeUnit.SECONDS);
            }
        }
    }

    public void onOrderCompleted(String orderId) {
        activeAssignments.entrySet().removeIf(entry -> entry.getValue().orderId().equals(orderId));
        processedOrders.add(orderId);
    }

    public void dumpMemory() {
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "===== DUMP DE MEMÓRIA (" + agv.getStaticName() + ") =====", true);
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Líder Ativo: " + (currentLeaderId != null ? Agv.getStaticNameFromId(currentLeaderId) : "Nenhum"), true);
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Estado do AGV: " + agv.getStatus(), true);
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Posição Atual: " + agv.getCurrentPosition(), true);
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Peers Ativos: " + activePeers.keySet(), true);
        
        java.util.List<String> pendings = new java.util.ArrayList<>();
        synchronized (pendingOrders) {
            for (Order o : pendingOrders) {
                pendings.add(o.orderId());
            }
        }
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Fila de Pendentes (pendingOrders): " + pendings, true);
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Pedidos Processados (processedOrders): " + processedOrders, true);
        
        java.util.List<String> assignments = new java.util.ArrayList<>();
        for (Map.Entry<String, Order> entry : activeAssignments.entrySet()) {
            assignments.add(Agv.getStaticNameFromId(entry.getKey()) + " -> " + entry.getValue().orderId());
        }
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Atribuições Ativas (activeAssignments): " + assignments, true);
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "Propostas de Lote (proposedBatches): " + proposedBatches.keySet(), true);
        br.usp.agv.logging.SystemLogger.info("DEBUG-MEM", "=============================================", true);
    }
}
