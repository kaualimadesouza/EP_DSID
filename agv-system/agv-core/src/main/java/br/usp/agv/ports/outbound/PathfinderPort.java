package br.usp.agv.ports.outbound;

import br.usp.agv.model.Position;
import br.usp.agv.model.Route;

import java.util.Set;

public interface PathfinderPort {

    /**
     * Calcula a rota mais curta de origin até destination evitando posições ocupadas.
     */
    Route calculateRoute(Position origin, Position destination,
                         Set<Position> occupied, String agvId);
}