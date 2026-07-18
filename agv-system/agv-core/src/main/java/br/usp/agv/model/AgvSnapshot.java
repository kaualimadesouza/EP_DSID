package br.usp.agv.model;

/** estado de um AGV em um dado momento, utilizado para
 *      - gerar lotes de pedidos com o estado de cada AGV (replicacao)
 *      - atualizar estado dos peers a cada heartbeat
 */
public record AgvSnapshot(String agvId, Position position, AgvStatus status, long lastSeen) {
    public AgvSnapshot(String agvId, Position position, AgvStatus status) {
        this(agvId, position, status, System.currentTimeMillis());
    }
}
