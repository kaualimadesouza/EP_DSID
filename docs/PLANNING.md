# Coordenação Distribuída de Agentes Autônomos com Exclusão Mútua via Broker Pub/Sub

## Índice

- [Contexto do Projeto](#ep---desenvolvimento-de-sistemas-de-informação-distribuídos): disciplina, escopo e visão geral do sistema
- [Protocolo de Operação](#como-funciona): como os carrinhos se coordenam
  - [Coordenação de Movimento](#movimentação): lock de células e navegação no grid
  - [Disputa por Pedidos via Eleição](#eleição-para-atribuição-de-pedidos): como os agentes competem para atender tarefas
- [Stack Tecnológico](#tecnologias): linguagem, broker e algoritmos candidatos
- [Arquitetura do Sistema](#arquitetura): estrutura de comunicação e componentes
  - [Canais de Comunicação do Broker](#tópicos-do-broker): hierarquia de tópicos pub/sub
  - [Módulos Internos de Cada Agente](#componentes-de-cada-carrinho): diagrama de componentes
  - [Fluxo Sem Contenção](#fluxo-normal-sem-conflito): sequência quando não há disputa por célula
  - [Resolução de Conflito](#conflito-mesma-célula): sequência quando dois agentes disputam a mesma célula
  - [Eleição para Atribuição de Pedido](#eleição-distribuída-disputa-por-pedido): sequência da disputa entre candidatos
  - [Topologia Geral](#visão-geral): diagrama de visão geral do sistema
  - [Garantias do Sistema](#propriedades-garantidas): safety, liveness, ordenação e tolerância a falhas

---

## EP - Desenvolvimento de Sistemas de Informação Distribuídos

Carrinhos autônomos navegam em um grid NxN sem colisões. Cada carrinho coordena seus movimentos com os demais via um **broker pub/sub** (a definir), usando um **algoritmo de exclusão mútua distribuída** (a definir) com relógios lógicos para desempate. Além da movimentação, o sistema utiliza um mecanismo de **eleição distribuída** para determinar qual carrinho atenderá cada novo pedido que surge no grid: quando um pedido é publicado, os carrinhos disponíveis disputam entre si através de um processo de eleição, e o vencedor assume a tarefa.

> **Nota:** O broker pub/sub e o algoritmo de exclusão mútua ainda serão definidos. Candidatos em avaliação:
>
> | Decisão | Candidatos |
> |---------|-----------|
> | Broker | MQTT (Mosquitto), RabbitMQ, Redis Pub/Sub, ZeroMQ |
> | Exclusão Mútua | Ricart-Agrawala, Maekawa, Token Ring, Lamport |
> | Relógio Lógico | Lamport, Vetorial |

## Como funciona

### Movimentação

1. Carrinho quer se mover para a célula `[x,y]`
2. Publica um `lock_request` com seu timestamp lógico
3. Os outros carrinhos respondem: aprovam se não querem a mesma célula, ou cedem prioridade ao menor timestamp
4. Com todas as aprovações, o carrinho se move e libera o lock

### Eleição para atribuição de pedidos

1. Um novo pedido é publicado no tópico `pedidos/novo`, contendo a célula de origem `[x,y]` e um identificador único
2. Cada carrinho disponível calcula sua **métrica de aptidão**
3. Cada candidato publica sua métrica no tópico `pedidos/{id}/candidatura`, dentro de uma janela de tempo
4. Após a janela, cada carrinho compara as métricas recebidas. O agente com a **menor métrica** (mais apto) vence. Empate: menor ID de processo
5. O vencedor publica confirmação em `pedidos/{id}/eleito` e navega até a célula do pedido

## Tecnologias

| Componente | Tecnologia |
|---|---|
| Comunicação | Broker Pub/Sub (a definir) |
| Linguagem | A definir (Python ou Java) |
| Sincronização | Relógio Lógico (a definir) |
| Exclusão Mútua | Algoritmo distribuído (a definir) |
| Eleição Distribuída | Algoritmo baseado em métrica (distância/carga) |

## Arquitetura

### Tópicos do Broker

> Os nomes de tópicos abaixo são ilustrativos. A estrutura será adaptada ao broker escolhido.

| Tópico | Descrição |
|--------|-----------|
| `carrinhos/{id}/posicao` | Broadcast da posição atual |
| `carrinhos/{id}/heartbeat` | Sinal de vida (detecta falhas) |
| `grid/{x}/{y}/lock_request` | Pedido de reserva de célula |
| `grid/{x}/{y}/lock_response` | Aprovação/negação do lock |
| `grid/{x}/{y}/lock_release` | Liberação da célula |
| `pedidos/novo` | Novo pedido publicado no grid |
| `pedidos/{id}/candidatura` | Candidatura de um carrinho ao pedido |
| `pedidos/{id}/eleito` | Resultado da eleição (vencedor) |

### Componentes de cada carrinho

```mermaid
graph LR
    NAV["Navegação"] -->|"quer mover"| LOCK["Gerenciador de Locks"]
    LOCK -->|"incrementa"| LAMP["Relógio Lógico"]
    LOCK -->|"pub/sub"| BROKER["Cliente Broker"]
    LOCK -->|"aprovado"| NAV
    NAV -->|"atualiza"| POS["Estado (posição)"]
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
    Note over B: Não disputa [2,3]
    B->>Broker: lock_response (granted)
    Broker->>A: aprovado
    Note over A: Move para [2,3]
    A->>Broker: lock_release [2,3]
```

### Conflito (mesma célula)

Quando dois carrinhos querem a mesma célula, o menor timestamp lógico tem prioridade. O perdedor aguarda a liberação e tenta novamente.

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

    Note over B: Célula livre, tenta novamente
    B->>Broker: lock_request [3,3] (ts=14)
```

### Eleição distribuída (disputa por pedido)

Quando um novo pedido surge, os carrinhos disputam entre si para ver quem o atende. Cada um publica sua métrica de aptidão e o mais apto vence.

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

    Note over A: d=3 é menor, eu venci!
    Note over B: d=3 < d=7, A venceu
    Note over C: d=3 < d=5, A venceu

    A->>Broker: pedidos/{id}/eleito = A
    Broker->>B: eleito = A
    Broker->>C: eleito = A

    Note over A: Navega até [4,2]
```

### Visão geral

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
        A1["Navegação + Relógio Lógico + Locks + Eleição"]
    end

    subgraph B["Carrinho B"]
        B1["Navegação + Relógio Lógico + Locks + Eleição"]
    end

    subgraph C["Carrinho C"]
        C1["Navegação + Relógio Lógico + Locks + Eleição"]
    end

    A1 <-->|pub/sub| Broker
    B1 <-->|pub/sub| Broker
    C1 <-->|pub/sub| Broker
```

### Propriedades garantidas

- **Segurança (safety):** no máximo um carrinho ocupa cada célula em qualquer instante
- **Vivacidade (liveness):** todo carrinho que solicita acesso a uma célula eventualmente obtém permissão
- **Ordenação causal:** os relógios lógicos garantem uma ordenação total compatível com a causalidade dos eventos
- **Distribuição autônoma de tarefas:** o mecanismo de eleição garante que pedidos são atribuídos ao agente mais apto sem intervenção central
- **Detecção de falhas:** o mecanismo de heartbeat permite identificar agentes inativos e evitar deadlocks
