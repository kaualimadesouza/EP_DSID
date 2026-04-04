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

    private final int rows;
    private final int cols;
    private final Set<Position> staticObstacles;

    public GridGraphAdapter(int rows, int cols, Set<Position> staticObstacles) {
        this.rows = rows;
        this.cols = cols;
        this.staticObstacles = staticObstacles;
    }

    /**
     * Gera um grafo navegável sem posições onde estão obstáculos estáticos e dinâmicos.
     *
     * @param dynamicObstacles posições ocupadas por outros AGVs no momento
     * @return grafo navegável
     */
    public Graph<Position, DefaultEdge> build(Set<Position> dynamicObstacles) {
        Graph<Position, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);

        // adiciona vértices: todas as células navegáveis
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Position pos = new Position(row, col);
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
        return p.x() >= 0 && p.x() < rows
                && p.y() >= 0 && p.y() < cols;
    }

    @Override
    public boolean isTraversable(Position p) {
        return inBounds(p) && !staticObstacles.contains(p);
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }
}