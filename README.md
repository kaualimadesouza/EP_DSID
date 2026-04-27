# EP DSID - Coordenação Distribuída de AGVs

Simulação de uma frota de **AGVs (Automated Guided Vehicles)** operando em um sistema de estoque automatizado, com coordenação descentralizada.

## Sobre

Este projeto foi desenvolvido para a disciplina de **Sistemas de Informação Distribuídos (USP)**. O objetivo é demonstrar a aplicação de conceitos fundamentais de sistemas distribuídos.

## Documentação

Mais detalhes em **[DOCUMENTACAO.md](./DOCUMENTACAO.md)** ou na forma de [slides](https://kaualimadesouza.github.io/EP_DSID).

## Estrutura do Projeto

- `agv-core`: Lógica de negócio, modelos e casos de uso.
- `agv-adapters`: Implementações de rede (UDP), pathfinding (A\*) e interface gráfica (Swing).
- `infra`: Ponto de entrada para execução de nós e simulações.

## Execução

Baixe o arquivo `agv-system-all.jar` disponível em [Releases](https://github.com/kaualimadesouza/EP_DSID/releases).
Certifique-se de ter o **Java 21+** instalado.

### Como rodar

O sistema é consolidado em um único executável que gerencia todos os componentes:

```bash
java -jar agv-system-all.jar
```

**Configuração:**
Ao iniciar, o programa perguntará interativamente:

- Número de AGVs desejados.
- Dimensões do Grid (Largura x Altura).
- Modo de posicionamento (Automático ou Manual).

**Comandos:**

Após a inicialização, você pode digitar comandos diretamente no console para gerar pedidos:

- `/random_orders <n>`: Gera *n* pedidos aleatórios.
- `/new_order <px> <py> <dx> <dy>`: Cria um pedido específico (pickup -> delivery).
- `exit`: Encerra todo o sistema e fecha os processos.
