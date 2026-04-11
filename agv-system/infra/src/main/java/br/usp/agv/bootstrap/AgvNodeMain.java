package br.usp.agv.bootstrap;

import br.usp.agv.messaging.AgvMessageDispatcher;
import br.usp.agv.messaging.UdpMessageBusAdapter;
import br.usp.agv.model.Agv;
import br.usp.agv.model.Position;
import br.usp.agv.pathfinder.AStarPathfinderAdapter;
import br.usp.agv.pathfinder.GridGraphAdapter;
import br.usp.agv.usecase.AgvBroadcaster;
import br.usp.agv.usecase.AgvController;
import br.usp.agv.usecase.ElectionUseCase;
import br.usp.agv.usecase.MovementUseCase;

import java.util.Collections;
import java.util.Scanner;

/**
 * Nó único AGV
 */
public class AgvNodeMain {
    public static void main(String[] args) {
        String agvId = args.length > 0 ? args[0] : "AGV-" + System.currentTimeMillis() % 1000;
        int startX = args.length > 2 ? Integer.parseInt(args[1]) : 0;
        int startY = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int gridWidth = args.length > 4 ? Integer.parseInt(args[3]) : 15;
        int gridHeight = args.length > 4 ? Integer.parseInt(args[4]) : 15;

        System.out.println("Iniciando Nó AGV: " + agvId + " em (" + startX + "," + startY + ") Grid: " + gridWidth + "x" + gridHeight);

        // Infra
        GridGraphAdapter world = new GridGraphAdapter(gridWidth, gridHeight, Collections.emptySet());
        AStarPathfinderAdapter pathfinder = new AStarPathfinderAdapter(world);
        UdpMessageBusAdapter network = new UdpMessageBusAdapter();

        // Core
        Agv agv = new Agv(agvId, new Position(startX, startY));
        AgvBroadcaster broadcaster = new AgvBroadcaster(agv, network);

        ElectionUseCase election = new ElectionUseCase(agv, pathfinder, broadcaster);
        MovementUseCase movement = new MovementUseCase(agv, broadcaster);

        AgvController controller = new AgvController(agv, election, movement, broadcaster);
        AgvMessageDispatcher dispatcher = new AgvMessageDispatcher(controller, network);

        dispatcher.start();
        controller.start();

        System.out.println("Nó " + agvId + " pronto e ouvindo na rede UDP.");
        
        // Mantém o processo vivo
        new Scanner(System.lineSeparator()).nextLine();
    }
}
