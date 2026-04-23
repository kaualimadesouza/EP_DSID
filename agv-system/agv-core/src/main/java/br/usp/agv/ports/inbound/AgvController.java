package br.usp.agv.ports.inbound;

import br.usp.agv.model.Batch;
import br.usp.agv.model.Order;

public interface AgvController {

    void onNewOrder(Order order);

    void onBatchProposal(Batch batch);

    void onBatchAck(String senderId, String batchId);

    void onHeartbeatReceived(String senderId, br.usp.agv.model.Position position, br.usp.agv.model.AgvStatus status);

    void onRouteClaimed(String agvId, String orderId, br.usp.agv.model.Route route);

    void onRouteReleased(String agvId);

    void onOrderCompleted(String orderId);
}