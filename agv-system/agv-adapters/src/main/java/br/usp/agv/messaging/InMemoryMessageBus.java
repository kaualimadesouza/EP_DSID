package br.usp.agv.messaging;


import br.usp.agv.model.AgvMessage;
import br.usp.agv.ports.outbound.MessageBusPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fake em memória — sem rede.
 * Usado nos testes e na simulação local da Fase 1.
 * Na Fase 2 será substituído por outro adapter que implemente rede (MQTT ou Socket).
 */
public class InMemoryMessageBus implements MessageBusPort {

    private final List<AgvMessage> sent = new ArrayList<>();
    private final List<Consumer<AgvMessage>> broadcastListeners = new ArrayList<>();

    @Override
    public void broadcast(AgvMessage message) {
        sent.add(message);
        broadcastListeners.forEach(l -> l.accept(message));
    }

    @Override
    public void publish(String topic, AgvMessage message) {
        sent.add(message);
    }

    @Override
    public void subscribe(String topic, Consumer<AgvMessage> handler) {
        // tópicos específicos entram na Fase 2
    }

    @Override
    public void unsubscribe(String topic) {
    }

    /**
     * Registra listener que recebe todos os broadcasts — usado para ligar AGVs entre si.
     */
    public void addBroadcastListener(Consumer<AgvMessage> listener) {
        broadcastListeners.add(listener);
    }

    public List<AgvMessage> getSent() {
        return Collections.unmodifiableList(sent);
    }

    public void clear() {
        sent.clear();
    }
}