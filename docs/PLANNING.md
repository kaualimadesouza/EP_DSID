# Coordenacao Distribuida de Agentes Autonomos com Exclusao Mutua via Broker Pub/Sub

## EP - Desenvolvimento de Sistemas de Informacao Distribuidos

Carrinhos autonomos navegam em um grid NxN sem colisoes. Cada carrinho coordena seus movimentos com os demais via um **broker pub/sub** (a definir), usando um **algoritmo de exclusao mutua distribuida** (a definir) com relogios logicos para desempate. Alem da movimentacao, o sistema utiliza um mecanismo de **eleicao distribuida** para determinar qual carrinho atendera cada novo pedido que surge no grid: quando um pedido e publicado, os carrinhos disponiveis disputam entre si atraves de um processo de eleicao, e o vencedor assume a tarefa.

> **Nota:** O broker pub/sub e o algoritmo de exclusao mutua ainda serao definidos. Candidatos em avaliacao:
>
> | Decisao | Candidatos |
> |---------|-----------|
> | Broker | MQTT (Mosquitto), RabbitMQ, Redis Pub/Sub, ZeroMQ |
> | Exclusao Mutua | Ricart-Agrawala, Maekawa, Token Ring, Lamport |
> | Relogio Logico | Lamport, Vetorial |
>
> Os exemplos abaixo usam MQTT e Ricart-Agrawala como ilustracao, mas a arquitetura e independente dessas escolhas.

## Como funciona

### Movimentacao

1. Carrinho quer se mover para a celula `[x,y]`
2. Publica um `lock_request` com seu timestamp logico
3. Os outros carrinhos respondem: aprovam se nao querem a mesma celula, ou cedem prioridade ao menor timestamp
4. Com todas as aprovacoes, o carrinho se move e libera o lock

### Eleicao para atribuicao de pedidos

1. Um novo pedido e publicado no topico `pedidos/novo`, contendo a celula de origem `[x,y]` e um identificador unico
2. Cada carrinho disponivel calcula sua **metrica de aptidao** (ex: distancia Manhattan ate o pedido, carga atual, ou combinacao ponderada)
3. Cada candidato publica sua metrica no topico `pedidos/{id}/candidatura`, dentro de uma janela de tempo
4. Apos a janela, cada carrinho compara as metricas recebidas. O agente com a **menor metrica** (mais apto) vence. Empate: menor ID de processo
5. O vencedor publica confirmacao em `pedidos/{id}/eleito` e navega ate a celula do pedido

## Tecnologias

| Componente | Tecnologia |
|---|---|
| Comunicacao | Broker Pub/Sub (a definir) |
| Linguagem | Python |
| Sincronizacao | Relogio Logico (a definir) |
| Exclusao Mutua | Algoritmo distribuido (a definir) |
| Eleicao Distribuida | Algoritmo baseado em metrica (distancia/carga) |

## Arquitetura

### Topicos do Broker

> Os nomes de topicos abaixo sao ilustrativos (estilo MQTT). A estrutura sera adaptada ao broker escolhido.

| Topico | Descricao |
|--------|-----------|
| `carrinhos/{id}/posicao` | Broadcast da posicao atual |
| `carrinhos/{id}/heartbeat` | Sinal de vida (detecta falhas) |
| `grid/{x}/{y}/lock_request` | Pedido de reserva de celula |
| `grid/{x}/{y}/lock_response` | Aprovacao/negacao do lock |
| `grid/{x}/{y}/lock_release` | Liberacao da celula |
| `pedidos/novo` | Novo pedido publicado no grid |
| `pedidos/{id}/candidatura` | Candidatura de um carrinho ao pedido |
| `pedidos/{id}/eleito` | Resultado da eleicao (vencedor) |

### Componentes de cada carrinho

```mermaid
graph LR
    NAV["Navegacao"] -->|"quer mover"| LOCK["Gerenciador de Locks"]
    LOCK -->|"incrementa"| LAMP["Relogio Logico"]
    LOCK -->|"pub/sub"| BROKER["Cliente Broker"]
    LOCK -->|"aprovado"| NAV
    NAV -->|"atualiza"| POS["Estado (posicao)"]
    POS -->|"publica"| BROKER
```

