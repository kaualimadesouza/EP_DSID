package br.usp.agv.usecase;

import br.usp.agv.model.Agv;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.Order;

public class AgvController implements br.usp.agv.ports.inbound.AgvController {

    private final ElectionUseCase election;

    public AgvController(Agv agv, ElectionUseCase election) {
        this.election = election;
    }

    @Override
    public void onNewOrder(Order order) {
        election.startElection(order);
    }

    @Override
    public void onMessageReceived(AgvMessage message) {
        switch (message.type()) {
            case ELECTION_REQUEST -> election.onElectionRequest(message);
            // outros casos vão sendo adicionados conforme as fases
        }
    }
}