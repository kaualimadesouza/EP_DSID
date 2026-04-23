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

    public static AgvMessage batchProposal(String senderId, Batch batch) {
        return new AgvMessage(
                senderId,
                MessageType.BATCH_PROPOSAL,
                Map.of("batch", batch)
        );
    }

    public static AgvMessage batchAck(String senderId, String batchId) {
        return new AgvMessage(
                senderId,
                MessageType.BATCH_ACK,
                Map.of("batchId", batchId)
        );
    }

    public static AgvMessage routeClaimed(Agv agv, String orderId, Route route) {
        return new AgvMessage(
                agv.getAgvId(),
                MessageType.ROUTE_CLAIMED,
                Map.of(
                        "agvId", agv.getAgvId(),
                        "orderId", orderId,
                        "route", route
                )
        );
    }
}
