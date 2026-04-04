package br.usp.agv.ports.outbound;

import br.usp.agv.model.AgvMessage;

import java.util.function.Consumer;

public interface MessageBusPort {

    void broadcast(AgvMessage message);

    void publish(String topic, AgvMessage message);

    void subscribe(String topic, Consumer<AgvMessage> handler);

    void unsubscribe(String topic);
}