### Fluxo normal (sem conflito)

```mermaid
sequenceDiagram
    participant A as Carrinho A
    participant Broker as Broker Pub/Sub
    participant B as Carrinho B

    A->>Broker: lock_request [2,3] (ts=5)
    Broker->>B: lock_request de A
    Note over B: Nao quer [2,3]
    B->>Broker: lock_response (granted)
    Broker->>A: aprovado
    Note over A: Move para [2,3]
    A->>Broker: lock_release [2,3]
```

### Conflito (mesma celula)

Quando dois carrinhos querem a mesma celula, o menor timestamp logico tem prioridade. O perdedor aguarda a liberacao e tenta novamente.

```mermaid
sequenceDiagram
    participant A as Carrinho A
    participant Broker as Broker Pub/Sub
    participant B as Carrinho B

    A->>Broker: lock_request [3,3] (ts=10)
    B->>Broker: lock_request [3,3] (ts=12)

    Note over B: ts=12 > ts=10, A tem prioridade
    B->>Broker: lock_response para A (granted)

    Broker->>A: aprovado
    Note over A: Move para [3,3]
    A->>Broker: lock_release [3,3]

    Note over B: Celula livre, tenta novamente
    B->>Broker: lock_request [3,3] (ts=14)
```

### Eleicao distribuida (disputa por pedido)

Quando um novo pedido surge, os carrinhos disputam entre si para ver quem o atende. Cada um publica sua metrica de aptidao e o mais apto vence.

```mermaid
sequenceDiagram
    participant A as Carrinho A
    participant Broker as Broker Pub/Sub
    participant B as Carrinho B
    participant C as Carrinho C

    Note over Broker: Novo pedido em [4,2]
    Broker->>A: pedidos/novo [4,2]
    Broker->>B: pedidos/novo [4,2]
    Broker->>C: pedidos/novo [4,2]

    Note over A: dist=3
    Note over B: dist=7
    Note over C: dist=5

    A->>Broker: candidatura (d=3)
    B->>Broker: candidatura (d=7)
    C->>Broker: candidatura (d=5)

    Broker->>A: broadcast candidaturas
    Broker->>B: broadcast candidaturas
    Broker->>C: broadcast candidaturas

    Note over A: d=3 e menor, eu venci!
    Note over B: d=3 < d=7, A venceu
    Note over C: d=3 < d=5, A venceu

    A->>Broker: pedidos/{id}/eleito = A
    Broker->>B: eleito = A
    Broker->>C: eleito = A

    Note over A: Navega ate [4,2]
```

### Visao geral

```mermaid
graph TB
    subgraph Broker["Broker Pub/Sub (a definir)"]
        T1["carrinhos/{id}/posicao"]
        T2["grid/{x}/{y}/lock_*"]
        T3["carrinhos/{id}/heartbeat"]
        T4["pedidos/novo"]
        T5["pedidos/{id}/candidatura"]
        T6["pedidos/{id}/eleito"]
    end

    subgraph A["Carrinho A"]
        A1["Navegacao + Relogio Logico + Locks + Eleicao"]
    end

    subgraph B["Carrinho B"]
        B1["Navegacao + Relogio Logico + Locks + Eleicao"]
    end

    subgraph C["Carrinho C"]
        C1["Navegacao + Relogio Logico + Locks + Eleicao"]
    end

    A1 <-->|pub/sub| Broker
    B1 <-->|pub/sub| Broker
    C1 <-->|pub/sub| Broker
```

### Propriedades garantidas

- **Seguranca (safety):** no maximo um carrinho ocupa cada celula em qualquer instante
- **Vivacidade (liveness):** todo carrinho que solicita acesso a uma celula eventualmente obtem permissao
- **Ordenacao causal:** os relogios logicos garantem uma ordenacao total compativel com a causalidade dos eventos
- **Distribuicao autonoma de tarefas:** o mecanismo de eleicao garante que pedidos sao atribuidos ao agente mais apto sem intervencao central
- **Deteccao de falhas:** o mecanismo de heartbeat permite identificar agentes inativos e evitar deadlocks
