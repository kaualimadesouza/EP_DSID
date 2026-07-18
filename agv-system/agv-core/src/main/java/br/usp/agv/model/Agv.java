package br.usp.agv.model;

import java.util.UUID;

public class Agv {

    private final String staticName; // nome de apresentacao
    private final String sessionUuid; // ID gerado ao ligar
    private final String agvId;
    // volatile: as threads locais de movimento, heartbeat
    // scheduler precisam ler o valor corrente compartilhado da posicao e status (sem salvar copia em cache)
    private volatile Position currentPosition;
    private volatile AgvStatus status;
    private Order currentOrder;    // null se ocioso
    private long lamportClock = 0;

    public Agv(String staticName, Position initialPosition) {
        this.staticName = staticName;
        this.sessionUuid = UUID.randomUUID().toString();
        this.agvId = staticName + "-" + this.sessionUuid;
        this.currentPosition = initialPosition;
        this.status = AgvStatus.IDLE;
        this.currentOrder = null;
    }

    public String getStaticName() {
        return staticName;
    }

    public String getSessionUuid() {
        return sessionUuid;
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

    public synchronized long getLamportClock() {
        return lamportClock;
    }

    public synchronized long incrementAndGetLamportClock() {
        return ++lamportClock;
    }

    public synchronized void updateLamportClock(long receivedTimestamp) {
        this.lamportClock = Math.max(this.lamportClock, receivedTimestamp) + 1;
    }

    public static String getStaticNameFromId(String id) {
        if (id == null) return "";
        String[] parts = id.split("-");
        if (parts.length >= 2 && parts[0].equals("AGV")) {
            return parts[0] + "-" + parts[1];
        }
        return id;
    }
}
