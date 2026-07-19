# Guia de Arquivos - EP_DSID

Referência rápida do que cada arquivo faz no projeto.

---

## `agv-core` - domínio - sem dependência externa

### `model/` - as classes de dados do domínio

**`Position.java`** - Um ponto `(x, y)` no grid. Tem `manhattanDistanceTo` (distância em linha reta nos eixos, sem diagonal) e `orthogonalNeighbors` (as 4 células vizinhas). É um `record`, ou seja, um valor imutável.

**`AgvStatus.java`** - Enum com os cinco estados possíveis de um AGV: `IDLE`, `ELECTING`, `MOVING`, `OFFLINE`, `FAIL_SAFE`.

**`Agv.java`** - A classe que representa o próprio robô: nome fixo (`staticName`), um UUID gerado ao ligar (`sessionUuid`), posição atual, status, pedido em andamento e o relógio lógico de Lamport.

**`Order.java`** - Um pedido: ID, ponto de coleta (`pickup`) e ponto de entrega (`delivery`). Só dado, sem comportamento.

**`Route.java`** - Uma rota calculada: um ID e a lista de posições (`waypoints`) a percorrer. Tem `destination()`, que devolve o último waypoint.

**`Batch.java`** - Um lote de pedidos proposto pelo líder: um ID, a lista de pedidos, e um "retrato" (snapshot) da posição/status de todos os AGVs naquele instante.

**`AgvSnapshot.java`** - Uma cópia congelada do estado de um AGV num instante específico (ID, posição, status, quando foi visto pela última vez). Diferente de `Agv`: este é imutável e serve só para guardar dentro de um `Batch`.

**`AgvMessage.java`** - O envelope de toda mensagem que trafega na rede: quem mandou, número de sequência, relógio de Lamport, tipo, e um payload genérico. Tem um método para cada tipo de mensagem (`heartbeat(...)`, `batchProposal(...)`, `election(...)` etc.), centralizando como cada uma é montada.

**`MessageType.java`** - Enum com todos os tipos de mensagem do protocolo: `HEARTBEAT`, `NEW_ORDER`, `BATCH_PROPOSAL`, `BATCH_ACK`, `ROUTE_CLAIMED`, `ROUTE_RELEASED`, `ORDER_COMPLETED`, `ELECTION`, `OK`, `COORDINATOR`, `NACK_REQUEST`, `NACK_RESPONSE`, `DEBUG_QUERY`.

### `ports/outbound/` - acesso ao mundo exterior pelo core

**`MessageBusPort.java`** - Interface para mandar e receber mensagens (`broadcast`, `publish`, `subscribe`, `unsubscribe`).

**`PathfinderPort.java`** - Interface com um único método, `calculateRoute`, para pedir uma rota entre dois pontos.

**`WorldMapPort.java`** - Interface mínima para perguntar se uma posição é atravessável (`isTraversable`). NAO IMPLEMENTADO

**`WorldObserverPort.java`** - Interface de callbacks para uma interface gráfica acompanhar o sistema (`onAgvMoved`, `onOrderCreated`, `onRouteCalculated`, `onLeaderChanged`...).

### `ports/inbound/` - acesso ao core pelo mundo exterior

**`AgvController.java`** (interface) - Um método por tipo de evento que pode chegar de fora (`onNewOrder`, `onBatchProposal`, `onHeartbeatReceived`, `onElectionReceived`...). É para onde o adaptador de mensageria entrega tudo já decodificado.

### `usecase/` - implementacoes de regras de negocio

**`AgvBroadcaster.java`** - Fachada que monta e envia cada tipo de `AgvMessage` (heartbeat, proposta de lote, ACK, eleição...), cuidando de número de sequência e relógio de Lamport num único lugar.

**`AgvController.java`** (implementação) - Ponto de entrada único para tudo que chega da rede: a maioria dos métodos só repassa para `BatchAssignmentUseCase` ou `MovementUseCase`, mas também guarda as rotas ativas de outros AGVs (`activePeerRoutes`) para calcular a própria rota evitando cruzar com elas.

