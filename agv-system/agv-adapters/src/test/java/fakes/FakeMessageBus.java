package fakes;

import br.usp.agv.model.AgvMessage;
import br.usp.agv.ports.outbound.MessageBusPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FakeMessageBus implements MessageBusPort {

    // tudo que foi enviado fica aqui para o teste inspecionar
    public final List<AgvMessage> sent = new ArrayList<>();

    @Override
    public void broadcast(AgvMessage message) {
        sent.add(message);
    }

    @Override
    public void publish(String topic, AgvMessage message) {
        sent.add(message);
    }

    @Override
    public void subscribe(String topic, Consumer<AgvMessage> handler) {
    }

    @Override
    public void unsubscribe(String topic) {
    }
}