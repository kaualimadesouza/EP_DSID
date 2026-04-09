package br.usp.agv.usecase;

import br.usp.agv.model.Agv;
import br.usp.agv.model.Position;
import br.usp.agv.model.Route;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.MessageType;
import br.usp.agv.ports.outbound.MessageBusPort;
import br.usp.agv.ports.outbound.WorldObserverPort;

import java.util.HashMap;
import java.util.Map;

public class MovementUseCase {

    private final Agv agv;
    private final WorldObserverPort observer;
    private final MessageBusPort messageBus;

    public MovementUseCase(Agv agv, WorldObserverPort observer, MessageBusPort messageBus) {
        this.agv = agv;
        this.observer = observer;
        this.messageBus = messageBus;
    }

    public void executeRoute(Route route) {
        new Thread(() -> {
            try {
                agv.setStatus(br.usp.agv.model.AgvStatus.MOVING);
                System.out.println("AGV " + agv.getAgvId() + " iniciando rota...");

                for (Position p : route.waypoints()) {
                    Thread.sleep(300); // Latência de movimento
                    agv.setCurrentPosition(p);
                    
                    broadcastHeartbeat();

                    if (observer != null) {
                        System.out.println("AGV " + agv.getAgvId() + "moveu: (" + p.x() + "," + p.y() + ")");
                        observer.onAgvMoved(agv.getAgvId(), p);
                    }
                }
                System.out.println("AGV " + agv.getAgvId() + " concluiu rota.");
                agv.setStatus(br.usp.agv.model.AgvStatus.IDLE);
                broadcastHeartbeat(); // Notifica estado final
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void broadcastHeartbeat() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("position", agv.getCurrentPosition());
        payload.put("status", agv.getStatus());

        AgvMessage heartbeat = new AgvMessage(
                agv.getAgvId(),
                MessageType.HEARTBEAT,
                payload
        );
        messageBus.broadcast(heartbeat);
    }
}
