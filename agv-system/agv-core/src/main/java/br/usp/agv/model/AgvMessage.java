package br.usp.agv.model;

import java.util.Map;

public record AgvMessage(
    String senderId,
    int sequenceNumber,
    long lamportTimestamp,
    MessageType type,
    Map<String, Object> payload
) {

    public static AgvMessage heartbeat(Agv agv, int seq) {
        return new AgvMessage(
                agv.getAgvId(),
                seq,
                agv.getLamportClock(),
                MessageType.HEARTBEAT,
                Map.of(
                        "position", agv.getCurrentPosition(),
                        "status", agv.getStatus()
                )
        );
    }

    public static AgvMessage batchProposal(String senderId, int seq, long clock, Batch batch) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.BATCH_PROPOSAL,
                Map.of("batch", batch)
        );
    }

    public static AgvMessage batchAck(String senderId, int seq, long clock, String batchId) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.BATCH_ACK,
                Map.of("batchId", batchId)
        );
    }

    public static AgvMessage routeClaimed(Agv agv, int seq, String orderId, Route route) {
        return new AgvMessage(
                agv.getAgvId(),
                seq,
                agv.getLamportClock(),
                MessageType.ROUTE_CLAIMED,
                Map.of(
                        "agvId", agv.getAgvId(),
                        "orderId", orderId,
                        "route", route
                )
        );
    }

    public static AgvMessage routeReleased(String agvId, int seq, long clock) {
        return new AgvMessage(
                agvId,
                seq,
                clock,
                MessageType.ROUTE_RELEASED,
                Map.of("agvId", agvId)
        );
    }

    public static AgvMessage orderCompleted(String senderId, int seq, long clock, String orderId) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.ORDER_COMPLETED,
                Map.of("orderId", orderId)
        );
    }

    public static AgvMessage nackRequest(String senderId, int seq, long clock, String targetSenderId, int targetSeq) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.NACK_REQUEST,
                Map.of(
                        "targetSenderId", targetSenderId,
                        "targetSequenceNumber", targetSeq
                )
        );
    }

    public static AgvMessage nackResponse(String senderId, int seq, long clock, AgvMessage lostMessage) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.NACK_RESPONSE,
                Map.of("lostMessage", lostMessage)
        );
    }

    public static AgvMessage election(String senderId, int seq, long clock) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.ELECTION,
                Map.of()
        );
    }

    public static AgvMessage ok(String senderId, int seq, long clock) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.OK,
                Map.of()
        );
    }

    public static AgvMessage coordinator(String senderId, int seq, long clock) {
        return new AgvMessage(
                senderId,
                seq,
                clock,
                MessageType.COORDINATOR,
                Map.of()
        );
    }
}

