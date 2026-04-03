package br.usp.agv.model;

public enum MessageType {
    // Entrada de pedido (Pub/Sub)
    NEW_ORDER,

    // Eleição (ideia: tratar par a par)
    ELECTION_REQUEST,   // "quero esse pedido, meu peso é X"
    ELECTION_CONCEDE,   // "ok, você tem prioridade"

    // Rotas (broadcast P2P)
    ROUTE_CLAIMED,      // "reservei esse caminho (manda a rota)"
    ROUTE_RELEASED,     // "terminei, caminho livre"

    // Exclusão mútua - Fase 2 (Pub/Sub)
    LOCK_REQUEST,
    LOCK_OK,
    LOCK_RELEASE,

    // Gestão - Fase 2
    HEARTBEAT
}