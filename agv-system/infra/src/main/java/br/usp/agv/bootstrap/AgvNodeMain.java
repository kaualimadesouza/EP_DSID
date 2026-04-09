package br.usp.agv.bootstrap;

import br.usp.agv.messaging.UdpMessageBusAdapter;
import br.usp.agv.model.Agv;
import br.usp.agv.model.Position;
import br.usp.agv.pathfinder.AStarPathfinderAdapter;
import br.usp.agv.pathfinder.GridGraphAdapter;
import br.usp.agv.usecase.AgvController;
import br.usp.agv.usecase.ElectionUseCase;
import br.usp.agv.usecase.MovementUseCase;

import br.usp.agv.model.MessageType;
import br.usp.agv.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Scanner;

public class AgvNodeMain {
    public static void main(String[] args) {
        String agvId = args.length > 0 ? args[0] : "AGV-" + System.currentTimeMillis() % 1000;
        int startX = args.length > 2 ? Integer.parseInt(args[1]) : 0;
        int startY = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        System.out.println("Iniciando Nó AGV: " + agvId + " em (" + startX + "," + startY + ")");

        // Infra
        GridGraphAdapter world = new GridGraphAdapter(15, 15, Collections.emptySet());
        AStarPathfinderAdapter pathfinder = new AStarPathfinderAdapter(world);
        UdpMessageBusAdapter network = new UdpMessageBusAdapter();

        // Core
        Agv agv = new Agv(agvId, new Position(startX, startY));
        
        // Observer que encaminha eventos para a rede (P2P)
        br.usp.agv.ports.outbound.WorldObserverPort networkObserver = 
                new br.usp.agv.messaging.NetworkObserverAdapter(agvId, network);

        ElectionUseCase election = new ElectionUseCase(agv, pathfinder, network);
        MovementUseCase movement = new MovementUseCase(agv, networkObserver, network); 
        
        AgvController controller = new AgvController(agv, election, movement, networkObserver, network);

        // Se inscreve para ouvir mensagens da rede
        network.subscribe("agv-system", (msg) -> {
            if (msg.type() == MessageType.NEW_ORDER) {
                ObjectMapper mapper = new ObjectMapper();
                String id = (String) msg.payload().get("orderId");
                Position pickup = mapper.convertValue(msg.payload().get("pickup"), Position.class);
                Position delivery = mapper.convertValue(msg.payload().get("delivery"), Position.class);
                
                Order order = new Order(id, pickup, delivery);
                System.out.println("Nó " + agvId + " recebeu novo pedido da rede: " + id);
                controller.onNewOrder(order);
            } else {
                controller.onMessageReceived(msg);
            }
        });

        controller.start();

        System.out.println("Nó " + agvId + " pronto e ouvindo na rede UDP.");
        
        // Mantém o processo vivo
        new Scanner(System.lineSeparator()).nextLine();
    }
}
