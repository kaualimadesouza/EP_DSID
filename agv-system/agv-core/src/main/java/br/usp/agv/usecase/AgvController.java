package br.usp.agv.usecase;

import br.usp.agv.model.Agv;
import br.usp.agv.model.AgvMessage;
import br.usp.agv.model.Order;
import br.usp.agv.ports.outbound.WorldObserverPort;

import java.util.List;

public class AgvController implements br.usp.agv.ports.inbound.AgvController {

    private final Agv agv;
    private final ElectionUseCase election;
    private final WorldObserverPort observer;

    public AgvController(Agv agv, ElectionUseCase election, WorldObserverPort observer) {
        this.agv = agv;
        this.election = election;
        this.observer = observer;
    }

    @Override
    public void onNewOrder(Order order) {
        if (observer != null) {
            observer.onOrderCreated(order);
        }
        
        ElectionUseCase.Candidacy candidacy = election.startElection(order);
        
        if (candidacy != null && observer != null && candidacy.route() != null) {
            observer.onRouteCalculated(agv.getAgvId(), candidacy.route());
            observer.onSystemStateChanged(List.of(agv), List.of(order));
        }
    }

    @Override
    public void onMessageReceived(AgvMessage message) {
        switch (message.type()) {
            case ELECTION_REQUEST -> election.onElectionRequest(message);
        }
    }
}