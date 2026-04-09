# EP DSID - Coordenação Distribuída de AGVs

Este documento detalha as decisões arquiteturais, os algoritmos distribuídos e o funcionamento geral do sistema de coordenação de AGVs (Automated Guided Vehicles).

## 1. Visão Geral do Projeto

O objetivo deste projeto é simular uma frota de robôs autônomos que operam em um grid (estoque) sem colidirem entre si e sem a necessidade de um servidor central de controle. Os robôs devem:
1.  **Descobrir** outros robôs na rede automaticamente.
2.  **Disputar** tarefas (pedidos de coleta) de forma justa e eficiente.
3.  **Navegar** pelo grid de forma otimizada.
4.  **Coordenar** movimentos para evitar colisões (em desenvolvimento).

## 2. Arquitetura do Sistema

O projeto adota a **Arquitetura Hexagonal (Ports & Adapters)**, garantindo que a lógica de negócio seja independente de detalhes técnicos como protocolos de rede ou bibliotecas de interface gráfica.

### Módulos Principais

*   **`agv-core`**: Contém o coração do sistema.
    *   **Models**: Representam o domínio (AGV, Posição, Rota, Pedido, Mensagem).
    *   **Use Cases**: Implementam os algoritmos distribuídos (Eleição, Movimentação).
    *   **Ports**: Interfaces que definem como o core se comunica com o mundo exterior (MessageBusPort, PathfinderPort).
*   **`agv-adapters`**: Implementações concretas dos ports.
    *   **Messaging**: `UdpMessageBusAdapter` utiliza **UDP Multicast** para comunicação P2P.
    *   **Pathfinding**: `AStarPathfinderAdapter` utiliza o algoritmo **A*** para cálculo de rotas.
    *   **UI**: `SwingVisualizerAdapter` para visualização em tempo real.
*   **`infra`**: Código de bootstrap e inicialização dos nós e da simulação.

## 3. Algoritmos Distribuídos

### 3.1 Descoberta de Nós e Monitoramento (Heartbeats)
Não existe uma lista estática de robôs. Cada AGV emite um sinal de "batida de coração" (**Heartbeat**) via UDP Multicast a cada 1 segundo.
*   **Funcionamento**: Ao receber um heartbeat, o AGV atualiza sua lista interna de vizinhos ativos.
*   **Tolerância a Falhas**: Se um AGV não enviar batidas por mais de 5 segundos, ele é removido da lista de vizinhos ativos, permitindo que o sistema continue operando mesmo com a queda de nós.

### 3.2 Eleição Distribuída para Atribuição de Pedidos
Quando um novo pedido surge no sistema, os AGVs disponíveis iniciam um processo de eleição para decidir quem atenderá a tarefa.

*   **Métrica de Aptidão**: O critério de desempate inicial é a **Distância de Manhattan** entre a posição atual do robô e o ponto de coleta do pedido.
*   **Protocolo de Consenso (Bully-based)**:
    1.  O AGV calcula seu "score" (distância) e envia um `ELECTION_REQUEST`.
    2.  Ao receber pedidos de outros, ele compara os scores.
    3.  Se o outro AGV for mais apto (menor distância), o nó atual envia um `ELECTION_CONCEDE` (desiste).
    4.  Se houver empate de score, o critério de desempate é o **ID do AGV** (ordem alfabética).
    5.  Um AGV só se considera vencedor quando recebe concessões de todos os vizinhos conhecidos ou quando seu tempo de candidatura expira sendo ele o melhor candidato.

### 3.3 Coordenação de Movimento e Exclusão Mútua
Para garantir que dois robôs não ocupem a mesma célula, o sistema utiliza o conceito de exclusão mútua distribuída.

*   **Estado Atual**: Atualmente, a movimentação é baseada em rotas calculadas via A*.
*   **Próximos Passos (Fase 2)**: Implementação do algoritmo de **Ricart-Agrawala** ou similar, onde cada movimento para uma nova célula do grid exige uma requisição de "lock" que deve ser aprovada pelos vizinhos que possam estar disputando o mesmo espaço, utilizando **Relógios Lógicos de Lamport** para garantir a ordenação total das requisições.

## 4. Comunicação P2P via UDP Multicast

Diferente de sistemas que usam um Broker Central (como RabbitMQ ou Mosquitto), este projeto utiliza **UDP Multicast (IP 230.0.0.1)**. 
*   **Vantagem**: Descentralização total. Não há ponto único de falha.
*   **Serialização**: As mensagens são transmitidas em formato **JSON** utilizando a biblioteca Jackson, facilitando a extensibilidade.

## 5. Como o Sistema Funciona (Fluxo de Dados)

1.  **Boot**: O nó AGV sobe, entra no grupo multicast e começa a emitir heartbeats.
2.  **Novo Pedido**: Uma mensagem `NEW_ORDER` chega à rede.
3.  **Disputa**: Os AGVs em estado `IDLE` calculam a rota e iniciam a eleição (`ELECTION_REQUEST`).
4.  **Consenso**: Através de trocas de `ELECTION_CONCEDE`, o AGV mais próximo "vence".
5.  **Execução**: O vencedor muda seu status para `MOVING`, executa o caminho e, ao final, volta para `IDLE`.
6.  **Visualização**: O `VisualizerMain` escuta todos os heartbeats e desenha o estado do grid em tempo real.

## 6. Tecnologias Utilizadas

*   **Linguagem**: Java 21+
*   **Build Tool**: Maven
*   **Grafos/Pathfinding**: JGraphT
*   **JSON**: Jackson
*   **Rede**: Java Networking (DatagramSocket, MulticastSocket)
