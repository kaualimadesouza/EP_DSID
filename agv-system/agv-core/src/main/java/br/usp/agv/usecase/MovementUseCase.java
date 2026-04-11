package br.usp.agv.usecase;

import br.usp.agv.model.*;
import br.usp.agv.ports.outbound.MessageBusPort;

import java.util.HashMap;
import java.util.Map;

public class MovementUseCase {

    private final Agv agv;
    private final AgvBroadcaster broadcaster;

    public MovementUseCase(Agv agv, AgvBroadcaster broadcaster) {
        this.agv = agv;
        this.broadcaster = broadcaster;
    }

    public void executeRoute(Route route) {
        new Thread(() -> {
            try {
                agv.setStatus(br.usp.agv.model.AgvStatus.MOVING);
                System.out.println("AGV " + agv.getAgvId() + " iniciando rota...");

                for (Position p : route.waypoints()) {
                    Thread.sleep(300); // Latência de movimento
                    agv.setCurrentPosition(p);
                    
                    broadcaster.broadcastHeartbeat();
                }
                System.out.println("AGV " + agv.getAgvId() + " concluiu rota.");
                agv.setStatus(br.usp.agv.model.AgvStatus.IDLE);
                broadcaster.broadcastHeartbeat(); // Notifica estado final
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
