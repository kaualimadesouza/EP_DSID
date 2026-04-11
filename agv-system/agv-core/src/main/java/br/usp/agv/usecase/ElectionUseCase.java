package br.usp.agv.usecase;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.MessageBusPort;
import br.usp.agv.ports.outbound.PathfinderPort;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ElectionUseCase {

    private final Agv agv;
    private final PathfinderPort pathfinder;
    private final AgvBroadcaster broadcaster;

    // Peers ativos baseados em Heartbeats (agvId -> timestamp)
    private final Map<String, Long> activePeers = new ConcurrentHashMap<>();
    
    // Contagem de concessões por pedido (orderId -> Set de agvIds que desistiram para mim)
    private final Map<String, Set<String>> concedesReceived = new ConcurrentHashMap<>();

    private final Map<String, Candidacy> knownCandidacies = new HashMap<>();

    private ElectionListener listener;

    public interface ElectionListener {
        void onElectionWon(String orderId, Route route);
    }

    public void setElectionListener(ElectionListener listener) {
        this.listener = listener;
    }

    public ElectionUseCase(Agv agv, PathfinderPort pathfinder, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.pathfinder = pathfinder;
        this.broadcaster = broadcaster;
    }

    /**
     * Atualiza a lista de vizinhos vivos
     */
    public void onHeartbeatReceived(String peerId) {
        activePeers.put(peerId, System.currentTimeMillis());
    }

    private void cleanDeadPeers() {
        long now = System.currentTimeMillis();
        activePeers.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
    }

    public void startElection(Order order) {
        if (agv.getStatus() != AgvStatus.IDLE) return;

        cleanDeadPeers();
        int score = calculateScore(order);
        // calcula rotas sem obstáculos por enquanto
        Route route = pathfinder.calculateRoute(agv.getCurrentPosition(), order.pickup(), Set.of(), agv.getAgvId());

        Candidacy myCandidacy = new Candidacy(agv.getAgvId(), order.orderId(), score, route);
        knownCandidacies.put(order.orderId(), myCandidacy);
        concedesReceived.put(order.orderId(), new HashSet<>());

        agv.setStatus(AgvStatus.ELECTING);
        broadcaster.broadcastCandidacy(myCandidacy.orderId(), myCandidacy.score(), myCandidacy.route());

        // Dá um pequeno tempo (assíncrono) para as mensagens de outros candidatos
        // cruzarem o barramento antes da primeira verificação de vitória.
        // TODO isso eh necessario?
        new Thread(() -> {
            try {
                Thread.sleep(500);
                checkVictory(order.orderId());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void onElectionRequest(AgvMessage message) {
        String orderId = (String) message.payload().get("orderId");
        String senderId = message.senderId();
        int incomingScore = (int) message.payload().get("score");

        Candidacy currentBest = knownCandidacies.get(orderId);
        int myScore = (currentBest != null && currentBest.agvId().equals(agv.getAgvId())) 
                      ? currentBest.score() : Integer.MAX_VALUE;

        // Se o outro for melhor, eu desisto
        if (incomingScore < myScore || (incomingScore == myScore && senderId.compareTo(agv.getAgvId()) < 0)) {
            broadcaster.broadcastConcede(orderId);
            agv.setStatus(AgvStatus.IDLE);
        } else if (myScore != Integer.MAX_VALUE) {
            // Se eu for melhor, eu re-afirmo minha candidatura (Bully)
            broadcaster.broadcastCandidacy(currentBest.orderId(), currentBest.score(), currentBest.route());
        }
    }

    public void onConcedeReceived(String senderId, String orderId) {
        Set<String> concedes = concedesReceived.get(orderId);
        if (concedes != null) {
            concedes.add(senderId);
            checkVictory(orderId);
        }
    }

    private void checkVictory(String orderId) {
        if (hasWon(orderId) && listener != null) {
            Candidacy candidacy = knownCandidacies.get(orderId);
            if (candidacy != null && candidacy.route() != null) {
                // Evita disparar vitória múltiplas vezes para o mesmo pedido
                if (agv.getStatus() == AgvStatus.ELECTING) {
                    System.out.println("AGV ganhou" + agv.getAgvId());
                    listener.onElectionWon(orderId, candidacy.route());
                }
            }
        }
    }

    public boolean hasWon(String orderId) {
        cleanDeadPeers();
        Set<String> concedes = concedesReceived.get(orderId);
        
        // Se a eleição nem começou para este pedido, não ganhou
        if (concedes == null) return false;

        // Se há peers conhecidos, PRECISA das concessões de todos eles
        if (!activePeers.isEmpty()) {
            return concedes.containsAll(activePeers.keySet());
        }
        
        // Se realmente não há ninguém, ele ganha (o checkVictory tratará o timing)
        return true;
    }

    private int calculateScore(Order order) {
        return agv.getCurrentPosition().manhattanDistanceTo(order.pickup());
    }

}
