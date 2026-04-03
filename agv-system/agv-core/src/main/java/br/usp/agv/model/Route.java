package br.usp.agv.model;

import java.util.List;

public record Route(String routeId, List<Position> waypoints) {
    public Position destination() {
        return waypoints.getLast();
    }
}
