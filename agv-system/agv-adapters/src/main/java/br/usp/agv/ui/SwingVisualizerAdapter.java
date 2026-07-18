package br.usp.agv.ui;

import br.usp.agv.model.Agv;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;
import br.usp.agv.ports.outbound.WorldObserverPort;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador de Saída que renderiza o estado do sistema usando Swing com interpolação suave.
 */
public class SwingVisualizerAdapter extends JFrame implements WorldObserverPort {

    private final int rows;
    private final int cols;
    private final int cellSize = 40;
    private final int offsetX = 45; 
    private final int offsetY = 45; 
    private final int statusWidth = 320;
    
    // Todo acesso a agvs/orders/activeRoutes é sincronizado em `this` (lock estável), nunca
    // nos próprios campos ja que onSystemStateChanged reassina era agvs/orders para uma nova lista
    private List<Agv> agvs = new ArrayList<>();
    private List<Order> orders = new ArrayList<>();
    private final Map<String, br.usp.agv.model.Route> activeRoutes = new HashMap<>();
    
    private final Map<String, Point2D.Double> visualPositions = new HashMap<>();
    private final float lerpFactor = 0.15f; 

    private final JTextPane statusPane;
    private long lastStatusUpdate = 0;
    private volatile String activeLeaderId = null;

