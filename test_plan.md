# Plano de Testes — Sistema de Coordenação Distribuída de AGVs

Roteiro para rodar o sistema e demonstrá-lo cobrindo cada requisito da `Especificacao_Grupo15.pdf`. Pensado para ser seguido ao vivo (banca/apresentação) ou por qualquer pessoa do grupo antes de uma entrega.

## 0. Pré-requisitos

- Java 21+ e Maven instalados (`java -version`, `mvn -version`)
- Linux (para o teste opcional de perda de pacote com `tc`/`iproute2` — os demais passos funcionam em qualquer SO)
- Terminal grande o suficiente para ver a janela do Visualizador ao lado do terminal

## 1. Build (roda os testes automatizados também)

```bash
cd agv-system
mvn clean package
```

Isso compila os três módulos (`agv-core`, `agv-adapters`, `infra`) **e executa os testes JUnit** (`BatchAssignmentUseCaseTest`, `AgvBroadcasterTest`) como parte do build — se algum deles falhar, o build para. Ao final, o executável fica em:

```
agv-system/infra/target/agv-system-all.jar
```

Se só quiser rodar os testes sem gerar o jar: `mvn test`.

## 2. Subindo o sistema

```bash
java -jar agv-system/infra/target/agv-system-all.jar
```

O programa pergunta interativamente:
- Número de AGVs (sugestão para a demo: **3**)
- Dimensões do grid (sugestão: **10x10**, grande o suficiente para dar tempo de ver o movimento, pequeno o suficiente para caber na tela)
- Modo de posicionamento: **Automático** é o mais simples para começar

Isso abre a janela do **Visualizador** e um terminal único onde os logs de cada componente aparecem coloridos por origem (`[AGV-1]`, `[AGV-2]`, `[GENERATOR]`, `[VISUALIZER]`). O mesmo terminal vira o console do Gerador de Pedidos — os comandos abaixo são digitados ali.

**Comandos disponíveis no console:**
| Comando | Efeito |
|---|---|
| `/random_orders <n>` | Gera *n* pedidos com pickup/delivery aleatórios |
| `/new_order <px> <py> <dx> <dy>` | Cria um pedido específico |
| `/multi_order <p1x> <p1y> <d1x> <d1y> [...]` | Cria vários pedidos específicos de uma vez |
| `/dump` (ou `/mem`) | Pede que **todos** os AGVs ativos imprimam seu estado interno (líder atual, peers, filas) — ótimo para inspecionar sem depender só do visual |
| `/clear` | Limpa a fila de retransmissão do gerador |
| `exit` / `quit` | Encerra todos os processos |

---

## 3. Roteiro de demonstração

Siga na ordem — cada passo cobre um requisito específico da especificação.

### Passo 1 — Descoberta Dinâmica de Peers (RF1)
Suba o sistema com 3 AGVs. **Observe**: dentro de 1-2s a janela do Visualizador mostra os 3 círculos nas posições iniciais, e o painel lateral "AGVs Ativos" lista os três com status `IDLE`. Isso confirma que cada AGV descobriu os outros via heartbeat multicast, sem nenhuma configuração manual de endereço.

### Passo 2 — Distribuição de Tarefas + Lote/ACK + Alocação Determinística (RF2, RF3, RF4)
No console, digite:
```
/random_orders 3
```
**Observe**: no terminal, logs tipo `recebeu atribuição para ORD-...` aparecem em **apenas um** AGV por pedido — o mais próximo do ponto de coleta (distância de Manhattan). No Visualizador, marcadores verdes (pickup) e vermelhos (delivery, em X) aparecem no grid, com uma linha azul semitransparente mostrando a rota calculada pelo A*.

### Passo 3 — Simulação de Deslocamento (RF5)
**Observe** (continuação do passo 2): o círculo do AGV designado se move suavemente célula a célula ao longo da linha da rota até o pickup, pausa (simulando carregamento), depois segue até o delivery. O status no painel lateral muda para `MOVING` durante o trajeto e volta a `IDLE` ao concluir.

### Passo 4 — Eleição Bully + Recuperação de Tarefa Órfã (RF6, RF7)
1. Identifique o líder atual: no Visualizador, o AGV com o anel dourado ao redor é o líder (o painel lateral também mostra "Líder Ativo").
2. Dispare um pedido para forçar o líder (ou outro AGV) a começar a se mover:
   ```
   /new_order 0 0 9 9
   ```
