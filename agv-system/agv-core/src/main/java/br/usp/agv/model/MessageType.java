package br.usp.agv.model;

public enum MessageType {
    // Entrada de pedido (Pub/Sub)
    NEW_ORDER,

    // Acordo de lote (Total Ordering)
    BATCH_PROPOSAL,
    BATCH_ACK,

    // Rotas (broadcast P2P)
    ROUTE_CLAIMED,      // "reservei esse caminho (manda a rota)"
    ROUTE_RELEASED,     // "terminei, caminho livre"

    // Conecta lista de peers, keep-alive
    HEARTBEAT,
    
    ORDER_COMPLETED
}