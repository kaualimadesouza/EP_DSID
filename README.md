# EP DSID - Coordenação Distribuída de AGVs

Simulação de uma frota de **AGVs (Automated Guided Vehicles)** operando em um sistema de estoque automatizado, com coordenação descentralizada.

## Sobre

Este projeto foi desenvolvido para a disciplina de **Sistemas de Informação Distribuídos (USP)**. O objetivo é demonstrar a aplicação de conceitos fundamentais de sistemas distribuídos.

## Documentação

Mais detalhes em **[DOCUMENTACAO.md](./DOCUMENTACAO.md)**

## Estrutura do Projeto

- `agv-core`: Lógica de negócio, modelos e casos de uso.
- `agv-adapters`: Implementações de rede (UDP), pathfinding (A\*) e interface gráfica (Swing).
- `infra`: Ponto de entrada para execução de nós e simulações.

## Execução

Baixe os arquivos disponíveis em [Releases](https://github.com/kaualimadesouza/EP_DSID/releases).
Certifique-se de ter o **Java 21+** instalado.

Existem duas formas principais de rodar a simulação:

### Opção 1: Execução Manual

1. **Visualizador (UI):**
   ```bash
   # java -jar visualizer.jar [cols] [rows]
   java -jar visualizer.jar 15 15
   ```
2. **Nós AGV:**
   Rode o seguinte programa para cada nó que deseja simular.
   ```bash
   # java -jar agv-node.jar [ID] [posX] [posY] [gridW] [gridH]
   java -jar agv-node.jar AGV-1 0 0 15 15
   ```
3. **Gerador de Pedidos:**
   ```bash
   java -jar generator.jar
   ```

### Opção 2: CLI interativa

**Requisito:** `tmux`.
Vantagens de rodar dessa forma: O script perguntará interativamente o tamanho do grid, número de AGVs, posições, etc.

1. Coloque os arquivos `agv-node.jar`, `visualizer.jar` e `generator.jar` na mesma pasta que o script `run_tui.sh`.
2. Dê permissão de execução: `chmod +x run_tui.sh`
3. Execute o script:
   ```bash
   ./run_tui.sh
   ```
