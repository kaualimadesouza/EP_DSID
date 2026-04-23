package br.usp.agv.messaging;

import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;
import br.usp.agv.ports.inbound.AgvController;
import br.usp.agv.ports.outbound.MessageBusPort;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adaptador de Entrada (Inbound Adapter).
 * Despacha mensagens do MessageBus para o AgvController.
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
        try {
            switch (message.type()) {
                case NEW_ORDER -> {
                    String id = (String) message.payload().get("orderId");
                    Position pickup = mapper.convertValue(message.payload().get("pickup"), Position.class);
                    Position delivery = mapper.convertValue(message.payload().get("delivery"), Position.class);
                    controller.onNewOrder(new Order(id, pickup, delivery));
                }
                case BATCH_PROPOSAL -> {
                    br.usp.agv.model.Batch batch = mapper.convertValue(message.payload().get("batch"), br.usp.agv.model.Batch.class);
                    controller.onBatchProposal(batch);
                }
                case BATCH_ACK -> {
                    String batchId = (String) message.payload().get("batchId");
                    controller.onBatchAck(message.senderId(), batchId);
                }
                case HEARTBEAT -> {
                    Position pos = mapper.convertValue(message.payload().get("position"), Position.class);
                    br.usp.agv.model.AgvStatus status = br.usp.agv.model.AgvStatus.valueOf(message.payload().get("status").toString());
                    controller.onHeartbeatReceived(message.senderId(), pos, status);
                }
                case ROUTE_CLAIMED -> {
                    String agvId = (String) message.payload().get("agvId");
                    String orderId = (String) message.payload().get("orderId");
                    br.usp.agv.model.Route route = mapper.convertValue(message.payload().get("route"), br.usp.agv.model.Route.class);
                    controller.onRouteClaimed(agvId, orderId, route);
                }
                case ROUTE_RELEASED -> {
                    String agvId = (String) message.payload().get("agvId");
                    controller.onRouteReleased(agvId);
                }
                case ORDER_COMPLETED -> {
                    String orderId = (String) message.payload().get("orderId");
                    controller.onOrderCompleted(orderId);
                }
                default -> System.out.println("Mensagem não suportada: " + message.type());
            }
        } catch (Exception e) {
            System.err.println("Erro ao despachar mensagem " + message.type() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