**`BatchAssignmentUseCase.java`** - Eleição de líder (Bully), agrupamento de pedidos em lotes, cálculo do vencedor do leilão por distância de Manhattan, monitoramento de peers vivos/mortos, modo Fail-Safe, recuperação de tarefas órfãs quando um AGV cai, retenção de pedidos excedentes em memória e re-coordenação automática baseada na disponibilidade dos AGVs.

**`MovementUseCase.java`** - Executa a rota já atribuída a um pedido: percorre os waypoints, pausa se o AGV estiver em `FAIL_SAFE`, manda heartbeat a cada passo, e avisa quando o pedido é concluído.

### `logging/`

**`SystemLogger.java`** - Log centralizado (grava em `agv-system.log` sempre; imprime no console só quando pedido explicitamente) para não poluir o terminal com todos os AGVs rodando ao mesmo tempo.

---

## `agv-adapters` - implementações das ports

### `messaging/`

**`UdpMessageBusAdapter.java`** - A implementação de rede de verdade: UDP Multicast com uma camada própria de confiabilidade (SRM - números de sequência, buffer, `NACK_REQUEST`/`NACK_RESPONSE`). A leitura do socket roda numa thread só para enfileirar pacotes; outra thread separada é quem realmente processa cada mensagem.

**`InMemoryMessageBus.java`** - A mesma interface (`MessageBusPort`), mas sem rede nenhuma: entrega mensagens em memória, cada uma numa thread nova, para simular assincronia sem precisar de socket. Usado no modo SimulationMain e nos testes.

**`AgvMessageDispatcher.java`** - Ponte entre o `MessageBusPort` (mensagens genéricas) e o `AgvController` (um método por tipo). É aqui que o payload genérico da mensagem é convertido de volta para `Position`, `Batch`, `Route` etc.

### `pathfinder/`

**`GridGraphAdapter.java`** - Constrói o grid do armazém como um grafo (JGraphT), célula por célula, ligando só vizinhos ortogonais e excluindo obstáculos estáticos e dinâmicos.

**`AStarPathfinderAdapter.java`** - Calcula a rota mais curta entre dois pontos usando A\* com heurística de distância de Manhattan.

### `ui/`

**`SwingVisualizerAdapter.java`** - Janela Swing que desenha o grid, os AGVs, pedidos pendentes (pickup/delivery) e rotas ativas, além de um painel de status em HTML.

### `test/`

**`fakes/FakeMessageBus.java`** - Um `MessageBusPort` de mentira que só guarda tudo que foi "enviado" numa lista, para os testes inspecionarem sem precisar de rede.

**`br/usp/agv/usecase/BatchAssignmentUseCaseTest.java`** - Testa o leilão determinístico (quem ganha por distância, o desempate por ID, e que só AGVs `IDLE` competem) e a comparação numérica de IDs usada na eleição Bully.

**`br/usp/agv/usecase/AgvBroadcasterTest.java`** - Testa se `AgvBroadcaster` monta corretamente o tipo e o payload de heartbeat, eleição, OK e coordenador.

---

## `infra` - pontos de entrada e orquestração

**`AgvNodeMain.java`** - O `main` de um AGV. Monta manualmente todas as peças (mensageria, pathfinder, use cases, controller) e mantém o processo vivo.

**`OrderGeneratorMain.java`** - O `main` do Gerador de Pedidos: lê comandos do console (`/new_order`, `/random_orders`, `/dump`...) e faz broadcast de `NEW_ORDER`/`DEBUG_QUERY`.

**`VisualizerMain.java`** - O `main` do Visualizador: renderiza o estado do sistema a partir das mensagens públicas.

**`SimulationMain.java`** - modo de teste rápido sobe 3 AGVs na mesma JVM usando `InMemoryMessageBus` , para testar a lógica rapidamente.

**`AgvSystemManager.java`** - O orquestrador principal que criamos para facilitar a execução do EP, quantos AGVs/tamanho de grid, sobe o Visualizador, o Gerador e cada AGV como processos separados do sistema operacional, e repassa o que você digita no console para o Gerador.
