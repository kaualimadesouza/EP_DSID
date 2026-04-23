package br.usp.agv.bootstrap;

import br.usp.agv.messaging.AgvMessageDispatcher;
import br.usp.agv.messaging.InMemoryMessageBus;
import br.usp.agv.model.Agv;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;
import br.usp.agv.pathfinder.AStarPathfinderAdapter;
import br.usp.agv.pathfinder.GridGraphAdapter;
import br.usp.agv.ui.SwingVisualizerAdapter;
import br.usp.agv.usecase.AgvBroadcaster;
import br.usp.agv.usecase.AgvController;
import br.usp.agv.usecase.BatchAssignmentUseCase;
import br.usp.agv.usecase.MovementUseCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simulador Local (Monolito, Sandbox).
 * Cria múltiplos AGVs no mesmo processo usando comunicação em memória ao invés de rede.
 */
public class SimulationMain {
    public static void main(String[] args) {
        int rows = 15;
        int cols = 15;

        // Infraestrutura Compartilhada
        GridGraphAdapter world = new GridGraphAdapter(rows, cols, Collections.emptySet());
        AStarPathfinderAdapter pathfinder = new AStarPathfinderAdapter(world);
        InMemoryMessageBus sharedBus = new InMemoryMessageBus();
        SwingVisualizerAdapter ui = new SwingVisualizerAdapter(rows, cols);

        // Criação dos AGVs (Nós locais)
        List<Agv> agvModels = new ArrayList<>();
        List<AgvController> controllers = new ArrayList<>();

        // AGV Alpha (Inicia no canto superior esquerdo)
        controllers.add(createAgv("Alpha", new Position(2, 2), pathfinder, sharedBus, ui, agvModels));

        // AGV Beta (Inicia no canto inferior esquerdo)
        controllers.add(createAgv("Beta", new Position(12, 2), pathfinder, sharedBus, ui, agvModels));
        
        // AGV Gamma (Inicia no centro-direita)
        controllers.add(createAgv("Gamma", new Position(7, 12), pathfinder, sharedBus, ui, agvModels));

        // 3. Estado inicial na UI
        ui.onSystemStateChanged(agvModels, Collections.emptyList());

        System.out.println("=== Simulador AGV Sandbox Iniciado ===");
        System.out.println("3 AGVs carregados (Alpha, Beta, Gamma).");
        System.out.println("Aguardando pedido de demonstração...");

        // 4. Simulação de um Cenário
        new Thread(() -> {
            try {
                Thread.sleep(4000);
                Order order = new Order("ORD-TEST-001", new Position(7, 7), new Position(0, 14));
                System.out.println("\n[EVENTO] Novo Pedido Criado no Centro do Grid!");
                
                // Em um sistema real, o Broker enviaria isso. Aqui, notificamos todos.
                for (AgvController c : controllers) {
                    c.onNewOrder(order);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static AgvController createAgv(String id, Position pos,
                                         AStarPathfinderAdapter pathfinder,
                                         InMemoryMessageBus bus,
                                         SwingVisualizerAdapter ui,
                                         List<Agv> modelList) {
        Agv agv = new Agv(id, pos);
        modelList.add(agv);

        AgvBroadcaster broadcaster = new AgvBroadcaster(agv, bus);

        BatchAssignmentUseCase batchAssignment = new BatchAssignmentUseCase(agv, pathfinder, broadcaster);
        MovementUseCase movement = new MovementUseCase(agv, broadcaster);

        AgvController controller = new AgvController(agv, batchAssignment, movement, pathfinder, broadcaster);
        AgvMessageDispatcher dispatcher = new AgvMessageDispatcher(controller, bus);

        dispatcher.start();
        controller.start();
        
        return controller;
    }
}