    public SwingVisualizerAdapter(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        
        setTitle("Sistema de AGVs - USP DSID");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        JPanel gridPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                
                drawGrid(g2);
                synchronized (this) {
                    drawOrders(g2);
                }
                synchronized (this) {
                    drawRoutes(g2);
                }
                synchronized (this) {
                    drawAgvs(g2);
                }
            }
        };
        gridPanel.setBackground(Color.WHITE);
        gridPanel.setPreferredSize(new Dimension(cols * cellSize + offsetX + 30, rows * cellSize + offsetY + 30));
        
        statusPane = new JTextPane();
        statusPane.setEditable(false);
        statusPane.setContentType("text/html");
        statusPane.setEditorKit(new HTMLEditorKit());
        statusPane.setBackground(new Color(250, 250, 250));
        
        JScrollPane scrollPane = new JScrollPane(statusPane);
        scrollPane.setPreferredSize(new Dimension(statusWidth, 0));
        scrollPane.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY));
        
        add(gridPanel, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.EAST);
        
        pack();
        setLocationRelativeTo(null);
        
        Timer timer = new Timer(16, e -> {
            try {
                synchronized (this) {
                    updateVisualPositions();
                }
                
                // Atualiza o texto de status apenas a cada 100ms para performance
                long now = System.currentTimeMillis();
                if (now - lastStatusUpdate > 100) {
                    updateStatusHtml();
                    lastStatusUpdate = now;
                }
                
                repaint();
            } catch (Exception ex) {
                // Previne crash da UI
            }
        });
        timer.start();
        
        setVisible(true);
    }

    private void updateVisualPositions() {
        for (Agv agv : agvs) {
            String id = agv.getAgvId();
            Position target = agv.getCurrentPosition();
            if (target == null) continue;
            
            double targetX = target.y() * cellSize + offsetX;
            double targetY = target.x() * cellSize + offsetY;
            
            Point2D.Double current = visualPositions.computeIfAbsent(id, 
                k -> new Point2D.Double(targetX, targetY));
            
            current.x += (targetX - current.x) * lerpFactor;
            current.y += (targetY - current.y) * lerpFactor;
        }
    }

    private void updateStatusHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: sans-serif; padding: 10px; color: #2c3e50;'>");
        
        sb.append("<div style='background-color: #34495e; color: white; padding: 10px; border-radius: 5px;'>");
        sb.append("<h2 style='margin: 0; font-size: 16px;'>Monitor de Sistema</h2>");
        sb.append("<span style='font-size: 10px; opacity: 0.8;'>Grade: ").append(rows).append("x").append(cols).append("</span>");
        if (activeLeaderId != null) {
            String leaderName = br.usp.agv.model.Agv.getStaticNameFromId(activeLeaderId);
            sb.append("<br><span style='font-size: 11px; color: #f1c40f;'>Líder Ativo: <b>").append(leaderName).append("</b></span>");
        } else {
            sb.append("<br><span style='font-size: 11px; color: #bdc3c7;'>Líder Ativo: <b>Nenhum</b></span>");
        }
        sb.append("</div><br>");
        
        sb.append("<b style='font-size: 13px; color: #7f8c8d;'>AGVs ATIVOS</b><hr>");
        synchronized (this) {
            if (agvs.isEmpty()) {
                sb.append("<i style='color: #bdc3c7;'>Aguardando conexão...</i>");
            }
            for (Agv agv : agvs) {
                Position pos = agv.getCurrentPosition();
                String posStr = (pos != null) ? String.format("(%d, %d)", pos.x(), pos.y()) : "---";
                
                String statusColor = switch(agv.getStatus()) {
                    case IDLE -> "#95a5a6";
                    case MOVING -> "#3498db";
                    case ELECTING -> "#e67e22";
                    case OFFLINE -> "#e74c3c";
                    case FAIL_SAFE -> "#e74c3c";
                    default -> "#2c3e50";
                };

                sb.append("<div style='margin-bottom: 8px; border-left: 4px solid ").append(statusColor).append("; padding-left: 8px;'>");
                sb.append("<b style='color: #2c3e50;'>").append(agv.getAgvId()).append("</b><br>");
                sb.append("<code style='font-size: 11px; background: #ecf0f1; padding: 2px 4px;'>POS: ").append(posStr).append("</code><br>");
                sb.append("<span style='font-size: 11px;'>Status: <b style='color:").append(statusColor).append(";'>")
                  .append(agv.getStatus()).append("</b></span>");
                
                if (agv.getCurrentOrder() != null) {
                    String shortId = agv.getCurrentOrder().orderId();
                    shortId = shortId.substring(0, Math.min(8, shortId.length()));
                    sb.append("<br><span style='font-size: 11px; color: #27ae60;'>Pedido: #").append(shortId).append("</span>");
                }
                sb.append("</div>");
            }
        }
        
        sb.append("<br><b style='font-size: 13px; color: #7f8c8d;'>FILA DE PEDIDOS</b><hr>");
        synchronized (this) {
            if (orders.isEmpty()) {
                sb.append("<i style='color: #bdc3c7; font-size: 11px;'>Nenhum pedido pendente</i>");
            } else {
                for (Order o : orders) {
                    String shortId = o.orderId().substring(0, Math.min(6, o.orderId().length()));
                    sb.append("<div style='font-size: 11px; margin-bottom: 4px;'>");
                    sb.append("<b style='color: #16a085;'>#").append(shortId).append("</b>: ");
                    sb.append("<code style='color: #d35400;'>").append(o.pickup()).append("</code> &rarr; ");
                    sb.append("<code style='color: #2980b9;'>").append(o.delivery()).append("</code>");
                    sb.append("</div>");
                }
            }
        }
        
        sb.append("</body></html>");
        
        String newHtml = sb.toString();
        // Evita re-renderizar se nada mudou
        if (!newHtml.equals(statusPane.getText())) {
            statusPane.setText(newHtml);
        }
    }

    private void drawAgvs(Graphics2D g) {
        for (Agv agv : agvs) {
            Point2D.Double p = visualPositions.get(agv.getAgvId());
            if (p == null) continue;
            
            boolean isLeader = agv.getAgvId().equals(activeLeaderId);
            boolean isOffline = agv.getStatus() == br.usp.agv.model.AgvStatus.OFFLINE;
            
            if (isLeader && !isOffline) {
                // Desenha uma borda dourada / coroa de brilho
                g.setColor(new Color(241, 196, 15, 80)); // Amarelo dourado semitransparente
                g.fillOval((int)p.x + 4, (int)p.y + 4, cellSize - 8, cellSize - 8);
            }
            
            // Desenha o corpo do AGV
            if (isOffline) {
                g.setColor(new Color(189, 195, 199, 120)); // Cinza claro semi-transparente
            } else {
                g.setColor(new Color(52, 152, 219)); 
            }
            g.fillOval((int)p.x + 8, (int)p.y + 8, cellSize - 16, cellSize - 16);
            
            if (isOffline) {
                g.setColor(new Color(149, 165, 166, 120));
            } else if (isLeader) {
                g.setColor(new Color(241, 196, 15)); // Borda dourada
            } else {
                g.setColor(new Color(41, 128, 185));
            }
            g.setStroke(new BasicStroke(2));
            g.drawOval((int)p.x + 8, (int)p.y + 8, cellSize - 16, cellSize - 16);
            
            // Desenha o "Nickname" acima do AGV (usa o nome estático curto para caber na tela)
            String id = br.usp.agv.model.Agv.getStaticNameFromId(agv.getAgvId());
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int textX = (int)p.x + (cellSize - fm.stringWidth(id)) / 2;
            int textY = (int)p.y + 5; // Posicionado acima do círculo
            
            // Sombra leve para legibilidade
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(id, textX + 1, textY + 1);
            
            // Texto principal
            if (isOffline) {
                g.setColor(new Color(127, 143, 144, 150));
            } else {
                g.setColor(new Color(44, 62, 80));
            }
            g.drawString(id, textX, textY);
        }
    }

    private void drawRoutes(Graphics2D g) {
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        for (Map.Entry<String, br.usp.agv.model.Route> entry : activeRoutes.entrySet()) {
            String agvId = entry.getKey();
            br.usp.agv.model.Route route = entry.getValue();
            if (route == null || route.waypoints().isEmpty()) continue;
            
            Agv currentAgv = agvs.stream()
                .filter(a -> a.getAgvId().equals(agvId))
                .findFirst()
                .orElse(null);
            
            Position currentPos = (currentAgv != null) ? currentAgv.getCurrentPosition() : null;
            
            int startIndex = 0;
            if (currentPos != null) {
                for (int i = 0; i < route.waypoints().size(); i++) {
                    if (route.waypoints().get(i).equals(currentPos)) {
                        startIndex = i;
                        break;
                    }
                }
            }

            g.setColor(new Color(41, 128, 185, 100)); // Cor azul semi-transparente uniforme para todas as rotas
            
            for (int i = startIndex; i < route.waypoints().size() - 1; i++) {
                Position from = route.waypoints().get(i);
                Position to = route.waypoints().get(i + 1);
                g.drawLine(
                    from.y() * cellSize + cellSize / 2 + offsetX, from.x() * cellSize + cellSize / 2 + offsetY,
                    to.y() * cellSize + cellSize / 2 + offsetX, to.x() * cellSize + cellSize / 2 + offsetY
                );
            }
        }
    }

    private void drawGrid(Graphics2D g) {
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        
        for (int i = 0; i <= rows; i++) {
            int y = i * cellSize + offsetY;
            g.setColor(new Color(235, 235, 235));
            g.drawLine(offsetX, y, cols * cellSize + offsetX, y);
            
            if (i < rows) {
                g.setColor(new Color(127, 140, 141));
                g.drawString(String.format("%2d", i), offsetX - 30, y + cellSize / 2 + 5);
            }
        }
        
        for (int j = 0; j <= cols; j++) {
            int x = j * cellSize + offsetX;
            g.setColor(new Color(235, 235, 235));
            g.drawLine(x, offsetY, x, rows * cellSize + offsetY);
            
            if (j < cols) {
                g.setColor(new Color(127, 140, 141));
                g.drawString(String.valueOf(j), x + cellSize / 2 - 5, offsetY - 15);
            }
        }
    }

    private void drawOrders(Graphics2D g) {
        for (Order order : orders) {
            g.setColor(new Color(46, 204, 113, 150));
            Position p = order.pickup();
            int px = p.y() * cellSize + 10 + offsetX;
            int py = p.x() * cellSize + 10 + offsetY;
            g.fillOval(px, py, cellSize - 20, cellSize - 20);
            g.setColor(new Color(39, 174, 96));
            g.drawOval(px, py, cellSize - 20, cellSize - 20);
            
            g.setColor(new Color(231, 76, 60));
            Position d = order.delivery();
            int dx = d.y() * cellSize + 8 + offsetX;
            int dy = d.x() * cellSize + 8 + offsetY;
            int size = cellSize - 16;
            g.setStroke(new BasicStroke(2));
            g.drawRect(dx, dy, size, size);
            g.drawLine(dx, dy, dx + size, dy + size);
            g.drawLine(dx + size, dy, dx, dy + size);
        }
    }

    @Override
    public void onAgvMoved(String agvId, Position newPosition) {
        repaint();
    }

    @Override
    public void onOrderCreated(Order order) {
        synchronized (this) {
            this.orders.add(order);
        }
        repaint();
    }

    @Override
    public void onRouteCalculated(String agvId, br.usp.agv.model.Route route) {
        synchronized (this) {
            this.activeRoutes.put(agvId, route);
        }
        repaint();
    }

    @Override
    public void onRouteReleased(String agvId) {
        synchronized (this) {
            this.activeRoutes.remove(agvId);
        }
        repaint();
    }

    @Override
    public void onOrderCompleted(String orderId) {
        synchronized (this) {
            this.orders.removeIf(o -> o.orderId().equals(orderId));
        }
        repaint();
    }

    @Override
    public void onSystemStateChanged(List<Agv> allAgvs, List<Order> pendingOrders) {
        synchronized (this) {
            this.agvs = new ArrayList<>(allAgvs);
        }
        synchronized (this) {
            this.orders = new ArrayList<>(pendingOrders);
        }
        repaint();
    }

    @Override
    public void onLeaderChanged(String leaderId) {
        synchronized (this) {
            this.activeLeaderId = leaderId;
        }
        repaint();
    }
}
