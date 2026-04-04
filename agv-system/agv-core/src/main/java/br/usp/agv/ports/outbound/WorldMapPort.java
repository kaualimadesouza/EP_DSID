package br.usp.agv.ports.outbound;

import br.usp.agv.model.Position;

public interface WorldMapPort {
    boolean isTraversable(Position p);}