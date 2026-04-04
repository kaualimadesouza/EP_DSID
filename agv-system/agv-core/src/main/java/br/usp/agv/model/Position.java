package br.usp.agv.model;

import java.util.List;

public record Position(int x, int y) {

    public int manhattanDistanceTo(Position other) {
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
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