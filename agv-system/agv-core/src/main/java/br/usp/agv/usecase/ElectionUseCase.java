package br.usp.agv.usecase;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.MessageBusPort;
import br.usp.agv.ports.outbound.PathfinderPort;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ElectionUseCase {

    private final Agv agv;
    private final PathfinderPort pathfinder;
    private final MessageBusPort messageBus;

    /**
     * Candidaturas que este AGV já viu (inclusive a própria)
     * key: orderId, value: melhor candidatura conhecida até agora
     **/
    private final Map<String, Candidacy> knownCandidacies = new HashMap<>();

    public ElectionUseCase(Agv agv, PathfinderPort pathfinder, MessageBusPort messageBus) {
        this.agv = agv;
        this.pathfinder = pathfinder;
        this.messageBus = messageBus;
    }

    /**
     * Melhor candidatura é o menor peso; desempate por menor agvId enquanto não temos clock
     **/
    private static Candidacy bestCandidacy(Candidacy a, Candidacy b) {
        if (a.score() != b.score()) {
            return a.score() < b.score() ? a : b;
        }
        return a.agvId().compareTo(b.agvId()) < 0 ? a : b;
    }

    public Candidacy startElection(Order order) {
        if (agv.getStatus() != AgvStatus.IDLE) {
            return null; // ocupado, não participa
        }

        int score = calculateScore(order);

        Route route = pathfinder.calculateRoute(
                agv.getCurrentPosition(),
                order.pickup(),
                Set.of(),   // sem obstáculos por enquanto
                agv.getAgvId()
        );

        Candidacy myCandidacy = new Candidacy(agv.getAgvId(), order.orderId(), score, route);
        knownCandidacies.put(order.orderId(), myCandidacy);

        agv.setStatus(AgvStatus.ELECTING);

        broadcastCandidacy(myCandidacy);
        
        return myCandidacy;
    }

    public void onElectionRequest(AgvMessage message) {
        String orderId = (String) message.payload().get("orderId");
        String senderId = message.senderId();
        int score = (int) message.payload().get("score");

        Candidacy incoming = new Candidacy(senderId, orderId, score, null);

        knownCandidacies.merge(orderId, incoming, ElectionUseCase::bestCandidacy);
    }

    private int calculateScore(Order order) {
        return agv.getCurrentPosition().manhattanDistanceTo(order.pickup());
    }

    private void broadcastCandidacy(Candidacy candidacy) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", candidacy.orderId());
        payload.put("score", candidacy.score());

        AgvMessage msg = new AgvMessage(
                agv.getAgvId(),
                MessageType.ELECTION_REQUEST,
                payload
        );

        messageBus.broadcast(msg);
    }

    /**
     * Estado interno
     **/
    public record Candidacy(String agvId, String orderId, int score, Route route) {
    }
}