package br.usp.agv.model;

public record AgvSnapshot(String agvId, Position position, AgvStatus status, long lastSeen) {
    public AgvSnapshot(String agvId, Position position, AgvStatus status) {
        this(agvId, position, status, System.currentTimeMillis());
    }
}
