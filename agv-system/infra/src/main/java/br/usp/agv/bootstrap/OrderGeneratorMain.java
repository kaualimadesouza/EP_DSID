package br.usp.agv.bootstrap;

import br.usp.agv.messaging.UdpMessageBusAdapter;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.MessageType;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class OrderGeneratorMain {
    public static void main(String[] args) {
        UdpMessageBusAdapter network = new UdpMessageBusAdapter();

        System.out.println("Gerador de pedidos iniciado.");
        System.out.println("Use: /new_order <px> <py> <dx> <dy>");
        System.out.println("Use: /multi_order <p1x> <p1y> <d1x> <d1y> [p2x p2y d2x d2y ...]");
        System.out.println("Use: /random_orders <n>");

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
            }
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
                sendOrder(network, Integer.parseInt(parts[b]), Integer.parseInt(parts[b+1]),
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
                sendOrder(network, rand.nextInt(15), rand.nextInt(15), rand.nextInt(15), rand.nextInt(15));
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
            sendOrder(network, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
        } catch (Exception e) {
            System.out.println("Erro: " + e.getMessage());
        }
    }

    private static void sendOrder(UdpMessageBusAdapter network, int px, int py, int dx, int dy) {
        System.out.printf("Disparando pedido: pickup(%d,%d) -> delivery(%d,%d)%n", px, py, dx, dy);
        Order order = new Order("ORD-" + System.nanoTime(), new Position(px, py), new Position(dx, dy));

        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", order.orderId());
        payload.put("pickup", order.pickup());
        payload.put("delivery", order.delivery());

        AgvMessage orderMsg = new AgvMessage("GENERATOR", MessageType.NEW_ORDER, payload);
        network.broadcast(orderMsg);
    }
}
