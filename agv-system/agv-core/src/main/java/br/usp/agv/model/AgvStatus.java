package br.usp.agv.model;

public enum AgvStatus {
    IDLE,               // parado, elegível para novos pedidos
    ELECTING,           // participando de uma eleição agora
    MOVING,             // em rota (Fase 2)
    OFFLINE,            // desligado
    FAIL_SAFE           // parado por perda de rede (Segurança)
}