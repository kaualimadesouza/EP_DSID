package br.usp.agv.bootstrap;

import br.usp.agv.messaging.UdpMessageBusAdapter;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.MessageType;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Entra manualmente com pedidos por console -- com implementação de broker simulado
 */
public class OrderGeneratorMain {
    private static final Map<String, Order> activeOrders = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void main(String[] args) {
        UdpMessageBusAdapter network = new UdpMessageBusAdapter();

        // Listener para remover pedidos concluídos (atribuídos)
        network.subscribe("orders", message -> {
            if (message.type() == MessageType.ROUTE_CLAIMED) {
                String orderId = (String) message.payload().get("orderId");
                if (orderId != null && activeOrders.remove(orderId) != null) {
                    System.out.println("\n[BROKER] Pedido " + orderId + " confirmado e removido da fila de retransmissão.");
                    System.out.print("> "); // Re-imprime o prompt
                }
            }
        });

        // Thread de re-transmissão (Simula persistência do MOM)
        scheduler.scheduleAtFixedRate(() -> {
            if (activeOrders.isEmpty()) return;
            
            for (Order order : activeOrders.values()) {
                // Removemos o log periódico por pedido para não sujar o terminal
                sendOrderMsg(network, order, false);
            }
        }, 10, 10, TimeUnit.SECONDS);

        System.out.println("Gerador de pedidos iniciado.");
        System.out.println("Use: /new_order <px> <py> <dx> <dy>");
        System.out.println("Use: /multi_order <p1x> <p1y> <d1x> <d1y> [p2x p2y d2x d2y ...]");
        System.out.println("Use: /random_orders <n>");
        System.out.print("> ");

        Scanner scanner = new Scanner(System.in);
        java.util.Random random = new java.util.Random();

        while (true) {
            String input = scanner.nextLine().trim();

            if (input.startsWith("/new_order")) {
                handleNewOrder(network, input);
            } else if (input.startsWith("/multi_order")) {
                handleMultiOrder(network, input);
            } else if (input.startsWith("/random_orders")) {
                handleRandomOrders(network, input, random);
            } else if (input.startsWith("/clear")) {
                activeOrders.clear();
                System.out.println("Fila limpa.");
            }
            System.out.print("> ");
        }
    }

    private static void handleMultiOrder(UdpMessageBusAdapter network, String input) {
        try {
            String[] parts = input.split("\\s+");
            int numCoords = parts.length - 1;
            if (numCoords == 0 || numCoords % 4 != 0) {
                System.out.println("Formato inválido. Use: /multi_order <px> <py> <dx> <dy> [px py dx dy ...]");
                return;
            }
            for (int i = 0; i < numCoords / 4; i++) {
                int b = 1 + (i * 4);
                createAndSendOrder(network, Integer.parseInt(parts[b]), Integer.parseInt(parts[b+1]),
                        Integer.parseInt(parts[b+2]), Integer.parseInt(parts[b+3]));
            }
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    private static void handleRandomOrders(UdpMessageBusAdapter network, String input, java.util.Random rand) {
        try {
            String[] parts = input.split("\\s+");
            if (parts.length != 2) {
                System.out.println("Use: /random_orders <n>");
                return;
            }
            int n = Integer.parseInt(parts[1]);
            for (int i = 0; i < n; i++) {
                createAndSendOrder(network, rand.nextInt(15), rand.nextInt(15), rand.nextInt(15), rand.nextInt(15));
            }
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    private static void handleNewOrder(UdpMessageBusAdapter network, String input) {
        try {
            String[] parts = input.split("\\s+");
            if (parts.length != 5) {
                System.out.println("Formato inválido. Use: /new_order <px> <py> <dx> <dy>");
                return;
            }
            createAndSendOrder(network, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    private static void createAndSendOrder(UdpMessageBusAdapter network, int px, int py, int dx, int dy) {
        Order order = new Order("ORD-" + System.nanoTime(), new Position(px, py), new Position(dx, dy));
        activeOrders.put(order.orderId(), order);
        sendOrderMsg(network, order, true);
    }

    private static void sendOrderMsg(UdpMessageBusAdapter network, Order order, boolean verbose) {
        if (verbose) {
            System.out.printf("Disparando pedido: %s pickup(%s) -> delivery(%s)%n", order.orderId(), order.pickup(), order.delivery());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.orderId());
        payload.put("pickup", order.pickup());
        payload.put("delivery", order.delivery());

        AgvMessage orderMsg = new AgvMessage("GENERATOR", MessageType.NEW_ORDER, payload);
        network.broadcast(orderMsg);
    }
}
