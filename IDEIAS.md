# Lista de ideias

- Pedidos com prioridade. Prioridade alta do pedido reduz o peso (incentiva participação)

- pode receber pedido enquanto ta andano?
- lsita de prioridade
- tem que esperar os 5 pedidos se tiver 1 livre ou da pra pegar so 1?
- O que acontece se alguem falhar o calculo?

    2. Se o AGV caído for um WORKER no meio da rota (Órfão)
       Aqui temos uma limitação da versão atual (v0.2.0): a tarefa não é absorvida automaticamente por outro par se o robô cair após ter assumido o compromisso.
    * O Problema: Quando um AGV emite um ROUTE_CLAIMED, o Gerador de Pedidos (que atua como o middleware de persistência) entende que o pedido foi "atendido" e o remove da fila de
      retransmissão.
    * Consequência: Se o AGV "morrer" com a carga no meio do caminho, o pedido fica num estado de limbo (órfão). Os outros robôs saberão que aquele AGV morreu (via timeout de heartbeat), mas
      na lógica atual, eles não "roubam" a tarefa que já estava em curso.

  Melhoria Teórica Necessária:
  Para resolver isso sob a ótica de sistemas distribuídos, o protocolo de consistência precisaria ser alterado para:
    * Confirmação Tardia: O Gerador de Pedidos (MOM) só deveria remover o pedido da memória ao receber um ORDER_COMPLETED, e não no ROUTE_CLAIMED.
    * Redesignação: Se o líder detectar que um AGV que tinha uma tarefa pendente morreu, ele deveria reinjetar esse pedido no próximo BATCH_PROPOSAL para que outro par o assuma.


lider: sera o menor id dos que estao DISPONIVEIS
servidor: gera pedidos e BROADCAST para TODOS os AGVs (ele nao sabe quem eh lider)

para que lider: todos os AGVs recebem pedidos, porem em ordem diferente ou pode haver perdas.
Ha um lider (menor ID DISPONIVEL), ou seja, os AGVs confiam APENAS no lider para separacao do LOTE

Tolerancia a falhas
1. a-
2. 
