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

    // Retransmissão de NACK: sem isso, um único NACK_REQUEST ou NACK_RESPONSE perdido
    // (perfeitamente possível em UDP) travava o peer para sempre esperando por aquela sequência.
    private final java.util.Set<String> inFlightNacks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ScheduledExecutorService nackScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private static final int MAX_NACK_RETRIES = 5;
    private static final long NACK_RETRY_DELAY_MS = 1500;

    // Fila de mensagens recebidas: a thread do socket só enfileira, nunca processa,
    // para que um handler lento nunca atrase o dreno do socket (e cause perda de pacote pelo SO).
    private final java.util.concurrent.BlockingQueue<AgvMessage> inboundQueue = new java.util.concurrent.LinkedBlockingQueue<>();

    // Última vez que cada sender apareceu no controle de sequência do SRM, usado só para
    // limpar lastSeenSequences/outOfOrderBuffers de peers que nunca mais vão mandar mensagem
    // (ex: reiniciaram com uma nova sessão/UUID) e evitar um vazamento lento dessas estruturas.
    private final java.util.Map<String, Long> lastSrmActivity = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long SRM_PEER_IDLE_TIMEOUT_MS = 60_000;

    public UdpMessageBusAdapter() {
        try {
            socket = new MulticastSocket(port);
            group = InetAddress.getByName(multicastAddress);
            
            // Habilita loopback local explicitamente para que processos na mesma máquina recebam
            socket.setLoopbackMode(false);
            
            // Força o uso da interface de loopback para estabilidade local absoluta
            java.net.NetworkInterface loopback = java.net.NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));
            if (loopback != null) {
                socket.setNetworkInterface(loopback);
                socket.joinGroup(new java.net.InetSocketAddress(group, port), loopback);
            } else {
                socket.joinGroup(group);
            }
            
            startListening();
            startSrmStateCleanup();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao inicializar rede UDP", e);
        }
    }

    /**
     * Remove periodicamente o estado de SRM (lastSeenSequences/outOfOrderBuffers) de senders
     * que não aparecem há muito tempo — sem isso, um peer que reinicia com uma sessão nova
     * (novo UUID) deixa para trás entradas órfãs que nunca mais são usadas nem liberadas.
     */
    private void startSrmStateCleanup() {
        nackScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            for (String sender : lastSrmActivity.keySet()) {
                Long last = lastSrmActivity.get(sender);
                if (last != null && (now - last) > SRM_PEER_IDLE_TIMEOUT_MS) {
                    lastSeenSequences.remove(sender);
                    outOfOrderBuffers.remove(sender);
                    lastSrmActivity.remove(sender);
                }
            }
        }, SRM_PEER_IDLE_TIMEOUT_MS, SRM_PEER_IDLE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
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
        // Thread dedicada exclusivamente a drenar o socket o mais rápido possível.
        // Processamento de mensagem (locks, I/O de log, etc.) nunca deve rodar aqui:
        // se atrasar, o buffer de recepção do SO pode encher e descartar pacotes
        // silenciosamente, um tipo de perda que o SRM (nível de aplicação) não detecta.
        new Thread(() -> {
            byte[] buffer = new byte[65535];
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
                            br.usp.agv.logging.SystemLogger.debug("RESOLUÇÃO DE NOMES", "Mapeado: " + senderId + " -> " + address);
                        }
                    }

                    // Ignora mensagens enviadas por si mesmo para evitar auto-processamento infinito
                    if (localSenderId != null && localSenderId.equals(senderId)) {
                        continue;
                    }

                    // Simula perda de pacotes para testar SRM (exceto para mensagens internas SRM de NACK)
                    if (dropProbability > 0.0 && Math.random() < dropProbability) {
                        if (msg.type() != br.usp.agv.model.MessageType.NACK_REQUEST && msg.type() != br.usp.agv.model.MessageType.NACK_RESPONSE) {
                            br.usp.agv.logging.SystemLogger.debug("SRM SIMULATOR", "SIMULANDO PERDA: " + senderId + " seq " + msg.sequenceNumber() + " (" + msg.type() + ")");
                            continue;
                        }
                    }

                    inboundQueue.add(msg);
                } catch (IOException e) {
                    if (!socket.isClosed()) e.printStackTrace();
                }
            }
        }, "udp-socket-receiver").start();

        // Thread dedicada a processar as mensagens já recebidas (SRM, dispatch aos handlers),
        // desacoplada da leitura do socket acima.
        new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    AgvMessage msg = inboundQueue.take();
                    processMessage(msg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable t) {
                    br.usp.agv.logging.SystemLogger.error("UDP", "Erro ao processar mensagem da fila: " + t.getMessage(), t);
                }
            }
        }, "udp-message-processor").start();
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

        // Mensagens do sistema (SYSTEM) e do gerador de pedidos (GENERATOR) não têm controle
        // estrito de sequência: nenhum dos dois tem UUID de sessão como os AGVs, então se o
        // processo reiniciar (contador de sequência volta a 1), os AGVs que já viram números
        // mais altos passariam a descartar todo NEW_ORDER/DEBUG_QUERY novo como "pacote antigo".
        if (msg.senderId().equals("SYSTEM") || msg.senderId().equals("GENERATOR")) {
            deliverMessage(msg);
            return;
        }

        int seq = msg.sequenceNumber();
        String sender = msg.senderId();
        lastSrmActivity.put(sender, System.currentTimeMillis());

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

                // Pede retransmissão de todos os pacotes em falta (com retry: ver attemptNack)
                for (int m = expected; m < seq; m++) {
                    java.util.Map<Integer, AgvMessage> peerBuffer = outOfOrderBuffers.get(sender);
                    if (peerBuffer == null || !peerBuffer.containsKey(m)) {
                        String nackKey = sender + ":" + m;
                        if (inFlightNacks.add(nackKey)) {
                            attemptNack(sender, m, 1);
                        }
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
            br.usp.agv.logging.SystemLogger.debug("SRM", "Re-enviando pacote perdido solicitado: " + targetId + " seq " + targetSeq);
            AgvMessage response = AgvMessage.nackResponse(localSenderId != null ? localSenderId : "SYSTEM", 0, 0, found);
            rawBroadcast(response);
        } else {
            br.usp.agv.logging.SystemLogger.debug("SRM", "NACK_REQUEST para " + targetId + " seq " + targetSeq
                    + " não encontrado no histórico local (já expirou do buffer de " + HISTORY_LIMIT
                    + " mensagens ou nunca passou por este nó)");
        }
    }

    private void handleNackResponse(AgvMessage nackResponseMsg) {
        try {
            AgvMessage lostMessage = mapper.convertValue(nackResponseMsg.payload().get("lostMessage"), AgvMessage.class);
            if (lostMessage != null) {
                br.usp.agv.logging.SystemLogger.debug("SRM", "Recuperado pacote perdido de " + lostMessage.senderId() + " seq " + lostMessage.sequenceNumber());
                processMessage(lostMessage);
            }
        } catch (Exception e) {
            br.usp.agv.logging.SystemLogger.error("SRM", "Erro ao converter mensagem retransmitida: " + e.getMessage(), e);
        }
    }

    /**
     * Solicita retransmissão de uma sequência perdida, com retry e backoff fixo.
     * Sem isso, um único NACK_REQUEST ou NACK_RESPONSE perdido em trânsito (ou uma mensagem já
     * expirada do histórico do remetente) travaria o buffer fora-de-ordem deste peer para sempre,
     * já que o protocolo original só pedia a retransmissão uma única vez.
     */
    private void attemptNack(String targetSenderId, int targetSeq, int attempt) {
        String key = targetSenderId + ":" + targetSeq;

        Integer lastSeen = lastSeenSequences.get(targetSenderId);
        java.util.Map<Integer, AgvMessage> buffered = outOfOrderBuffers.get(targetSenderId);
        boolean jaResolvido = (lastSeen != null && lastSeen >= targetSeq)
                || (buffered != null && buffered.containsKey(targetSeq));
        if (jaResolvido) {
            inFlightNacks.remove(key);
            return;
        }

        if (attempt > MAX_NACK_RETRIES) {
            inFlightNacks.remove(key);
            br.usp.agv.logging.SystemLogger.error("SRM",
                    "Pacote " + targetSenderId + " seq " + targetSeq + " perdido definitivamente após "
                    + MAX_NACK_RETRIES + " tentativas de NACK. Avançando a sequência para não travar "
                    + "o recebimento de mensagens futuras deste peer (perda aceita, registrada em log).",
                    null);
            forceSkipSequence(targetSenderId, targetSeq);
            return;
        }

        if (localSenderId != null) {
            br.usp.agv.logging.SystemLogger.debug("SRM", "Solicitando retransmissão (NACK) tentativa "
                    + attempt + "/" + MAX_NACK_RETRIES + ": " + targetSenderId + " seq " + targetSeq);
            AgvMessage nack = AgvMessage.nackRequest(localSenderId, 0, 0, targetSenderId, targetSeq);
            rawBroadcast(nack);
        }

        nackScheduler.schedule(() -> attemptNack(targetSenderId, targetSeq, attempt + 1),
                NACK_RETRY_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Último recurso após esgotar as tentativas de NACK: aceita a perda da sequência indicada
     * e libera qualquer mensagem já bufferizada que dependia dela, para o peer não ficar travado
     * para sempre esperando por um pacote que nunca mais vai chegar.
     */
    private void forceSkipSequence(String sender, int seq) {
        synchronized (lastSeenSequences) {
            Integer current = lastSeenSequences.get(sender);
            if (current == null || current < seq) {
                lastSeenSequences.put(sender, seq);
            }

            java.util.Map<Integer, AgvMessage> buffer = outOfOrderBuffers.get(sender);
            if (buffer != null) {
                int expected = seq;
                while (buffer.containsKey(expected + 1)) {
                    expected++;
                    AgvMessage nextMsg = buffer.remove(expected);
                    lastSeenSequences.put(sender, expected);
                    deliverMessage(nextMsg);
                }
            }
        }
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
        // Armazena no histórico de mensagens enviadas para responder a NACKs futuros.
        // HEARTBEAT é isento de SRM (processMessage entrega direto, sem checar sequência),
        // então nunca é alvo de um NACK_REQUEST — guardá-lo aqui só desperdiça espaço do
        // buffer de HISTORY_LIMIT mensagens, fazendo mensagens que IMPORTAM para o SRM
        // (BATCH_PROPOSAL, BATCH_ACK, NEW_ORDER) expirarem mais rápido do histórico.
        boolean precisaHistorico = message.type() != br.usp.agv.model.MessageType.NACK_REQUEST
                && message.type() != br.usp.agv.model.MessageType.NACK_RESPONSE
                && message.type() != br.usp.agv.model.MessageType.HEARTBEAT;
        if (precisaHistorico) {
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
