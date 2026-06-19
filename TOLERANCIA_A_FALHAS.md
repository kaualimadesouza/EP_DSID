# Tolerância a Falhas — Coordenação Distribuída de AGVs

> Documento de análise das propriedades de _dependability_ (confiabilidade) do sistema.
> As respostas distinguem **(A) o objetivo de projeto** — o que faz sentido tolerar dado o domínio — de **(B) o que a implementação atual realmente garante**, com referências ao código (`agv-core`).

---

## 1. Disponibilidade ou confiabilidade?

**Confiabilidade (correção/consistência) é mais importante que disponibilidade — e o design já tomou esse lado.**

O sistema controla **robôs físicos que se movem e podem colidir**. Os modos de falha não são simétricos:

| Tipo de falha | Consequência | Reversível? |
|---|---|---|
| **Confiabilidade** (dois AGVs no mesmo pedido, dois carrinhos na mesma célula, nós divergindo sobre rotas) | Colisão / dano físico | ❌ Não |
| **Disponibilidade** (sistema pausa a atribuição por alguns segundos) | Espera | ✅ Sim |

Quando um lado da falha quebra o mundo real e o outro só causa atraso, prioriza-se evitar o primeiro.

**O design confirma essa escolha** — é uma arquitetura **CP** (no teorema CAP), que prefere bloquear a progredir de forma inconsistente:

- **Multicast de Ordenação Total**: existe para garantir que todos os nós vejam a *mesma* sequência de tarefas (consistência), não para maximizar throughput.
- **Execução só após acordo unânime**: `executeBatch` só dispara quando os ACKs cobrem **todos** os pares do lote — `acks.containsAll(requiredPeers)` (`BatchAssignmentUseCase.java:121`). Esperar todo mundo concordar é literalmente trocar disponibilidade por consistência.
- **Determinismo local no leilão**: todos rodam o mesmo algoritmo sobre o mesmo `snapshot` para nunca divergirem na atribuição (`getBestAgvId`, `BatchAssignmentUseCase.java:158`).

> **Nuance:** o sistema *recupera* a disponibilidade após falhas (re-eleição de líder, limpeza de peers mortos), mas durante a janela de incerteza ele **pausa** em vez de arriscar uma decisão errada. Se isto fosse um feed de recomendação, a resposta seria a oposta — mas para um sistema ciber-físico, confiabilidade vem primeiro.

---

## 2. Quais tipos de falha deseja-se tolerar?

### Objetivo de projeto
Por ser uma simulação acadêmica de SD com nós homogêneos numa rede confiável (multicast local), o alvo realista é o **modelo crash-stop (fail-stop)**: um AGV pode simplesmente parar (processo morto, desligado), e o sistema deve continuar coordenando os demais.

### O que a implementação realmente tolera

| Tipo de falha | Tolerado? | Evidência / Justificativa |
|---|---|---|
| **Crash** (parada silenciosa) | ⚠️ **Parcial** | Detectado por timeout de heartbeat para fins de **eleição** e **futuros lotes** (`cleanDeadPeers`, `:47`). **Mas** um crash *durante* um lote em andamento o trava para sempre (ver §3). |
| **Omissão** (perda de mensagem) | ❌ **Não** | UDP é _fire-and-forget_, sem ACK/retransmissão (`UdpMessageBusAdapter.broadcast`, `:57`). Perda de heartbeat é mascarada pela **redundância periódica** (1 Hz vs. timeout de 10 s ⇒ ~10 chances). Mas perda de `BATCH_PROPOSAL`/`BATCH_ACK`/`NEW_ORDER` **não** é recuperada → trava o lote ou perde o pedido. |
| **Temporal** (resposta fora do prazo) | ❌ **Não** | Modelo 100% baseado em timeout numa rede assíncrona. Um nó lento (pausa de GC, atraso de rede) por >10 s é **falsamente declarado morto** (_false suspicion_). Não há limites de tempo garantidos nem reentrada segura do nó suspeito. |
| **Resposta** (computação/valor incorreto) | ❌ **Não** | Baseia-se em "determinismo local": assume que todos os nós computam idêntico. Se um nó tem bug/versão diferente, ele diverge **silenciosamente** — não há verificação cruzada do resultado do leilão. |
| **Bizantina** (malicioso/arbitrário) | ❌ **Não** | Sem autenticação nem assinatura. Qualquer um no grupo multicast pode forjar qualquer `agvId` em JSON aberto (`230.0.0.1:4446`). Mensagens são confiadas cegamente. |

**Resumo:** o sistema *mira* tolerância a **crash**, e tolera bem a perda esporádica de heartbeat (por redundância). Não trata omissão de mensagens de controle, falhas temporais, de resposta nem bizantinas.

---

## 3. Quantos processos falhantes serão suportados?

A resposta depende de **qual mecanismo** se olha — e a divergência é o achado mais importante desta análise.

### Descoberta / Eleição de líder → tolera até **N − 1** falhas
A eleição é implícita pelo menor ID sobre a lista de peers vivos (`isLeader`, `:54`). Se todos menos um caírem, o sobrevivente vira líder e segue operando. Robusto.

