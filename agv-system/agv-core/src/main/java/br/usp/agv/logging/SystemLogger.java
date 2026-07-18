package br.usp.agv.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SystemLogger {
    private static PrintWriter fileWriter;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    static {
        try {
            fileWriter = new PrintWriter(new FileWriter("agv-system.log", true), true);
        } catch (IOException e) {
            System.err.println("Falha ao inicializar arquivo de log: " + e.getMessage());
        }
    }

    public static synchronized void debug(String tag, String message) {
        log("DEBUG", tag, message, false);
    }

    public static synchronized void info(String tag, String message, boolean printToConsole) {
        log("INFO", tag, message, printToConsole);
    }

    public static synchronized void error(String tag, String message, Throwable t) {
        log("ERROR", tag, message, true);
        if (t != null) {
            if (fileWriter != null) {
                t.printStackTrace(fileWriter);
            }
            t.printStackTrace(System.err);
        }
    }

    private static void log(String level, String tag, String message, boolean printToConsole) {
        String timestamp = dateFormat.format(new Date());
        String formatted = String.format("%s [%s] [%s] %s", timestamp, level, tag, message);
        
        if (fileWriter != null) {
            fileWriter.println(formatted);
        }
        
        if (printToConsole) {
            System.out.print("\r\033[K"); 
            System.out.println("[" + tag + "] " + message);
            System.out.print("> "); 
            System.out.flush();
        }
    }
}
