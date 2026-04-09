package br.usp.agv.bootstrap;

import br.usp.agv.messaging.UdpMessageBusAdapter;
import br.usp.agv.model.*;
import br.usp.agv.ui.SwingVisualizerAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VisualizerMain {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        int rows = 15;
        int cols = 15;

        SwingVisualizerAdapter ui = new SwingVisualizerAdapter(rows, cols);
        UdpMessageBusAdapter network = new UdpMessageBusAdapter();

        Map<String, Agv> knownAgvs = new ConcurrentHashMap<>();
        Map<String, Order> knownOrders = new ConcurrentHashMap<>();

        network.subscribe("agv-system", (AgvMessage msg) -> {
            try {
                String senderId = msg.senderId();
                
                if (msg.type() == MessageType.NEW_ORDER) {
                    System.out.println("Visualizer: Recebeu NEW_ORDER de " + senderId);
                    String id = (String) msg.payload().get("orderId");

                    // Deserialização segura das posições
                    Position pickup = mapper.convertValue(msg.payload().get("pickup"), Position.class);
                    Position delivery = mapper.convertValue(msg.payload().get("delivery"), Position.class);

                    System.out.println("DEBUG: Pedido " + id + " Pickup: (" + pickup.x() + "," + pickup.y() + ") Delivery: (" + delivery.x() + "," + delivery.y() + ")");

                    Order order = new Order(id, pickup, delivery);
                    knownOrders.put(id, order);
                    ui.onOrderCreated(order);
                }
 else if (!senderId.equals("VISUALIZER")) {
                    if (msg.type() == MessageType.HEARTBEAT) {
                        Agv agv = knownAgvs.computeIfAbsent(senderId, id -> new Agv(id, new Position(0,0)));
                        Object posObj = msg.payload().get("position");
                        Position newPos = mapper.convertValue(posObj, Position.class);
                        agv.setCurrentPosition(newPos);
                        ui.onAgvMoved(senderId, newPos);
                    } else if (msg.type() == MessageType.ROUTE_CLAIMED) {
                        br.usp.agv.model.Route route = mapper.convertValue(msg.payload().get("route"), br.usp.agv.model.Route.class);
                        System.out.println("Visualizer: Recebeu ROTA de " + senderId);
                        ui.onRouteCalculated(senderId, route);
                    }
                }
                
                // Atualiza o estado geral da UI
                ui.onSystemStateChanged(
                    new ArrayList<>(knownAgvs.values()), 
                    new ArrayList<>(knownOrders.values())
                );
            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem no Visualizer: " + e.getMessage());
                e.printStackTrace();
            }
        });

        System.out.println("Visualizador de Rede Iniciado. Aguardando Heartbeats e Pedidos...");
    }
}
