package br.usp.agv.pathfinder;

import br.usp.agv.model.Position;
import br.usp.agv.model.Route;
import br.usp.agv.ports.outbound.PathfinderPort;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AStarShortestPath;
import org.jgrapht.graph.DefaultEdge;

import java.util.Set;

public class AStarPathfinderAdapter implements PathfinderPort {

    private final GridGraphAdapter gridGraphAdapter;

    public AStarPathfinderAdapter(GridGraphAdapter gridGraphAdapter) {
        this.gridGraphAdapter = gridGraphAdapter;
    }

    @Override
    public Route calculateRoute(Position origin, Position destination,
                                Set<Position> occupied, String agvId) {

        Graph<Position, DefaultEdge> graph = gridGraphAdapter.build(occupied);

        if (!graph.containsVertex(origin)) {
            throw new IllegalStateException(
                    "Origem %s bloqueada ou fora dos limites".formatted(origin));
        }
        if (!graph.containsVertex(destination)) {
            throw new IllegalStateException(
                    "Destino %s bloqueado ou fora dos limites".formatted(destination));
        }

        GraphPath<Position, DefaultEdge> path =
                new AStarShortestPath<>(graph, this::heuristic).getPath(origin, destination);

        if (path == null) {
            throw new IllegalStateException(
                    "Nenhuma rota de %s até %s".formatted(origin, destination));
        }

        return new Route(agvId + "-" + System.currentTimeMillis(), path.getVertexList());
    }

    private double heuristic(Position a, Position b) {
        return a.manhattanDistanceTo(b);
    }
}