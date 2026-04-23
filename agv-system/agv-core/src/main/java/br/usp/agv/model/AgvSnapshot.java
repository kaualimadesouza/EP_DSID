package br.usp.agv.model;

public record AgvSnapshot(String agvId, Position position, AgvStatus status) {
}
