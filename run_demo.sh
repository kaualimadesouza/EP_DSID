#!/bin/bash

# Acessa a pasta do projeto
cd agv-system || exit

# Cores para o terminal
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # Sem Cor

echo -e "${BLUE}=== Iniciando Simulação de AGVs ===${NC}"

# 1. Compilar o projeto
echo -e "${GREEN}[1/4] Compilando o projeto com Maven...${NC}"
mvn clean install -DskipTests -q

if [ $? -ne 0 ]; then
    echo "Erro na compilação. Abortando."
    exit 1
fi

# 2. Iniciar o Visualizador (UI)
echo -e "${GREEN}[2/4] Iniciando Interface Gráfica...${NC}"
mvn exec:java -pl infra -Dexec.mainClass="br.usp.agv.bootstrap.VisualizerMain" -q &
UI_PID=$!

# Pequena pausa para a UI subir
sleep 2

# 3. Iniciar 5 Nós AGV em diferentes posições
echo -e "${GREEN}[3/4] Subindo 5 nós AGV (AGV-1 a AGV-5)...${NC}"

# Posições iniciais: (1,7), (0,10), (7,7), (14,14), (3,4)
mvn exec:java -pl infra -Dexec.mainClass="br.usp.agv.bootstrap.AgvNodeMain" -Dexec.args="AGV-1 1 7" -q &
AGV1_PID=$!
mvn exec:java -pl infra -Dexec.mainClass="br.usp.agv.bootstrap.AgvNodeMain" -Dexec.args="AGV-2 0 10" -q &
AGV2_PID=$!
mvn exec:java -pl infra -Dexec.mainClass="br.usp.agv.bootstrap.AgvNodeMain" -Dexec.args="AGV-3 7 7" -q &
AGV3_PID=$!
mvn exec:java -pl infra -Dexec.mainClass="br.usp.agv.bootstrap.AgvNodeMain" -Dexec.args="AGV-4 14 14" -q &
AGV4_PID=$!
mvn exec:java -pl infra -Dexec.mainClass="br.usp.agv.bootstrap.AgvNodeMain" -Dexec.args="AGV-5 3 4" -q &
AGV5_PID=$!

# Função para limpar processos ao sair
cleanup() {
    echo -e "\n${BLUE}Encerrando processos...${NC}"
    kill $UI_PID $AGV1_PID $AGV2_PID $AGV3_PID $AGV4_PID $AGV5_PID 2>/dev/null
    exit
}
trap cleanup SIGINT

echo -e "${BLUE}=== Sistema rodando! ===${NC}"
echo -e "${GREEN}[4/4] Iniciando Gerador de Pedidos (Interativo)...${NC}"
echo "Digite /new_order <px> <py> <dx> <dy> no terminal para criar pedidos."

# Mantemos o Gerador em primeiro plano para o usuário interagir
mvn exec:java -pl infra -Dexec.mainClass="br.usp.agv.bootstrap.OrderGeneratorMain" -q

# Ao sair do gerador (ou se ele for interrompido), chama o cleanup
cleanup
