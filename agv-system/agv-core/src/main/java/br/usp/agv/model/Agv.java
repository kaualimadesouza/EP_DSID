package br.usp.agv.model;

public class Agv {

    private final String agvId;
    private Position currentPosition;
    private AgvStatus status;
    private Order currentOrder;    // null se ocioso

    public Agv(String agvId, Position initialPosition) {
        this.agvId = agvId;
        this.currentPosition = initialPosition;
        this.status = AgvStatus.IDLE;
        this.currentOrder = null;
    }

    public String getAgvId() {
        return agvId;
    }

    public Position getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(Position p) {
        this.currentPosition = p;
    }

    public AgvStatus getStatus() {
        return status;
    }

    public void setStatus(AgvStatus s) {
        this.status = s;
    }

    public Order getCurrentOrder() {
        return currentOrder;
    }

    public void setCurrentOrder(Order o) {
        this.currentOrder = o;
    }
}
