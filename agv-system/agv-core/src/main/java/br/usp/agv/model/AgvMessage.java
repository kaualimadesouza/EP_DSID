package br.usp.agv.model;

import java.util.Map;

public record AgvMessage(String senderId, MessageType type, Map<String, Object> payload) {}