### Consenso de lote (atribuição de pedidos) → tolera **f = 0** na janela crítica
Este é o ponto crítico. A execução exige **ACK de _todos_** os pares fotografados no `snapshot` do lote:

```java
// BatchAssignmentUseCase.java:120-124
Set<String> requiredPeers = batch.agvStates().keySet();
if (acks.containsAll(requiredPeers)) {   // unânime: N-de-N
    executeBatch(batch);
}
```

Não há **timeout de ACK**, **retransmissão** nem **remoção do par morto do lote pendente** (o `cleanDeadPeers` limpa `activePeers`, mas o conjunto exigido está *congelado* em `batch.agvStates()`). Consequência:

> Se **um único** AGV do snapshot cair (ou tiver seu `BATCH_ACK` perdido) entre a proposta e o acordo, aquele lote **nunca executa** — fica preso em `proposedBatches` para sempre. Pior: os pedidos já foram marcados em `processedOrders` (`:105`), então **não são re-propostos**. Os pedidos somem.

Ou seja, **o mecanismo de consenso, hoje, não tolera nenhuma falha durante seu ciclo** — é um quórum unânime, não majoritário.

### Comparação com sistemas de quórum clássicos

| Abordagem | Falhas toleradas (f) | Este projeto |
|---|---|---|
| Quórum majoritário (Paxos/Raft) | `f < N/2` | ❌ não usa |
| Tolerância bizantina (PBFT) | `f < N/3` | ❌ não usa |
| **Acordo unânime (atual)** | **`f = 0`** durante o lote | ✅ é o que está implementado |

**Recomendação:** para realmente tolerar `f ≥ 1` crash, trocar o "ACK de todos" por **(a)** um timeout de ACK que reabre os pedidos não-confirmados para um novo lote, e **(b)** um quórum majoritário `⌊N/2⌋ + 1` em vez de unanimidade.

---

## 4. Qual a estratégia para detectar falhas?

**Detector de falhas por _heartbeat_ + _timeout_ (push-based), do tipo "eventualmente perfeito" (◇P).**

### Como funciona (no código)

1. **Emissão** — cada AGV transmite um `HEARTBEAT` via multicast a **1 Hz**:
   ```java
   // AgvController.java:38-39
   broadcaster.broadcastHeartbeat();
   Thread.sleep(1000); // Heartbeat a cada 1Hz
   ```
2. **Registro** — ao receber, o par é marcado vivo com carimbo de tempo `lastSeen = now`:
   ```java
   // BatchAssignmentUseCase.java:43-45  +  AgvSnapshot.java:4-6
   activePeers.put(peerId, new AgvSnapshot(peerId, position, status)); // lastSeen = System.currentTimeMillis()
   ```
3. **Suspeita** — uma varredura a cada **5 s** remove quem não dá sinal há mais de **10 s**:
   ```java
   // BatchAssignmentUseCase.java:40, 47-52
   scheduler.scheduleAtFixedRate(this::cleanDeadPeers, 5, 5, TimeUnit.SECONDS);
   ...
   long timeout = 10000; // 10 s sem heartbeat
   activePeers.entrySet().removeIf(e -> (now - e.getValue().lastSeen()) > timeout);
   ```
4. **Reação** — sair de `activePeers` automaticamente: (i) muda o resultado de `isLeader()` (re-eleição implícita), e (ii) reduz o conjunto de pares para os *próximos* lotes.

### Parâmetros
| Parâmetro | Valor | Efeito |
|---|---|---|
| Intervalo de heartbeat | 1 s | Frequência do sinal de vida |
| Período de varredura | 5 s | Granularidade da detecção |
| Timeout de suspeita | 10 s | Tolera ~10 heartbeats perdidos antes de declarar morto |

### Propriedades e limitações
- **Mascaramento de omissão:** a folga 1 s ↔ 10 s torna a detecção robusta a perdas esporádicas de heartbeat (precisaria perder ~10 seguidos).
- **Completude (eventual):** um nó morto *será* detectado em ≤ ~15 s (timeout + próxima varredura).
- **Acurácia imperfeita:** num sistema assíncrono não há limite de atraso garantido → um nó vivo mas lento por >10 s é **falsamente suspeito**. Em sistemas distribuídos é impossível um detector ser ao mesmo tempo completo e perfeitamente acurado (FLP/Chandra–Toueg); este escolhe completude.
- **Lacuna de reintegração:** o código remove peers mortos, mas **não há tratamento explícito** para um nó suspeito que volta — ele simplesmente reaparece no próximo heartbeat, o que pode causar inconsistência se isso ocorrer no meio de um lote.

---

## Síntese

| # | Pergunta | Resposta curta |
|---|---|---|
| 1 | Disponibilidade ou confiabilidade? | **Confiabilidade** — sistema ciber-físico, design é **CP**. |
| 2 | Tipos de falha a tolerar | Alvo: **crash**. Hoje: crash (parcial) + omissão de heartbeat (por redundância). Não trata temporal, resposta nem bizantina. |
| 3 | Quantos processos falhantes | Eleição: até **N−1**. Consenso de lote: **f = 0** (ACK unânime sem timeout — limitação a corrigir). |
| 4 | Estratégia de detecção | **Heartbeat 1 Hz + timeout de 10 s** (detector ◇P, push-based, eventualmente perfeito). |
