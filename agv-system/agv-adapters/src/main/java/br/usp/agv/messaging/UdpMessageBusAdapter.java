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
    
    private String localSenderId;
    private double dropProbability = 0.0; // Probability of dropping incoming messages (for testing)

    private final List<Consumer<AgvMessage>> handlers = new java.util.ArrayList<>();
    private final java.util.Map<String, java.net.InetSocketAddress> nameResolutionTable = new java.util.concurrent.ConcurrentHashMap<>();

    // SRM protocol state
    private final java.util.Map<String, Integer> lastSeenSequences = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, java.util.Map<Integer, AgvMessage>> outOfOrderBuffers = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<AgvMessage> messageHistory = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private static final int HISTORY_LIMIT = 200;

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

    public void setLocalSenderId(String localSenderId) {
        this.localSenderId = localSenderId;
    }

    public void setDropProbability(double dropProbability) {
        this.dropProbability = dropProbability;
    }

    public java.util.Map<String, java.net.InetSocketAddress> getNameResolutionTable() {
        return java.util.Collections.unmodifiableMap(nameResolutionTable);
    }

    private void startListening() {
        new Thread(() -> {
            byte[] buffer = new byte[1024 * 8];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    AgvMessage msg = mapper.readValue(packet.getData(), 0, packet.getLength(), AgvMessage.class);
                    
                    // Resolução de Nomes Flat
                    String senderId = msg.senderId();
                    if (senderId != null && !senderId.equals("SYSTEM") && !senderId.equals("GENERATOR")) {
                        java.net.InetSocketAddress address = new java.net.InetSocketAddress(packet.getAddress(), packet.getPort());
                        java.net.InetSocketAddress old = nameResolutionTable.put(senderId, address);
                        if (old == null || !old.equals(address)) {
                            System.out.println("[RESOLUÇÃO DE NOMES] Mapeado: " + senderId + " -> " + address);
                        }
                    }

                    // Ignora mensagens enviadas por si mesmo para evitar auto-processamento infinito
                    if (localSenderId != null && localSenderId.equals(senderId)) {
                        continue;
                    }

                    // Simula perda de pacotes para testar SRM (exceto para mensagens internas SRM de NACK)
                    if (dropProbability > 0.0 && Math.random() < dropProbability) {
                        if (msg.type() != br.usp.agv.model.MessageType.NACK_REQUEST && msg.type() != br.usp.agv.model.MessageType.NACK_RESPONSE) {
                            System.out.println("[SRM SIMULATOR] SIMULANDO PERDA: " + senderId + " seq " + msg.sequenceNumber() + " (" + msg.type() + ")");
                            continue;
                        }
                    }

                    processMessage(msg);
                } catch (IOException e) {
                    if (!socket.isClosed()) e.printStackTrace();
                }
            }
        }).start();
    }

    private void processMessage(AgvMessage msg) {
        if (msg.type() == br.usp.agv.model.MessageType.NACK_REQUEST) {
            handleNackRequest(msg);
            return;
        }
        if (msg.type() == br.usp.agv.model.MessageType.NACK_RESPONSE) {
            handleNackResponse(msg);
            return;
        }

        // Mensagens que não exigem confiabilidade/ordenamento estrito de sequência (SRM)
        if (msg.type() == br.usp.agv.model.MessageType.HEARTBEAT || 
            msg.type() == br.usp.agv.model.MessageType.ELECTION || 
            msg.type() == br.usp.agv.model.MessageType.OK || 
            msg.type() == br.usp.agv.model.MessageType.COORDINATOR ||
            msg.type() == br.usp.agv.model.MessageType.ROUTE_CLAIMED ||
            msg.type() == br.usp.agv.model.MessageType.ROUTE_RELEASED ||
            msg.type() == br.usp.agv.model.MessageType.ORDER_COMPLETED) {
            deliverMessage(msg);
            return;
        }

        // Mensagens do sistema (SYSTEM) não têm controle estrito de sequência
        if (msg.senderId().equals("SYSTEM")) {
            deliverMessage(msg);
            return;
        }

        int seq = msg.sequenceNumber();
        String sender = msg.senderId();

        synchronized (lastSeenSequences) {
            if (!lastSeenSequences.containsKey(sender)) {
                // Primeira mensagem vista deste peer: assume que está correta e inicializa
                lastSeenSequences.put(sender, seq);
                deliverMessage(msg);
                return;
            }

            int expected = lastSeenSequences.get(sender) + 1;
            if (seq == expected) {
                lastSeenSequences.put(sender, seq);
                deliverMessage(msg);

                // Entrega mensagens armazenadas que agora estão na sequência correta
                java.util.Map<Integer, AgvMessage> buffer = outOfOrderBuffers.get(sender);
                if (buffer != null) {
                    while (buffer.containsKey(expected + 1)) {
                        expected++;
                        AgvMessage nextMsg = buffer.remove(expected);
                        lastSeenSequences.put(sender, expected);
                        deliverMessage(nextMsg);
                    }
                }
            } else if (seq > expected) {
                // Mensagem fora de ordem (futura), guarda no buffer
                outOfOrderBuffers.computeIfAbsent(sender, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(seq, msg);
                
                // Pede retransmissão de todos os pacotes em falta
                for (int m = expected; m < seq; m++) {
                    java.util.Map<Integer, AgvMessage> peerBuffer = outOfOrderBuffers.get(sender);
                    if (peerBuffer == null || !peerBuffer.containsKey(m)) {
                        sendNackRequest(sender, m);
                    }
                }
            } else {
                // seq < expected: pacote duplicado ou retransmissão tardia, descarta
            }
        }
    }

    private void handleNackRequest(AgvMessage nackRequestMsg) {
        String targetId = (String) nackRequestMsg.payload().get("targetSenderId");
        int targetSeq = (Integer) nackRequestMsg.payload().get("targetSequenceNumber");

        AgvMessage found = null;
        synchronized (messageHistory) {
            for (AgvMessage m : messageHistory) {
                if (m.senderId().equals(targetId) && m.sequenceNumber() == targetSeq) {
                    found = m;
                    break;
                }
            }
        }

        if (found != null) {
            System.out.println("[SRM] Re-enviando pacote perdido solicitado: " + targetId + " seq " + targetSeq);
            AgvMessage response = AgvMessage.nackResponse(localSenderId != null ? localSenderId : "SYSTEM", 0, 0, found);
            rawBroadcast(response);
        }
    }

    private void handleNackResponse(AgvMessage nackResponseMsg) {
        try {
            AgvMessage lostMessage = mapper.convertValue(nackResponseMsg.payload().get("lostMessage"), AgvMessage.class);
            if (lostMessage != null) {
                System.out.println("[SRM] Recuperado pacote perdido de " + lostMessage.senderId() + " seq " + lostMessage.sequenceNumber());
                processMessage(lostMessage);
            }
        } catch (Exception e) {
            System.err.println("Erro ao converter mensagem retransmitida: " + e.getMessage());
        }
    }

    private void sendNackRequest(String targetSenderId, int targetSeq) {
        if (localSenderId == null) return;
        System.out.println("[SRM] Solicitando retransmissão (NACK): " + targetSenderId + " seq " + targetSeq);
        AgvMessage nack = AgvMessage.nackRequest(localSenderId, 0, 0, targetSenderId, targetSeq);
        rawBroadcast(nack);
    }

    private void deliverMessage(AgvMessage msg) {
        synchronized (handlers) {
            for (Consumer<AgvMessage> handler : handlers) {
                handler.accept(msg);
            }
        }
    }

    private void rawBroadcast(AgvMessage message) {
        try {
            byte[] buffer = mapper.writeValueAsBytes(message);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void broadcast(AgvMessage message) {
        // Armazena no histórico de mensagens enviadas para responder a NACKs futuros
        if (message.type() != br.usp.agv.model.MessageType.NACK_REQUEST && message.type() != br.usp.agv.model.MessageType.NACK_RESPONSE) {
            synchronized (messageHistory) {
                messageHistory.add(message);
                if (messageHistory.size() > HISTORY_LIMIT) {
                    messageHistory.remove(0);
                }
            }
        }
        rawBroadcast(message);
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
