package br.usp.agv.ports.inbound;

import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.Order;

public interface AgvController {

    /**
     * Notifica chegada de novo pedido. AGV decide se participa da eleição.
     */
    void onNewOrder(Order order);

    /**
     * Entrega mensagem recebida da rede para processamento interno.
     */
    void onMessageReceived(AgvMessage message);
}