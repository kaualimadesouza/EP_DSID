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

Baixe os arquivos disponíveis em Releases aqui no Github: https://github.com/kaualimadesouza/EP_DSID/releases.
Certifique-se de ter o Java 21+ instalado.

```bash
# Para o visualizador
java -jar visualizer.jar
# Para um nó AGV (passando ID e posição inicial)
java -jar agv-node.jar AGV-1 0 0
# Para o gerador de pedidos
java -jar generator.jar
```

---
