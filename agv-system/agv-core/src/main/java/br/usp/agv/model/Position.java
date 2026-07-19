package br.usp.agv.model;

import java.util.List;

public record Position(int x, int y) {

    public int manhattanDistanceTo(Position other) {
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }

    public List<Position> orthogonalNeighbors() {
        // nota: não filtra limites do mapa, ja que quem chama é responsável por validar
        return List.of(
                new Position(x, y - 1),  // cima
                new Position(x, y + 1),  // baixo
                new Position(x - 1, y),  // esquerda
                new Position(x + 1, y)   // direita
        );
    }
}