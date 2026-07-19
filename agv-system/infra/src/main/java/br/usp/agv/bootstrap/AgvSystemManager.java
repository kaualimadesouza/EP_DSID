package br.usp.agv.bootstrap;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

// Orquestrador para a execução de múltiplos processos (Nodes, Gerador e Visualizador), ponto de entrada do projeto
public class AgvSystemManager {
    private static final List<Process> processes = new ArrayList<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Cores ANSI
        String BLUE = "\033[0;34m";
        String GREEN = "\033[0;32m";
        String YELLOW = "\033[1;33m";
        String RESET = "\033[0m";

        System.out.println(BLUE + "=== AGV System Java Manager (Cross-Platform) ===" + RESET);

        // parametrização
        System.out.print("Número de AGVs (default 3): ");
        String numAgvInput = sc.nextLine();
        int numAgvs = numAgvInput.isEmpty() ? 3 : Integer.parseInt(numAgvInput);

        System.out.print("Largura do Grid (default 15): ");
        String gridWInput = sc.nextLine();
        int gridW = gridWInput.isEmpty() ? 15 : Integer.parseInt(gridWInput);

        System.out.print("Altura do Grid (default 15): ");
        String gridHInput = sc.nextLine();
        int gridH = gridHInput.isEmpty() ? 15 : Integer.parseInt(gridHInput);

        System.out.print("Posicionamento (A)utomático ou (M)anual? [A/m]: ");
        String posMode = sc.nextLine();
        List<String> positions = new ArrayList<>();
        if (posMode.equalsIgnoreCase("m")) {
            for (int i = 1; i <= numAgvs; i++) {
                System.out.print("Posição do AGV-" + i + " (ex: 2,5): ");
                positions.add(sc.nextLine());
            }
        }

        // Setup de Execução
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        
        // Tenta descobrir o caminho do próprio JAR ou o classpath atual
        String classpath = System.getProperty("java.class.path");
        try {
            String jarPath = AgvSystemManager.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            if (jarPath != null && jarPath.endsWith(".jar")) {
                classpath = jarPath;
            }
        } catch (Exception ignored) {}

        System.out.println(GREEN + "Iniciando componentes..." + RESET);

        // Executa Visualizador
        launch(javaBin, classpath, "br.usp.agv.bootstrap.VisualizerMain", "VISUALIZER", String.valueOf(gridW), String.valueOf(gridH));

        // Executa Gerador
        Process generatorProcess = launch(javaBin, classpath, "br.usp.agv.bootstrap.OrderGeneratorMain", "GENERATOR", String.valueOf(gridW), String.valueOf(gridH));
        
        // Dispara processos para cada AGV Node
        for (int i = 1; i <= numAgvs; i++) {
            String id = "AGV-" + i;
            String x, y;
            if (positions.size() >= i && positions.get(i-1).contains(",")) {
                String[] parts = positions.get(i - 1).split(",");
                x = parts[0].trim();
                y = parts[1].trim();
            } else {
                x = String.valueOf((i - 1) % gridW);
                y = String.valueOf(((i - 1) / gridW) % gridH);
            }
            launch(javaBin, classpath, "br.usp.agv.bootstrap.AgvNodeMain", id, id, x, y, String.valueOf(gridW), String.valueOf(gridH));
        }

        System.out.println(GREEN + "\nSistema iniciado com sucesso!" + RESET);
        System.out.println(YELLOW + "Comandos disponíveis (ex: /random_orders 5, /new_order 0 0 10 10)" + RESET);
        System.out.println(YELLOW + "Digite 'exit' para encerrar o sistema.\n" + RESET);

        // Adiciona limpeza de processos se o usuário der Ctrl+C (evita processos fantasmas apos parar o orquestrador)
        Runtime.getRuntime().addShutdownHook(new Thread(AgvSystemManager::shutdown));

        // Loop de controle: input do console para o Gerador
        if (generatorProcess != null) {
            try (PrintWriter genIn = new PrintWriter(generatorProcess.getOutputStream(), true)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                        break;
                    }
                    genIn.println(line);
                }
            }
        }

        shutdown();
    }

    private static Process launch(String javaBin, String cp, String mainClass, String label, String... args) {
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(cp);
        command.add(mainClass);
        Collections.addAll(command, args);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            processes.add(p);

            // Consumir saídas em threads separadas para não bloquear
            executor.submit(() -> pipeStream(p.getInputStream(), System.out, label));
            executor.submit(() -> pipeStream(p.getErrorStream(), System.err, label));

            return p;
        } catch (IOException e) {
            System.err.println("Erro ao lançar " + label + ": " + e.getMessage());
            return null;
        }
    }

    private static void pipeStream(InputStream in, PrintStream out, String label) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            String color = getColorForLabel(label);
            String RESET = "\033[0m";
            while ((line = br.readLine()) != null) {
                out.println(color + "[" + label + "]" + RESET + " " + line);
            }
        } catch (IOException ignored) {}
    }

    private static String getColorForLabel(String label) {
        if (label.equals("GENERATOR")) return "\033[0;33m"; // Amarelo
        if (label.equals("VISUALIZER")) return "\033[0;36m"; // Ciano
        if (label.startsWith("AGV")) {
            try {
                int i = Integer.parseInt(label.substring(4));
                String[] colors = {"\033[0;32m", "\033[0;35m", "\033[0;31m", "\033[1;32m", "\033[1;35m"};
                return colors[(i-1) % colors.length];
            } catch (Exception e) { return "\033[0;32m"; }
        }
        return "";
    }

    private static void shutdown() {
        System.out.println("\n\033[0;34mEncerrando processos e limpando sistema...\033[0m");
        for (Process p : processes) {
            if (p.isAlive()) {
                p.destroy();
            }
        }
        executor.shutdownNow();
    }
}
