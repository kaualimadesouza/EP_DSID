package br.usp.agv.pathfinder;

import br.usp.agv.model.Position;
import br.usp.agv.ports.outbound.WorldMapPort;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.Set;

/**
 * Implementação do World core usando grafos
 */
public class GridGraphAdapter implements WorldMapPort {

    private final int width;
    private final int height;
    private final Set<Position> staticObstacles;

    public GridGraphAdapter(int width, int height, Set<Position> staticObstacles) {
        this.width = width;
        this.height = height;
        this.staticObstacles = staticObstacles;
    }

    // Gera um grafo navegável sem posições onde estão obstáculos estáticos e dinâmicos.
    // dynamicObstacles: posições ocupadas por outros AGVs no momento
    public Graph<Position, DefaultEdge> build(Set<Position> dynamicObstacles) {
        Graph<Position, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);

        // adiciona vértices: todas as células navegáveis
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Position pos = new Position(x, y);
                if (!staticObstacles.contains(pos) && !dynamicObstacles.contains(pos)) {
                    graph.addVertex(pos);
                }
            }
        }

        // adiciona arestas: vizinhos ortogonais
        for (Position pos : graph.vertexSet()) {
            for (Position neighbor : pos.orthogonalNeighbors()) {
                if (graph.containsVertex(neighbor)) {
                    graph.addEdge(pos, neighbor);
                }
            }
        }

        return graph;
    }

    public boolean inBounds(Position p) {
        return p.x() >= 0 && p.x() < width
                && p.y() >= 0 && p.y() < height;
    }

    @Override
    public boolean isTraversable(Position p) {
        return inBounds(p) && !staticObstacles.contains(p);
    }
}