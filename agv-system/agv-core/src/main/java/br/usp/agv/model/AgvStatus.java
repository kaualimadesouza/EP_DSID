package br.usp.agv.model;

public enum AgvStatus {
    IDLE,               // parado, elegível para novos pedidos
    ELECTING,           // participando de uma eleição agora
    MOVING,             // em rota (Fase 2)
    WAITING_FOR_LOCK,   // bloqueado por outro AGV (Fase 2)
    OFFLINE             // desligado
}