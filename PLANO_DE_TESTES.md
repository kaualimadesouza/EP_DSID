# Plano de Teste

## Configuração

1. Encerrar processos pendentes:
   ```bash
   killall -9 java
   ```

2. Iniciar o programa:
   ```bash
   java -jar infra/target/agv-system-all.jar
   ```

3. Configurar os parametros:
   * **Número de AGVs**: `2`
   * **Largura do Grid**: `5`
   * **Altura do Grid**: `5`
   * **Posicionamento**: `m` (Manual)
   * **Posição do AGV-1**: `0,0` (Canto inferior esquerdo)
   * **Posição do AGV-2**: `4,4` (Canto superior direito)

---

## Caso 1: Proximidade e Concorrência

Este teste garante que as ordens são delegadas para o robô fisicamente mais próximo e que ambos executam tarefas concorrentemente.

### Passos:
1. No console do `GENERATOR` (onde você digita comandos), insira os dois comandos a seguir:
   ```text
   /new_order 1 1 0 2
   /new_order 3 3 4 2
   ```

### Resultados Esperados:
* **AGV-1** (inicialmente em `0,0`) deve receber a tarefa `(1,1) -> (0,2)` por proximidade.
* **AGV-2** (inicialmente em `4,4`) deve receber a tarefa `(3,3) -> (4,2)` por proximidade.
* Ambos os robôs devem se mover simultaneamente em direção aos seus respectivos alvos no Visualizador.
* Ao final das entregas, o painel do Visualizador deve indicar os dois pedidos como concluídos e os AGVs devem parar e voltar ao status `IDLE`.

---

## Caso 2: Prevenção de Colisão e Exclusão Mútua (NAO IMPLEMENTADO)

Este teste valida se o planejador de rotas evita colisões de forma segura quando os caminhos dos dois robôs se cruzam no centro do grid.

### Passos:
1. Garanta que ambos os robôs estejam parados (`IDLE`).
2. Digite no console do `GENERATOR`:
   ```text
   /new_order 0 4 4 0
   /new_order 4 0 0 4
   ```

### Resultados Esperados:
* Os caminhos dos robôs se cruzarão na diagonal central.
* Um dos AGVs reservará a rota primeiro. O outro aguardará ou desviará da célula reservada (exclusão mútua do SRM).
* Os robôs **não podem ocupar o mesmo quadrado** no Visualizador em nenhum momento.
* Ambos devem completar suas rotas sem travamentos/deadlock.

---

## Caso 3: Tolerância a Falhas e Recuperação

Este teste valida a eleição automática e a recuperação de tarefas inacabadas quando um líder cai durante a entrega.

### Passos:
1. Identifique qual robô assumiu a liderança (exibido na barra superior ou na lateral como `Líder Ativo: AGV-X`). Suponha para este exemplo que o líder ativo seja o **AGV-2**.
2. Digite no console do `GENERATOR` dois pedidos que vão ocupar os dois robôs:
   ```text
   /new_order 1 2 1 4
   /new_order 3 2 3 0
   ```
3. Enquanto os robôs estão se movendo na tela, abra um terminal separado e **mate o processo do líder** (ex: AGV-2):
   ```bash
   # Lista os processos Java de AGV ativos
   ps aux | grep AgvNodeMain
   
   # Mate o processo do AGV correspondente ao líder (ex: se for AGV-2)
   kill -9 <PID_DO_AGV_2>
   ```

### Resultados Esperados:
* O robô sobrevivente (**AGV-1**) detectará a ausência de batimentos cardíacos do líder após 10 segundos.
* O terminal do **AGV-1** exibirá: `[LÍDER] Líder AGV-2 caiu. Iniciando eleição...`.
* O **AGV-1** assumirá a liderança e se autodeclarará Coordenador Primário.
* O **AGV-1** detectará que o **AGV-2** tinha uma tarefa pendente em execução (`ORD-XXXX`).
* O terminal exibirá: `[ÓRFÃOS] Recuperando tarefa órfã de AGV-2 para retransmissão`.
* Após concluir seu próprio pedido atual, o **AGV-1** se deslocará automaticamente para coletar e entregar o pedido que pertencia ao falecido **AGV-2**, concluindo com sucesso a tarefa pendente no sistema.
