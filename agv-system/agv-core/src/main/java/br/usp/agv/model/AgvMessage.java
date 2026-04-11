package br.usp.agv.model;

import java.util.Map;

public record AgvMessage(String senderId, MessageType type, Map<String, Object> payload) {

    public static AgvMessage heartbeat(Agv agv) {
        return new AgvMessage(
                agv.getAgvId(),
                MessageType.HEARTBEAT,
                Map.of(
                        "position", agv.getCurrentPosition(),
                        "status", agv.getStatus()
                )
        );
    }

    public static AgvMessage candidacy(Agv agv, String orderId, int score, Route route) {
        return new AgvMessage(
                agv.getAgvId(),
                MessageType.ELECTION_REQUEST,
                Map.of(
                        "orderId", orderId,
                        "score", score,
                        "route", route
                )
        );
    }

    public static AgvMessage concede(Agv agv, String orderId) {
        return new AgvMessage(
                agv.getAgvId(),
                MessageType.ELECTION_CONCEDE,
                Map.of("orderId", orderId)
        );
    }

    public static AgvMessage routeClaimed(Agv agv, Route route) {
        return new AgvMessage(
                agv.getAgvId(),
                MessageType.ROUTE_CLAIMED,
                Map.of(
                        "agvId", agv.getAgvId(),
                        "route", route
                )
        );
    }
}
