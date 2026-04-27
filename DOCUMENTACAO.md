# EP DSID - Coordenação Distribuída de AGVs

- Isabelle
- Kauã
- Kevin
- Victor

<!-- slide -->

## Sobre o projeto

O objetivo deste projeto é simular uma frota de robôs autônomos que operam em um armazém sem a necessidade de um servidor central de controle. Os robôs devem:

1.  **Descobrir** outros robôs na rede automaticamente.
2.  **Disputar** tarefas (pedidos de coleta) de forma eficiente.
3.  **Navegar** pelo armazém, garantindo a coleta e entrega de pedidos e um ponto A a um ponto B do armazém.

**Componentes do sistema**
1. AGV 
2. Coordenador de Pedidos: Responsável por enviar pedidos

Os carrinhos receberão pedidos do Cordenador de Pedidos via broadcast. Porém, precisamos que eles concordem em quais pedidos processar por vez, quem será encarregado de cada pedido

<!-- slide -->

### Objetivos
- Explorar algoritmos de ordenação distribuída e eleição para que os carrinhos processem a mesma lista de pedidos
- Reduzir necessidade de um processamento central e aumentar a escalabilidade, com todos os carrinhos possuindo o mesmo código e lógica de peer.

<!-- slide -->

## Etapas do projeto

- **Etapa 0**: Discussão inicial (abstrações)
- **Etapa 1**: Coordenação de pedidos (eleição)
- **Etapa 2**: Sistema de colisão
- **Etapa 3**: Otimizações

<!-- slide -->

## Perguntas

**Por que pub/sub é relevante para o projeto? O broker parece um coordenador geral que funciona por broadcast (ou seja, parece have tópicos e inscrições)**

