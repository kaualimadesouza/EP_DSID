#!/bin/bash

# Cores para o terminal
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # Sem Cor

SESSION_NAME="agv_system"

echo -e "${BLUE}=== AGV System TUI Manager ===${NC}"

# 1. Parametrização Inicial
# Se as variáveis já estiverem setadas (pelo agv-cli.py), use-as. Senão, pergunte.
if [ -z "$NUM_AGVS" ]; then
    read -p "Número de AGVs (default 3): " NUM_AGVS
fi
NUM_AGVS=${NUM_AGVS:-3}

if [ -z "$GRID_W" ]; then
    read -p "Largura do Grid (default 15): " GRID_W
fi
GRID_W=${GRID_W:-15}

if [ -z "$GRID_H" ]; then
    read -p "Altura do Grid (default 15): " GRID_H
fi
GRID_H=${GRID_H:-15}

# Novo: Escolha de Posicionamento
if [ -z "$AGV_POSITIONS" ]; then
    read -p "Posicionamento (A)utomático ou (M)anual? [A/m]: " POS_MODE
    if [[ $POS_MODE == "m" || $POS_MODE == "M" ]]; then
        AGV_POSITIONS=""
        echo -e "${YELLOW}Digite as posições (X,Y) para cada AGV:${NC}"
        for i in $(seq 1 $NUM_AGVS); do
            read -p "Posição do AGV-$i (ex: 2,5): " POS
            AGV_POSITIONS="$AGV_POSITIONS $POS"
        done
    fi
fi

echo -e "\n${GREEN}Configuração: ${NUM_AGVS} AGVs no Grid ${GRID_W}x${GRID_H}${NC}"

# 2. Localizar JARs
# Procura no diretório atual (.) ou no target do Maven
if [ -f "./agv-node.jar" ] && [ -f "./generator.jar" ] && [ -f "./visualizer.jar" ]; then
    JAR_DIR="."
    NEED_BUILD=false
elif [ -f "agv-system/infra/target/agv-node.jar" ]; then
    JAR_DIR="agv-system/infra/target"
    NEED_BUILD=false
else
    NEED_BUILD=true
fi

if [ "$NEED_BUILD" = true ]; then
    echo -e "${YELLOW}JARs não encontrados localmente. Tentando compilar projeto...${NC}"
    if [ -d "agv-system" ]; then
        cd agv-system && mvn clean package -DskipTests -q && cd ..
        JAR_DIR="agv-system/infra/target"
    else
        echo -e "${RED}Erro: agv-system não encontrado e JARs ausentes.${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}JARs encontrados em: $JAR_DIR${NC}"
    if [ "$JAR_DIR" == "agv-system/infra/target" ]; then
        read -p "Deseja recompilar o projeto? (y/N): " REBUILD
        if [[ $REBUILD == "y" || $REBUILD == "Y" ]]; then
            cd agv-system && mvn clean package -DskipTests -q && cd ..
        fi
    fi
fi

# 3. Encerrar sessão tmux anterior se existir
tmux kill-session -t $SESSION_NAME 2>/dev/null

# 4. Iniciar Nova Sessão Tmux
# Janela 1: Controle (Order Generator e Visualizer logs)
tmux new-session -d -s $SESSION_NAME -n "Control"

# --- Configuração Visual do Tmux (Dicas Fixas no Rodapé) ---
# Cor de fundo da barra
tmux set-option -t $SESSION_NAME status-style "bg=blue,fg=white"
# Dicas no lado direito da barra de status
tmux set-option -t $SESSION_NAME status-right-length 60
tmux set-option -t $SESSION_NAME status-right "#[fg=yellow,bold] [Ctrl+b n: Prox. Aba] [Ctrl+b d: ENCERRAR] #[default]"
# Formato das abas
tmux set-option -t $SESSION_NAME window-status-current-style "bg=yellow,fg=black,bold"
# ----------------------------------------------------------

# Painel Superior: Order Generator
tmux send-keys -t $SESSION_NAME:Control "java -jar $JAR_DIR/generator.jar" C-m

# Split horizontal para o Visualizer (no fundo)
tmux split-window -v -t $SESSION_NAME:Control
tmux send-keys -t $SESSION_NAME:Control.1 "java -jar $JAR_DIR/visualizer.jar $GRID_W $GRID_H" C-m
tmux select-pane -t $SESSION_NAME:Control.0

# Janela 2: AGV Nodes (Individual Debug)
tmux new-window -t $SESSION_NAME -n "AGV-Logs"

# Loop para subir os AGVs e dividir a tela
# Converte a string de posições em um array
read -r -a pos_array <<< "$AGV_POSITIONS"

for i in $(seq 1 $NUM_AGVS); do
    AGV_ID="AGV-$i"
    
    # Lógica de Posicionamento
    if [ ${#pos_array[@]} -gt 0 ]; then
        current_pos=${pos_array[$((i-1))]}
        # Extrai X e Y removendo a vírgula (formato x,y)
        START_X=$(echo $current_pos | cut -d',' -f1)
        START_Y=$(echo $current_pos | cut -d',' -f2)
    else
        # Posição inicial automática simples: (i-1, 0)
        START_X=$((i-1))
        START_Y=0
    fi
    
    CMD="java -jar $JAR_DIR/agv-node.jar $AGV_ID $START_X $START_Y $GRID_W $GRID_H"
    
    if [ $i -eq 1 ]; then
        # Primeiro AGV no primeiro painel da janela AGV-Logs
        tmux send-keys -t $SESSION_NAME:AGV-Logs "$CMD" C-m
    else
        # Divide o painel para o próximo AGV
        tmux split-window -v -t $SESSION_NAME:AGV-Logs
        tmux select-layout -t $SESSION_NAME:AGV-Logs tiled
        tmux send-keys -t $SESSION_NAME:AGV-Logs "$CMD" C-m
    fi
done

# Voltar para a janela de controle
tmux select-window -t $SESSION_NAME:Control

echo -e "${GREEN}Iniciando TMUX...${NC}"
echo -e "${YELLOW}Dica: 'Ctrl+b' depois 'n' para trocar de aba (Control <-> AGV-Logs)${NC}"
echo -e "${RED}Dica: 'Ctrl+b' depois 'd' para ENCERRAR TUDO (limpeza automática)${NC}"

sleep 1
tmux attach-session -t $SESSION_NAME
# Quando o usuário dá detach (Ctrl+b d), o script continua e mata a sessão
tmux kill-session -t $SESSION_NAME 2>/dev/null
echo -e "${BLUE}Sistema encerrado com sucesso.${NC}"
