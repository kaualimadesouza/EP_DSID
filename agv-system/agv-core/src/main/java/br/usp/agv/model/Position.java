package br.usp.agv.model;

import java.util.List;

public record Position(int x, int y) {

    public Position {
        if (x < 0 || y < 0)
            throw new IllegalArgumentException("Posição inválida: (%d,%d)".formatted(x, y));
    }

    public int manhattanDistanceTo(Position other) {
        return Math.abs(this.x - other.y) + Math.abs(this.x - other.y);
    }

    public List<Position> orthogonalNeighbors() {
        return List.of(
                new Position(x - 1, y),  // cima
                new Position(x + 1, y),  // baixo
                new Position(x, y - 1),  // esquerda
                new Position(x, y + 1)   // direita
        );
        // nota: não filtra limites do mapa — quem chama é responsável por validar
    }
}