R: Por agora, o grupo decidiu seguir por um outro caminho, adotando integralmente a estrutura peer-to-peer com mensagens via broadcast. Deixamos o pub/sub de lado ao perceber que a estrutura P2P já contemplaria os nossos problemas para as etapas 1 e 2, além de simplificar a implementação, visto que todos os nós já se conhecem, e o orquestrador de pedidos necessariamente conhece todos os nós da rede (por definição). Mais detalhes pode ser encontrado na seção [Discussão inicial (definição do problema e abstrações)](##discussão-inicial-definição-do-problema-e-abstrações)

<!-- slide -->

**Há algum motivo para o carrinho se mover fora atender a um pedido?**

R: Na fase 1 (coordenação), os carrinhos só se movem para atender a pedidos. Na fase 2 (colisões), os carrinhos podem se mover para desviar de uma rota de colisão (movimento fora da rota do pedido).

<!-- slide -->

**"Ordenação causal: os relógios lógicos garantem uma ordenação total compatível com a causalidade dos eventos" => os relógio lógicos por si só não garantem por si só. Para ordenação causal é preciso relógio vetorial**

R: Entendemos, obrigado pelo feedback! Após discussões, o sistema evoluiu de uma proposta inicial baseada em relógios lógicos para uma implementação de Multicast de Ordenação Total (Total Ordering Multicast, ou Atomic Multicast). No nosso novo modelo:
  1. O líder agrupa as ordens em lotes (Batch), garantindo uma sequência única para o sistema.
  2. Utilizamos um mecanismo de confirmações (Proposal/Ack) para que todos os nós concordem com o conteúdo e a ordem do lote antes de iniciarem os cálculos.
  3. Como o estado é compatilhado por todos os nós, eles executam o mesmo algoritmo de atribuição de rotas, o que os leva a chegar nas mesmas conclusões sobre quais carrinhos devem ser responsáveis por quais pedidos, sem trocas adicionais de mensagens.

Como garantimos a ordenação total das tarefas, a ordenação causal é preservada.

<!-- slide -->

**Como pretendem simular a movimentação dos carrinhos?**

R: Mapearemos o armazém em uma grade de posições.

- A **posição** de um carrinho será definida por um ponto nessa grade, ou matriz (p. ex. `(2,3)`).
- Uma **rota** é definida por uma sequência de pontos (p. ex. `{ (2,3), (2,2), (2,1) }`).
- Um carrinho se movimenta quando sua posição atual se atualiza para uma nova posição.
  - Na simulação, cada carrinho terá uma Thread que percorrerá as posições da rota, atualizando a posição do carrinho para a próxima posição dentro da rota em um tempo fixo (p. ex. **300ms**).
- Não é permitido se movimentar na diagonal (p. ex. `(2,3) -> (1,4)`)

**"no máximo um carrinho ocupa cada célula em qualquer instante" => Isso quer dizer que a cada movimento unitário (para um local adjacente no grid) é preciso solicitar o deslocamento?**

R: Por enquanto, o grupo discutiu várias possibilidades para solucionar o problema de colisão. Porém, como isso não será abordado na etapa 1 do projeto, vamos resguardar essas discussões para depois. Uma introdução ao problema pode ser encontrado na seção [Sistema de colisão](#sistema-de-colisão)

<!-- slide -->

## Discussão inicial (definição do problema e abstrações)

Para esse EP, iremos definir um ambiente virtual que irá simular um sistema de controle de estoque utilizando AGVs, de forma que eles se comuniquem entre si para definir a coleta de pedidos, sem necessidade de um orquestrador central. Para isso, precisamos abstrair alguns componentes do mundo real para o mundo virtual.

<!-- slide -->

- **AGV (Automated Guided Vehicle)**: Na simulação, cada AGV é representado por um nó independente na rede com um identificador único. Ele possui estado interno (posição `(x, y)` e status como `IDLE` ou `MOVING`) e é responsável por calcular suas próprias rotas e participar do consenso para atribuição de tarefas.
- **Mundo (Grid)**: O armazém é abstraído como uma grade bidimensional (matriz) de células. O sistema utiliza um grafo onde cada célula é um vértice e as conexões entre células adjacentes (norte, sul, leste, oeste) são arestas. Obstáculos estáticos (prateleiras) e dinâmicos (outros AGVs) tornam células temporariamente inacessíveis.
- **Pedido (Order)**: Um pedido é composto por um identificador único, um ponto de coleta (**pickup**) e um ponto de entrega (**delivery**). Os pedidos são injetados no sistema por um "Gerador de Pedidos" via broadcast.
- **Lote (Batch)**: Para garantir a ordenação total, os pedidos não são processados individualmente assim que chegam, mas agrupados em lotes pelo líder. Um lote contém uma lista de pedidos e um snapshot (estado) da posição de todos os AGVs ativos, servindo como a unidade de sincronização do sistema.
- **Comunicação P2P**: Não há servidor central. Toda a troca de informações (batimentos cardíacos, propostas de lotes, confirmações e reservas de rota) ocorre via UDP Multicast, simulando um ambiente onde todos os nós podem ouvir uns aos outros diretamente.

<!-- slide -->

## Coordenação de pedidos (eleição) [EM PROGRESSO]

Em vez de um coordenador central, os AGVs utilizam um protocolo de **Multicast de Ordenação Total** para garantir que todos os nós processem a mesma sequência de tarefas.

### Eleição do Líder (Sequenciador)
O sistema utiliza uma eleição implícita baseada no identificador único de cada AGV. 
- **Critério**: O AGV ativo com o **menor ID alfanumérico** assume o papel de líder (sequenciador).
- **Descoberta**: Através de mensagens de `HEARTBEAT` via UDP Multicast, cada nó mantém uma lista de `activePeers`. Se um nó percebe que seu ID é o menor da lista atual, ele se comporta como líder.
- **Tolerância a Falhas**: Se o líder parar de enviar heartbeats por mais de 10 segundos, ele é removido da lista de pares ativos e o próximo nó com o menor ID assume a liderança.

<!-- slide -->

### Fluxo de Atribuição
Para evitar que dois AGVs tentem coletar o mesmo pedido ou que a ordem dos pedidos seja vista de forma diferente, utilizamos um ciclo de três fases:

1. **Proposta (BATCH_PROPOSAL)**: O líder agrupa novos pedidos recebidos em um objeto `Batch`. Este objeto contém não apenas os pedidos, mas também um **Snapshot** (um estado das posições e status de todos os AGVs naquele instante).
2. **Acordo (BATCH_ACK)**: Todos os AGVs recebem a proposta e respondem com um ACK, garantindo a sincronia do estado global.
3. **Execução Síncrona**: Uma vez que o líder recebe ACKs de todos os pares conhecidos, ele (e todos os outros nós) executa a função de atribuição localmente.

<!-- slide -->

### Leilão
Com o estado sincronizado entre os AGVs ativos, podemos assumir um **Determinismo Local** na execução do algorítmo de atribuíção de rotas:

- Como todos os nós possuem o mesmo `Batch` (pedidos + snapshot de posições), todos executam o mesmo algoritmo:
  - Para cada pedido no lote:
    - Calcula-se a **Distância de Manhattan** de todos os AGVs `IDLE` até o ponto de coleta.
    - O AGV com a menor distância é "vencedor". Em caso de empate, o menor ID vence.
    - O estado desse AGV é atualizado virtualmente para `MOVING` para o próximo pedido do mesmo lote.
- **Resultado**: Como os dados de entrada e o algoritmo são idênticos em todos os robôs, eles chegam à mesma conclusão de quem deve pegar qual pedido sem precisar trocar mais nenhuma mensagem.

<!-- slide -->

## Sistema de colisão [TO-DO]

A princípio, definiremos obstáculos estáticos e dinâmicos na simulação:

- **Obstáculos estáticos:** Paredes, pedidos (de outros carrinhos), estantes do armazém. Esses obstáculos fazem parte do grid, que é a abstração do espaço físico do armazém. Isso é mapeado e conhecido pelos carrinhos, portanto seus algoritmos de geração de rotas (A*, entre outros), naturalmente irão evitar esses pontos de colisão.
- **Obstáculos dinâmicos:** Outros carrinhos. Isso introduz um problema maior, que discutiremos a seguir.

Nessa etapa, introduziremos uma nova regra: **no máximo um carrinho ocupa cada célula em qualquer instante**.

Pergunta: **Isso quer dizer que a cada movimento unitário (para um local adjacente no grid) é preciso solicitar o deslocamento?**

R: Não necessariamente. O grupo pensou em duas possibilidades iniciais, que definiremos melhor no momento de implementação dessa etapa.

<!-- slide -->

- **Opção 1:** Sim, os carrinhos irão solicitar deslocamento para cada movimento unitário. Terá um mecanismo de espera e sincronização de mensagens para cada movimento, que irá exigir uma grande quantidade de mensagens trocadas por rede.
- **Opção 2:** Não, os carrinhos irão se deslocar livremente, e solicitar deslocamento apenas em pontos estratégicos. Isso funcionaria da seguinte forma:
  - Cada carrinho já calcula as rotas atribuídas para seus pares, devido à natureza do algoritmo de ordenação total.
  - Cada carrinho guarda em memória as rotas correntes e quem é o responsável por ela.
  - O carrinho solicita o deslocamento se, e somente se, deseja se deslocar para uma posição pertencente à rota existente e corrente de um de seus pares. Do contrário, ele pode se deslocar livremente (assume-se que não haverá outros carrinhos, já que suas rotas são conhecidas). Isso reduz significativamente a troca de mensagens por rede, mas aumenta a necessidade de armazenamento em memória nos carrinhos.

<!-- slide -->

## Otimizações [TO-DO]

A ser definido.

<!-- slide -->

## Arquitetura do Sistema

Optamos por implementar a **Arquitetura Hexagonal (Ports & Adapters)**, de forma que a lógica de negócio seja independente de detalhes técnicos como protocolos de rede ou bibliotecas de interface gráfica.

Dessa forma, podemos ter um foco maior no algoritmo distribuído e como seus componentes interagem. A implementação concreta dessa lógica fica flexível.

<!-- slide -->

## Módulos Principais

- **`agv-core`**: Onde se localiza as classes principais e algoritmos distribuídos relevantes.
  - **Models**: AGV, Posição, Rota, Pedido, Mensagem.
  - **Use Cases**: Implementam os algoritmos distribuídos (Eleição, Movimentação).
  - **Ports**: Interfaces que definem como se comunicar com o mundo exterior (MessageBusPort, PathfinderPort).
- **`agv-adapters`**: Implementações concretas.
  - **Messaging**: `UdpMessageBusAdapter` utiliza **UDP Multicast** para comunicação P2P.
  - **Pathfinding**: `AStarPathfinderAdapter` utiliza o algoritmo **A\*** para cálculo de rotas.
  - **UI**: `SwingVisualizerAdapter` para visualização em tempo real.
- **`infra`**: Módulos integradores.
  - Ambiente de simulação, visualizador, programa do nó AGV individual

<!-- slide -->

## Tecnologias Utilizadas

- **Linguagem**: Java 21+
- **Build Tool**: Maven
- **Grafos/Pathfinding**: JGraphT
- **JSON**: Jackson
- **Rede**: Java Networking (DatagramSocket, MulticastSocket)
