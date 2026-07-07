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
        int cols = args.length > 1 ? Integer.parseInt(args[0]) : 15;
        int rows = args.length > 1 ? Integer.parseInt(args[1]) : 15;

        SwingVisualizerAdapter ui = new SwingVisualizerAdapter(rows, cols);
        UdpMessageBusAdapter network = new UdpMessageBusAdapter();

        Map<String, Agv> knownAgvs = new ConcurrentHashMap<>();
        Map<String, Long> lastSeen = new ConcurrentHashMap<>(); // Timestamp do último sinal
        Map<String, Order> knownOrders = new ConcurrentHashMap<>();

        // Thread para limpar AGVs inativos (Timeout de 5 segundos)
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    long now = System.currentTimeMillis();
                    boolean changed = false;
                    
                    for (String agvId : lastSeen.keySet()) {
                        if (now - lastSeen.get(agvId) > 15000) { // 15 segundos sem sinal
                            System.out.println("Visualizer: AGV " + agvId + " removido por inatividade.");
                            knownAgvs.remove(agvId);
                            lastSeen.remove(agvId);
                            changed = true;
                        }
                    }
                    
                    if (changed) {
                        final java.util.List<Agv> finalAgvs = new ArrayList<>(knownAgvs.values());
                        final java.util.List<Order> finalOrders = new ArrayList<>(knownOrders.values());
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            ui.onSystemStateChanged(finalAgvs, finalOrders);
                        });
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "AGV-Cleanup-Thread").start();

        network.subscribe("agv-system", (AgvMessage msg) -> {
            javax.swing.SwingUtilities.invokeLater(() -> {
                try {
                    String senderId = msg.senderId();
                    if (senderId.equals("VISUALIZER")) return;

                    // Atualiza timestamp de atividade para qualquer mensagem recebida do AGV
                    lastSeen.put(senderId, System.currentTimeMillis());
                    
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
                            Position startPos = (route != null && !route.waypoints().isEmpty()) ? route.waypoints().get(0) : new Position(0,0);
                            knownAgvs.computeIfAbsent(senderId, id -> new Agv(id, startPos));
                            ui.onRouteCalculated(senderId, route);
                        } else if (msg.type() == MessageType.ROUTE_RELEASED) {
                            System.out.println("Visualizer: ROTA LIBERADA por " + senderId);
                            ui.onRouteReleased(senderId);
                        } else if (msg.type() == MessageType.ORDER_COMPLETED) {
                            String orderId = (String) msg.payload().get("orderId");
                            System.out.println("Visualizer: PEDIDO CONCLUÍDO: " + orderId);
                            knownOrders.remove(orderId);
                            ui.onOrderCompleted(orderId);
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
        });

        System.out.println("Visualizador de Rede Iniciado. Aguardando Heartbeats e Pedidos...");
    }
}
