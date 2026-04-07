package br.usp.agv.bootstrap;

import br.usp.agv.model.Agv;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;
import br.usp.agv.pathfinder.AStarPathfinderAdapter;
import br.usp.agv.pathfinder.GridGraphAdapter;
import br.usp.agv.ui.SwingVisualizerAdapter;
import br.usp.agv.usecase.ElectionUseCase;
import br.usp.agv.messaging.InMemoryMessageBus;

import java.util.Collections;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        int rows = 15;
        int cols = 15;

        GridGraphAdapter world = new GridGraphAdapter(rows, cols, Collections.emptySet());
        AStarPathfinderAdapter pathfinder = new AStarPathfinderAdapter(world);
        InMemoryMessageBus bus = new InMemoryMessageBus();
        SwingVisualizerAdapter ui = new SwingVisualizerAdapter(rows, cols);

        Agv agv = new Agv("Alpha-1", new Position(2, 2));
        ElectionUseCase election = new ElectionUseCase(agv, pathfinder, bus);

        ui.onSystemStateChanged(List.of(agv), Collections.emptyList());

        System.out.println("Sistema AGV Inicializado. Aguardando pedido...");

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                Order order = new Order("ORD-001", new Position(10, 10), new Position(2, 12));
                System.out.println("Novo pedido: " + order.orderId());
                
                // No nosso caso simplificado, vamos pegar a rota diretamente da eleição para simular
                ElectionUseCase.Candidacy candidacy = election.startElection(order);
                ui.onOrderCreated(order);
                
                if (candidacy != null && candidacy.route() != null) {
                    ui.onRouteCalculated(agv.getAgvId(), candidacy.route());
                    
                    System.out.println("Iniciando movimento simulado...");
                    for (Position p : candidacy.route().waypoints()) {
                        Thread.sleep(300); // 300ms por passo
                        agv.setCurrentPosition(p);
                        ui.onAgvMoved(agv.getAgvId(), p);
                        ui.onSystemStateChanged(List.of(agv), List.of(order));
                    }
                    System.out.println("AGV chegou ao pickup!");
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
