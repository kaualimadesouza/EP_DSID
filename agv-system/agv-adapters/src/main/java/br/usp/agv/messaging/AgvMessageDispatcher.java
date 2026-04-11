package br.usp.agv.messaging;

import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.MessageType;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;
import br.usp.agv.ports.inbound.AgvController;
import br.usp.agv.ports.outbound.MessageBusPort;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adaptador de Entrada (Inbound Adapter).
 * Despacha mensagens brutas do MessageBus para métodos semânticos do AgvController.
 */
public class AgvMessageDispatcher {

    private final AgvController controller;
    private final MessageBusPort messageBus;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgvMessageDispatcher(AgvController controller, MessageBusPort messageBus) {
        this.controller = controller;
        this.messageBus = messageBus;
    }

    public void start() {
        messageBus.subscribe("agv-system", this::dispatch);
    }

    private void dispatch(AgvMessage message) {
        if (message.type() == MessageType.NEW_ORDER) {
            try {
                // Converte o payload genérico para objeto de domínio
                String id = (String) message.payload().get("orderId");
                Position pickup = mapper.convertValue(message.payload().get("pickup"), Position.class);
                Position delivery = mapper.convertValue(message.payload().get("delivery"), Position.class);
                
                Order order = new Order(id, pickup, delivery);
                controller.onNewOrder(order);
            } catch (Exception e) {
                System.err.println("Erro ao processar NEW_ORDER: " + e.getMessage());
            }
        } else {
            // Outras mensagens (Heartbeat, Election, etc) vão para processamento genérico no core
            controller.onMessageReceived(message);
        }
    }
}
