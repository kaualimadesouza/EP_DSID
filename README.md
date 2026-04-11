# EP DSID - Coordenação Distribuída de AGVs

Simulação de uma frota de **AGVs (Automated Guided Vehicles)** operando em um sistema de estoque automatizado, com coordenação 100% descentralizada.

## 🚀 Sobre o Projeto

Este projeto foi desenvolvido para a disciplina de **Sistemas de Informação Distribuídos (USP)**. O objetivo é demonstrar a aplicação de conceitos fundamentais de sistemas distribuídos, como:

- **Comunicação P2P** via UDP Multicast.
- **Descoberta Dinâmica** de nós via Heartbeats.
- **Eleição Distribuída** baseada em métricas de aptidão.
- **Exclusão Mútua** para evitar colisões em um grid compartilhado.
- **Arquitetura Hexagonal** para separação de preocupações.

## 🛠️ Documentação Técnica

Para detalhes sobre decisões arquiteturais, algoritmos utilizados e funcionamento interno, consulte o arquivo:
👉 **[DOCUMENTACAO.md](./DOCUMENTACAO.md)**

## 📦 Estrutura do Projeto

- `agv-core`: Lógica de negócio, modelos e casos de uso.
- `agv-adapters`: Implementações de rede (UDP), pathfinding (A\*) e interface gráfica (Swing).
- `infra`: Ponto de entrada para execução de nós e simulações.

## 🚦 Como Executar

Baixe os arquivos disponíveis em [Releases](https://github.com/kaualimadesouza/EP_DSID/releases).
Certifique-se de ter o **Java 21+** instalado.

Existem duas formas principais de rodar a simulação:

### Opção 1: Interface Gerenciada (TUI - Recomendado)
Ideal para desenvolvedores e testers que desejam uma visão unificada e depuração fácil.
**Requisito:** `tmux` instalado (nativo no Linux/macOS).

1. Coloque os arquivos `agv-node.jar`, `visualizer.jar` e `generator.jar` na mesma pasta que o script `run_tui.sh`.
2. Dê permissão de execução: `chmod +x run_tui.sh`
3. Execute o script:
   ```bash
   ./run_tui.sh
   ```
   *O script perguntará interativamente o número de AGVs e o tamanho do grid.*

### Opção 2: Execução Manual Individual
Ideal para rodar cada componente em terminais separados.

1. **Visualizador (UI):**
   ```bash
   # java -jar visualizer.jar [cols] [rows]
   java -jar visualizer.jar 15 15
   ```
2. **Nós AGV (Repita para cada nó):**
   ```bash
   # java -jar agv-node.jar [ID] [posX] [posY] [gridW] [gridH]
   java -jar agv-node.jar AGV-1 0 0 15 15
   ```
3. **Gerador de Pedidos (Interativo):**
   ```bash
   java -jar generator.jar
   ```
   *No terminal do gerador, digite `/new_order <px> <py> <dx> <dy>` para criar ordens.*

---
