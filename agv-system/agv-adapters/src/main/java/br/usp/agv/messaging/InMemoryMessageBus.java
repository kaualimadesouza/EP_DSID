package br.usp.agv.messaging;

import br.usp.agv.model.AgvMessage;
import br.usp.agv.ports.outbound.MessageBusPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Barramento de mensagens em memória para testes locais e simulação monolítica.
 * Suporta múltiplos inscritos por tópico.
 */
public class InMemoryMessageBus implements MessageBusPort {

    private final Map<String, List<Consumer<AgvMessage>>> handlers = new ConcurrentHashMap<>();

    @Override
    public void broadcast(AgvMessage message) {
        // No simulador local, broadcast envia para todos os inscritos em "agv-system"
        publish("agv-system", message);
    }

    @Override
    public void publish(String topic, AgvMessage message) {
        List<Consumer<AgvMessage>> topicHandlers = handlers.get(topic);
        if (topicHandlers != null) {
            // Sincroniza para evitar ConcurrentModificationException se alguém se inscrever durante o envio
            synchronized (topicHandlers) {
                for (Consumer<AgvMessage> handler : topicHandlers) {
                    // Executa em uma nova thread para simular o comportamento assíncrono da rede
                    new Thread(() -> handler.accept(message)).start();
                }
            }
        }
    }

    @Override
    public void subscribe(String topic, Consumer<AgvMessage> handler) {
        handlers.computeIfAbsent(topic, k -> new ArrayList<>());
        List<Consumer<AgvMessage>> topicHandlers = handlers.get(topic);
        synchronized (topicHandlers) {
            topicHandlers.add(handler);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        handlers.remove(topic);
    }
}
