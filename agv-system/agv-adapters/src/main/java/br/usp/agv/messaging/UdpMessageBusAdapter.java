package br.usp.agv.messaging;

import br.usp.agv.model.AgvMessage;
import br.usp.agv.ports.outbound.MessageBusPort;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adaptador de Mensageria usando UDP Multicast para comunicação P2P.
 */
public class UdpMessageBusAdapter implements MessageBusPort {

    private final String multicastAddress = "230.0.0.1";
    private final int port = 4446;
    private final ObjectMapper mapper = new ObjectMapper();
    private MulticastSocket socket;
    private InetAddress group;
    private final List<Consumer<AgvMessage>> handlers = new ArrayList<>();

    public UdpMessageBusAdapter() {
        try {
            socket = new MulticastSocket(port);
            group = InetAddress.getByName(multicastAddress);
            socket.joinGroup(group);
            startListening();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao inicializar rede UDP", e);
        }
    }

    private void startListening() {
        new Thread(() -> {
            byte[] buffer = new byte[1024 * 4];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    AgvMessage msg = mapper.readValue(packet.getData(), 0, packet.getLength(), AgvMessage.class);
                    synchronized (handlers) {
                        for (Consumer<AgvMessage> handler : handlers) {
                            handler.accept(msg);
                        }
                    }
                } catch (IOException e) {
                    if (!socket.isClosed()) e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void broadcast(AgvMessage message) {
        try {
            byte[] buffer = mapper.writeValueAsBytes(message);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void publish(String topic, AgvMessage message) {
        broadcast(message);
    }

    @Override
    public void subscribe(String topic, Consumer<AgvMessage> handler) {
        synchronized (handlers) {
            handlers.add(handler);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        // Implementação simplificada
    }
}
