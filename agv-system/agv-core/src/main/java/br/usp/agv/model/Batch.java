package br.usp.agv.model;

import java.util.List;
import java.util.Map;

public record Batch(String batchId, List<Order> orders, Map<String, AgvSnapshot> agvStates) {
}