3. Enquanto o AGV designado está em rota (`MOVING`), derrube o processo dele à força:
   ```bash
   jps -l | grep AgvNodeMain     # identifica o PID de cada nó AGV
   kill -9 <PID_DO_AGV_ESCOLHIDO>
   ```
4. **Observe**:
   - Depois de ~10s sem heartbeat, os AGVs restantes removem o peer morto (log `Peer ... removido por inatividade`).
   - Se o morto era o **líder**: dentro de alguns segundos ocorre uma eleição Bully (`ELEIÇÃO` nos logs) e um novo líder assume — o anel dourado migra no Visualizador.
   - Se o morto tinha um pedido em andamento: o líder detecta a tarefa órfã e a reinjeta num novo lote (`ÓRFÃOS: Recuperando tarefa órfã...`) — outro AGV assume a entrega, visível como uma nova rota sendo calculada para o mesmo pedido.

### Passo 5 — Safety / Fail-Safe (seção XI da especificação)
Requer Linux com `iproute2` (`tc`). Com pelo menos um AGV em `MOVING`:
```bash
sudo tc qdisc add dev lo root netem loss 100%
```
**Observe**: em até 6s, o(s) AGV(s) em movimento mudam de status para `FAIL_SAFE` (vermelho no Visualizador) e param fisicamente de andar — mesmo sem receber nenhum "comando de parar", a ausência de heartbeats de outros peers já é suficiente. Reverta a regra:
```bash
sudo tc qdisc del dev lo root netem
```
**Observe**: dentro de poucos segundos os AGVs voltam a `MOVING`/`IDLE` normalmente (log `Conectividade restabelecida`).

> ⚠️ Isso corta a rede de **todos** os processos ao mesmo tempo (todos rodam via loopback). É uma demonstração válida e direta do requisito de Safety, só não isola um único nó.

### Passo 6 — Confiabilidade de Rede / SRM sob perda de pacote (NFR1)
Mesma técnica do passo 5, mas com uma perda **parcial** em vez de total, para observar o protocolo se recuperando em vez de travar:
```bash
sudo tc qdisc add dev lo root netem loss 25%
/random_orders 5
sudo tc qdisc del dev lo root netem   # reverter ao final
```
**Observe**: mesmo com 25% de perda simulada no nível de rede, os pedidos ainda são todos processados corretamente (podem demorar um pouco mais) — o protocolo SRM (sequência + NACK com retry) se encarrega de detectar e pedir retransmissão das mensagens perdidas em vez de deixar o sistema divergir ou travar.

### Passo 7 (opcional/bônus) — Escala do Bully com 10+ AGVs
Suba o sistema de novo com **11 AGVs**. Derrube o líder (passo 4). **Observe** nos logs que a comparação de liderança é numérica: `AGV-10` vence `AGV-9` quando ambos estão ativos (não lexicográfica, onde "AGV-10" perderia de "AGV-9" por comparação de caractere a caractere).

---

## 4. Ferramenta de apoio: inspecionar o estado interno

A qualquer momento durante a demonstração, digite `/dump` no console — cada AGV ativo imprime seu líder conhecido, posição, status, peers ativos, fila de pedidos pendentes e atribuições ativas. Útil para responder perguntas da banca sobre o estado interno sem depender só do que é visível no Visualizador.

## 5. Encerrando

```
exit
```
Encerra e mata todos os processos filhos (AGVs, Gerador, Visualizador) de uma vez.

---

## Checklist de cobertura (especificação → passo do roteiro)

- [ ] RF1 — Descoberta Dinâmica de Peers → Passo 1
- [ ] RF2 — Distribuição de Tarefas → Passo 2
- [ ] RF3 — Proposta e Sincronia de Lotes → Passo 2
- [ ] RF4 — Alocação Determinística (leilão) → Passo 2
- [ ] RF5 — Simulação de Deslocamento → Passo 3
- [ ] RF6 — Recuperação de Tarefas Órfãs → Passo 4
- [ ] RF7 — Eleição Ativa (Bully) → Passo 4
- [ ] NFR1 — Confiabilidade de Rede (SRM) → Passo 6
- [ ] NFR2 — Arquitetura Descentralizada (P2P) → implícito em todos os passos (nenhum processo é "servidor de robôs")
- [ ] NFR3 — Consistência Sequencial → Passo 2 (todos os AGVs chegam à mesma decisão de leilão a partir do mesmo snapshot)
- [ ] Safety (Fail-Safe) → Passo 5
- [ ] Tolerância a falhas / Crash-Stop → Passo 4
